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
import androidx.compose.ui.test.onFirst
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
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class HomeTabFlowTest : AbstractUiTest() {
    @Test
    fun homeTabRendersSeededCustomSounds() {
        TestData.seedCustomSounds(context, count = 2)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText("custom_1").assertIsDisplayed()
            composeRule.awaitNodeWithText("custom_2").assertIsDisplayed()
        }
    }

    @Test
    fun tapPlaySwapsPlayIconToPause() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(playLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(pauseLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun shareButtonEmitsActionSendChooserIntent() {
        TestData.seedCustomSounds(context, count = 1)
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(shareLabel()).performClick()
            composeRule.waitForIdle()

            intended(hasAction(Intent.ACTION_CHOOSER))
        }
    }

    @Test
    fun editMenuItemLaunchesAddButtonActivityWithSoundExtras() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        intending(hasComponent(hasClassName(ADD_BUTTON_ACTIVITY)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(renameLabel()).performClick()
            composeRule.waitForIdle()

            // Hamcrest 1.3 (transitively pinned in the test APK) lacks the 2-arg `allOf`
            // that Kotlin compiles to. Stick to the most discriminating matcher per
            // `intended()` call — extras are covered by Robolectric unit tests.
            intended(hasComponent(hasClassName(ADD_BUTTON_ACTIVITY)))
        }
    }

    @Test
    fun swipeLeftToDeleteThenUndoRestoresSound() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { swipeLeft() }
            val undoLabel = context.getString(R.string.app_undo)
            // awaitNodeWithText only confirms the snackbar's undo button exists in the
            // semantics tree; the M3 snackbar enter animation may still be sliding it up.
            // Settle the animation before clicking so the touch target is stable.
            composeRule.awaitNodeWithText(undoLabel)
            composeRule.waitForIdle()
            composeRule.onNodeWithText(undoLabel).performClick()
            composeRule.awaitNodeWithText(sound.name).assertIsDisplayed()
        }
    }

    @Test
    fun swipeLeftToDeleteWithoutUndoEventuallyRemovesTheSound() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { swipeLeft() }
            // Snackbar (long timeout) → confirmDelete → card vanishes from the list.
            composeRule.waitUntil(timeoutMillis = SNACKBAR_LONG_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(sound.name)).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    @Test
    fun pinIconButtonMarksSoundAsPinned() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(pinLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(unpinLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun swipeRightPinsACustomSound() {
        TestData.seedCustomSounds(context, count = 2)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText("custom_2").performTouchInput { swipeRight() }
            composeRule.awaitNodeWithContentDescription(unpinLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun homeScreenExposesA11yContentDescriptionsForKeyControls() {
        // The search action lives in the top app bar and is always present; the per-sound controls
        // (play/share/pin) just need at least one row to exist, matched once per row via onFirst().
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(searchLabel()).assertHasClickAction()
            composeRule.awaitNodeWithContentDescription(overflowLabel()).assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(playLabel()).onFirst().assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(shareLabel()).onFirst().assertHasClickAction()
            composeRule.onAllNodesWithContentDescription(pinLabel()).onFirst().assertHasClickAction()
        }
    }

    @Test
    fun overflowSeeHowItWorksOpensTheTour() {
        // A seeded custom sound hides the welcome footer, so the overflow item is the only "See how it
        // works"; tapping it opens the full onboarding tour at step 1.
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(overflowLabel()).performClick()
            composeRule.awaitNodeWithText(seeHowItWorksLabel()).performClick()

            composeRule.awaitNodeWithText(tourStep1Title()).assertIsDisplayed()
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

    private fun seeHowItWorksLabel() = context.getString(R.string.app_my_sounds_empty_secondary)

    private fun tourStep1Title() = context.getString(R.string.app_onboarding_step1_title)

    companion object {
        private const val SNACKBAR_LONG_TIMEOUT_MS = 15_000L
        private const val ADD_BUTTON_ACTIVITY =
            "com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity"
    }
}
