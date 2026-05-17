/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Canonical `screen_name` values emitted by [AnalyticsTracker.logScreen]. Pulled out as constants so the regression
 * net (`AnalyticsCoverageMatrixTest`) can enumerate them and so call-sites cannot drift to literal strings on a
 * refactor. Adding a new entry must come with an update to the canonical regression list.
 */
object CanonicalScreenName {
    const val MY_SOUNDS = "my_sounds"
    const val EXPLORE_SOUNDS = "explore_sounds"
    const val ABOUT = "about"
    const val SEARCH_SOUND = "search_sound"
    const val ADD_SOUND = "add_sound"
    const val EDIT_SOUND = "edit_sound"

    /** Vault tab — list of private collections; no audio content shown until per-collection unlock. */
    const val VAULT = "vault"

    /** Per-collection biometric unlock prompt invocation surface (analytics-only marker). */
    const val VAULT_UNLOCK = "vault_unlock"

    /** Immersive listen view: a single private collection's audios, post-unlock, listen-only. */
    const val VAULT_COLLECTION_LISTEN = "vault_collection_listen"

    /** Create-collection sheet (reachable from Vault FAB or from Add/Edit "+ Nueva" chip). */
    const val COLLECTION_CREATE = "collection_create"

    val ALL: List<String> =
        listOf(
            MY_SOUNDS,
            EXPLORE_SOUNDS,
            ABOUT,
            SEARCH_SOUND,
            ADD_SOUND,
            EDIT_SOUND,
            VAULT,
            VAULT_UNLOCK,
            VAULT_COLLECTION_LISTEN,
            COLLECTION_CREATE,
        )
}
