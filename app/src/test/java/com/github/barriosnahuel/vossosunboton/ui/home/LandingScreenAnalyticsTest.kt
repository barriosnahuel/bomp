/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class LandingScreenAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fake: FakeAnalyticsTracker

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `LandingScreen emits screen_view my_sounds on initial composition`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        fake.assertScreenView(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `LandingScreen emits screen_view explore_sounds when explore tab is selected`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
        composeTestRule.waitForIdle()

        fake.assertScreenView(CanonicalScreenName.EXPLORE_SOUNDS)
    }

    @Test
    fun `LandingScreen emits screen_view search_sound when the search overlay opens`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.showSearch()
        composeTestRule.waitForIdle()

        fake.assertScreenView(CanonicalScreenName.SEARCH_SOUND)
    }

    @Test
    fun `LandingScreen re-emits screen_view of the active tab when search overlay closes`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.showSearch()
        composeTestRule.waitForIdle()
        viewModel.hideSearch()
        composeTestRule.waitForIdle()

        val mySoundsHits = fake.screens.count { it.name == CanonicalScreenName.MY_SOUNDS }
        assertThat(mySoundsHits).isAtLeast(2)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel =
        SoundsViewModel(
            ApplicationProvider.getApplicationContext(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
}
