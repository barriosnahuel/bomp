/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.playstore

import android.net.Uri

/**
 * Builds Play Store links carrying a Google Play `referrer` (UTM) for install attribution. The taxonomy is a
 * small controlled vocabulary so every placement is cut cleanly in Play Console / Firebase and future
 * placements follow the same pattern:
 *
 * - `utm_source` = [SOURCE] (constant): the install came from inside Bomp (a Bomper sharing) — distinct from
 *   future paid/ASO sources.
 * - `utm_medium` = [MEDIUM_AUDIO] / [MEDIUM_APP]: what was shared (a single audio vs the app itself).
 * - `utm_content` = [CONTENT_CAPTION] / [CONTENT_MENU] / [CONTENT_ABOUT]: the exact in-app placement.
 *
 * `utm_campaign` is intentionally reserved (not emitted yet) for future time-boxed initiatives.
 */
object PlayStoreReferrer {
    private const val SOURCE = "bomp"

    const val MEDIUM_AUDIO = "audio"
    const val MEDIUM_APP = "app"

    const val CONTENT_CAPTION = "caption"
    const val CONTENT_MENU = "menu"
    const val CONTENT_ABOUT = "about"

    private const val PLAY_STORE_DETAILS_URL = "https://play.google.com/store/apps/details"

    /**
     * The published Play Store package — a fixed external identity, intentionally NOT `BuildConfig.APPLICATION_ID`:
     * debug/benchmark variants carry an `applicationIdSuffix` (e.g. `.debug`) that points the link at an app that
     * doesn't exist on the Store. The listing is always this one id, in every build variant.
     */
    private const val STORE_PACKAGE_NAME = "com.github.barriosnahuel.vossosunboton"

    /**
     * Play Store URL for the published listing with the install referrer for [medium] + [content]. The referrer
     * value is URL-encoded (Play requires it encoded, ≤ 512 chars — this stays well under).
     */
    fun playStoreUrl(
        medium: String,
        content: String,
    ): String {
        val referrer = "utm_source=$SOURCE&utm_medium=$medium&utm_content=$content"
        return "$PLAY_STORE_DETAILS_URL?id=$STORE_PACKAGE_NAME&referrer=${Uri.encode(referrer)}"
    }
}
