/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for instrumented-test isolation: [TestData.clearAll] must leave MY_SOUNDS free
 * of the welcome sticker so the rest of the suite's "exactly one play/share button per seeded
 * sound" assertions hold.
 *
 * The bug this guards: the persistent-welcome migration runs on every SoundsViewModel.init and
 * resets consumed=false. clearAll wipes the migration guard, so the migration re-armed on every
 * test and resurrected the welcome as a second MY_SOUNDS row — surfacing across unrelated tests as
 * "Failed to inject touch input: found 2 nodes" or ComposeTimeout on a count-bearing header.
 */
@RunWith(AndroidJUnit4::class)
internal class WelcomeIsolationGuardTest : AbstractUiTest() {
    @Test
    fun clearAllHidesTheWelcomeSoASeededSoundHasExactlyOnePlayButton() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Wait for the seeded sound to render before asserting the negatives below.
            composeRule.awaitNodeWithText("custom_1")

            val welcomeTitle = context.getString(R.string.app_welcome_sticker_title)
            composeRule.onAllNodesWithText(welcomeTitle).assertCountEquals(0)
            // One seeded sound ⇒ exactly one Play control; a resurrected welcome would make it two.
            composeRule.onAllNodesWithContentDescription(playLabel()).assertCountEquals(1)
        }
    }

    private fun playLabel() = context.getString(R.string.app_play)
}
