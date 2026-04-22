/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Application
import org.robolectric.shadows.ShadowLog
import timber.log.Timber

internal class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ShadowLog.stream = System.out

        Timber.plant(Timber.DebugTree())
        Timber.d("Creating TEST (%s) application...", BuildConfig.BUILD_TYPE)
    }
}
