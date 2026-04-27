/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundDao
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

internal class SoundsViewModelAnalyticsTest : AbstractRobolectricTest() {
    private lateinit var fake: FakeAnalyticsTracker

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        ApplicationProvider
            .getApplicationContext<android.app.Application>()
            .getSharedPreferences("my-prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `togglePin emits pin_toggle with the resulting pinned state`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        val event = fake.assertEmitted("pin_toggle")
        assertThat(event.params["pinned"]).isEqualTo(true)
    }

    @Test
    fun `playOrStop emits sound_play with surface = my_sounds when home tab is active`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)

        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `playOrStop emits sound_play with surface = explore_sounds when explore tab is active`() {
        val viewModel = givenAViewModel()
        viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
        val sound = Sound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)

        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.EXPLORE_SOUNDS)
    }

    @Test
    fun `playOrStop emits sound_play with surface = search_sound while search overlay is visible`() {
        val viewModel = givenAViewModel()
        viewModel.showSearch()
        val sound = Sound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)

        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.SEARCH_SOUND)
    }

    @Test
    fun `playOrStop increments lifetime_plays user property monotonically`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)
        viewModel.playOrStop(sound)

        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_PLAYS]).isEqualTo("2")
    }

    @Test
    fun `playOrStop on a playing sound does not emit sound_play`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", null, 1, isPlaying = true)

        viewModel.playOrStop(sound)

        fake.assertNotEmitted("sound_play")
    }

    @Test
    fun `confirmDelete emits sound_delete after the dao commit`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = givenAViewModel()
        val sound = Sound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.confirmDelete(context)

        fake.assertEmitted("sound_delete")
    }

    @Test
    fun `confirmDelete on a bundled sound does not emit sound_delete`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = givenAViewModel()
        val bundled = Sound("bundled", rawRes = 1)
        viewModel.injectSounds(listOf(bundled))
        viewModel.deleteSound(bundled)

        viewModel.confirmDelete(context)

        fake.assertNotEmitted("sound_delete")
    }

    @Test
    fun `restoreSound emits sound_delete_undone and never sound_delete`() {
        val viewModel = givenAViewModel()
        val sound = Sound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.restoreSound()

        fake.assertEmitted("sound_delete_undone")
        fake.assertNotEmitted("sound_delete")
    }

    @Test
    fun `togglePin updates current_pinned user property to the count of pinned sounds`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_PINNED]).isEqualTo("1")
    }

    @Test
    fun `loadSounds emits milestone_sounds_3 when crossing the threshold for the first time`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dao = SoundDao()
        repeat(3) { idx -> dao.save(context, Sound("custom-$idx", "custom-$idx.mp3")) }

        givenAViewModel()

        fake.assertEmitted("milestone_sounds_3")
    }

    @Test
    fun `loadSounds does not re-emit milestone_sounds_3 once already fired`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dao = SoundDao()
        repeat(3) { idx -> dao.save(context, Sound("custom-$idx", "custom-$idx.mp3")) }
        givenAViewModel()
        // Clear only the recorded events, NOT the fired flags — simulates the same install loading the VM again.
        fake.events.clear()

        givenAViewModel()

        fake.assertNotEmitted("milestone_sounds_3")
    }

    @Test
    fun `search emits search_zero_results with the query length when there is no match`() {
        val viewModel = givenAViewModel(searchDebounceMs = 0L)
        viewModel.injectSounds(listOf(Sound("alpha", "a.mp3"), Sound("beta", "b.mp3")))

        viewModel.onSearchQueryChange("zzzz")

        val event = fake.assertEmitted("search_zero_results")
        assertThat(event.params["query_length"]).isEqualTo(4)
    }

    @Test
    fun `search does not emit search_zero_results when there is at least one match`() {
        val viewModel = givenAViewModel(searchDebounceMs = 0L)
        viewModel.injectSounds(listOf(Sound("alpha", "a.mp3"), Sound("beta", "b.mp3")))

        viewModel.onSearchQueryChange("alp")

        fake.assertNotEmitted("search_zero_results")
    }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(searchDebounceMs: Long = 200L): SoundsViewModel =
        SoundsViewModel(
            ApplicationProvider.getApplicationContext(),
            ioDispatcher = UnconfinedTestDispatcher(),
            searchDebounceMs = searchDebounceMs,
        )
}
