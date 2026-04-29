/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AboutScreenFlowTest : AbstractUiTest() {
    @Test
    fun overflowMenuOpensAboutScreen() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).assertIsDisplayed()
            composeRule.onNodeWithText(licenseLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun backArrowReturnsToLanding() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).performClick()
            composeRule.waitForIdle()
            // Search FAB on Landing is back in view.
            composeRule.onNodeWithContentDescription(context.getString(R.string.app_search)).assertIsDisplayed()
        }
    }

    @Test
    fun systemBackReturnsToLanding() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            Espresso.pressBack()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(context.getString(R.string.app_search)).assertIsDisplayed()
        }
    }

    @Test
    fun playBrandingAudioButtonIsClickable() {
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
    fun tapCreditsRowExpandsSectionWithAiAttributionCards() {
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
    fun licenseButtonOpensModalBottomSheetWithAgplExcerpt() {
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
    fun sourceCodeButtonEmitsActionViewIntentToRepo() {
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithText(sourceLabel()).performScrollTo().performClick()
            composeRule.waitForIdle()
            // The data URL is unique to this button's ACTION_VIEW; matching the URL alone
            // is sufficient and avoids the hamcrest 1.3 `allOf(2-arg)` API gap.
            intended(hasData(context.getString(R.string.app_about_source_url)))
        }
    }

    @Test
    fun privacyPolicyItemEmitsActionViewIntentWithLocalizedHl() {
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithText(privacyPolicyLabel()).performScrollTo().performClick()
            composeRule.waitForIdle()
            intended(hasData(matchesSupportedHl(context.getString(R.string.app_about_privacy_policy_url))))
        }
    }

    @Test
    fun dataSafetyItemEmitsActionViewIntentWithLocalizedHl() {
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithText(dataSafetyLabel()).performScrollTo().performClick()
            composeRule.waitForIdle()
            intended(hasData(matchesSupportedHl(context.getString(R.string.app_about_data_safety_url))))
        }
    }

    @Test
    fun externalLegalItemsExposeOpensInBrowserHint() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithText(privacyPolicyLabel()).performScrollTo()
            composeRule
                .onAllNodesWithContentDescription(context.getString(R.string.app_about_open_in_browser))
                .fetchSemanticsNodes()
                .let { nodes ->
                    assert(nodes.size == EXTERNAL_LEGAL_ITEMS) {
                        "Expected $EXTERNAL_LEGAL_ITEMS open-in-browser hints, got ${nodes.size}"
                    }
                }
        }
    }

    @Test
    fun aboutScreenExposesA11yContentDescriptions() {
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

    private fun privacyPolicyLabel() = context.getString(R.string.app_about_privacy_policy)

    private fun dataSafetyLabel() = context.getString(R.string.app_about_data_safety)

    private fun matchesSupportedHl(baseUrl: String): Matcher<Uri> =
        object : TypeSafeMatcher<Uri>() {
            override fun describeTo(description: Description) {
                description.appendText("URI matching $baseUrl with supported ?hl= value")
            }

            override fun matchesSafely(uri: Uri): Boolean {
                if ("${uri.scheme}://${uri.authority}${uri.path}" != baseUrl) return false
                val hl = uri.getQueryParameter("hl") ?: return false
                return hl in SUPPORTED_HL
            }
        }

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
        private const val EXTERNAL_LEGAL_ITEMS = 3
        private val SUPPORTED_HL = setOf("es-AR", "es-419", "es-ES", "en", "pt-BR")
    }
}
