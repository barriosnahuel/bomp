/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class RecorderEnvelopeTest {
    @Test
    fun `empty samples yield null so the caller decodes instead`() {
        assertThat(buildRecorderEnvelope(emptyList(), BARS)).isNull()
    }

    @Test
    fun `more samples than bars downsample to barCount with the loudest normalized to 1`() {
        // 200 samples, a single loud spike among quiet ones.
        val samples = List(200) { if (it == 120) 0.8f else 0.1f }

        val envelope = buildRecorderEnvelope(samples, BARS)!!

        assertThat(envelope.size).isEqualTo(BARS)
        assertThat(envelope.maxOrNull()).isEqualTo(1f)
        assertThat(envelope.all { it >= FLOOR }).isTrue()
    }

    @Test
    fun `fewer samples than bars interpolate to barCount with no gaps`() {
        val samples = listOf(0.2f, 0.9f, 0.3f, 0.7f, 0.4f)

        val envelope = buildRecorderEnvelope(samples, BARS)!!

        assertThat(envelope.size).isEqualTo(BARS)
        // Interpolation must fill every bar — a short clip never renders as sparse spikes.
        assertThat(envelope.none { it < FLOOR }).isTrue()
        assertThat(envelope.maxOrNull()).isEqualTo(1f)
    }

    @Test
    fun `all-silent samples yield null so the caller decodes the real file`() {
        // Covers the getMaxAmplitude()-always-0 device quirk: a flat live wave would hide real audio.
        assertThat(buildRecorderEnvelope(List(100) { 0f }, BARS)).isNull()
    }

    @Test
    fun `recorderSeekTargetMs maps a scrub fraction to millis within the clip`() {
        assertThat(recorderSeekTargetMs(durationMs = 10_000, fraction = 0.5f)).isEqualTo(5_000)
    }

    @Test
    fun `recorderSeekTargetMs coerces out-of-range fractions into the clip bounds`() {
        assertThat(recorderSeekTargetMs(durationMs = 10_000, fraction = 1.5f)).isEqualTo(10_000)
        assertThat(recorderSeekTargetMs(durationMs = 10_000, fraction = -0.3f)).isEqualTo(0)
    }

    @Test
    fun `recorderSeekTargetMs returns null for a non-positive duration so nothing seeks`() {
        assertThat(recorderSeekTargetMs(durationMs = 0, fraction = 0.5f)).isNull()
    }

    private companion object {
        const val BARS = 48
        const val FLOOR = 0.06f
    }
}
