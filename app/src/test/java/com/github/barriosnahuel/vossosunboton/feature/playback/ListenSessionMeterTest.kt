/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The player samples position about every 100 ms, so every step below stays in that ballpark. */
internal class ListenSessionMeterTest {
    private fun ListenSessionMeter.play(
        fromMs: Int,
        toMs: Int,
        stepMs: Int = 100,
    ) {
        var at = fromMs
        onPosition(at)
        while (at < toMs) {
            at = minOf(at + stepMs, toMs)
            onPosition(at)
        }
    }

    @Test
    fun `advancing playback accrues the audio that went by`() {
        val meter = ListenSessionMeter()

        meter.play(fromMs = 0, toMs = 2_500)

        assertThat(meter.listenedMs).isEqualTo(2_500)
    }

    @Test
    fun `the first sample is a baseline and accrues nothing on its own`() {
        val meter = ListenSessionMeter()

        // A session resumed mid-clip starts sampling at a non-zero position; counting that offset
        // would credit the Bomper with audio heard on a previous session.
        meter.onPosition(30_000)

        assertThat(meter.listenedMs).isEqualTo(0)
    }

    @Test
    fun `a pause holds the position so nothing accrues while it lasts`() {
        val meter = ListenSessionMeter()

        meter.play(fromMs = 0, toMs = 1_000)
        repeat(10) { meter.onPosition(1_000) }

        assertThat(meter.listenedMs).isEqualTo(1_000)
    }

    @Test
    fun `seeking backwards never subtracts what was already heard`() {
        val meter = ListenSessionMeter()

        meter.play(fromMs = 0, toMs = 2_000)
        meter.onPosition(500)

        assertThat(meter.listenedMs).isEqualTo(2_000)
    }

    @Test
    fun `re-listening after a seek back accrues again`() {
        val meter = ListenSessionMeter()

        meter.play(fromMs = 0, toMs = 2_000)
        meter.play(fromMs = 500, toMs = 1_000)

        // 2s heard, then 0,5s of it heard a second time: consumption, not screen time.
        assertThat(meter.listenedMs).isEqualTo(2_500)
    }

    @Test
    fun `dragging the waveform forward is a scrub, not audio heard`() {
        val meter = ListenSessionMeter()

        meter.play(fromMs = 0, toMs = 1_000)
        // A drag lands far past the last sample; crediting it would report audio nobody heard and
        // could push the tally past the audio's own length.
        meter.onPosition(80_000)

        assertThat(meter.listenedMs).isEqualTo(1_000)
    }

    @Test
    fun `a restored meter keeps the audio heard before the rotation`() {
        val meter = ListenSessionMeter(initialListenedMs = 30_000)

        meter.play(fromMs = 30_000, toMs = 30_800)

        assertThat(meter.listenedMs).isEqualTo(30_800)
    }

    @Test
    fun `duration keeps the longest value seen, ignoring the nulls before it loads`() {
        val meter = ListenSessionMeter()

        meter.onDuration(null)
        meter.onDuration(98_000)
        meter.onDuration(null)

        assertThat(meter.durationMs).isEqualTo(98_000)
    }
}
