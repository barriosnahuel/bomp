/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-3 added the rename/delete entry-point: per-row overflow menu inside [AssignCollectionSheet].
 * The repo + VM methods existed since round-1 but were unreachable from any UI; this flow + its
 * sibling [DeleteCollectionFlowTest] are the proof that the surface wires correctly.
 *
 * The full happy path:
 * 1. Long-press a sound → "Add to collection" → assign sheet opens.
 * 2. Tap the per-row overflow (⋮) on a non-system collection → dropdown with Rename + Delete.
 * 3. Tap Rename → the assign sheet dismisses, the existing rename sheet (CollectionSheetHost) opens
 *    with the current name pre-filled.
 * 4. Clear the field, type the new name, save.
 * 5. The filter chip on My Sounds updates to show the new name (`Familia` → `Recetas`).
 *
 * System collections (the seeded Baúl) intentionally hide the overflow icon — that's covered as
 * a smoke assertion in [tappingTheOverflowOpensRenameAndDeleteForNonSystemCollectionsOnly].
 */
@RunWith(AndroidJUnit4::class)
internal class RenameCollectionFlowTest : AbstractUiTest() {
    @Test
    fun renameCollectionFlowUpdatesTheFilterChipLabel() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Baseline: the public chip on My Sounds reads "Familia".
            composeRule.awaitNodeWithText("Familia").assertIsDisplayed()

            // Open the assign sheet.
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            composeRule.awaitNodeWithText(sheetTitle()).assertIsDisplayed()

            // Tap the overflow on the Familia row.
            composeRule.awaitNodeWithContentDescription(overflowFor("Familia")).performClick()
            composeRule.awaitNodeWithText(renameLabel()).performClick()

            // The assign sheet dismisses and the rename sheet (CollectionSheetHost) opens with
            // the current name pre-filled. Clear + retype.
            composeRule.awaitNodeWithText(renameSheetTitle()).assertIsDisplayed()
            composeRule
                .awaitNode(
                    hasSetTextAction().and(hasText("Familia", substring = true, ignoreCase = false)),
                ).performTextClearance()
            composeRule
                .awaitNode(
                    hasSetTextAction().and(hasText(nameLabel(), substring = true, ignoreCase = false)),
                ).performTextInput("Recetas")

            // The rename sheet's Save shares its label resource with several other Save buttons
            // in the app; disambiguate via the sibling rename-sheet title.
            composeRule
                .awaitNode(
                    hasText(saveLabel()).and(hasAnySibling(hasText(renameSheetTitle()))),
                ).performClick()

            // Final state: the filter chip on My Sounds shows the new name and no chip says Familia.
            composeRule.waitUntil(RENAME_PROPAGATION_TIMEOUT_MS) {
                composeRule.onAllNodesWithText("Recetas").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Recetas").assertIsDisplayed()
            composeRule.onAllNodesWithText("Familia").assertCountEquals(0)
        }
    }

    @Test
    fun tappingTheOverflowOpensRenameAndDeleteForNonSystemCollectionsOnly() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")
        // Skip biometric and force the system Baúl into existence — it must NOT expose an overflow.
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            composeRule.awaitNodeWithText(sheetTitle()).assertIsDisplayed()

            // Non-system: overflow available.
            composeRule.onNodeWithContentDescription(overflowFor("Familia")).assertIsDisplayed()
            // System Baúl: no overflow rendered.
            composeRule
                .onAllNodesWithText(systemBaulLabel())
                .fetchSemanticsNodes()
                .let { require(it.isNotEmpty()) { "Expected the system Baúl row to render" } }
            composeRule
                .onAllNodesWithText(overflowFor(systemBaulLabel()))
                .fetchSemanticsNodes()
                .let { require(it.isEmpty()) { "System collections must not expose an overflow icon" } }
        }
    }

    private fun addToCollectionLabel() = context.getString(R.string.app_audio_menu_add_to_collection)

    private fun sheetTitle() = context.getString(R.string.app_assign_collection_sheet_title)

    private fun overflowFor(name: String) = context.getString(R.string.app_vault_card_overflow_description, name)

    private fun renameLabel() = context.getString(R.string.app_vault_card_overflow_rename)

    private fun renameSheetTitle() = context.getString(R.string.app_collection_sheet_title_rename)

    private fun nameLabel() = context.getString(R.string.app_collection_sheet_name_label)

    private fun saveLabel() = context.getString(R.string.app_collection_sheet_save)

    private fun systemBaulLabel() = context.getString(R.string.app_vault_baul_name)

    companion object {
        private const val RENAME_PROPAGATION_TIMEOUT_MS = 5_000L
    }
}
