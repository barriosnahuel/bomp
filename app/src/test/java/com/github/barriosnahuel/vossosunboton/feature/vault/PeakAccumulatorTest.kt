/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The PCM decode itself needs a device (MediaCodec), but the bucketing + normalization that turns
 * samples into bar heights is pure — and it's where the visual correctness lives. These guard it.
 */
internal class PeakAccumulatorTest {
    @Test
    fun `loudest bucket normalizes to 1`() {
        // 4 samples over 4 buckets, total = 4: one sample per bucket, rising magnitude.
        val accumulator = PeakAccumulator(barCount = 4, estimatedTotalSamples = 4)
        listOf<Short>(1000, 2000, 4000, 8000).forEach(accumulator::add)

        val peaks = accumulator.result()

        assertThat(peaks).hasLength(4)
        // 8000 is the loudest → its bucket is 1.0; the rest are proportional.
        assertThat(peaks[3]).isWithin(TOLERANCE).of(1f)
        assertThat(peaks[2]).isWithin(TOLERANCE).of(0.5f)
        assertThat(peaks[1]).isWithin(TOLERANCE).of(0.25f)
    }

    @Test
    fun `keeps the peak (max abs) within a bucket, not the last sample`() {
        // Two samples per bucket; the bucket peak must be the larger magnitude, sign-independent.
        val accumulator = PeakAccumulator(barCount = 2, estimatedTotalSamples = 4)
        listOf<Short>(-8000, 1000, 2000, 1000).forEach(accumulator::add)

        val peaks = accumulator.result()

        // Bucket 0 peak = |−8000| = 8000 (loudest overall → 1.0); bucket 1 peak = 2000 → 0.25.
        assertThat(peaks[0]).isWithin(TOLERANCE).of(1f)
        assertThat(peaks[1]).isWithin(TOLERANCE).of(0.25f)
    }

    @Test
    fun `silence yields a flat minimum baseline, never zero`() {
        val accumulator = PeakAccumulator(barCount = 5, estimatedTotalSamples = 5)
        repeat(5) { accumulator.add(0) }

        val peaks = accumulator.result()

        assertThat(peaks).hasLength(5)
        peaks.forEach { assertThat(it).isGreaterThan(0f) }
        // All equal (flat baseline).
        assertThat(peaks.toSet()).hasSize(1)
    }

    @Test
    fun `extra samples beyond the estimate clamp into the last bucket without crashing`() {
        val accumulator = PeakAccumulator(barCount = 3, estimatedTotalSamples = 3)
        // 6 samples though only 3 were estimated — must not throw or index out of bounds.
        repeat(6) { accumulator.add(3000) }

        assertThat(accumulator.result()).hasLength(3)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
