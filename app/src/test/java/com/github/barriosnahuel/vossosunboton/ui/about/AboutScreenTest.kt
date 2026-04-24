/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import junit.framework.TestCase.assertTrue
import org.junit.After
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

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
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

    @Test
    fun `pronunciation is shown when locale is English`() {
        Locale.setDefault(Locale.ENGLISH)
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_pronunciation))
            .assertIsDisplayed()
    }

    @Test
    fun `pronunciation is hidden when locale is not English`() {
        Locale.setDefault(Locale("es"))
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_pronunciation))
            .assertDoesNotExist()
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

    // --- Legal section ---

    @Test
    fun `source license button is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_license))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `source code button is displayed`() {
        launch()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_about_source))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `license bottom sheet opens on license button click`() {
        launch()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about_license)).performScrollTo().performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("GNU AFFERO GENERAL PUBLIC LICENSE", substring = true)
            .assertIsDisplayed()
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
}
