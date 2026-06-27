/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.waveform

import kotlin.math.max

/**
 * Shared baseline floor for a normalized waveform bar (0..1). A silent bar never collapses to zero —
 * it shows this minimum so the wave still reads as a wave, not a gap. Single source for the floor
 * used by the decoded `PeakAccumulator`, the live `buildRecorderEnvelope`, and the [EnvelopeWaveform]
 * renderer (was duplicated as `MIN_BAR` / `WAVEFORM_MIN_FRACTION` across all three).
 */
internal const val WAVEFORM_MIN_BAR = 0.06f

/**
 * Normalizes [raw] amplitude buckets so the [loudest] becomes 1.0, with every bar floored at
 * [WAVEFORM_MIN_BAR]. Pure (no Android types) so the bucketing callers stay unit-testable.
 * Precondition: [loudest] > 0 — callers own the all-silent case, which is deliberately not folded
 * in here: docs/adr/0020-shared-envelope-waveform.md.
 */
internal fun normalizeEnvelope(
    raw: FloatArray,
    loudest: Float,
): FloatArray = FloatArray(raw.size) { max(WAVEFORM_MIN_BAR, raw[it] / loudest) }
