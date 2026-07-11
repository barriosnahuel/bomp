/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Stands in for the mic across the instrumented recorder flows: the bugs those tests guard are about
 * lifecycle, FileProvider and StrictMode, not audio capture. Everything downstream of the engine runs
 * for real on the emulator.
 *
 * `start` writes the same 5 s silent clip the rest of the suite seeds, so the captured file is real
 * audio — the naming screen's preview and the save pipeline's MIME/size validation both read it, and
 * a zero-byte stub would exercise their failure branches instead of the happy path.
 */
internal class FakeRecorderEngine : RecorderEngine {
    override var onMaxDurationReached: (() -> Unit)? = null
    override var onInterrupted: (() -> Unit)? = null

    override fun start(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(TEST_ASSET).use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    override fun stop(): Boolean = true

    override fun maxAmplitude(): Float = 0.4f

    override fun release() = Unit

    private companion object {
        const val TEST_ASSET = "test_sound.mp3"
    }
}
