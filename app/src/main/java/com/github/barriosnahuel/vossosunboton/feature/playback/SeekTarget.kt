/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

/**
 * Maps a scrub [fraction] (0..1) to a millisecond seek target within a clip of [durationMs]. Returns
 * `null` for a non-positive duration (nothing to seek). Pure — the single source for the fraction→ms
 * mapping shared by the waveform scrubs and the progress sliders (was inlined per surface).
 *
 * [reserveEndMargin] controls the end semantics, which differ per surface:
 * - `true` (default) — clamps just shy of the end so a full-right scrub stays in-range instead of
 *   landing on EOS. Used by the waveform scrubs (listen + recorder review), where landing on EOS
 *   fires onCompletion and stops/resets the preview.
 * - `false` — lets a full-right value reach exactly [durationMs] so a progress slider dragged to the
 *   end can complete naturally. Used by the `AudioPreview` / `SoundItem` sliders.
 */
internal fun seekTargetMs(
    durationMs: Long,
    fraction: Float,
    reserveEndMargin: Boolean = true,
): Int? {
    if (durationMs <= 0L) return null
    val target = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
    val maxTarget = if (reserveEndMargin) durationMs - 1L else durationMs
    return target.coerceIn(0L, maxTarget).toInt()
}
