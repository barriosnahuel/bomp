/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AboutScreenFlowTest : AbstractUiTest() {
    @Test
    fun overflow_menu_opens_about_screen() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).assertIsDisplayed()
            composeRule.onNodeWithText(licenseLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun back_arrow_returns_to_landing() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).performClick()
            composeRule.waitForIdle()
            // Search FAB on Landing is back in view.
            composeRule.onNodeWithContentDescription(context.getString(R.string.app_search)).assertIsDisplayed()
        }
    }

    @Test
    fun system_back_returns_to_landing() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            Espresso.pressBack()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(context.getString(R.string.app_search)).assertIsDisplayed()
        }
    }

    @Test
    fun play_branding_audio_button_is_clickable() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule
                .onNodeWithContentDescription(playBrandingAudioLabel())
                .assertHasClickAction()
                .performClick()
            composeRule.waitForIdle()
            // SoundPool playback is fire-and-forget; nothing to assert in the UI tree, but
            // performing the click verifies the handler does not throw.
        }
    }

    @Test
    fun tap_credits_row_expands_section_with_ai_attribution_cards() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            // Before expansion the AI cards are not in the tree.
            composeRule.onAllNodes(hasText(geminiName())).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "Gemini card visible before expanding Credits." }
            }
            composeRule.onNodeWithText(creditsLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(geminiName()).assertIsDisplayed()
            composeRule.onNodeWithText(claudeName()).assertIsDisplayed()
        }
    }

    @Test
    fun license_button_opens_modal_bottom_sheet_with_agpl_excerpt() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithText(licenseLabel()).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                // The license file (raw/app_license) starts with the AGPL header.
                composeRule
                    .onAllNodes(hasText("GNU AFFERO GENERAL PUBLIC LICENSE", substring = true))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun source_code_button_emits_action_view_intent_to_repo() {
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithText(sourceLabel()).performClick()
            composeRule.waitForIdle()
            // The data URL is unique to this button's ACTION_VIEW; matching the URL alone
            // is sufficient and avoids the hamcrest 1.3 `allOf(2-arg)` API gap.
            intended(hasData(context.getString(R.string.app_about_source_url)))
        }
    }

    @Test
    fun about_screen_exposes_a11y_content_descriptions() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).assertHasClickAction()
            composeRule.onNodeWithContentDescription(playBrandingAudioLabel()).assertHasClickAction()
        }
    }

    private fun openAbout() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.app_about)).performClick()
        composeRule.waitForIdle()
    }

    private fun backLabel() = context.getString(R.string.app_about_back)

    private fun playBrandingAudioLabel() = context.getString(R.string.app_about_play_branding_audio)

    private fun licenseLabel() = context.getString(R.string.app_about_license)

    private fun sourceLabel() = context.getString(R.string.app_about_source)

    private fun creditsLabel() = context.getString(R.string.app_about_credits)

    private fun geminiName() = context.getString(R.string.app_about_ai_gemini_name)

    private fun claudeName() = context.getString(R.string.app_about_ai_claude_name)

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
