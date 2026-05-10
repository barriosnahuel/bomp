/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExploreTabFlowTest : AbstractUiTest() {
    private lateinit var bundled: List<Sound>

    override fun setUp() {
        super.setUp()
        bundled = PackagedAudios.get(context)
        assumeTrue(
            "Bundled audios are required for this test. Populate model/src/debug/res/raw/ first.",
            bundled.isNotEmpty(),
        )
    }

    @Test
    fun bottomBarIsVisibleWhenBundledSoundsExist() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            // Find by label text — the icon's contentDescription only lives in the unmerged
            // tree, while the label (a Text composable) bubbles up through merge.
            composeRule.awaitNodeWithText(homeTabLabel()).assertIsDisplayed()
            composeRule.onNodeWithText(exploreTabLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun exploreTabShowsBundledAndHidesCustomWhenSeeded() {
        TestData.seedCustomSounds(context, count = 1)
        val firstBundledName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(exploreTabLabel()).performClick()
            composeRule.awaitNodeWithText(firstBundledName).assertIsDisplayed()
            // The seeded "custom_1" lives only on Home; switching to Explore hides it.
            composeRule.onAllNodes(hasText("custom_1")).assertCountEquals(0)
        }
    }

    @Test
    fun swipeLeftOnBundledCardDoesNotPinOrRemove() {
        val firstBundledName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(exploreTabLabel()).performClick()
            composeRule.awaitNodeWithText(firstBundledName).performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            // Card stays at the same position with the pin icon (not unpin) — swipe-left
            // only fires a reject haptic for bundled sounds.
            composeRule.onNodeWithText(firstBundledName).assertIsDisplayed()
            // Sound should not be pinned after swipe left — bundled cards reject pin via swipe-left.
            composeRule
                .onAllNodesWithContentDescription(unpinLabel())
                .assertCountEquals(0)
        }
    }

    @Test
    fun swipeRightOnBundledCardPinsIt() {
        val targetName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(exploreTabLabel()).performClick()
            composeRule.awaitNodeWithText(targetName).performTouchInput { swipeRight() }
            composeRule.awaitNodeWithContentDescription(unpinLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun pinIconOnBundledCardTogglesToUnpin() {
        val targetName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(exploreTabLabel()).performClick()
            // Wait for the bundled cards to seed before grabbing the first pin icon.
            // `onAllNodes(...).onFirst()` is the "first of N" shape that the helper does not cover;
            // the wait + lookup stays inline.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithContentDescription(pinLabel()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithContentDescription(pinLabel()).onFirst().performClick()
            composeRule.awaitNodeWithContentDescription(unpinLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun exploreTabExposesA11yContentDescriptions() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(exploreTabLabel()).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithContentDescription(playLabel()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithContentDescription(playLabel()).onFirst().assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(shareLabel()).onFirst().assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(pinLabel()).onFirst().assertHasClickAction()
        }
    }

    private fun playLabel() = context.getString(R.string.app_play)

    private fun shareLabel() = context.getString(R.string.app_share_chooser_title)

    private fun pinLabel() = context.getString(R.string.app_pin)

    private fun unpinLabel() = context.getString(R.string.app_unpin)

    private fun homeTabLabel() = context.getString(R.string.app_navigation_menu_item_my_sounds)

    private fun exploreTabLabel() = context.getString(R.string.app_navigation_menu_item_explore_sounds)

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
