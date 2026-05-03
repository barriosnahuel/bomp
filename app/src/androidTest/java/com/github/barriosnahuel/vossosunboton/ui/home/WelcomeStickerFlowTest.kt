/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the welcome sticker (Sticker Cero) on a real device. Robolectric
 * already covers the VM, store, and Compose smoke tests; this suite catches the things only an
 * emulator can:
 *
 *  - Locale-qualified `R.raw.app_welcome_sticker` resolves correctly under `Configuration.locale`.
 *  - The new `combinedClickable` long-press dropdown survives Compose's merged-vs-unmerged
 *    semantics tree (the AddButton flake on the same trip-pattern is a recent reminder).
 *  - The Material3 `SwipeToDismissBox` threshold + animation actually fire on a real surface
 *    (the `rememberSaveable` re-trigger bug documented in `SoundItem.kt:164` was a device-only
 *    discovery).
 *  - The post-Undo demotion is observable in the actual rendered list order.
 */
@RunWith(AndroidJUnit4::class)
internal class WelcomeStickerFlowTest : AbstractUiTest() {
    @Test
    fun welcomeStickerRendersAtRow0WithLocalizedTitleAndOriginLabel() {
        TestData.enableWelcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val origin = context.getString(R.string.app_welcome_sticker_origin)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(origin).assertIsDisplayed()
        }
    }

    @Test
    fun swipeLeftOnWelcomeShowsUndoSnackbarAndDemotesToBottomOnUndo() {
        TestData.enableWelcomeSticker(context)
        TestData.seedCustomSounds(context, count = 1)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val undoLabel = context.getString(R.string.app_undo)
        val dismissedMessage = context.getString(R.string.app_welcome_sticker_feedback_dismissed)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(title).performTouchInput { swipeLeft() }
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(dismissedMessage)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(undoLabel).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }

            // The welcome must reappear BELOW the user's custom sound — row 0 belongs to the
            // user once they've shown they want this sticker back rather than letting it consume.
            val welcomeTop = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
            val customTop = composeRule.onNodeWithText("custom_1").fetchSemanticsNode().boundsInRoot.top
            assertThat(welcomeTop).isGreaterThan(customTop)
        }
    }

    @Test
    fun longPressOnWelcomeOpensDropdownContainingOnlyDeleteThatDismisses() {
        TestData.enableWelcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val deleteLabel = context.getString(R.string.app_delete)
        val editLabel = context.getString(R.string.app_edit)
        val dismissedMessage = context.getString(R.string.app_welcome_sticker_feedback_dismissed)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(title).performTouchInput { longClick() }
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(deleteLabel).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText(deleteLabel).assertIsDisplayed()
            // Edit must NOT appear — the welcome is a system anchor, not an editable user sound.
            assertThat(composeRule.onAllNodesWithText(editLabel).fetchSemanticsNodes()).isEmpty()

            composeRule.onNodeWithText(deleteLabel).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(dismissedMessage)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(dismissedMessage).assertIsDisplayed()
        }
    }

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
