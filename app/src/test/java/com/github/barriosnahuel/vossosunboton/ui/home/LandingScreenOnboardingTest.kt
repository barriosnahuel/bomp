/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
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

/**
 * Integration coverage for the onboarding tour wired through the real [LandingScreen]: every entry
 * point (empty-state secondary, welcome footer, overflow menu) opens it, the final CTA lands on the Hub
 * (returning to My Bomps first when opened from another tab), skipping returns to My Bomps, and the step
 * index is durable across an Activity recreate. System animations are off so the looping touch-dot
 * indicator never keeps the Compose clock busy past `waitForIdle`.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class LandingScreenOnboardingTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fake: FakeAnalyticsTracker
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `Hub bring-from-apps opens the guide and emits the onboarding screen view`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(BRING_LABEL).performClick()
        composeTestRule.waitForIdle()

        // The guide reuses the IMPORT step content but reports its own BRING_GUIDE screen_view (kept
        // distinct from the onboarding tour); its entry is the import_hub_bring_selected event.
        composeTestRule.onNodeWithText(STEP1_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(GUIDE_CTA).assertIsDisplayed()
        fake.assertScreenView(CanonicalScreenName.BRING_GUIDE)
        fake.assertEmitted("import_hub_bring_selected")
    }

    @Test
    fun `closing the bring-from-apps guide returns to My Bomps`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(BRING_LABEL).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(GUIDE_CTA).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertIsDisplayed()
    }

    @Test
    fun `bring-from-apps guide survives an Activity recreate`() {
        val viewModel = givenAViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(BRING_LABEL).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(GUIDE_CTA).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // bringGuideVisible is rememberSaveable in the host: a recreate (rotation, theme, system kill)
        // must not silently dump the user back to My Bomps mid-guide.
        composeTestRule.onNodeWithText(GUIDE_CTA).assertIsDisplayed()
    }

    @Test
    fun `empty-state see-how-it-works opens the tour`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectSounds(emptyList())
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(SECONDARY_LABEL).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(STEP1_TITLE).assertIsDisplayed()
        assertThat(fake.assertEmitted("onboarding_opened").params["source"]).isEqualTo("my_sounds_empty_state")
    }

    @Test
    fun `welcome footer opens the tour with source welcome_footer`() {
        // Fresh install = only the welcome sticker → the list is non-empty (not the ZRP), so the footer
        // is the new user's path to the tour. The overflow's "See how it works" is closed/uncomposed, so
        // SECONDARY_LABEL is unique to the footer here.
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(SECONDARY_LABEL).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(STEP1_TITLE).assertIsDisplayed()
        assertThat(fake.assertEmitted("onboarding_opened").params["source"]).isEqualTo("welcome_footer")
    }

    @Test
    fun `welcome footer disappears once a custom Bomp exists`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectSounds(listOf(testSound("a bomp", file = "a.mp3")))
        composeTestRule.waitForIdle()

        // List no longer all-welcome → footer hidden; the overflow item is uncomposed (menu closed), so
        // "See how it works" is absent entirely.
        composeTestRule.onNodeWithText(SECONDARY_LABEL).assertDoesNotExist()
    }

    @Test
    fun `finishing the tour lands on the Hub`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromEmptyState(viewModel)
        composeTestRule.onNodeWithText("Go on").performClick()
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.onNodeWithText("Start").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(HUB_TITLE).assertIsDisplayed()
    }

    @Test
    fun `finishing the tour opened from another tab returns to My Bomps before the Hub`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        // Open the tour from the overflow (reachable on every tab) while standing on the Vault tab.
        composeTestRule.selectTab(AppTab.VAULT)

        composeTestRule.onNodeWithContentDescription(OVERFLOW_LABEL).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(OVERFLOW_SEE_HOW_IT_WORKS_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Go on").performClick()
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.onNodeWithText("Start").performClick()
        composeTestRule.waitForIdle()

        // The Hub opened so the user can add a first Bomp, AND the tab returned to My Bomps so that Bomp
        // lands where the user is looking — not into the unseen My Bomps list behind Vault.
        composeTestRule.onNodeWithText(HUB_TITLE).assertIsDisplayed()
        assertThat(fake.screens.last().name).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `skipping the tour returns to My Bomps`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromEmptyState(viewModel)
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()

        // Back on the empty My Bomps (the tour was opened from there, so the list is empty → no FAB):
        // its "see how it works" secondary is shown again, and MY_SOUNDS is re-emitted on the way back.
        composeTestRule.onNodeWithText(SECONDARY_LABEL).performScrollTo().assertIsDisplayed()
        val mySoundsHits = fake.screens.count { it.name == CanonicalScreenName.MY_SOUNDS }
        assertThat(mySoundsHits).isAtLeast(2)
    }

    @Test
    fun `tour step survives an Activity recreate`() {
        val viewModel = givenAViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromEmptyState(viewModel)
        composeTestRule.onNodeWithText("Go on").performClick()
        composeTestRule.onNodeWithText(STEP2_TITLE).assertIsDisplayed()
        val stepViewsBeforeRestore = fake.events.count { it.name == "onboarding_step_viewed" }

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // The step index is rememberSaveable in the host: a recreate must not rewind to step 1.
        composeTestRule.onNodeWithText(STEP2_TITLE).assertIsDisplayed()
        // …and must NOT emit a phantom onboarding_step_viewed (the `lastLoggedStep` guard): a rotation
        // mid-tour would otherwise inflate the funnel and mis-attribute the re-view to method=open.
        val stepViewsAfterRestore = fake.events.count { it.name == "onboarding_step_viewed" }
        assertThat(stepViewsAfterRestore).isEqualTo(stepViewsBeforeRestore)
    }

    // The full tour is reachable from the empty My Bomps secondary (the Hub now opens the focused
    // bring-from-apps guide instead). Injecting an empty list reveals the welcome-empty state that hosts it.
    private fun openTourFromEmptyState(viewModel: SoundsViewModel) {
        viewModel.injectSounds(emptyList())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(SECONDARY_LABEL).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { vm.isInitialLoadComplete.first { it } } }
        return vm
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            // Safe: _sounds is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }

    private companion object {
        // Bounds the init-load await so a missed signal fails in seconds, not at CI's no-output
        // timeout (ADR ratchet on unbounded runBlocking flow-awaits in tests).
        const val AWAIT_TIMEOUT_MS = 5_000L
        const val HUB_TITLE = "How do you add one?"
        const val OVERFLOW_LABEL = "More options"
        const val SECONDARY_LABEL = "See how it works"
        const val BRING_LABEL = "Bring audios from other apps"
        const val GUIDE_CTA = "Got it"
        const val STEP1_TITLE = "Bring in the voices you already have."
        const val STEP2_TITLE = "Keep them your way."
    }
}
