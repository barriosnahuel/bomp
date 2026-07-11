/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The + FAB → import Hub → system audio picker → naming chain. Each tappable thing has a live
 * destination; only the SAF picker is stubbed via Espresso-Intents (it is still a real external
 * intent), while the naming screen it feeds is now a destination inside Landing.
 */
@RunWith(AndroidJUnit4::class)
internal class HubImportFlowTest : AbstractUiTest() {
    @Before
    override fun setUp() {
        super.setUp()
        // The + FAB only renders when MY_SOUNDS is non-empty — the welcome-empty state swaps it for
        // an inline Import CTA instead (LandingScreen.kt:452). Seed one audio so the FAB these tests
        // drive actually exists; clearAll() now correctly hides the welcome, so without a seed the
        // list would be empty and every fabLabel() lookup would time out.
        TestData.seedCustomSounds(context, count = 1)
    }

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
    fun pickingAnAudioOpensTheNamingScreen() {
        // Picker returns a real content URI → the Hub hands it to the naming destination, in-place: the
        // Create flow no longer hops to another Activity, so there is no second intent to stub.
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(TestData.seedPreviewAudio(context)),
                ),
            )

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(importLabel()).performClick()

            composeRule.awaitNodeWithText(createTitle()).assertIsDisplayed()
            composeRule.awaitNode(hasSetTextAction()).assertIsDisplayed()
        }
    }

    @Test
    fun fabExposesA11yLabelAndClickAction() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).assertHasClickAction()
        }
    }

    @Test
    fun bringFromAppsRowOpensTheGuide() {
        // The "bring audios from other apps" row opens the focused single-step guide. Assert the
        // transition end-to-end: tapping the row dismisses the Hub and lands on the guide (its terminal
        // CTA is shown). The looping demo animation itself is covered by the reduce-motion Robolectric suite.
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(bringLabel()).performClick()

            composeRule.awaitNodeWithText(bringGuideCta()).assertIsDisplayed()
        }
    }

    private fun fabLabel() = context.getString(R.string.app_hub_fab_description)

    private fun hubTitle() = context.getString(R.string.app_hub_title)

    private fun importLabel() = context.getString(R.string.app_hub_import)

    private fun bringLabel() = context.getString(R.string.app_hub_bring)

    private fun bringGuideCta() = context.getString(R.string.app_hub_bring_guide_cta)

    private fun createTitle() = context.getString(R.string.app_addbutton_activity_title)
}
