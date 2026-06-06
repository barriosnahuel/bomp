/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertIsDisplayed
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
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
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
 *  - The date-driven ordering is observable in the actual rendered list with real file timestamps
 *    (feedback v2.1.0 #1 — the welcome is no longer force-positioned).
 */
@RunWith(AndroidJUnit4::class)
internal class WelcomeStickerFlowTest : AbstractUiTest() {
    @Test
    fun welcomeStickerRendersAtRow0WithLocalizedTitleAndOriginLabel() {
        TestData.enableWelcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val origin = context.getString(R.string.app_welcome_sticker_origin)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(origin).assertIsDisplayed()
        }
    }

    @Test
    fun swipeLeftOnWelcomeShowsUndoSnackbarAndRestoresItSortedByDate() {
        TestData.enableWelcomeSticker(context)
        // Seeded before launch, so the custom file's timestamp predates the welcome's install
        // timestamp (captured at first load) — the welcome is the newer audio.
        TestData.seedCustomSounds(context, count = 1)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val undoLabel = context.getString(R.string.app_undo)
        val dismissedMessage = context.getString(R.string.app_welcome_sticker_feedback_dismissed)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(title).performTouchInput { swipeLeft() }
            composeRule.awaitNodeWithText(dismissedMessage).assertIsDisplayed()
            composeRule.onNodeWithText(undoLabel).performClick()
            composeRule.awaitNodeWithText(title).assertIsDisplayed()

            // The welcome reappears sorted by date like any other audio (feedback v2.1.0 #1). It is
            // the newer audio here (its install timestamp postdates the pre-seeded custom file), so
            // it sits ABOVE the custom — no longer force-demoted to the bottom.
            val welcomeTop =
                composeRule
                    .onNodeWithText(title)
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            val customTop =
                composeRule
                    .onNodeWithText("custom_1")
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            assertThat(welcomeTop).isLessThan(customTop)
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
            composeRule.awaitNodeWithText(title).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(deleteLabel).assertIsDisplayed()
            // Edit must NOT appear — the welcome is a system anchor, not an editable user sound.
            assertThat(composeRule.onAllNodesWithText(editLabel).fetchSemanticsNodes()).isEmpty()

            composeRule.onNodeWithText(deleteLabel).performClick()
            composeRule.awaitNodeWithText(dismissedMessage).assertIsDisplayed()
        }
    }
}
