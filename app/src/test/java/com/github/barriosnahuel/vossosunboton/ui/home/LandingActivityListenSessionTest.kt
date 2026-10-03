/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** When the grid listen session closes because the Bomper left the Landing screen. */
internal class LandingActivityListenSessionTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fake = FakeAnalyticsTracker()
    private val sound = testSound("grid", "grid.mp3")

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(fake)
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    private fun ActivityScenario<LandingActivity>.startGridPlayback() {
        composeTestRule.waitForIdle()
        onActivity { activity ->
            ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                .onPlayerStart(sound, durationMs = 1_000, positionMs = 0)
        }
        fake.assertEmitted("listen_session_start")
    }

    @Test
    fun `leaving the screen closes the grid listen session`() {
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            scenario.startGridPlayback()

            scenario.moveToState(Lifecycle.State.CREATED)

            fake.assertEmitted("listen_session_end")
        }
    }

    @Test
    fun `rotating keeps the grid listen session open`() {
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            scenario.startGridPlayback()

            scenario.recreate()
            composeTestRule.waitForIdle()

            fake.assertNotEmitted("listen_session_end")
            assertThat(fake.events.count { it.name == "listen_session_start" }).isEqualTo(1)
        }
    }
}
