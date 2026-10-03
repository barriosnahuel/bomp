/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.welcomeSticker
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * What a long listen reports when it starts, and the grid's own session pair. Lives apart from [SoundsViewModelAnalyticsTest] so that
 * class stays under detekt's LargeClass budget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SoundsViewModelListenSessionAnalyticsTest : AbstractRobolectricTest() {
    private lateinit var fake: FakeAnalyticsTracker
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        runBlocking { SoundsRepository(ApplicationProvider.getApplicationContext()).clearForTest() }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startListenSession(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    private fun givenAViewModel(): SoundsViewModel {
        val vm = buildLoadedSoundsViewModel(createdViewModels)
        runBlocking { delay(50) }
        return vm
    }

    @Test
    fun `startListenSession emits sound_play on the listening surface, not the tab underneath`() {
        val viewModel = givenAViewModel()
        viewModel.setActiveTab(AppTab.VAULT)
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.startListenSession(sound)

        // Regression: this reported `vault`, which made a long listen indistinguishable from a
        // Vault list tap in every play query.
        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.VAULT_LISTEN)
    }

    @Test
    fun `startListenSession does not open a session — a resume would count as a second one`() {
        val viewModel = givenAViewModel()
        viewModel.setActiveTab(AppTab.VAULT)
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.startListenSession(sound)

        // The session pair belongs to the listening screen: a resume after a pause re-enters this
        // method, so emitting here would report one session per play tap.
        fake.assertNotEmitted("listen_session_start")
    }

    @Test
    fun `a grid playback reports its session on the tab it was played from`() {
        val viewModel = givenAViewModel()
        viewModel.setActiveTab(AppTab.VAULT)
        val sound = testSound("grid", "grid.mp3")

        viewModel.onPlayerStart(sound, durationMs = 1_000, positionMs = 0)
        viewModel.onProgressUpdate(400)
        viewModel.onPlayerStop(sound, completed = true)

        assertThat(fake.assertEmitted("listen_session_start").params["surface"]).isEqualTo(CanonicalScreenName.VAULT)
        val end = fake.assertEmitted("listen_session_end")
        assertThat(end.params["surface"]).isEqualTo(CanonicalScreenName.VAULT)
        assertThat(end.params["listened_ms"]).isEqualTo(1_000)
        assertThat(end.params["duration_ms"]).isEqualTo(1_000)
    }

    @Test
    fun `the welcome sticker opens no grid session, like it reports no sound_play`() {
        val viewModel = givenAViewModel()
        val welcome = welcomeSticker(ApplicationProvider.getApplicationContext())

        viewModel.onPlayerStart(welcome, durationMs = 1_000, positionMs = 0)
        viewModel.onPlayerStop(welcome, completed = true)

        fake.assertNotEmitted("listen_session_start")
        fake.assertNotEmitted("listen_session_end")
    }

    @Test
    fun `a long-form listen opens no grid session — the listening screen reports its own`() {
        val viewModel = givenAViewModel()
        val sound = testSound("long", "long.mp3")
        viewModel.startListenSession(sound)

        viewModel.onPlayerStart(sound, durationMs = 60_000, positionMs = 0)
        viewModel.onPlayerStop(sound, completed = true)

        fake.assertNotEmitted("listen_session_start")
        fake.assertNotEmitted("listen_session_end")
    }

    @Test
    fun `the same audio played from the grid after its long listen ended is a grid session`() {
        val viewModel = givenAViewModel()
        val sound = testSound("long", "long.mp3")
        viewModel.startListenSession(sound)
        viewModel.onPlayerStart(sound, durationMs = 60_000, positionMs = 0)
        viewModel.onPlayerStop(sound, completed = false)

        viewModel.playOrStop(sound)
        viewModel.onPlayerStart(sound, durationMs = 60_000, positionMs = 0)

        assertThat(fake.assertEmitted("listen_session_start").params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `the session keeps the surface of the tap even if the tab changes before the audio starts`() {
        val viewModel = givenAViewModel()
        val sound = testSound("grid", "grid.mp3")

        viewModel.playOrStop(sound)
        viewModel.setActiveTab(AppTab.VAULT)
        viewModel.onPlayerStart(sound, durationMs = 1_000, positionMs = 0)

        assertThat(fake.assertEmitted("listen_session_start").params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `pause then another audio then back to the first reports three sessions in order`() {
        val viewModel = givenAViewModel()
        val a = testSound("a", "a.mp3")
        val b = testSound("b", "b.mp3")

        // The controller's real order: a preemption fires onPlayerPause for the old audio first.
        viewModel.onPlayerStart(a, durationMs = 1_000, positionMs = 0)
        viewModel.onProgressUpdate(200)
        viewModel.onPlayerPause(a, positionMs = 250, durationMs = 1_000)
        viewModel.onPlayerPause(a, positionMs = 250, durationMs = 1_000)
        viewModel.onPlayerStart(b, durationMs = 1_000, positionMs = 0)
        viewModel.onPlayerPause(b, positionMs = 100, durationMs = 1_000)
        viewModel.onPlayerStart(a, durationMs = 1_000, positionMs = 250)
        viewModel.onLeftForeground()

        val sessions = fake.events.map { it.name }.filter { it.startsWith("listen_session") }
        assertThat(sessions)
            .containsExactly(
                "listen_session_start",
                "listen_session_end",
                "listen_session_start",
                "listen_session_end",
                "listen_session_start",
                "listen_session_end",
            ).inOrder()
        val listened = fake.events.filter { it.name == "listen_session_end" }.map { it.params["listened_ms"] }
        assertThat(listened).containsExactly(250, 100, 0).inOrder()
    }

    @Test
    fun `leaving the screen closes the grid session while the audio is still loaded`() {
        val viewModel = givenAViewModel()
        val sound = testSound("grid", "grid.mp3")
        viewModel.onPlayerStart(sound, durationMs = 1_000, positionMs = 0)
        viewModel.onPlayerPause(sound, positionMs = 300, durationMs = 1_000)

        viewModel.onLeftForeground()

        assertThat(fake.assertEmitted("listen_session_end").params["listened_ms"]).isEqualTo(300)
    }
}
