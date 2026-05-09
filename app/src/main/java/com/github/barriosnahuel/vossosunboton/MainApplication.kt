/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * App-wide [android.app.Application]. Build-type specifics live in [CustomBuildTypeApplication].
 *
 * Firebase emits `first_open` and `session_start` automatically — nothing custom is needed here. Initial user
 * properties are set lazily on the first call-site that has the real state (see plan 04 §5.4).
 */
internal class MainApplication : CustomBuildTypeApplication() {
    /**
     * Process-lived scope for fire-and-forget initialization that needs to outlive any single
     * Activity / VM. Cancellation is implicit on process death; [SupervisorJob] keeps a single
     * failed coroutine from cascading into siblings.
     */
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.d("Starting %s application", BuildConfig.BUILD_TYPE)

        // Warm up the analytics tracker on a background coroutine so the first UI call site
        // (`LandingScreen`'s `LaunchedEffect { tracker.logScreen(...) }`) doesn't block on the
        // DataStore cache prime that happens inside each store's constructor.
        applicationScope.launch {
            AnalyticsTrackerProvider.get(this@MainApplication)
        }
    }
}
