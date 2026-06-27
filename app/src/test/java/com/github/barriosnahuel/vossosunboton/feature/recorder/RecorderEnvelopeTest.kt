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
    fun `review wave follows the live player position while the clip is loaded`() {
        assertThat(reviewWavePositionMs(playerPositionMs = 2_000, pendingScrubMs = 5_000)).isEqualTo(2_000)
    }

    @Test
    fun `review wave holds a pre-play scrub when no player is loaded`() {
        assertThat(reviewWavePositionMs(playerPositionMs = null, pendingScrubMs = 3_000)).isEqualTo(3_000)
    }

    @Test
    fun `review wave rests at the start with no player and no pending scrub (e g after completion)`() {
        // Regression: parity once let the last scrub stick after completion; the listen screen rewinds
        // to 0, so the review must too once the pending scrub has been consumed by play.
        assertThat(reviewWavePositionMs(playerPositionMs = null, pendingScrubMs = null)).isEqualTo(0)
    }

    private companion object {
        const val BARS = 48
        const val FLOOR = 0.06f
    }
}
