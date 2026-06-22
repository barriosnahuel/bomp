/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Substitution seam for [RecorderEngine], mirroring `AddButtonFeatureProvider`: production resolves a
 * real [MediaRecorder][android.media.MediaRecorder]-backed engine; tests swap a fake via [setForTest]
 * so a `RecordingActivity` smoke test (or VM test through the factory) never touches the real mic.
 */
object RecorderEngineProvider {
    @Volatile
    private var override: RecorderEngine? = null

    fun get(context: Context): RecorderEngine = override ?: RecorderEngine.create(context)

    @VisibleForTesting
    fun setForTest(engine: RecorderEngine?) {
        override = engine
    }
}
