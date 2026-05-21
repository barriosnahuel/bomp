/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end coverage for the long-press → "Add to collection" sheet.
 *
 * The sheet reuses the same chip UI as the New Bomp assign section ([AssignToCollectionsSection]):
 * public collections as chips, then the Vault group behind the per-session unlock. Tapping a chip
 * applies immediately (no save step). The flow under test:
 *
 * 1. Long-press a sound card → dropdown menu with "Add to collection".
 * 2. Selecting it opens the sheet with chips.
 * 3. Tapping the Baúl chip tags the audio into the Vault — it becomes private-only and drops out of
 *    My Sounds. The Baúl chip is asserted (not "Familia") because the public collection name also
 *    appears in the My Sounds filter chip row, while the Baúl chip is unique to this sheet.
 */
@RunWith(AndroidJUnit4::class)
internal class AssignCollectionSheetFlowTest : AbstractUiTest() {
    @Test
    fun longPressMenuExposesAddToCollectionEntry() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun tappingAddToCollectionOpensTheSheetWithChips() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")
        // Pre-grant the per-session unlock so the Vault group renders without a biometric. On
        // emulators with no lock configured the gate falls through anyway; this covers both branches.
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()

            // Sheet title proves the sheet rendered; the Baúl chip proves the chip UI (and the
            // unlocked Vault group) is showing. The Baúl label is unique to the sheet.
            composeRule.awaitNodeWithText(sheetTitle()).assertIsDisplayed()
            composeRule.awaitNodeWithText(systemBaulLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun tappingTheBaulChipMovesTheAudioOutOfMySounds() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Baseline: the audio is in My Sounds.
            composeRule.awaitNodeWithText(sound.name).assertIsDisplayed()

            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            // Tap the Baúl chip (unique to the sheet) → tags the audio into the Vault immediately.
            composeRule.awaitNodeWithText(systemBaulLabel()).performClick()
            composeRule.awaitNodeWithText(doneLabel()).performClick()

            // Private-only now → the audio drops out of the My Sounds list.
            composeRule.waitUntil(SHEET_DISMISS_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(sound.name).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun addToCollectionLabel() = context.getString(R.string.app_audio_menu_add_to_collection)

    private fun sheetTitle() = context.getString(R.string.app_assign_collection_sheet_title)

    private fun systemBaulLabel() = context.getString(R.string.app_vault_baul_name)

    private fun doneLabel() = context.getString(R.string.app_assign_collection_sheet_done)

    companion object {
        private const val SHEET_DISMISS_TIMEOUT_MS = 5_000L
    }
}
