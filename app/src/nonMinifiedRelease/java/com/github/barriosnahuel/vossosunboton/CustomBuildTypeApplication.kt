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
 * Base [Application] for the `nonMinifiedRelease` build type (Baseline Profile generation). Each build
 * type carries its own `CustomBuildTypeApplication` (source sets resolve by build-type name, not
 * `initWith`, so it can't reuse the release one) — this one's **implementation must mirror
 * `src/release/.../CustomBuildTypeApplication.kt`** (`ErrorTrackerTree`, no StrictMode, no seeding) so
 * the generated profile reflects the real release startup path. **If you change the release one, change
 * this too** — nothing fails the build if they drift, and a drifted copy silently degrades the profile.
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
