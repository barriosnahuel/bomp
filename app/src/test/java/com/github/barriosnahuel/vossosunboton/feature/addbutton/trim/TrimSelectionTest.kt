/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The trim range algebra: every handle drag, and the clamp that makes a zero-length selection
 * unreachable rather than something the Save button has to validate against (ADR 0028 D5).
 */
internal class TrimSelectionTest {
    @Test
    fun `an untouched selection covers the whole clip`() {
        assertThat(TrimSelection.WHOLE.isWholeClip(CLIP_MS)).isTrue()
        assertThat(TrimSelection.WHOLE.keptMs(CLIP_MS)).isEqualTo(CLIP_MS)
    }

    @Test
    fun `moving either handle stops the selection counting as the whole clip`() {
        assertThat(TrimSelection.WHOLE.withStart(0.2f, CLIP_MS).isWholeClip(CLIP_MS)).isFalse()
        assertThat(TrimSelection.WHOLE.withEnd(0.8f, CLIP_MS).isWholeClip(CLIP_MS)).isFalse()
    }

    @Test
    fun `dragging the start handle past the end leaves the minimum keepable second`() {
        val selection = TrimSelection.WHOLE.withEnd(0.5f, CLIP_MS).withStart(0.9f, CLIP_MS)

        // Within a millisecond: the clamp works in fractions, so the exact integer depends on float
        // rounding at this clip length — the contract is "never shorter than the floor", not a literal.
        assertThat(selection.keptMs(CLIP_MS)).isAtLeast(TrimSelection.MIN_TRIM_MS - 1)
        assertThat(selection.keptMs(CLIP_MS)).isAtMost(TrimSelection.MIN_TRIM_MS + 1)
        assertThat(selection.startMs(CLIP_MS)).isLessThan(selection.endMs(CLIP_MS))
    }

    @Test
    fun `dragging the end handle past the start leaves the minimum keepable second`() {
        val selection = TrimSelection.WHOLE.withStart(0.5f, CLIP_MS).withEnd(0.1f, CLIP_MS)

        // Within a millisecond: the clamp works in fractions, so the exact integer depends on float
        // rounding at this clip length — the contract is "never shorter than the floor", not a literal.
        assertThat(selection.keptMs(CLIP_MS)).isAtLeast(TrimSelection.MIN_TRIM_MS - 1)
        assertThat(selection.keptMs(CLIP_MS)).isAtMost(TrimSelection.MIN_TRIM_MS + 1)
        assertThat(selection.startMs(CLIP_MS)).isLessThan(selection.endMs(CLIP_MS))
    }

    @Test
    fun `a drag that moves the start handle is routed to the start handle`() {
        val dragged = resolveHandleDrag(TrimSelection.WHOLE, start = 0.3f, end = 1f, durationMs = CLIP_MS)

        assertThat(dragged.startFraction).isEqualTo(0.3f)
        assertThat(dragged.endFraction).isEqualTo(1f)
    }

    @Test
    fun `a drag that moves the end handle is routed to the end handle`() {
        val dragged = resolveHandleDrag(TrimSelection.WHOLE, start = 0f, end = 0.4f, durationMs = CLIP_MS)

        assertThat(dragged.startFraction).isEqualTo(0f)
        assertThat(dragged.endFraction).isEqualTo(0.4f)
    }

    @Test
    fun `an emission where neither handle moved changes nothing instead of guessing one`() {
        val current = TrimSelection(startFraction = 0.2f, endFraction = 0.8f)

        val unchanged = resolveHandleDrag(current, start = 0.2f, end = 0.8f, durationMs = CLIP_MS)

        assertThat(unchanged).isEqualTo(current)
    }

    @Test
    fun `handles never leave the clip on either side`() {
        assertThat(TrimSelection.WHOLE.withStart(-3f, CLIP_MS).startFraction).isEqualTo(0f)
        assertThat(TrimSelection.WHOLE.withEnd(4f, CLIP_MS).endFraction).isEqualTo(1f)
    }

    @Test
    fun `a drag arriving before the duration does nothing instead of dividing by zero`() {
        assertThat(TrimSelection.WHOLE.withStart(0.4f, durationMs = 0)).isEqualTo(TrimSelection.WHOLE)
        assertThat(TrimSelection.WHOLE.withEnd(0.4f, durationMs = 0)).isEqualTo(TrimSelection.WHOLE)
    }

    @Test
    fun `millisecond bounds follow the fractions`() {
        val selection = TrimSelection.WHOLE.withStart(0.25f, CLIP_MS).withEnd(0.75f, CLIP_MS)

        assertThat(selection.startMs(CLIP_MS)).isEqualTo(15_000)
        assertThat(selection.endMs(CLIP_MS)).isEqualTo(45_000)
        assertThat(selection.keptMs(CLIP_MS)).isEqualTo(30_000)
    }

    @Test
    fun `the trim editor is offered from two seconds up and not below`() {
        assertThat(TrimSelection.isTrimmable(TrimSelection.MIN_TRIMMABLE_MS)).isTrue()
        assertThat(TrimSelection.isTrimmable(TrimSelection.MIN_TRIMMABLE_MS - 1)).isFalse()
        assertThat(TrimSelection.isTrimmable(0)).isFalse()
    }

    private companion object {
        /** A one-minute voice note — the shape of audio this feature exists for. */
        const val CLIP_MS = 60_000
    }
}
