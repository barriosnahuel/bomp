/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Application
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker

internal object DebugTools {
    fun configure(application: Application) {
        StrictModeConfigurator.initializeWithDefaults(Tracker)
        application.registerActivityLifecycleCallbacks(JankStatsLogger())
    }
}
