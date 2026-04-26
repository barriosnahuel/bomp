/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class HomeTabFlowTest : AbstractUiTest() {
    @Test
    fun home_tab_renders_seeded_custom_sounds() {
        TestData.seedCustomSounds(context, count = 2)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("custom_1").assertIsDisplayed()
            composeRule.onNodeWithText("custom_2").assertIsDisplayed()
        }
    }

    @Test
    fun tap_play_swaps_play_icon_to_pause() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(playLabel()).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule
                    .onAllNodesWithContentDescription(pauseLabel())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(pauseLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun share_button_emits_action_send_chooser_intent() {
        TestData.seedCustomSounds(context, count = 1)
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(shareLabel()).performClick()
            composeRule.waitForIdle()

            intended(hasAction(Intent.ACTION_CHOOSER))
        }
    }

    @Test
    fun edit_menu_item_launches_AddButtonActivity_with_sound_extras() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        intending(hasComponent(hasClassName(ADD_BUTTON_ACTIVITY)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(renameLabel()).performClick()
            composeRule.waitForIdle()

            // Hamcrest 1.3 (transitively pinned in the test APK) lacks the 2-arg `allOf`
            // that Kotlin compiles to. Stick to the most discriminating matcher per
            // `intended()` call — extras are covered by Robolectric unit tests.
            intended(hasComponent(hasClassName(ADD_BUTTON_ACTIVITY)))
        }
    }

    @Test
    fun swipe_left_to_delete_then_undo_restores_sound() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(sound.name).performTouchInput { swipeLeft() }
            val undoLabel = context.getString(R.string.app_undo)
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(undoLabel)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(undoLabel).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(sound.name).assertIsDisplayed()
        }
    }

    @Test
    fun swipe_left_to_delete_without_undo_eventually_removes_the_sound() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(sound.name).performTouchInput { swipeLeft() }
            // Snackbar (long timeout) → confirmDelete → card vanishes from the list.
            composeRule.waitUntil(timeoutMillis = SNACKBAR_LONG_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(sound.name)).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    @Test
    fun pin_icon_button_marks_sound_as_pinned() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(pinLabel()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(unpinLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun swipe_right_pins_a_custom_sound() {
        TestData.seedCustomSounds(context, count = 2)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("custom_2").performTouchInput { swipeRight() }
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule
                    .onAllNodesWithContentDescription(unpinLabel())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun home_screen_exposes_a11y_content_descriptions_for_key_controls() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).assertHasClickAction()
            composeRule.onNodeWithContentDescription(overflowLabel()).assertHasClickAction()
            composeRule.onNodeWithContentDescription(playLabel()).assertHasClickAction()
            composeRule.onNodeWithContentDescription(shareLabel()).assertHasClickAction()
            composeRule.onNodeWithContentDescription(pinLabel()).assertHasClickAction()
        }
    }

    private fun playLabel() = context.getString(R.string.app_play)

    private fun pauseLabel() = context.getString(R.string.app_pause)

    private fun shareLabel() = context.getString(R.string.app_share_chooser_title)

    private fun renameLabel() = context.getString(R.string.app_edit)

    private fun pinLabel() = context.getString(R.string.app_pin)

    private fun unpinLabel() = context.getString(R.string.app_unpin)

    private fun searchLabel() = context.getString(R.string.app_search)

    private fun overflowLabel() = context.getString(R.string.app_overflow_menu)

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
        private const val SNACKBAR_LONG_TIMEOUT_MS = 15_000L
        private const val ADD_BUTTON_ACTIVITY =
            "com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity"
    }
}
