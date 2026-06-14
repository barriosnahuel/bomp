/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
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

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class LandingScreenAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fake: FakeAnalyticsTracker
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Join every VM scope so the reactive `repo.sounds` collector each VM starts in `init`
        // cannot outlive this class and re-fire `milestone_sounds_3` (or other one-shot events)
        // into a later test's `FakeAnalyticsTracker` — see ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
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

    /**
     * Regression for the assumption documented next to `SoundsViewModel.currentSurface`: while About is the active
     * destination the search action and the sound list (the only entry points for `playOrStop` / `share`) must be
     * unreachable, so `surface` and the latest `screen_name` cannot disagree.
     */
    @Test
    fun `no play or share UI is reachable while About is open`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about)).performClick()
        composeTestRule.waitForIdle()

        // About is the active destination — proven by both the screen_view emission and the absence of the
        // home shell's chrome. The shell's search action (top app bar) and sound list are the only entry
        // points into `playOrStop` / `share`, and the open sub-screen clears them from the semantics tree.
        fake.assertScreenView(CanonicalScreenName.ABOUT)
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_search)).assertDoesNotExist()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        return vm
    }
}
