/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/** `search_performed`: the denominator of `search_zero_results`, reported only for searches the user ran. */
internal class SoundsViewModelSearchAnalyticsTest : AbstractRobolectricTest() {
    private lateinit var fake: FakeAnalyticsTracker
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).clearForTest()
            WelcomeStickerStore(ApplicationProvider.getApplicationContext()).clearForTest()
            com.github.barriosnahuel.vossosunboton.model.data.manager
                .CollectionsRepository(ApplicationProvider.getApplicationContext())
                .clearForTest()
            com.github.barriosnahuel.vossosunboton.feature.collections
                .MySoundsFilterStore(ApplicationProvider.getApplicationContext())
                .clearForTest()
            com.github.barriosnahuel.vossosunboton.feature.vault
                .VaultFilterStore(ApplicationProvider.getApplicationContext())
                .clearForTest()
            com.github.barriosnahuel.vossosunboton.feature.collections
                .DualHomeCoachStore(ApplicationProvider.getApplicationContext())
                .clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.pause() } answers { nothing }
        every { PlayerControllerFactory.instance.startListenSession(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Deterministically stop the reactive `repo.sounds` collector each VM starts in `init`.
        // A bare `cancel()` is fire-and-forget: the collector can outlive the
        // test, parked on the process-singleton DataStore, and the next test's `clearForTest()` /
        // `save(...)` writes emit through it — leaking events (`milestone_sounds_3`,
        // `search_zero_results`) into the new test's `fake`. `cancelAndJoinAll()` joins until it
        // unwinds — see ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `search emits search_performed with the number of results`() {
        val viewModel = givenAViewModel()
        viewModel.injectSounds(listOf(testSound("alpha", "a.mp3"), testSound("alpine", "b.mp3"), testSound("beta", "c.mp3")))

        viewModel.onSearchQueryChange("alp")

        assertThat(fake.assertEmitted("search_performed").params["results"]).isEqualTo(2)
    }

    @Test
    fun `a search with no match emits search_performed alongside search_zero_results`() {
        val viewModel = givenAViewModel()
        viewModel.injectSounds(listOf(testSound("alpha", "a.mp3")))

        viewModel.onSearchQueryChange("zzzz")

        // Same trigger for both, so zero results over performed is the zero-result rate.
        assertThat(fake.assertEmitted("search_performed").params["results"]).isEqualTo(0)
        fake.assertEmitted("search_zero_results")
    }

    @Test
    fun `clearing the query does not emit search_performed`() {
        val viewModel = givenAViewModel()
        viewModel.injectSounds(listOf(testSound("alpha", "a.mp3")))

        viewModel.onSearchQueryChange("")

        fake.assertNotEmitted("search_performed")
    }

    @Test
    fun `refreshing results after a pin does not emit another search_performed`() {
        val viewModel = givenAViewModel()
        val sound = testSound("alpha", "a.mp3")
        viewModel.injectSounds(listOf(sound))
        viewModel.onSearchQueryChange("alp")

        viewModel.togglePin(sound)

        // The pin re-filters what is shown; the user did not search again.
        assertThat(fake.events.count { it.name == "search_performed" }).isEqualTo(1)
    }

    @Test
    fun `refreshing a zero-result search after a pin does not emit another search_zero_results`() {
        val viewModel = givenAViewModel()
        val sound = testSound("alpha", "a.mp3")
        viewModel.injectSounds(listOf(sound))
        viewModel.onSearchQueryChange("zzzz")

        viewModel.togglePin(sound)

        // Numerator and denominator share one trigger, so the rate can never pass 100%.
        assertThat(fake.events.count { it.name == "search_zero_results" }).isEqualTo(1)
        assertThat(fake.events.count { it.name == "search_performed" }).isEqualTo(1)
    }

    private fun givenAViewModel(): SoundsViewModel = buildLoadedSoundsViewModel(createdViewModels, searchDebounceMs = 0L)

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }
}
