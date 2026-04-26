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
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun bottom_bar_is_visible_when_bundled_sounds_exist() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(homeTabLabel()).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(exploreTabLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun explore_tab_shows_bundled_and_hides_custom_when_seeded() {
        TestData.seedCustomSounds(context, count = 1)
        val firstBundledName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(exploreTabLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(firstBundledName).assertIsDisplayed()
            // The seeded "custom_1" lives only on Home; switching to Explore hides it.
            composeRule.onAllNodes(hasText("custom_1")).assertCountEquals(0)
        }
    }

    @Test
    fun swipe_left_on_bundled_card_does_not_pin_or_remove() {
        val firstBundledName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(exploreTabLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(firstBundledName).performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            // Card stays at the same position with the pin icon (not unpin) — swipe-left
            // only fires a reject haptic for bundled sounds.
            composeRule.onNodeWithText(firstBundledName).assertIsDisplayed()
            composeRule
                .onAllNodesWithContentDescription(unpinLabel())
                .fetchSemanticsNodes()
                .let { assert(it.isEmpty()) { "Sound should not be pinned after swipe left." } }
        }
    }

    @Test
    fun swipe_right_on_bundled_card_pins_it() {
        val targetName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(exploreTabLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(targetName).performTouchInput { swipeRight() }
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule
                    .onAllNodesWithContentDescription(unpinLabel())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun pin_icon_on_bundled_card_toggles_to_unpin() {
        val targetName = bundled.first().name

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(exploreTabLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithContentDescription(pinLabel()).onFirst().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(unpinLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun explore_tab_exposes_a11y_content_descriptions() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(exploreTabLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithContentDescription(playLabel()).onFirst().assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(shareLabel()).onFirst().assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(pinLabel()).onFirst().assertHasClickAction()
        }
    }

    private fun playLabel() = context.getString(R.string.app_play)

    private fun shareLabel() = context.getString(R.string.app_share_chooser_title)

    private fun pinLabel() = context.getString(R.string.app_pin)

    private fun unpinLabel() = context.getString(R.string.app_unpin)

    private fun homeTabLabel() = context.getString(R.string.app_navigation_menu_item_home)

    private fun exploreTabLabel() = context.getString(R.string.app_navigation_menu_item_explore)

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
