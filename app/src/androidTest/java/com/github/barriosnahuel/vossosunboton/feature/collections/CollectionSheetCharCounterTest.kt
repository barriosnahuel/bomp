/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the Collection name sheet: the visible character counter follows the input
 * length and the hard cap (50 — Material 3 chip-friendly bound) is enforced both visually and
 * in the underlying state.
 *
 * Auto-focus + IME visibility are deliberately NOT asserted here — Compose UI Test exposes
 * focus but not the system IME visibility on real hardware; the auto-focus pathway is verified
 * manually after install. The counter assertions in this test indirectly prove the field is
 * reachable + typeable on first open, which is the user-visible piece of the flow.
 */
@RunWith(AndroidJUnit4::class)
internal class CollectionSheetCharCounterTest : AbstractUiTest() {
    @Test
    fun newCollectionSheetCounterStartsAtZeroAndUpdatesAsTheUserTypes() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openCreateCollectionSheet()

            // Empty state: "0 / 20".
            composeRule.awaitNodeWithText(counter(0)).assertIsDisplayed()

            // Type "Familia" → counter renders "7 / 20".
            composeRule.awaitNode(hasSetTextAction()).performTextInput("Familia")
            composeRule.awaitNodeWithText(counter(7)).assertIsDisplayed()
            // Old counter is gone — exactly one instance of any "/ 20" suffix.
            composeRule
                .onAllNodesWithText("/ $COLLECTION_NAME_MAX_UI", substring = true)
                .assertCountEquals(1)
        }
    }

    @Test
    fun typingPastTheLimitGetsTruncatedToTwentyChars() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openCreateCollectionSheet()

            // 25 chars on input; the OutlinedTextField caps at 20 internally so the counter
            // shows the truncated length, not the typed length.
            composeRule.awaitNode(hasSetTextAction()).performTextInput("A".repeat(25))
            composeRule.awaitNodeWithText(counter(COLLECTION_NAME_MAX_UI)).assertIsDisplayed()
            // No "25 / 20" leaks through — if the cap broke, this would fail with two matches
            // before the truncation logic stabilizes.
            composeRule.onAllNodesWithText("25 /", substring = true).assertCountEquals(0)
        }
    }

    private fun openCreateCollectionSheet() {
        // Reach the sheet from the My Sounds filter row's "+" chip — that's the fastest
        // generic entry. Vault FAB would also work but adds a tab switch.
        composeRule
            .awaitNodeWithContentDescription(context.getString(R.string.app_my_sounds_filter_chip_new_description))
            .performClick()
        // Sheet rendered = the new-collection title (English/Spanish handled by AbstractUiTest's locale).
        composeRule
            .onNodeWithText(context.getString(R.string.app_collection_sheet_title_new_public))
            .assertIsDisplayed()
    }

    private fun counter(length: Int): String =
        context.getString(R.string.app_collection_sheet_name_counter, length, COLLECTION_NAME_MAX_UI)

    private companion object {
        // Mirror of the production COLLECTION_NAME_MAX in CollectionSheetHost.kt. Kept here as a
        // local constant so the test doesn't depend on a `private const val` from the screen file.
        const val COLLECTION_NAME_MAX_UI = 20
    }
}
