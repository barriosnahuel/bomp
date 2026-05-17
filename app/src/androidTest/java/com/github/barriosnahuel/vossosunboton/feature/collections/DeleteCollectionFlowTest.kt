/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Companion to [RenameCollectionFlowTest] for the delete branch. Verifies that:
 *
 * - The confirmation dialog renders with the audio count copy when the collection has audios
 *   ("%1$d audios will leave this collection. …").
 * - Confirming removes the collection (chip disappears from My Sounds) while leaving the audio
 *   files themselves in the library — the dialog body promises this and the repo's `delete()`
 *   only drops the tag.
 * - Dismissing the dialog without confirming leaves both the collection and the audio intact.
 *
 * The "system collection has no overflow" assertion lives in [RenameCollectionFlowTest] so the
 * coverage doesn't drift if/when the surfaces are split — both tests share the assign-sheet
 * entry-point and the same row layout.
 */
@RunWith(AndroidJUnit4::class)
internal class DeleteCollectionFlowTest : AbstractUiTest() {
    @Test
    fun deletingACollectionRemovesItsChipAndKeepsTheAudio() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(sound.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText("Familia").assertIsDisplayed()
            composeRule.onNodeWithText(sound.name).assertIsDisplayed()

            // Drive the delete flow from the assign sheet's per-row overflow.
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(overflowFor("Familia")).performClick()
            composeRule.awaitNodeWithText(deleteLabel()).performClick()

            // Material 3 AlertDialog produces twin semantic nodes for every visible element
            // (title, body, and even the buttons) — both nodes claim the same coordinates and
            // actions. The duplication is real in the semantics tree, not a matcher problem, so
            // any single-node matcher throws. Take the first match deterministically.
            composeRule.waitUntil(DIALOG_TIMEOUT_MS) {
                composeRule
                    .onAllNodes(hasClickAction().and(hasText(confirmLabel())))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule
                .onAllNodes(hasClickAction().and(hasText(confirmLabel())))
                .onFirst()
                .performClick()

            // Final state: chip is gone, audio still present.
            composeRule.waitUntil(DELETE_PROPAGATION_TIMEOUT_MS) {
                composeRule.onAllNodesWithText("Familia").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onAllNodesWithText("Familia").assertCountEquals(0)
            composeRule.onNodeWithText(sound.name).assertIsDisplayed()
        }
    }

    @Test
    fun keepingTheCollectionFromTheDialogLeavesBothCollectionAndAudioIntact() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(sound.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(overflowFor("Familia")).performClick()
            composeRule.awaitNodeWithText(deleteLabel()).performClick()

            // Same twin-node bypass as the deletion path.
            composeRule.waitUntil(DIALOG_TIMEOUT_MS) {
                composeRule
                    .onAllNodes(hasClickAction().and(hasText(dismissLabel())))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule
                .onAllNodes(hasClickAction().and(hasText(dismissLabel())))
                .onFirst()
                .performClick()

            // Dismiss path: nothing changes.
            composeRule.awaitNodeWithText("Familia").assertIsDisplayed()
            composeRule.onNodeWithText(sound.name).assertIsDisplayed()
        }
    }

    private fun addToCollectionLabel() = context.getString(R.string.app_audio_menu_add_to_collection)

    private fun overflowFor(name: String) = context.getString(R.string.app_vault_card_overflow_description, name)

    private fun deleteLabel() = context.getString(R.string.app_vault_card_overflow_delete)

    private fun confirmLabel() = context.getString(R.string.app_collection_delete_dialog_confirm)

    private fun dismissLabel() = context.getString(R.string.app_collection_delete_dialog_dismiss)

    companion object {
        private const val DELETE_PROPAGATION_TIMEOUT_MS = 5_000L
        private const val DIALOG_TIMEOUT_MS = 5_000L
    }
}
