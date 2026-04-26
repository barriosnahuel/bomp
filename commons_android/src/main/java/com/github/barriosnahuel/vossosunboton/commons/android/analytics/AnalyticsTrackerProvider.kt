/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Factory for the singleton [AnalyticsTracker]. Call sites obtain the tracker via [get]; tests inject a fake via
 * [setForTest]. The `by lazy`-style cache mirrors the rest of the project's singleton pattern.
 */
object AnalyticsTrackerProvider {
    @Volatile
    private var cached: AnalyticsTracker? = null

    /**
     * Return the production tracker, creating it on first call. Thread-safe and idempotent.
     */
    fun get(context: Context): AnalyticsTracker = cached ?: synchronized(this) {
        cached ?: FirebaseAnalyticsTracker(
            firebase = FirebaseAnalytics.getInstance(context.applicationContext),
            store = SharedPrefsAnalyticsStore(context.applicationContext),
        ).also { cached = it }
    }

    /**
     * Replace the cached tracker for tests. Call from `@Before`; reset to `null` from `@After` if isolation is needed.
     */
    @VisibleForTesting
    fun setForTest(tracker: AnalyticsTracker?) {
        cached = tracker
    }
}
