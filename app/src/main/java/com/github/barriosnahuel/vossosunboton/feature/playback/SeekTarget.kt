/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

/**
 * Maps a scrub [fraction] (0..1) to a millisecond seek target within a clip of [durationMs]. Returns
 * `null` for a non-positive duration (nothing to seek). Clamps just shy of the end so a full-right
 * scrub stays a valid in-range position instead of landing exactly on EOS. Pure — the single source
 * for the fraction→ms mapping shared by the listen and recorder-review waveform scrubs (was inlined
 * per surface).
 */
internal fun seekTargetMs(
    durationMs: Long,
    fraction: Float,
): Int? {
    if (durationMs <= 0L) return null
    val target = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
    return target.coerceIn(0L, durationMs - 1L).toInt()
}
