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
import com.github.barriosnahuel.vossosunboton.playstore.PlayStoreReferrer
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
        Timber.d("Preparing share intent for audio: %s", sound.name)

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
                // getType() reads the platform MIME map from disk, so build the intent on IO — otherwise the
                // debug StrictMode disk-read guard crashes when prepareShareIntent resumes on the main thread.
                val intent = withContext(Dispatchers.IO) { buildShareIntent(applicationContext, resolution.value) }
                ShareIntentOutcome.Success(intent, surface)
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

    private fun buildShareIntent(
        context: Context,
        contentUri: Uri,
    ): Intent =
        Intent().apply {
            action = Intent.ACTION_SEND
            // A concrete MIME (e.g. audio/mpeg) makes some clients (Telegram) render the inline audio player
            // instead of a generic "file to download"; `audio/*` stays only as the last-resort fallback.
            type = resolveAudioMimeType(context.contentResolver.getType(contentUri), contentUri.lastPathSegment)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            // Soft install invite + attributed Play link. Clients that keep a caption on audio (Telegram)
            // show it; those that drop it (WhatsApp) just send the audio — no harm either way.
            val playUrl = PlayStoreReferrer.playStoreUrl(PlayStoreReferrer.MEDIUM_AUDIO, PlayStoreReferrer.CONTENT_CAPTION)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_share_caption_invite, playUrl))
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
            Tracker.log("share.soundFile=${sound.file}")
            Tracker.track(RuntimeException("Couldn't create FileProvider URI for sound", e))
            ShareOutcome.Failure(R.string.app_share_feedback_unshareable)
        }

    private fun resolveFileForSharing(
        context: Context,
        sound: Sound,
    ): ShareOutcome<File> =
        when {
            sound.file != null -> ShareOutcome.Success(getFile(context, sound.file!!))
            sound.rawRes == 0 -> {
                Tracker.log("share.soundName=${sound.name}")
                Tracker.track(RuntimeException("Sound has neither file URI nor raw resource ID"))
                ShareOutcome.Failure(R.string.app_share_feedback_broken_data)
            }
            else -> resolveBundledFileForSharing(context, sound)
        }

    private fun resolveBundledFileForSharing(
        context: Context,
        sound: Sound,
    ): ShareOutcome<File> {
        // Cache key is the stable id, not the display name: names are no longer unique (ADR 0008),
        // and this path is reached only for bundled sounds, whose id is `"bundled:$rawRes"`.
        val fileForSharing = getFile(context, "bundled_${sound.rawRes}.mp3")
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
            Tracker.log("share.soundName=${sound.name}")
            Tracker.track(RuntimeException("Couldn't copy bundled audio to shareable directory", e))
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

private const val WILDCARD_AUDIO_MIME = "audio/*"

/**
 * Audio MIME by lowercase file extension. Covers the formats Bomp actually stores: bundled `mp3`s plus
 * whatever the user imports via the share sheet — notably WhatsApp voice notes (`opus`).
 */
private val AUDIO_MIME_BY_EXTENSION =
    mapOf(
        "mp3" to "audio/mpeg",
        "opus" to "audio/opus",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
    )

/**
 * Resolves the MIME type to attach to the share intent. Prefers [resolverType] (from
 * `ContentResolver.getType`) when it is a concrete audio type; otherwise derives it from [fileName]'s
 * extension, falling back to the [WILDCARD_AUDIO_MIME] wildcard.
 *
 * The fallback is SDK-agnostic on purpose: `ContentResolver.getType` / `MimeTypeMap` can return `null` (or a
 * non-audio `application/octet-stream`) for `.opus` on older API levels, so we never trust a non-audio answer.
 */
internal fun resolveAudioMimeType(
    resolverType: String?,
    fileName: String?,
): String {
    if (resolverType != null && resolverType.startsWith("audio/")) {
        return resolverType
    }
    val extension = fileName?.substringAfterLast('.', "").orEmpty().lowercase()
    return AUDIO_MIME_BY_EXTENSION[extension] ?: WILDCARD_AUDIO_MIME
}
