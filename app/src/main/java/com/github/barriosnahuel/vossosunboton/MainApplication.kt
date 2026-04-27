/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import timber.log.Timber

/**
 * App-wide [android.app.Application]. Build-type specifics live in [CustomBuildTypeApplication].
 *
 * Firebase emits `first_open` and `session_start` automatically — nothing custom is needed here. Initial user
 * properties are set lazily on the first call-site that has the real state (see plan 04 §5.4).
 */
internal class MainApplication : CustomBuildTypeApplication() {
    override fun onCreate() {
        super.onCreate()

        Timber.d("Creating %s application...", BuildConfig.BUILD_TYPE)
    }
}
