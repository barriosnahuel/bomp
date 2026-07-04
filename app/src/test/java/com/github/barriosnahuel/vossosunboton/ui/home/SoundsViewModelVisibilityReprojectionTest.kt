/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-function coverage for [reprojectVisibilityToggle] — the optimistic My Sounds "Todo" update
 * that makes a visibility toggle reflect synchronously (ADR 0012), instead of only after the
 * DataStore write round-trips back through `loadSounds` (the latency that previously flaked
 * [SoundsViewModelVisibilityTest] under CI load). No Robolectric / dispatcher / DataStore here, so
 * these assertions can't race a background `loadSounds` — they isolate the synchronous list logic.
 */
internal class SoundsViewModelVisibilityReprojectionTest {
    // Mirror of SoundsViewModel.SOUND_ORDER (private): pinned first, then most-recent by dateAdded,
    // then name. The production toggle passes that exact comparator into reprojectVisibilityToggle.
    private val order: Comparator<Sound> =
        compareByDescending<Sound> { it.isPinned }
            .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() }

    @Test
    fun `hiding drops the audio and preserves the rest of the ordered list`() {
        val current = listOf(sound("alpha", 3), sound("bravo", 2), sound("charlie", 1))

        val result = reprojectVisibilityToggle(current, "custom:bravo", visible = false, catalog = current, order = order)

        assertThat(result.map { it.name }).containsExactly("alpha", "charlie").inOrder()
    }

    @Test
    fun `showing inserts the audio from the catalog at its date-ordered slot`() {
        val alpha = sound("alpha", 3)
        val bravo = sound("bravo", 2)
        val charlie = sound("charlie", 1)
        // "bravo" is hidden (absent from the visible list) but present in the full catalog.
        val current = listOf(alpha, charlie)

        val result =
            reprojectVisibilityToggle(current, "custom:bravo", visible = true, catalog = listOf(alpha, bravo, charlie), order = order)

        assertThat(result.map { it.name }).containsExactly("alpha", "bravo", "charlie").inOrder()
    }

    @Test
    fun `showing re-sorts so a pinned audio jumps ahead of unpinned newer ones`() {
        val newest = sound("newest", 9)
        val pinned = sound("pinned", 1, isPinned = true)
        val current = listOf(newest)

        val result = reprojectVisibilityToggle(current, "custom:pinned", visible = true, catalog = listOf(newest, pinned), order = order)

        assertThat(result.map { it.name }).containsExactly("pinned", "newest").inOrder()
    }

    @Test
    fun `showing keeps a pre-existing sticker in its slot`() {
        // Stand-in for the welcome sticker: a non-target item must survive the show re-sort in place.
        val sticker = sound("welcome", 5)
        val alpha = sound("alpha", 3)
        val current = listOf(sticker, alpha)

        val result =
            reprojectVisibilityToggle(
                current,
                "custom:bravo",
                visible = true,
                catalog = listOf(sticker, alpha, sound("bravo", 2)),
                order = order,
            )

        assertThat(result.map { it.name }).containsExactly("welcome", "alpha", "bravo").inOrder()
    }

    @Test
    fun `showing an already-visible audio is a no-op`() {
        val current = listOf(sound("alpha", 3), sound("bravo", 2))

        val result = reprojectVisibilityToggle(current, "custom:bravo", visible = true, catalog = current, order = order)

        assertThat(result).isEqualTo(current)
    }

    @Test
    fun `showing an audio missing from the catalog leaves the list untouched`() {
        val current = listOf(sound("alpha", 3))

        val result = reprojectVisibilityToggle(current, "custom:ghost", visible = true, catalog = current, order = order)

        assertThat(result).isEqualTo(current)
    }

    private fun sound(
        name: String,
        dateAdded: Long,
        isPinned: Boolean = false,
    ): Sound = testSound(name, file = "$name.mp3", dateAdded = dateAdded, isPinned = isPinned)
}
