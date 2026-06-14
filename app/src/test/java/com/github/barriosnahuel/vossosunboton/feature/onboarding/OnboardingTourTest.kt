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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Step-routing + smoke coverage for the [OnboardingTour] overlay in isolation (the host's state
 * plumbing is covered by `LandingScreenOnboardingTest`). Runs with system animations off so the
 * looping touch-dot indicator never keeps the Compose clock busy past `waitForIdle`.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class OnboardingTourTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun disableAnimations() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun `renders each step and advances through the tour`() {
        setTour()

        composeTestRule.onNodeWithText("Bring in the voices you already have.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go on").performClick()

        composeTestRule.onNodeWithText("Keep them forever, your way.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").performClick()

        composeTestRule.onNodeWithText("Send it to whoever needs to hear it.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun `the last step CTA invokes onFinish`() {
        var finished = false
        setTour(initialStep = 2, onFinish = { finished = true })

        composeTestRule.onNodeWithText("Start").performClick()

        assertThat(finished).isTrue()
    }

    @Test
    fun `Skip invokes onSkip`() {
        var skipped = false
        setTour(onSkip = { skipped = true })

        composeTestRule.onNodeWithText("Skip").performClick()

        assertThat(skipped).isTrue()
    }

    @Test
    fun `back from a later step returns to the previous step`() {
        setTour()
        composeTestRule.onNodeWithText("Go on").performClick()
        composeTestRule.onNodeWithText("Keep them forever, your way.").assertIsDisplayed()

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
