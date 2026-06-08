/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Application
import android.content.Intent
import com.github.barriosnahuel.vossosunboton.commons.android.error.ErrorTrackerTree
import timber.log.Timber

/**
 * Base [Application] for the `nonMinifiedRelease` build type (Baseline Profile generation). Mirrors the
 * release variant exactly — `ErrorTrackerTree`, no StrictMode, no seeding — so the generated profile
 * reflects the real release startup path. Each build type carries its own `CustomBuildTypeApplication`
 * (debug/release/benchmark); this is the release one, kept un-minified for profile collection.
 */
internal abstract class CustomBuildTypeApplication : Application() {
    override fun onCreate() {
        Timber.plant(ErrorTrackerTree())

        super.onCreate()
    }

    /** No-op: seeding sample My Sounds is a debug-only dev convenience; generation profiles cold start. */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun seedDebugSoundsIfRequested(intent: Intent) {
        // Intentionally empty — release/generation never seeds.
    }
}
