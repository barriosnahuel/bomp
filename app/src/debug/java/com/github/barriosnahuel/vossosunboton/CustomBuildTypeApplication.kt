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

internal abstract class CustomBuildTypeApplication : Application() {
    override fun onCreate() {
        Timber.plant(Timber.DebugTree())
        Timber.plant(ErrorTrackerTree())

        super.onCreate()

        DebugTools.configure(this)
    }

    fun seedDebugSoundsIfRequested(intent: Intent) {
        DebugSoundSeeder.seedIfRequested(this, intent)
    }
}
