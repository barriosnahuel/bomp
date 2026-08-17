/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import androidx.compose.runtime.saveable.Saver

/**
 * The portion of a clip the user chose to keep, as start/end fractions (0..1) of the whole.
 *
 * Fractions rather than milliseconds because the handles are dragged over a fixed-width waveform:
 * a fraction survives the duration arriving late (metadata extraction is async) and a rotation that
 * re-measures the canvas. Milliseconds are derived on demand via [startMs] / [endMs].
 *
 * Every mutation goes through [withStart] / [withEnd], which clamp so the kept range can never fall
 * below [MIN_TRIM_MS] — the invalid zero-length selection is unreachable rather than validated
 * against, so Save is never disabled by the trimmer: docs/adr/0028-add-flow-audio-trim.md.
 *
 * Pure Kotlin (no Android types) so every transition is JVM-unit-tested.
 */
internal data class TrimSelection(
    val startFraction: Float,
    val endFraction: Float,
) {
    fun startMs(durationMs: Int): Int = (startFraction * durationMs).toInt().coerceIn(0, durationMs)

    fun endMs(durationMs: Int): Int = (endFraction * durationMs).toInt().coerceIn(0, durationMs)

    fun keptMs(durationMs: Int): Int = (endMs(durationMs) - startMs(durationMs)).coerceAtLeast(0)

    /**
     * True when the selection still covers the whole clip, i.e. the user opened the editor but did
     * not actually move a handle. The save path checks this to skip the export entirely — trimming
     * a clip to itself would transcode it for nothing.
     *
     * Compared in milliseconds, not fractions, so a sub-millisecond drag that rounds away does not
     * trigger a pointless re-encode.
     */
    fun isWholeClip(durationMs: Int): Boolean = startMs(durationMs) == 0 && endMs(durationMs) >= durationMs

    /** Moves the start handle to [fraction], pushed back if it would leave less than [MIN_TRIM_MS]. */
    fun withStart(
        fraction: Float,
        durationMs: Int,
    ): TrimSelection {
        if (durationMs <= 0) return this
        val minSpan = minSpanFraction(durationMs)
        val newStart = fraction.coerceIn(0f, (endFraction - minSpan).coerceAtLeast(0f))
        return copy(startFraction = newStart)
    }

    /** Moves the end handle to [fraction], pushed forward if it would leave less than [MIN_TRIM_MS]. */
    fun withEnd(
        fraction: Float,
        durationMs: Int,
    ): TrimSelection {
        if (durationMs <= 0) return this
        val minSpan = minSpanFraction(durationMs)
        val newEnd = fraction.coerceIn((startFraction + minSpan).coerceAtMost(1f), 1f)
        return copy(endFraction = newEnd)
    }

    private fun minSpanFraction(durationMs: Int): Float = (MIN_TRIM_MS.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        /** The untouched selection: the whole clip. */
        val WHOLE = TrimSelection(startFraction = 0f, endFraction = 1f)

        /**
         * Shortest keepable range. Matches the recorder's own floor (ADR 0019): below a second an
         * audio is a click, not a Bomp.
         */
        const val MIN_TRIM_MS = 1_000

        /**
         * Below this the trim affordance is not offered at all. A clip this short is already a
         * sticker, and with [MIN_TRIM_MS] as the floor there would be almost no range left to
         * choose — an editor whose handles barely move reads as broken, not as precise.
         */
        const val MIN_TRIMMABLE_MS = 2_000

        fun isTrimmable(durationMs: Int): Boolean = durationMs >= MIN_TRIMMABLE_MS
    }
}

/**
 * Applies one range-slider emission to [current]: whichever bound moved identifies the handle the
 * user is dragging, and routing it through [TrimSelection.withStart] / [TrimSelection.withEnd] is what
 * applies the minimum-span clamp — the slider itself will happily collapse both thumbs onto one value.
 *
 * An emission where neither bound moved returns [current] untouched rather than guessing a handle.
 * Extracted from the Composable so the branch is unit-tested without driving a gesture.
 */
internal fun resolveHandleDrag(
    current: TrimSelection,
    start: Float,
    end: Float,
    durationMs: Int,
): TrimSelection =
    when {
        start != current.startFraction -> current.withStart(start, durationMs)
        end != current.endFraction -> current.withEnd(end, durationMs)
        else -> current
    }

/**
 * Keeps a dragged range across an Activity recreate. The two fractions travel as a `FloatArray`,
 * which a Bundle stores natively — a rotation mid-edit must not silently rewind the user back to the
 * whole clip (CLAUDE.md § Stateful Composables).
 */
internal val TrimSelectionSaver: Saver<TrimSelection, Any> =
    Saver(
        save = { floatArrayOf(it.startFraction, it.endFraction) },
        restore = { value ->
            val fractions = value as FloatArray
            TrimSelection(startFraction = fractions[0], endFraction = fractions[1])
        },
    )
