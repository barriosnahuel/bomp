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
import androidx.compose.ui.test.assertCountEquals
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
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
internal class AboutScreenFlowTest : AbstractUiTest() {
    @Test
    fun overflowMenuOpensAboutScreen() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.awaitNodeWithText(licenseLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun backArrowReturnsToLanding() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).performClick()
            // Overflow menu is the Landing sentinel (absent on About); the Search FAB is unusable here since it is gated on sound count.
            composeRule.awaitNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).assertIsDisplayed()
        }
    }

    @Test
    fun systemBackReturnsToLanding() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            Espresso.pressBack()
            composeRule.awaitNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).assertIsDisplayed()
        }
    }

    @Test
    fun aboutScreenHidesTheUnderlyingLandingFromSemantics() {
        // Layering invariant (ADR 0011): Landing is composed behind About so predictive back can
        // reveal it, but its semantics are cleared while About is open. The Landing overflow ⋮ is a
        // base-layer-only sentinel (About has only a back arrow) — it must be absent from the tree
        // while About is open, or TalkBack/tests would reach nodes hidden behind the opaque overlay.
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).assertDoesNotExist()
        }
    }

    @Test
    fun playBrandingAudioButtonIsClickable() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule
                .awaitNodeWithContentDescription(playBrandingAudioLabel())
                .assertHasClickAction()
                .performClick()
            // SoundPool playback is fire-and-forget; nothing to assert in the UI tree, but
            // performing the click verifies the handler does not throw.
        }
    }

    @Test
    fun tapCreditsRowExpandsSectionWithAiAttributionCards() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            // Before expansion the AI cards are not in the tree.
            composeRule.onAllNodes(hasText(geminiName())).assertCountEquals(0)
            composeRule.awaitNodeWithText(creditsLabel()).performClick()
            composeRule.awaitNodeWithText(geminiName()).assertIsDisplayed()
            composeRule.onNodeWithText(claudeName()).assertIsDisplayed()
        }
    }

    @Test
    fun licenseButtonOpensModalBottomSheetWithAgplExcerpt() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.awaitNodeWithText(licenseLabel()).performClick()
            // The license file (raw/app_license) starts with the AGPL header. Substring matching
            // does not fit the awaitNodeWithText helper (which is exact-match), so this stays inline.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
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
            composeRule.awaitNodeWithText(sourceLabel()).performScrollTo().performClick()
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
            composeRule.awaitNodeWithText(privacyPolicyLabel()).performScrollTo().performClick()
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
            composeRule.awaitNodeWithText(dataSafetyLabel()).performScrollTo().performClick()
            composeRule.waitForIdle()
            intended(hasData(matchesSupportedHl(context.getString(R.string.app_about_data_safety_url))))
        }
    }

    @Test
    fun externalLegalItemsExposeOpensInBrowserHint() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.awaitNodeWithText(privacyPolicyLabel()).performScrollTo()
            composeRule
                .onAllNodesWithContentDescription(context.getString(R.string.app_about_open_in_browser))
                .assertCountEquals(EXTERNAL_LEGAL_ITEMS)
        }
    }

    @Test
    fun aboutScreenExposesA11yContentDescriptions() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.onNodeWithContentDescription(backLabel()).assertHasClickAction()
            composeRule.awaitNodeWithContentDescription(playBrandingAudioLabel()).assertHasClickAction()
        }
    }

    @Test
    fun kofiButtonEmitsActionViewIntentToKofiUrl() {
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.awaitNodeWithText(kofiLabel()).performScrollTo().performClick()
            composeRule.waitForIdle()
            intended(hasData(context.getString(R.string.app_about_gratitude_kofi_url)))
        }
    }

    @Test
    fun cafecitoButtonEmitsActionViewIntentToCafecitoUrlWhenLocaleIsAR() {
        assumeTrue("Cafecito button only renders on es-AR devices", Locale.getDefault().country == "AR")
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openAbout()
            composeRule.awaitNodeWithText(cafecitoLabel()).performScrollTo().performClick()
            composeRule.waitForIdle()
            intended(hasData(context.getString(R.string.app_about_gratitude_cafecito_url)))
        }
    }

    private fun openAbout() {
        composeRule.awaitNodeWithContentDescription(context.getString(R.string.app_overflow_menu)).performClick()
        composeRule.awaitNodeWithText(context.getString(R.string.app_about)).performClick()
        composeRule.awaitNodeWithContentDescription(backLabel()).assertIsDisplayed()
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

    private fun kofiLabel() = context.getString(R.string.app_about_gratitude_kofi_button)

    private fun cafecitoLabel() = context.getString(R.string.app_about_gratitude_cafecito_button)

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
        private const val EXTERNAL_LEGAL_ITEMS = 4
        private val SUPPORTED_HL = setOf("es-AR", "es-419", "es-ES", "en", "pt-BR")
    }
}
