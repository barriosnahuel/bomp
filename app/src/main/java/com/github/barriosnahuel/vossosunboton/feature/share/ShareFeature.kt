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

/**
 * Outcome of [ShareFeature.prepareShareIntent]. [Success] carries a ready-to-launch chooser intent
 * and the originating surface so [ShareFeature.launchChooser] can log the analytics event with the
 * matching `screen_name`. [Failure] carries an `@StringRes` feedback id for the caller to surface,
 * paired with a non-fatal already tracked via `Tracker.track`.
 */
sealed class ShareIntentOutcome {
    data class Success(
        val intent: Intent,
        val surface: String,
    ) : ShareIntentOutcome()

    data class Failure(
        @get:StringRes
        @param:StringRes
        val feedback: Int,
    ) : ShareIntentOutcome()
}

interface ShareFeature {
    /**
     * Resolves the audio file for [sound] on the IO dispatcher, wraps it in a FileProvider URI, and
     * builds the `ACTION_SEND` intent. Returns either a ready-to-launch [ShareIntentOutcome.Success]
     * or a [ShareIntentOutcome.Failure] with an `@StringRes` feedback id (each failure is also recorded
     * as non-fatal).
     *
     * Receives [applicationContext] — file resolution and FileProvider don't need an Activity context.
     * @param surface canonical screen_name from `CanonicalScreenName` describing where the share originated.
     */
    suspend fun prepareShareIntent(
        applicationContext: Context,
        sound: Sound,
        surface: String,
    ): ShareIntentOutcome

    /**
     * Wraps [intent] in a chooser and dispatches via `startActivity`, then logs the analytics event
     * for [surface] only if the chooser launched. Returns `null` on success or
     * `R.string.app_share_feedback_no_app_for_audio` if no activity can handle the intent (also tracked
     * as non-fatal).
     *
     * Must be invoked from a UI-thread caller with an [activityContext] — Android's chooser expects
     * the launching call on the Activity's Looper.
     */
    fun launchChooser(
        activityContext: Context,
        intent: Intent,
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
    override suspend fun prepareShareIntent(
        applicationContext: Context,
        sound: Sound,
        surface: String,
    ): ShareIntentOutcome {
        Timber.d("Preparing share intent for button: %s", sound.name)

        val resolution = withContext(Dispatchers.IO) { resolveContentUri(applicationContext, sound) }
        return when (resolution) {
            is ShareOutcome.Failure -> ShareIntentOutcome.Failure(resolution.feedback)
            is ShareOutcome.Success -> {
                Timber.d(
                    "Share intent ready for: %s; contentUri=%s; URI=%s; rawResId=%s; surface=%s",
                    sound.name,
                    resolution.value,
                    sound.file,
                    sound.rawRes,
                    surface,
                )
                ShareIntentOutcome.Success(buildShareIntent(resolution.value), surface)
            }
        }
    }

    override fun launchChooser(
        activityContext: Context,
        intent: Intent,
        surface: String,
    ): Int? {
        // Track AFTER the chooser actually launches: if `startActivity` throws (ActivityNotFoundException, OS reject)
        // we want `lifetime_shares` to stay accurate. If the chooser shows but the user cancels there is no reliable
        // callback, so "chooser displayed" remains the canonical share signal — matches Firebase's recommended event.
        try {
            activityContext.startActivity(
                Intent.createChooser(intent, activityContext.getString(R.string.app_share_chooser_title)),
            )
        } catch (e: ActivityNotFoundException) {
            Tracker.track(RuntimeException("No activity available to handle audio share intent", e))
            return R.string.app_share_feedback_no_app_for_audio
        }

        val tracker = AnalyticsTrackerProvider.get(activityContext.applicationContext)
        tracker.log(AnalyticsEvent.Share(surface = surface))
        val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_SHARES)
        tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_SHARES, newCount.toString())
        return null
    }

    private fun buildShareIntent(contentUri: Uri): Intent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
     * so it can flow through `ShareOutcome<File> → ShareOutcome<Uri>` without re-wrapping. Private to
     * the impl — the public boundary is [ShareIntentOutcome].
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
