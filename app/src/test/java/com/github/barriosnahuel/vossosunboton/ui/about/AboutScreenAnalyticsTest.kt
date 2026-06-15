/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AboutScreenAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var fake: FakeAnalyticsTracker

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
    }

    @Test
    fun `expanding credits emits about_credits_open only on the open transition`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }
        val creditsHeader = context.getString(R.string.app_about_credits)

        composeTestRule.onNodeWithText(creditsHeader).performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("about_credits_open")

        composeTestRule.onNodeWithText(creditsHeader).performClick()
        composeTestRule.waitForIdle()

        val openHits = fake.events.count { it.name == "about_credits_open" }
        kotlin.check(openHits == 1) { "Expected single open emission across toggle, got $openHits" }
    }

    @Test
    fun `tap on license button emits about_license_open`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }

        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_license))
            .performScrollTo()
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        fake.assertEmitted("about_license_open")
    }

    @Test
    fun `tap on source code button emits about_source_open`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }

        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_source))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("about_source_open")
    }

    @Test
    fun `tap on privacy policy item emits about_privacy_policy_open`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }

        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_privacy_policy))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("about_privacy_policy_open")
    }

    @Test
    fun `tap on data safety item emits about_data_safety_open`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }

        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_data_safety))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("about_data_safety_open")
    }

    @Test
    fun `tap on terms of service item emits about_terms_of_service_open`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }

        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_terms_of_service))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        fake.assertEmitted("about_terms_of_service_open")
    }

    @Test
    fun `tap on branding audio button does not emit before the SoundPool finishes loading`() {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = {}) } }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.app_about_play_branding_audio))
            .performClick()
        composeTestRule.waitForIdle()

        // Robolectric does not invoke the SoundPool load completion callback, so soundId stays at 0
        // and the call-site rightly skips both playback and the analytics emission.
        fake.assertNotEmitted("about_branding_audio_play")
    }
}
