/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the system "Remove animations" accessibility setting is on
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`). Surfaces that drive their own motion — the
 * SaveSuccessOverlay entry spring, the [predictiveBackTransition] gesture — read this to degrade to a
 * static, instantaneous equivalent, matching what the OS does to its own system transitions.
 *
 * Read once per composition (`remember(context)`): the setting only changes via a trip to system
 * Settings, which tears down and recreates the Activity, so there is no need to observe it live.
 */
@Composable
fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
