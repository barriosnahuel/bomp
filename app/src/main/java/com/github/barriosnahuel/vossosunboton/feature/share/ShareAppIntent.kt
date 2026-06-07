/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.github.barriosnahuel.vossosunboton.BuildConfig
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.playstore.PlayStoreReferrer

/**
 * App-recommendation share: a plain `text/plain` message (no audio stream) inviting the recipient to get
 * Bomp, with an attributed Play link. Unlike the audio caption, a text-only share keeps its body on every
 * client (including WhatsApp), so this is the higher-reach acquisition surface.
 */
object ShareAppIntent {
    /** Bare `ACTION_SEND` text intent recommending Bomp, carrying the Play referrer for [content]. */
    fun build(
        context: Context,
        content: String,
    ): Intent {
        val playUrl = PlayStoreReferrer.playStoreUrl(BuildConfig.APPLICATION_ID, PlayStoreReferrer.MEDIUM_APP, content)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_share_app_invite, playUrl))
        }
    }

    /** Wraps [build] in a chooser and launches it; records a non-fatal if nothing can handle the share. */
    fun launch(
        context: Context,
        content: String,
    ) {
        try {
            context.startActivity(
                Intent.createChooser(build(context, content), context.getString(R.string.app_share_chooser_title)),
            )
        } catch (e: ActivityNotFoundException) {
            Tracker.track(RuntimeException("No activity available to share the app", e))
        }
    }
}
