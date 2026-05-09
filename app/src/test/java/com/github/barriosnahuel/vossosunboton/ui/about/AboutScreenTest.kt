/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.Locale

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@Suppress("TooManyFunctions")
internal class AboutScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fakeTracker = FakeAnalyticsTracker()
    private val originalLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(fakeTracker)
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        Locale.setDefault(originalLocale)
    }

    private fun launch(onBack: () -> Unit = {}) {
        composeTestRule.setContent { AppTheme { AboutScreen(onBack = onBack) } }
    }

    // --- Hero section ---

    @Test
    fun `AboutScreen renders without crashing`() {
        launch()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `app name is displayed`() {
        launch()
        composeTestRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
    }

    @Test
    fun `tagline is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_tagline), substring = true)
            .assertIsDisplayed()
    }

    // --- Audio button ---

    @Test
    fun `audio branding button is visible and enabled`() {
        launch()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.app_about_play_branding_audio))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    // --- Collapsible credits ---

    @Test
    fun `credits header is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_credits))
            .assertIsDisplayed()
    }

    @Test
    fun `credits content is hidden by default`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_ai_gemini_name))
            .assertDoesNotExist()
    }

    @Test
    fun `expanding credits reveals AI copilot cards`() {
        launch()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_credits)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_ai_gemini_name)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_ai_claude_name)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `expanding credits reveals first-audios collaborators card`() {
        launch()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_credits)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_collaborators_first_audios_name))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_collaborators_first_audios_role))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `collaborators card is hidden when credits are collapsed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_collaborators_first_audios_name))
            .assertDoesNotExist()
    }

    @Test
    fun `expanding credits reveals library entries`() {
        launch()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_credits)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Kotlin Programming Language", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `credits collapse when tapped again`() {
        launch()
        val creditsHeader = context.getString(R.string.app_about_credits)
        val geminiName = context.getString(R.string.app_about_ai_gemini_name)
        composeTestRule.onNodeWithText(creditsHeader).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(creditsHeader).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.onNodeWithText(geminiName).assertDoesNotExist()
    }

    // --- Legal & Privacy section ---

    @Test
    fun `legal section header is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_legal_section_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `source license item is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_license))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `privacy policy item is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_privacy_policy))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `data safety item is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_data_safety))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `source code item is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_source))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `external items expose the opens-in-browser accessibility hint`() {
        launch()
        composeTestRule
            .onAllNodesWithContentDescription(context.getString(R.string.app_about_open_in_browser))
            .assertCountEquals(EXTERNAL_LEGAL_ITEMS)
    }

    @Test
    fun `tap on privacy policy when the intent succeeds does not show the no-browser snackbar`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_privacy_policy))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_error_no_browser))
            .assertDoesNotExist()
    }

    @Test
    fun `license bottom sheet opens on license item click`() {
        launch()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_license)).performScrollTo().performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("GNU AFFERO GENERAL PUBLIC LICENSE", substring = true)
            .assertIsDisplayed()
    }

    // --- Gratitude section ---

    @Test
    fun `gratitude eyebrow title and body are displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_eyebrow))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_body))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `kofi button is always displayed regardless of locale`() {
        Locale.setDefault(Locale.US)
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_kofi_button))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `cafecito button is displayed when locale country is AR`() {
        Locale.setDefault(Locale("es", "AR"))
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_cafecito_button))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `cafecito button is hidden when locale country is not AR`() {
        Locale.setDefault(Locale.US)
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_cafecito_button))
            .assertDoesNotExist()
    }

    @Test
    fun `tap on kofi emits about_gratitude_kofi_open after intent succeeds`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_kofi_button))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        fakeTracker.assertEmitted("about_gratitude_kofi_open")
    }

    @Test
    fun `tap on cafecito emits about_gratitude_cafecito_open after intent succeeds`() {
        Locale.setDefault(Locale("es", "AR"))
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_gratitude_cafecito_button))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        fakeTracker.assertEmitted("about_gratitude_cafecito_open")
    }

    // --- Back navigation ---

    @Test
    fun `back button invokes onBack callback`() {
        var called = false
        launch(onBack = { called = true })
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.app_about_back))
            .performClick()
        assertTrue(called)
    }

    private companion object {
        const val EXTERNAL_LEGAL_ITEMS = 4
    }
}
