/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.vault.WaveformExtractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented coverage for the review waveform's real-data path: [WaveformExtractor] decoding a clip
 * from a `content://` FileProvider URI (the temp recording is not yet a saved Sound). MediaCodec
 * decoding only runs on a device — never under Robolectric — so this guards on the emulator that the
 * review wave is a genuine amplitude envelope, not the synthetic shape it replaced.
 */
@RunWith(AndroidJUnit4::class)
internal class WaveformExtractorContentUriTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        RecorderTempFiles.purge(context)
    }

    @Test
    fun extractsARealEnvelopeFromAContentUri() {
        val clip = File(File(context.cacheDir, "recordings").apply { mkdirs() }, "extractor-fixture.mp3")
        context.resources.openRawResource(R.raw.app_branding_audio).use { input ->
            clip.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = RecorderTempFiles.contentUriFor(context, clip)

        val peaks = runBlocking { WaveformExtractor.extract(context, uri, RECORDER_WAVEFORM_BARS) }

        assertThat(peaks).isNotNull()
        assertThat(peaks!!.size).isEqualTo(RECORDER_WAVEFORM_BARS)
        // A real envelope varies bar to bar; the old synthetic/placeholder path would be uniform.
        assertThat(peaks.toSet().size).isGreaterThan(1)
        // Source normalization copies the uri to a temp file — it must not outlive the decode.
        assertThat(
            context.cacheDir
                .listFiles()
                .orEmpty()
                .filter { it.name.startsWith("waveform_src_") },
        ).isEmpty()
    }
}
