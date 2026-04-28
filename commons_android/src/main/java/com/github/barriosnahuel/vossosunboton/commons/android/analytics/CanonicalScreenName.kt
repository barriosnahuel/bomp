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

    val ALL: List<String> = listOf(MY_SOUNDS, EXPLORE_SOUNDS, ABOUT, SEARCH_SOUND, ADD_SOUND, EDIT_SOUND)
}
