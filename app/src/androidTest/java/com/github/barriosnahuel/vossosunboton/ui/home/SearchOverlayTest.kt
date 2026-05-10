/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.WAIT_TIMEOUT_MS
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SearchOverlayTest : AbstractUiTest() {
    @Test
    fun fabOpensSearchOverlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            // The overlay's TopAppBar back arrow uses "Close search" as content description.
            composeRule.awaitNodeWithContentDescription(closeSearchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun typingQueryFiltersResultsToMatchingSound() {
        TestData.seedCustomSounds(context, count = 3)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            composeRule.awaitNode(hasSetTextAction()).performTextInput("custom_2")
            // The SearchOverlay surfaces "custom_2" once the 200ms debounce fires. After typing,
            // "custom_2" lives in three places at once: the editable input itself, the overlay
            // result card, and the underlying LandingScreen card still in the semantics tree
            // behind the overlay — so awaitNodeWithText (single-node + assertIsDisplayed) can't
            // be used here. Wait for *presence* with onAllNodes instead.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText("custom_2")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun queryWithNoMatchShowsZeroResultsMessage() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            composeRule.awaitNode(hasSetTextAction()).performTextInput("zzz_no_match")
            composeRule.awaitNodeWithText(context.getString(R.string.app_search_empty_headline)).assertIsDisplayed()
        }
    }

    @Test
    fun trailingClearIconClearsQueryWithoutClosingOverlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            composeRule.awaitNode(hasSetTextAction()).performTextInput("custom")
            composeRule.awaitNodeWithContentDescription(clearSearchLabel()).performClick()
            // Initial hint is back (overlay still open) and the back arrow remains visible.
            composeRule
                .awaitNodeWithText(context.getString(R.string.app_search_initial_hint))
                .assertIsDisplayed()
            composeRule.onNodeWithContentDescription(closeSearchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun backArrowClosesOverlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(closeSearchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(closeSearchLabel()).assertIsNotDisplayed()
            // FAB returns to view → we are back on Landing.
            composeRule.onNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun systemBackClosesOverlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            // Wait for overlay to be on screen before pressing back, otherwise pressBack closes the Activity instead.
            composeRule.awaitNodeWithContentDescription(closeSearchLabel()).assertIsDisplayed()
            Espresso.pressBack()
            // pressBack is deterministic; flush the back-handler recomposition so the FAB is
            // settled in the next frame before assertIsDisplayed checks visibility.
            composeRule.waitForIdle()
            composeRule.awaitNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun searchOverlayExposesA11yContentDescriptions() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(closeSearchLabel()).assertHasClickAction()
            composeRule.awaitNode(hasSetTextAction()).performTextInput("c")
            composeRule.awaitNodeWithContentDescription(clearSearchLabel()).assertHasClickAction()
            // Defensive cleanup: close the overlay so the activity tears down with the IME hidden
            // and no soft-input residue carries over to the next test in the suite.
            composeRule.awaitNodeWithContentDescription(closeSearchLabel()).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun searchLabel() = context.getString(R.string.app_search)

    private fun closeSearchLabel() = context.getString(R.string.app_search_close)

    private fun clearSearchLabel() = context.getString(R.string.app_search_clear)
}
