/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import kotlin.math.max

/**
 * Builds the recorder review envelope from the amplitude samples captured live during recording (one
 * per poll tick), so the review wave renders **instantly** without re-decoding the file — killing the
 * post-stop placeholder gap (ADR 0019). [barCount] bars normalized to loudest = 1.0 with a [MIN_BAR]
 * floor (same floor as the decoded `PeakAccumulator`, so a live-fed envelope looks like a decoded one).
 *
 * Returns `null` — caller decodes the real file instead — when there is **no usable live signal**:
 * empty input, or every sample silent. The all-silent case matters because some devices' MediaRecorder
 * always reports `getMaxAmplitude() == 0`; a flat live envelope would otherwise hide the real waveform.
 * Pure (no Android) so the bucketing/interpolation is unit-tested.
 */
internal fun buildRecorderEnvelope(
    samples: List<Float>,
    barCount: Int,
): FloatArray? {
    if (samples.isEmpty()) return null
    val raw = FloatArray(barCount)
    if (samples.size >= barCount) {
        // Downsample: max per positional bucket, preserving peaks (same idea as the PCM bucketing).
        samples.forEachIndexed { i, sample ->
            val bucket = (i.toLong() * barCount / samples.size).toInt().coerceAtMost(barCount - 1)
            if (sample > raw[bucket]) raw[bucket] = sample
        }
    } else {
        // Upsample a short clip: linear interpolation across the series so there are no sparse gaps.
        val lastSample = samples.size - 1
        val lastBar = (barCount - 1).coerceAtLeast(1)
        for (i in 0 until barCount) {
            val pos = i.toFloat() * lastSample / lastBar
            val lo = pos.toInt()
            val hi = (lo + 1).coerceAtMost(lastSample)
            raw[i] = samples[lo] + (samples[hi] - samples[lo]) * (pos - lo)
        }
    }
    val loudest = raw.maxOrNull() ?: 0f
    // No usable signal (true silence, or the getMaxAmplitude()-always-0 device quirk) → null so the
    // caller decodes the real file instead of showing a fake flat line for a clip that has audio.
    return if (loudest <= 0f) null else FloatArray(barCount) { max(MIN_BAR, raw[it] / loudest) }
}

/** Baseline floor for a normalized bar — matches the decoded envelope's floor (`PeakAccumulator`). */
private const val MIN_BAR = 0.06f
