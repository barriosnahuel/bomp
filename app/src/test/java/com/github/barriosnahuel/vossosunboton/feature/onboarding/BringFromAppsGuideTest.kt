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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * Smoke + close-callback coverage for the [BringFromAppsGuide] single-step guide. Runs with system
 * animations off so the reused IMPORT demo's looping animation never keeps the Compose clock busy past
 * `waitForIdle`, mirroring `OnboardingTourTest`.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class BringFromAppsGuideTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @Test
    fun `renders the import lesson and its terminal CTA`() {
        setGuide()

        composeTestRule.onNodeWithText("Bring in the voices you already have.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Got it").assertIsDisplayed()
    }

    @Test
    fun `tapping Got it invokes onClose`() {
        var closed = false
        setGuide(onClose = { closed = true })

        composeTestRule.onNodeWithText("Got it").performClick()
        composeTestRule.waitForIdle()

        assertThat(closed).isTrue()
    }

    private fun setGuide(onClose: () -> Unit = {}) {
        composeTestRule.setContent {
            AppTheme {
                BringFromAppsGuide(onClose = onClose)
            }
        }
        composeTestRule.waitForIdle()
    }
}
