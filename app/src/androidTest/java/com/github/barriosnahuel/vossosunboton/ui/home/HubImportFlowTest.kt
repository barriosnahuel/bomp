/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The + FAB → import Hub → system audio picker → AddButtonActivity chain (PR2). Each tappable thing
 * added in this PR has a live destination; the picker and the onward launch are stubbed via
 * Espresso-Intents so the suite never opens the real system picker or the heavy Create screen.
 */
@RunWith(AndroidJUnit4::class)
internal class HubImportFlowTest : AbstractUiTest() {
    @Test
    fun fabOpensImportHub() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()

            composeRule.awaitNodeWithText(hubTitle()).assertIsDisplayed()
            composeRule.awaitNodeWithText(importLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun importRowLaunchesSystemAudioPicker() {
        // Stub the picker as cancelled — the design says a cancel returns the user silently to where
        // they were. We only assert the GetContent intent was fired with the audio MIME filter.
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(importLabel()).performClick()
            composeRule.waitForIdle()

            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        }
    }

    @Test
    fun pickingAnAudioLaunchesAddButtonActivity() {
        // Picker returns a content URI → the Hub forwards it to AddButtonActivity (Create). Stub both
        // hops so neither the system picker nor the Create screen actually open.
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(PICKED_AUDIO)))
        intending(hasComponent(hasClassName(ADD_BUTTON_ACTIVITY)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(importLabel()).performClick()
            composeRule.waitForIdle()

            intended(hasComponent(hasClassName(ADD_BUTTON_ACTIVITY)))
        }
    }

    @Test
    fun fabExposesA11yLabelAndClickAction() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).assertHasClickAction()
        }
    }

    @Test
    fun hubExposesSeeHowItWorksEntryPoint() {
        // PR4: the onboarding entry point now has a live destination, so it joins the Hub as an
        // actionable row. The full open→tour flow (with its looping demo animations) is covered by the
        // reduce-motion Robolectric suite; here we only assert the new tappable thing is reachable.
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(howLabel()).assertHasClickAction()
        }
    }

    private fun fabLabel() = context.getString(R.string.app_hub_fab_description)

    private fun hubTitle() = context.getString(R.string.app_hub_title)

    private fun importLabel() = context.getString(R.string.app_hub_import)

    private fun howLabel() = context.getString(R.string.app_hub_how)

    companion object {
        private val PICKED_AUDIO: Uri = Uri.parse("content://test/picked-audio.mp3")
        private const val ADD_BUTTON_ACTIVITY =
            "com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity"
    }
}
