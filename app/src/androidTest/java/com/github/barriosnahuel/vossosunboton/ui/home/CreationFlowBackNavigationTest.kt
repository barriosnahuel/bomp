/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.WAIT_TIMEOUT_MS
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.feature.recorder.FakeRecorderEngine
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderEngineProvider
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderTempFiles
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back semantics of the creation flow, now that recording and naming are destinations inside Landing
 * rather than Activities (ADR 0024 D4). What "back" means depends on where naming was opened from,
 * and getting it wrong loses the user's take:
 *
 * - Recorder → naming: naming sits ON TOP of the review, so back returns the clip, it does not
 *   discard the whole flow.
 * - Import → naming: nothing sits underneath, so back lands on the tab.
 * - Save from the recording flow: pops the naming AND the recorder, so the user is never dropped back
 *   onto a clip they just saved.
 */
@RunWith(AndroidJUnit4::class)
internal class CreationFlowBackNavigationTest : AbstractUiTest() {
    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Before
    override fun setUp() {
        super.setUp()
        RecorderEngineProvider.setForTest(FakeRecorderEngine())
        // The + FAB (the only way into the Hub) renders only when My Sounds is non-empty.
        TestData.seedCustomSounds(context, count = 1)
    }

    @After
    override fun tearDown() {
        RecorderEngineProvider.setForTest(null)
        RecorderTempFiles.purge(context)
        super.tearDown()
    }

    @Test
    fun backFromNamingReturnsToTheRecorderReview() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            recordAClip()
            composeRule.awaitNodeWithText(string(R.string.app_recorder_use)).performClick()
            composeRule.awaitNodeWithText(createTitle()).assertIsDisplayed()
            composeRule.waitForIdle()

            Espresso.pressBack()
            composeRule.waitForIdle()

            // The take survives: back pops only the naming destination, landing on the review it was
            // pushed over — a user who changes their mind about the name does not lose the recording.
            composeRule.awaitNodeWithText(string(R.string.app_recorder_use)).assertIsDisplayed()
            composeRule.awaitNodeWithText(string(R.string.app_recorder_rerecord)).assertIsDisplayed()
        }
    }

    @Test
    fun backFromNamingOpenedByImportReturnsToTheTab() {
        stubPickerWithAudio()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
            composeRule.awaitNodeWithText(string(R.string.app_hub_import)).performClick()
            composeRule.awaitNodeWithText(createTitle()).assertIsDisplayed()
            composeRule.waitForIdle()

            Espresso.pressBack()
            composeRule.waitForIdle()

            // Nothing sits under the naming destination on the import path (the Hub sheet closed when the
            // picker launched), so back returns the user to the list they started from.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(createTitle()).fetchSemanticsNodes().isEmpty()
            }
            composeRule.awaitNodeWithContentDescription(fabLabel()).assertIsDisplayed()
            composeRule.awaitNodeWithText("custom_1").assertIsDisplayed()
        }
    }

    @Test
    fun savingFromTheRecordingFlowReturnsToTheTab() {
        val newName = "recorded_clip"

        ActivityScenario.launch(LandingActivity::class.java).use {
            recordAClip()
            composeRule.awaitNodeWithText(string(R.string.app_recorder_use)).performClick()
            composeRule.awaitNodeWithText(createTitle()).assertIsDisplayed()

            composeRule.awaitNode(hasSetTextAction()).performTextInput(newName)
            composeRule.awaitNodeWithText(string(R.string.app_addbutton_save)).performClick()

            // The save pops the whole creation flow (naming + recorder): a single pop would drop the user
            // back into the recorder, on a clip they just saved.
            composeRule.waitUntil(timeoutMillis = SAVE_SETTLE_MS) {
                composeRule.onAllNodesWithText(string(R.string.app_recorder_use)).fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodesWithText(createTitle()).fetchSemanticsNodes().isEmpty()
            }
            composeRule.awaitNodeWithText(newName).assertIsDisplayed()
            composeRule.awaitNodeWithContentDescription(fabLabel()).assertIsDisplayed()
        }
    }

    /** + FAB → Hub → "record" → capture past the 1 s minimum → Stop, leaving the recorder in Review. */
    private fun recordAClip() {
        composeRule.awaitNodeWithContentDescription(fabLabel()).performClick()
        composeRule.awaitNodeWithText(string(R.string.app_hub_record)).performClick()
        composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_record)).performClick()
        composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_stop)).assertIsDisplayed()
        // Below the 1 s minimum Stop rejects the clip as "too short" and never reaches Review.
        SystemClock.sleep(MIN_CLIP_WAIT_MS)
        composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_stop)).performClick()
    }

    /** The SAF picker is still a real external intent; hand it a playable audio the save path accepts. */
    private fun stubPickerWithAudio() {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(TestData.seedPreviewAudio(context)),
                ),
            )
    }

    private fun fabLabel() = string(R.string.app_hub_fab_description)

    private fun createTitle() = string(R.string.app_addbutton_activity_title)

    private fun string(resId: Int): String = context.getString(resId)

    private companion object {
        const val MIN_CLIP_WAIT_MS = 1_300L

        // The success overlay holds on screen before the flow pops, so the save needs more room than the
        // default node-wait.
        const val SAVE_SETTLE_MS = 2 * WAIT_TIMEOUT_MS
    }
}
