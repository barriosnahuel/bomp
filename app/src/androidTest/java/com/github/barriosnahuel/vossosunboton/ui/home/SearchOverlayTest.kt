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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SearchOverlayTest : AbstractUiTest() {
    @Test
    fun fab_opens_search_overlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            // The overlay's TopAppBar back arrow uses "Close search" as content description.
            composeRule.onNodeWithContentDescription(closeSearchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun typing_query_filters_results_to_matching_sound() {
        TestData.seedCustomSounds(context, count = 3)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNode(hasSetTextAction()).performTextInput("custom_2")
            // The SearchOverlay surfaces "custom_2" once the 200ms debounce fires.
            // Filter logic itself is covered by SoundsViewModelSearchTest — here we only
            // verify the matching result becomes visible (the underlying LandingScreen
            // remains in the semantic tree behind the overlay so we can't assert custom_1
            // is *absent*).
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText("custom_2")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun query_with_no_match_shows_zero_results_message() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNode(hasSetTextAction()).performTextInput("zzz_no_match")
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule
                    .onAllNodes(hasText(context.getString(R.string.app_search_empty_headline)))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun trailing_clear_icon_clears_query_without_closing_overlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNode(hasSetTextAction()).performTextInput("custom")
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(clearSearchLabel()).performClick()
            composeRule.waitForIdle()
            // Initial hint is back (overlay still open) and the back arrow remains visible.
            composeRule
                .onNodeWithText(context.getString(R.string.app_search_initial_hint))
                .assertIsDisplayed()
            composeRule.onNodeWithContentDescription(closeSearchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun back_arrow_closes_overlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(closeSearchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(closeSearchLabel()).assertIsNotDisplayed()
            // FAB returns to view → we are back on Landing.
            composeRule.onNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun system_back_closes_overlay() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            Espresso.pressBack()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun search_overlay_exposes_a11y_content_descriptions() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(closeSearchLabel()).assertHasClickAction()
            composeRule.onNode(hasSetTextAction()).performTextInput("c")
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(clearSearchLabel()).assertHasClickAction()
        }
    }

    private fun searchLabel() = context.getString(R.string.app_search)

    private fun closeSearchLabel() = context.getString(R.string.app_search_close)

    private fun clearSearchLabel() = context.getString(R.string.app_search_clear)

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
