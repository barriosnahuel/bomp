/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Canonical Firebase user property names emitted by [AnalyticsTracker.setUserProperty]. The two `lifetime_*`
 * properties also double as keys into the [CounterStore], so the same constant must be used on both sides — that's
 * exactly what this object enforces. The regression net (`AnalyticsCoverageMatrixTest`) enumerates these and fails
 * when a new entry is added without a matching call-site test.
 */
object AnalyticsUserProperty {
    /** Snapshot of the user-created sound count. Updated on add/delete. */
    const val CURRENT_SOUNDS = "current_sounds"

    /** Snapshot of the pinned sound count. Updated on togglePin. */
    const val CURRENT_PINNED = "current_pinned"

    /** Monotonic counter — total times the user shared a sound. */
    const val LIFETIME_SHARES = "lifetime_shares"

    /** Monotonic counter — total times the user played a sound. */
    const val LIFETIME_PLAYS = "lifetime_plays"

    val ALL: List<String> = listOf(CURRENT_SOUNDS, CURRENT_PINNED, LIFETIME_SHARES, LIFETIME_PLAYS)
}
