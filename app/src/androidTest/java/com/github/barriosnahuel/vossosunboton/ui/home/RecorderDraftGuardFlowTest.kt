/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the guard "+" → Record shows while a draft is pending (ADR 0019 § Draft
 * recovery), on the real Landing screen and DataStore: backing out of the question must leave the draft
 * and its banner exactly as they were. The JVM `LandingDraftGuardTest` covers the two choices.
 */
@RunWith(AndroidJUnit4::class)
internal class RecorderDraftGuardFlowTest : AbstractUiTest() {
    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun backingOutOfTheGuardKeepsThePendingDraftAndItsBanner() {
        // One custom sound so My Bomps renders its normal list (not a first-run onboarding surface).
        TestData.seedCustomSounds(context, count = 1)
        TestData.seedRecorderDraft(context, durationMs = 3_000)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(string(R.string.app_hub_fab_description)).performClick()
            composeRule.awaitNodeWithText(string(R.string.app_hub_record)).performClick()
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_guard_message)).assertIsDisplayed()

            Espresso.pressBack()
            composeRule.waitForIdle()

            composeRule.onAllNodesWithText(string(R.string.app_recorder_draft_guard_message)).assertCountEquals(0)
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_banner_message)).assertIsDisplayed()
            composeRule.onAllNodesWithText(string(R.string.app_recorder_ready_hint)).assertCountEquals(0)
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
