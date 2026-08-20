/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.home.cancelAndJoinAll
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Transport use from the app's own listening screen. Its twin — the same actions driven from the
 * media notification or lock screen — is guarded by `ListenSessionTransportAnalyticsTest`; together
 * they are what makes the `origin` param readable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ImmersiveTransportAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val fake = FakeAnalyticsTracker()
    private val controller = mockk<PlayerController>(relaxed = true)
    private val createdViewModels = mutableListOf<SoundsViewModel>()
    private lateinit var soundId: String

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5_000L
    }

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(fake)
        // A relaxed controller, not a chain of `every { ...instance.x() }`: chained stubbing builds a
        // NON-relaxed mock, so any controller call this flow happens to make without an explicit stub
        // throws MockKException — and which calls happen depends on timing, so it only bites in CI.
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance } returns controller
        every { controller.playbackState } returns MutableStateFlow(null)
        runBlocking {
            val repo = SoundsRepository(context)
            repo.clearForTest()
            repo.save(testSound("vaulted-audio", file = "vaulted.mp3"))
            soundId =
                repo.sounds
                    .first()
                    .first { it.name == "vaulted-audio" }
                    .id
        }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    private fun launchHost() {
        val viewModel =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += viewModel
        runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { viewModel.isInitialLoadComplete.first { it } } }
        composeTestRule.setContent {
            AppTheme { ImmersiveListenHost(viewModel = viewModel, soundId = soundId, onBack = {}) }
        }
        composeTestRule.waitForIdle()
        fake.reset()
    }

    private fun transportEvents() = fake.events.filter { it.name == "listen_transport" }

    @Test
    fun `tapping play on the listening screen reports transport use from the screen`() {
        launchHost()

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_play)).performClick()
        composeTestRule.waitForIdle()

        val event = transportEvents().single()
        assertThat(event.params["action"]).isEqualTo("play")
        assertThat(event.params["origin"]).isEqualTo("screen")
    }

    @Test
    fun `tapping back-to-start reports a restart from the screen`() {
        launchHost()

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_vault_immersive_restart)).performClick()
        composeTestRule.waitForIdle()

        // Before this, restarting an audio emitted nothing at all.
        val event = transportEvents().single()
        assertThat(event.params["action"]).isEqualTo("restart")
        assertThat(event.params["origin"]).isEqualTo("screen")
    }
}
