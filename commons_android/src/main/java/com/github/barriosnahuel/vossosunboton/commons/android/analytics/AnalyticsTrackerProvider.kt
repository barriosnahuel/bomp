/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.os.StrictMode
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
    fun get(context: Context): AnalyticsTracker =
        cached ?: synchronized(this) {
            cached ?: createTracker(context).also { cached = it }
        }

    // Firebase init is lazy: the first call to FirebaseAnalytics.getInstance(...) triggers GMS Dynamite module
    // loading + Cronet net stack init + Chimera ContentProvider query, all of which legitimately read from disk.
    // We can't envelope those reads inside the SDK, so we scope them at our call-site instead of suppressing
    // detectDiskReads()/detectCustomSlowCalls() globally. SharedPreferences first-load is also wrapped because
    // getSharedPreferences() reads the prefs file on first access. See StrictModeConfigurator for runtime-only
    // violations (NonSdkApi reflection, framework finalizers) that can't be scoped this way.
    //
    // The @SuppressLint silences a cross-module MissingPermission check: FirebaseAnalytics.getInstance requires
    // INTERNET / ACCESS_NETWORK_STATE / WAKE_LOCK, declared by the consuming :app module's manifest — Lint can't
    // see them from this :commons_android library.
    @SuppressLint("MissingPermission")
    private fun createTracker(context: Context): AnalyticsTracker {
        val app = context.applicationContext
        val previousPolicy = StrictMode.allowThreadDiskReads()
        return try {
            FirebaseAnalyticsTracker(
                firebase = FirebaseAnalytics.getInstance(app),
                store = SharedPrefsAnalyticsStore(app),
            )
        } finally {
            StrictMode.setThreadPolicy(previousPolicy)
        }
    }

    /**
     * Replace the cached tracker for tests. Call from `@Before`; reset to `null` from `@After` if isolation is needed.
     */
    @VisibleForTesting
    fun setForTest(tracker: AnalyticsTracker?) {
        cached = tracker
    }
}
