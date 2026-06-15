/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.onboarding

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Step-routing + "stories" tap-navigation + funnel-analytics coverage for the [OnboardingTour] overlay
 * in isolation (the host's open/source plumbing is covered by `LandingScreenOnboardingTest`). Runs with
 * system animations off so the looping touch-dot indicator never keeps the Compose clock busy past
 * `waitForIdle`.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class OnboardingTourTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fake: FakeAnalyticsTracker

    @Before
    fun setUp() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
    }

    @Test
    fun `renders each step and advances through the tour`() {
        setTour()

        composeTestRule.onNodeWithText("Bring in the voices you already have.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go on").performClick()

        composeTestRule.onNodeWithText("Keep them your way.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").performClick()

        composeTestRule.onNodeWithText("Send it to whoever needs to hear it.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun `opening the tour emits step_viewed for step 1 attributed to the open`() {
        setTour()

        val event = fake.assertEmitted("onboarding_step_viewed")
        assertThat(event.params["step"]).isEqualTo(1)
        assertThat(event.params["step_key"]).isEqualTo("import")
        assertThat(event.params["method"]).isEqualTo("open")
    }

    @Test
    fun `the last step CTA invokes onFinish and emits completed via button`() {
        var finished = false
        setTour(initialStep = 2, onFinish = { finished = true })

        composeTestRule.onNodeWithText("Start").performClick()

        assertThat(finished).isTrue()
        assertThat(fake.assertEmitted("onboarding_completed").params["method"]).isEqualTo("button")
    }

    @Test
    fun `Skip invokes onSkip and emits dismissed with the current step via button`() {
        var skipped = false
        setTour(initialStep = 1, onSkip = { skipped = true })

        composeTestRule.onNodeWithText("Skip").performClick()

        assertThat(skipped).isTrue()
        val event = fake.assertEmitted("onboarding_dismissed")
        assertThat(event.params["step"]).isEqualTo(2)
        assertThat(event.params["step_key"]).isEqualTo("organize")
        assertThat(event.params["method"]).isEqualTo("button")
    }

    @Test
    fun `back from a later step returns to the previous step`() {
        setTour()
        composeTestRule.onNodeWithText("Go on").performClick()
        composeTestRule.onNodeWithText("Keep them your way.").assertIsDisplayed()

        pressBack()

        composeTestRule.onNodeWithText("Bring in the voices you already have.").assertIsDisplayed()
    }

    @Test
    fun `back from the first step dismisses the tour`() {
        var skipped = false
        setTour(onSkip = { skipped = true })

        pressBack()

        assertThat(skipped).isTrue()
    }

    @Test
    fun `tapping the right zone advances and attributes the step view to the tap`() {
        setTour()

        composeTestRule.onNodeWithTag(ONBOARDING_TAP_NEXT).performTouchInput { click() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Keep them your way.").assertIsDisplayed()
        val views = fake.events.filter { it.name == "onboarding_step_viewed" }
        assertThat(views.last().params["step"]).isEqualTo(2)
        assertThat(views.last().params["step_key"]).isEqualTo("organize")
        assertThat(views.last().params["method"]).isEqualTo("tap")
    }

    @Test
    fun `tapping the left zone goes back a step`() {
        setTour(initialStep = 1)

        composeTestRule.onNodeWithTag(ONBOARDING_TAP_PREV).performTouchInput { click() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Bring in the voices you already have.").assertIsDisplayed()
    }

    @Test
    fun `tapping the left zone on the first step is a no-op`() {
        var skipped = false
        setTour(onSkip = { skipped = true })

        composeTestRule.onNodeWithTag(ONBOARDING_TAP_PREV).performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Still on step 1, tour not dismissed — an accidental left tap must not eject the user.
        composeTestRule.onNodeWithText("Bring in the voices you already have.").assertIsDisplayed()
        assertThat(skipped).isFalse()
    }

    @Test
    fun `tapping the right zone off the last step finishes the tour via tap`() {
        var finished = false
        setTour(initialStep = 2, onFinish = { finished = true })

        composeTestRule.onNodeWithTag(ONBOARDING_TAP_NEXT).performTouchInput { click() }
        composeTestRule.waitForIdle()

        assertThat(finished).isTrue()
        assertThat(fake.assertEmitted("onboarding_completed").params["method"]).isEqualTo("tap")
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun setTour(
        initialStep: Int = 0,
        onSkip: () -> Unit = {},
        onFinish: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                var step by remember { mutableIntStateOf(initialStep) }
                OnboardingTour(
                    step = step,
                    onAdvance = { step = (step + 1).coerceAtMost(ONBOARDING_STEP_COUNT - 1) },
                    onStepBack = { step = (step - 1).coerceAtLeast(0) },
                    onSkip = onSkip,
                    onFinish = onFinish,
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
