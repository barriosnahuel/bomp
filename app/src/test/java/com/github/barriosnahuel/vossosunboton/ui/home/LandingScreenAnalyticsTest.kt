/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
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

    @Test
    fun `tapping see how it works in the overflow emits onboarding_opened with source overflow_menu`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).performClick()
        composeTestRule.waitForIdle()
        // The menu item shares its label with the welcome footer; address it by its stable tag so this
        // resolves the overflow entry even when the footer renders the same text.
        composeTestRule.onNodeWithTag(OVERFLOW_SEE_HOW_IT_WORKS_TAG).performClick()
        composeTestRule.waitForIdle()

        val event = fake.assertEmitted("onboarding_opened")
        assertThat(event.params["source"]).isEqualTo("overflow_menu")
    }

    @Test
    fun `tapping the FAB emits import_hub_opened with source fab`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        // A fresh install seeds the welcome sticker, so the list is non-empty and the FAB shows.
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_hub_fab_description)).performClick()
        composeTestRule.waitForIdle()

        val event = fake.assertEmitted("import_hub_opened")
        assertThat(event.params["source"]).isEqualTo("fab")
    }

    @Test
    fun `tapping the empty-state Import CTA emits import_hub_opened with source my_sounds_empty_state`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        // Empty the rendered list to reach the welcome-empty state, where the inline "Import" CTA
        // (not the FAB) carries the open. Wait for init's load so the injection is not overwritten.
        runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { viewModel.isInitialLoadComplete.first { it } } }
        viewModel.injectSounds(emptyList())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.app_my_sounds_empty_cta)).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val event = fake.assertEmitted("import_hub_opened")
        assertThat(event.params["source"]).isEqualTo("my_sounds_empty_state")
    }

    @Test
    fun `tapping the import row in the Hub emits import_hub_import_selected`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_hub_fab_description)).performClick()
        composeTestRule.waitForIdle()
        // The row animates the sheet closed before invoking onImport (see ImportHubSheetTest).
        composeTestRule.onNodeWithText(context.getString(R.string.app_hub_import)).performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("import_hub_import_selected")
    }

    @Test
    fun `tapping the record row in the Hub emits import_hub_record_selected`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_hub_fab_description)).performClick()
        composeTestRule.waitForIdle()
        // The row animates the sheet closed before invoking onRecord (see ImportHubSheetTest).
        composeTestRule.onNodeWithText(context.getString(R.string.app_hub_record)).performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("import_hub_record_selected")
    }

    @Test
    fun `tapping the bring-from-apps row in the Hub emits import_hub_bring_selected`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_hub_fab_description)).performClick()
        composeTestRule.waitForIdle()
        // The row animates the sheet closed before invoking onBringFromApps (see ImportHubSheetTest).
        composeTestRule.onNodeWithText(context.getString(R.string.app_hub_bring)).performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("import_hub_bring_selected")
    }

    @Test
    fun `finishing the onboarding tour opens the Hub with source onboarding_finish`() {
        val viewModel = givenAViewModel()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        // Reach the welcome-empty state, whose "See how it works" secondary opens the tour; that
        // path never touches isHubVisible, so the finish-time openHub guard must still fire.
        runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { viewModel.isInitialLoadComplete.first { it } } }
        viewModel.injectSounds(emptyList())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.app_my_sounds_empty_secondary)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        // Tap through the three step CTAs; the last ("Start") finishes and drops the user on the Hub.
        composeTestRule.onNodeWithText(context.getString(R.string.app_onboarding_step1_cta)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.app_onboarding_step2_cta)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.app_onboarding_step3_cta)).performClick()
        composeTestRule.waitForIdle()

        val event = fake.assertEmitted("import_hub_opened")
        assertThat(event.params["source"]).isEqualTo("onboarding_finish")
    }

    // Drives the rendered list (`_sounds`) directly to reach the welcome-empty state — a fresh
    // install seeds the welcome sticker, so emptying it is the only way there. Inject AFTER init's
    // load has settled so the in-flight load does not overwrite the value.
    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
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

    private companion object {
        // Bounds the init-load await so a missed signal fails in seconds, not at CI's no-output
        // timeout (ADR ratchet on unbounded runBlocking flow-awaits in tests).
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
