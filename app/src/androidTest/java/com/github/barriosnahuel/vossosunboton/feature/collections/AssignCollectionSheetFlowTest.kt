/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * The sheet is new in round-3 and replaces the prior "tag-only-from-Add/Edit" entry-point. The
 * flow under test:
 *
 * 1. User long-presses a sound card → dropdown menu opens with the "Add to collection" item.
 * 2. Selecting that item dismisses the menu and opens an `AssignCollectionSheet` that lists
 *    public collections first, then a divider, then private.
 * 3. Toggling a public collection writes through `SoundsViewModel.toggleAudioInCollection`,
 *    which triggers `audioCollectionsIndex` to re-emit and the card's meta row to show the
 *    collection label inline next to the date — this is the regression we paid for during round 3
 *    (tags used to render as a separate chip row above the card; now they live inside the card).
 *
 * The test asserts on the inline meta-row label rather than the chip row to keep this test
 * focused on the new sheet flow; chip-row behaviour has its own coverage in
 * [MySoundsFilterChipsFlowTest].
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
    fun tappingAddToCollectionOpensTheGroupedSheet() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")
        // Pre-grant the per-session unlock so the private group also renders without a biometric.
        // On devices with no biometric configured (typical emulator state) the gate falls through
        // automatically; this call covers the protected-device branch and the no-protection
        // branch with the same setup.
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()

            // The sheet title is unique to this surface, so a positive match on it proves the
            // sheet rendered. Section headers ("Collections" / "Vault") — assertions go through the
            // per-row contentDescription that disambiguates Familia-the-chip from
            // Familia-the-sheet-row.
            composeRule.awaitNodeWithText(sheetTitle()).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(addToActionFor("Familia")).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(addToActionFor(systemBaulLabel())).assertIsDisplayed()
        }
    }

    @Test
    fun togglingACollectionInTheSheetTagsTheAudioWithItsLabel() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Baseline: "Familia" appears exactly once (in the filter chip row).
            composeRule.awaitNodeWithText("Familia").assertIsDisplayed()
            composeRule.onAllNodesWithText("Familia", substring = true).assertCountEquals(1)

            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            // Target the row by its contentDescription so we don't accidentally click the
            // filter chip with the same name.
            composeRule
                .onNodeWithContentDescription(addToActionFor("Familia"))
                .performClick()
            composeRule.awaitNodeWithText(doneLabel()).performClick()

            // After dismiss: the sheet is gone, the filter chip is still there, AND the card's
            // meta row now carries the inline collection label. Two surfaces show "Familia".
            composeRule.waitUntil(SHEET_DISMISS_TIMEOUT_MS) {
                composeRule
                    .onAllNodes(hasText("Familia", substring = true))
                    .fetchSemanticsNodes()
                    .size >= 2
            }
            composeRule.onAllNodesWithText("Familia", substring = true).assertCountEquals(2)
        }
    }

    private fun addToCollectionLabel() = context.getString(R.string.app_audio_menu_add_to_collection)

    private fun sheetTitle() = context.getString(R.string.app_assign_collection_sheet_title)

    private fun systemBaulLabel() = context.getString(R.string.app_vault_baul_name)

    private fun doneLabel() = context.getString(R.string.app_assign_collection_sheet_done)

    private fun addToActionFor(name: String) = context.getString(R.string.app_assign_collection_sheet_row_action_unselected, name)

    companion object {
        private const val SHEET_DISMISS_TIMEOUT_MS = 5_000L
    }
}
