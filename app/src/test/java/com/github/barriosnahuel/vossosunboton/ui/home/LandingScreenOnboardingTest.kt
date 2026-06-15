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
 * Integration coverage for the PR4 onboarding tour wired through the real [LandingScreen]: both entry
 * points (Hub row + empty-state secondary) open it, the final CTA lands on the Hub, skipping returns
 * to My Bomps, and the step index is durable across an Activity recreate. System animations are off
 * so the looping touch-dot indicator never keeps the Compose clock busy past `waitForIdle`.
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
    fun `Hub see-how-it-works opens the tour and emits the onboarding screen view`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromHub()

        composeTestRule.onNodeWithText(STEP1_TITLE).assertIsDisplayed()
        fake.assertScreenView(CanonicalScreenName.ONBOARDING)
        assertThat(fake.assertEmitted("onboarding_opened").params["source"]).isEqualTo("import_hub")
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
    fun `finishing the tour lands on the Hub`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromHub()
        composeTestRule.onNodeWithText("Go on").performClick()
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.onNodeWithText("Start").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(HUB_TITLE).assertIsDisplayed()
    }

    @Test
    fun `skipping the tour returns to My Bomps`() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromHub()
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertIsDisplayed()
        val mySoundsHits = fake.screens.count { it.name == CanonicalScreenName.MY_SOUNDS }
        assertThat(mySoundsHits).isAtLeast(2)
    }

    @Test
    fun `tour step survives an Activity recreate`() {
        val viewModel = givenAViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        openTourFromHub()
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

    private fun openTourFromHub() {
        composeTestRule.onNodeWithContentDescription("Add a Bomp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(SECONDARY_LABEL).performClick()
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
        const val SECONDARY_LABEL = "See how it works"
        const val STEP1_TITLE = "Bring in the voices you already have."
        const val STEP2_TITLE = "Keep them your way."
    }
}
