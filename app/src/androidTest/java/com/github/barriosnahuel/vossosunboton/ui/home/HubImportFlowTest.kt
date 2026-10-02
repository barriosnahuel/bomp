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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.WAIT_TIMEOUT_MS
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The + FAB → import Hub → bring guide → system audio picker → naming chain. Each tappable thing has a
 * live destination; only the SAF picker is stubbed via Espresso-Intents (it is still a real external
 * intent), while the naming screen it feeds is a destination inside Landing.
 */
@RunWith(AndroidJUnit4::class)
internal class HubImportFlowTest : AbstractUiTest() {
    @Before
    override fun setUp() {
        super.setUp()
        // The + FAB only renders when MY_SOUNDS is non-empty — the welcome-empty state swaps it for
        // an inline CTA instead. Seed one audio so the FAB these tests drive actually exists; clearAll()
        // hides the welcome, so without a seed the list would be empty and every fabLabel() lookup
        // would time out.
        TestData.seedCustomSounds(context, count = 1)
    }

    @Test
    fun fabOpensImportHubWithItsTwoPaths() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()

            composeRule.awaitNodeWithText(hubTitle()).assertIsDisplayed()
            composeRule.awaitNodeWithText(string(R.string.app_hub_record)).assertIsDisplayed()
            composeRule.awaitNodeWithText(bringLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun backDismissesTheHubAndReturnsToTheList() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(hubTitle()).assertIsDisplayed()

            Espresso.pressBack()

            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(hubTitle()).fetchSemanticsNodes().isEmpty()
            }
            composeRule.awaitNodeWithContentDescription(fabLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun guideFooterLaunchesSystemAudioPicker() {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openGuide()
            composeRule.awaitNodeWithText(filesLabel()).performClick()
            composeRule.waitForIdle()

            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        }
    }

    @Test
    fun cancelledPickerShowsTheNoticeOnTheGuide() {
        // A cancel used to return silently; now the guide stays up and says where voice notes come from.
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            openGuide()
            composeRule.awaitNodeWithText(filesLabel()).performClick()

            composeRule.awaitNodeWithText(string(R.string.app_import_empty_message)).assertIsDisplayed()
            composeRule.awaitNodeWithText(bringGuideCta()).assertIsDisplayed()
        }
    }

    @Test
    fun pickingAnAudioOpensTheNamingScreen() {
        // Picker returns a real content URI → the guide hands it to the naming destination, in-place: the
        // Create flow does not hop to another Activity, so there is no second intent to stub.
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(TestData.seedPreviewAudio(context)),
                ),
            )

        val analytics = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(analytics)
        try {
            ActivityScenario.launch(LandingActivity::class.java).use {
                openGuide()
                composeRule.awaitNodeWithText(filesLabel()).performClick()

                composeRule.awaitNodeWithText(createTitle()).assertIsDisplayed()
                composeRule.awaitNode(hasSetTextAction()).assertIsDisplayed()
                composeRule.waitForIdle()
            }
            // Guide → naming never passes through the list, so screen_view must not report one in between.
            val afterGuide = analytics.screens.map { it.name }.dropWhile { it != CanonicalScreenName.BRING_GUIDE }
            assertThat(afterGuide).isNotEmpty()
            assertThat(afterGuide).doesNotContain(CanonicalScreenName.MY_SOUNDS)
        } finally {
            AnalyticsTrackerProvider.setForTest(null)
        }
    }

    @Test
    fun fabExposesA11yLabelAndClickAction() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).assertHasClickAction()
        }
    }

    @Test
    fun bringRowOpensTheGuideWithItsFileBrowserFooter() {
        // Tapping the row dismisses the Hub and lands on the guide: its terminal CTA and the file-browser
        // footer are both on screen. The looping demo animation itself is covered by the reduce-motion
        // Robolectric suite.
        ActivityScenario.launch(LandingActivity::class.java).use {
            openGuide()

            composeRule.awaitNodeWithText(filesLabel()).assertIsDisplayed()
        }
    }

    private fun openGuide() {
        composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
        composeRule.awaitNodeWithText(bringLabel()).performClick()
        composeRule.awaitNodeWithText(bringGuideCta()).assertIsDisplayed()
    }

    private fun fabLabel() = string(R.string.app_hub_fab_description)

    private fun hubTitle() = string(R.string.app_hub_title)

    private fun bringLabel() = string(R.string.app_hub_bring)

    private fun filesLabel() = string(R.string.app_hub_files_cta)

    private fun bringGuideCta() = string(R.string.app_hub_bring_guide_cta)

    private fun createTitle() = string(R.string.app_addbutton_activity_title)

    private fun string(resId: Int): String = context.getString(resId)
}
