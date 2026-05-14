/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class SoundsViewModelSearchTest : AbstractRobolectricTest() {
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Deterministically stop the reactive `repo.sounds` collector each VM starts in `init`
        // (post-PR-#1130 fix). A bare `cancel()` is fire-and-forget: the collector can outlive the
        // test, parked on the process-singleton DataStore, and pollute the next test by re-running
        // `loadSounds` against stale state. `cancelAndJoinAll()` joins until it unwinds — see
        // ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        unmockkAll()
    }

    @Test
    fun `searchResults emits empty list when query is blank`() {
        val viewModel = givenAViewModel()
        viewModel.injectAllSounds(listOf(Sound("Bromas de oficina", rawRes = 1), Sound("Casa feliz", file = "casa.mp3")))

        viewModel.onSearchQueryChange("")

        assertThat(viewModel.searchResults.value).isEmpty()
    }

    @Test
    fun `searchResults filters by name case-insensitively`() {
        val viewModel = givenAViewModel()
        val bromas = Sound("Bromas de oficina", rawRes = 1)
        val casa = Sound("Casa feliz", file = "casa.mp3")
        viewModel.injectAllSounds(listOf(bromas, casa))

        viewModel.onSearchQueryChange("bro")

        assertThat(viewModel.searchResults.value).containsExactly(bromas)
    }

    @Test
    fun `searchResults reflects a pin toggled while overlay is open`() {
        val viewModel = givenAViewModel()
        val sound = Sound("custom sound", file = "custom.mp3")
        viewModel.injectSoundsAndAllSounds(listOf(sound))
        viewModel.onSearchQueryChange("custom")

        viewModel.togglePin(sound)

        assertThat(
            viewModel.searchResults.value
                .single { it.name == "custom sound" }
                .isPinned,
        ).isTrue()
    }

    @Test
    fun `searchResults removes a deleted sound immediately`() {
        val viewModel = givenAViewModel()
        val sound = Sound("custom sound", file = "custom.mp3")
        viewModel.injectSoundsAndAllSounds(listOf(sound))
        viewModel.onSearchQueryChange("custom")

        viewModel.deleteSound(sound)

        assertThat(viewModel.searchResults.value.none { it.name == "custom sound" }).isTrue()
    }

    @Test
    fun `searchResults sorts pinned result first after togglePin`() {
        val viewModel = givenAViewModel()
        val alpha = Sound("test alpha", file = "a.mp3")
        val beta = Sound("test beta", file = "b.mp3")
        viewModel.injectSoundsAndAllSounds(listOf(alpha, beta))
        viewModel.onSearchQueryChange("test")

        viewModel.togglePin(beta)

        assertThat(
            viewModel.searchResults.value
                .first()
                .name,
        ).isEqualTo("test beta")
    }

    @Test
    fun `hideSearch resets query and clears results`() {
        val viewModel = givenAViewModel()
        viewModel.injectAllSounds(listOf(Sound("Bromas de oficina", rawRes = 1)))
        viewModel.showSearch()
        viewModel.onSearchQueryChange("bro")

        viewModel.hideSearch()

        assertThat(viewModel.isSearchVisible.value).isFalse()
        assertThat(viewModel.searchQuery.value).isEmpty()
        assertThat(viewModel.searchResults.value).isEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectAllSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    private fun SoundsViewModel.injectSoundsAndAllSounds(sounds: List<Sound>) {
        injectSounds(sounds)
        injectAllSounds(sounds)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
                searchDebounceMs = 0L,
            )
        createdViewModels += vm
        return vm
    }
}
