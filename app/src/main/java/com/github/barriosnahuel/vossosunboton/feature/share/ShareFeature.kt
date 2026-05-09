/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import java.io.FileOutputStream

internal interface ShareFeature {
    /**
     * @param surface canonical screen_name from `CanonicalScreenName` describing where the share originated.
     * @throws IllegalStateException when any required parameter is `null`
     */
    suspend fun share(
        context: Context,
        sound: Sound,
        surface: String,
    )

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
    ) {
        Timber.d("Trying to share button: %s", sound.name)

        // Resolving the file path and (for bundled sounds, first share only) copying raw resources to disk
        // both touch the file system. Run that part on IO; startActivity + analytics stay on the caller's
        // Main dispatcher because Android's chooser expects the launching call on the UI thread.
        val buttonFileContentUri = withContext(Dispatchers.IO) { getContentUriForSound(sound, context) }
        if (buttonFileContentUri == null) {
            // FileProvider rejected the Sound's path (e.g. legacy persisted absolute path outside the configured root).
            // Surface a non-blocking message to the user; the failure is already tracked as a non-fatal in getContentUriForSound.
            Toast.makeText(context, R.string.app_share_feedback_unshareable, Toast.LENGTH_LONG).show()
            return
        }
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "audio/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, buttonFileContentUri)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        Timber.d(
            "Starting disambiguation window for: %s: contentUri=%s; URI=%s; rawResId=%s;",
            sound.name,
            buttonFileContentUri,
            sound.file,
            sound.rawRes,
        )

        // Track AFTER the chooser actually launches: if `startActivity` throws (ActivityNotFoundException, OS reject)
        // we want `lifetime_shares` to stay accurate. If the chooser shows but the user cancels there is no reliable
        // callback, so "chooser displayed" remains the canonical share signal — matches Firebase's recommended event.
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.app_share_chooser_title)))

        val tracker = AnalyticsTrackerProvider.get(context.applicationContext)
        tracker.log(AnalyticsEvent.Share(surface = surface))
        val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_SHARES)
        tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_SHARES, newCount.toString())
    }

    private fun getContentUriForSound(
        sound: Sound,
        context: Context,
    ): Uri? {
        val fileForSharing =
            when (sound.file) {
                null -> {
                    check(sound.rawRes != 0) { "Either file URI or raw resource ID must exist on a given sound" }

                    val rawResourceInputStream = context.resources.openRawResource(sound.rawRes)

                    val fileForSharing = getFile(context, sound.name + ".mp3")
                    if (fileForSharing.exists()) {
                        Timber.d("Packaged audio already copied to share directory: %s", fileForSharing)
                    } else {
                        Timber.d("Packaged audio is gonna be copied to share directory: %s", fileForSharing)
                        copy(rawResourceInputStream, FileOutputStream(fileForSharing))
                    }

                    fileForSharing
                }
                else -> getFile(context, sound.file!!)
            }

        return try {
            FileProvider.getUriForFile(context, authority, fileForSharing)
        } catch (e: IllegalArgumentException) {
            Tracker.track(RuntimeException("Couldn't create FileProvider URI for sound: ${sound.file}", e))
            null
        }
    }
}
