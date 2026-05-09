/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.github.barriosnahuel.vossosunboton.BuildConfig
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal interface ShareFeature {
    /**
     * @param surface canonical screen_name from `CanonicalScreenName` describing where the share originated.
     * @return `null` when the chooser launched, otherwise an `@StringRes` id with a user-facing
     *         feedback message that the caller is expected to surface (Snackbar/Toast). Every
     *         non-null return is paired with a non-fatal recorded via `Tracker.track`.
     */
    suspend fun share(
        context: Context,
        sound: Sound,
        surface: String,
    ): Int?

    companion object {
        val instance: ShareFeature by lazy { ShareFeatureImpl() }
    }
}

private class ShareFeatureImpl : ShareFeature {
    private val authority = BuildConfig.APPLICATION_ID + ".fileprovider"

    /**
     * For more info check
     * [developer.android.com/training/secure-file-sharing/setup-sharing](https://developer.android.com/training/secure-file-sharing/setup-sharing)
     */
    override suspend fun share(
        context: Context,
        sound: Sound,
        surface: String,
    ): Int? {
        Timber.d("Trying to share button: %s", sound.name)

        // Resolving the file path and (for bundled sounds, first share only) copying raw resources to disk
        // both touch the file system. Run that part on IO; startActivity + analytics stay on the caller's
        // Main dispatcher because Android's chooser expects the launching call on the UI thread.
        val resolution = withContext(Dispatchers.IO) { resolveContentUri(context, sound) }
        return when (resolution) {
            is ShareOutcome.Failure -> resolution.feedback
            is ShareOutcome.Success -> launchChooserAndTrack(context, sound, resolution.value, surface)
        }
    }

    private fun launchChooserAndTrack(
        context: Context,
        sound: Sound,
        contentUri: Uri,
        surface: String,
    ): Int? {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "audio/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        Timber.d(
            "Starting disambiguation window for: %s: contentUri=%s; URI=%s; rawResId=%s;",
            sound.name,
            contentUri,
            sound.file,
            sound.rawRes,
        )

        // Track AFTER the chooser actually launches: if `startActivity` throws (ActivityNotFoundException, OS reject)
        // we want `lifetime_shares` to stay accurate. If the chooser shows but the user cancels there is no reliable
        // callback, so "chooser displayed" remains the canonical share signal — matches Firebase's recommended event.
        try {
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.app_share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            Tracker.track(RuntimeException("No activity available to handle audio share intent", e))
            return R.string.app_share_feedback_no_app_for_audio
        }

        val tracker = AnalyticsTrackerProvider.get(context.applicationContext)
        tracker.log(AnalyticsEvent.Share(surface = surface))
        val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_SHARES)
        tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_SHARES, newCount.toString())
        return null
    }

    private fun resolveContentUri(
        context: Context,
        sound: Sound,
    ): ShareOutcome<Uri> =
        when (val fileResolution = resolveFileForSharing(context, sound)) {
            is ShareOutcome.Failure -> fileResolution
            is ShareOutcome.Success -> wrapInFileProviderUri(context, sound, fileResolution.value)
        }

    private fun wrapInFileProviderUri(
        context: Context,
        sound: Sound,
        file: File,
    ): ShareOutcome<Uri> =
        try {
            ShareOutcome.Success(FileProvider.getUriForFile(context, authority, file))
        } catch (e: IllegalArgumentException) {
            Tracker.track(RuntimeException("Couldn't create FileProvider URI for sound: ${sound.file}", e))
            ShareOutcome.Failure(R.string.app_share_feedback_unshareable)
        }

    private fun resolveFileForSharing(
        context: Context,
        sound: Sound,
    ): ShareOutcome<File> =
        when {
            sound.file != null -> ShareOutcome.Success(getFile(context, sound.file!!))
            sound.rawRes == 0 -> {
                Tracker.track(RuntimeException("Sound has neither file URI nor raw resource ID: ${sound.name}"))
                ShareOutcome.Failure(R.string.app_share_feedback_broken_data)
            }
            else -> resolveBundledFileForSharing(context, sound)
        }

    private fun resolveBundledFileForSharing(
        context: Context,
        sound: Sound,
    ): ShareOutcome<File> {
        val fileForSharing = getFile(context, sound.name + ".mp3")
        return if (fileForSharing.exists()) {
            Timber.d("Packaged audio already copied to share directory: %s", fileForSharing)
            ShareOutcome.Success(fileForSharing)
        } else {
            Timber.d("Packaged audio is gonna be copied to share directory: %s", fileForSharing)
            copyBundledAudioForSharing(context, sound, fileForSharing)
        }
    }

    private fun copyBundledAudioForSharing(
        context: Context,
        sound: Sound,
        fileForSharing: File,
    ): ShareOutcome<File> =
        try {
            copy(context.resources.openRawResource(sound.rawRes), FileOutputStream(fileForSharing))
            ShareOutcome.Success(fileForSharing)
        } catch (e: IOException) {
            Tracker.track(RuntimeException("Couldn't copy bundled audio to shareable directory: ${sound.name}", e))
            ShareOutcome.Failure(R.string.app_share_feedback_copy_failed)
        }

    /**
     * Two-step resolution result: either a value of [T] (file path, then content URI) or an
     * `@StringRes` feedback id paired with a non-fatal already tracked. `Failure : ShareOutcome<Nothing>`
     * so it can flow through `ShareOutcome<File> → ShareOutcome<Uri>` without re-wrapping.
     */
    private sealed class ShareOutcome<out T> {
        data class Success<out T>(
            val value: T,
        ) : ShareOutcome<T>()

        data class Failure(
            @get:StringRes
            @param:StringRes
            val feedback: Int,
        ) : ShareOutcome<Nothing>()
    }
}
