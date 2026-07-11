/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.WAIT_TIMEOUT_MS
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the naming screen in Edit mode (case 1.6). Edit is a destination inside
 * Landing's graph now, reached by long-pressing a row and picking Rename — there is no Activity to
 * launch with an edit intent, and a save pops back to the list instead of finishing a task.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonEditFlowTest : AbstractUiTest() {
    @Test
    fun editModeRendersPreviewCardAndExistingName() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            openEditFor(sound)

            // Both the OutlinedTextField and the AudioPreview header render the sound name,
            // so a plain hasText() matcher returns 2 nodes. Scope to the editable input.
            composeRule.awaitNode(hasSetTextAction()).assertIsDisplayed()
            // AudioPreview gates its Card render on `isReady`, which is flipped from a
            // LaunchedEffect after `withContext(Dispatchers.IO) { player.prepare() }` finishes —
            // an IO round-trip that `waitForIdle()` does not await.
            composeRule.awaitNodeWithContentDescription(string(R.string.app_addbutton_preview_audio)).assertHasClickAction()
        }
    }

    @Test
    fun saveWithBlankNameShowsRequiredError() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            openEditFor(sound)

            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            composeRule.onNodeWithText(string(R.string.app_addbutton_save_changes)).performClick()

            composeRule.awaitNodeWithText(string(R.string.app_addbutton_name_is_required_error)).assertIsDisplayed()
        }
    }

    @Test
    fun saveWithValidNameShowsConfirmationAndReturnsToTheList() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val newName = "renamed_custom"

        ActivityScenario.launch(LandingActivity::class.java).use {
            openEditFor(sound)

            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            nameField().performTextInput(newName)
            composeRule.onNodeWithText(string(R.string.app_addbutton_save_changes)).performClick()
            // Confirmation lives inside the success overlay (no snackbar). Its semantics carry the
            // localised announcement with the new name interpolated.
            composeRule
                .awaitNodeWithContentDescription(context.getString(R.string.app_feedback_button_renamed, newName))
                .assertIsDisplayed()

            // The overlay's entry+hold+exit window ends by popping the creation flow: the user lands back
            // on the list with the audio renamed, instead of an Activity finishing itself.
            composeRule.waitUntil(timeoutMillis = SAVE_SETTLE_MS) {
                composeRule.onAllNodesWithText(editTitle()).fetchSemanticsNodes().isEmpty()
            }
            composeRule.awaitNodeWithText(newName).assertIsDisplayed()
        }
    }

    @Test
    fun editedNameSurvivesRotation() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val typed = "mid_edit_typed"

        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            openEditFor(sound)

            // Replace the existing name with a half-typed value, then rotate WITHOUT saving. The
            // OutlinedTextField is the screen's primary action; losing the user's draft on rotation
            // is a worse UX bug than the (already covered) Success overlay disappearing.
            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            nameField().performTextInput(typed)

            scenario.recreate()

            // The destination itself must come back from the saved back stack, not just its text.
            composeRule.awaitNodeWithText(editTitle()).assertIsDisplayed()
            composeRule.awaitNode(hasSetTextAction()).assertTextContains(typed)
        }
    }

    @Test
    fun saveSuccessOverlaySurvivesRotation() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val newName = "rotated_rename"

        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            openEditFor(sound)

            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            nameField().performTextInput(newName)
            composeRule.onNodeWithText(string(R.string.app_addbutton_save_changes)).performClick()
            // Wait for the overlay to render BEFORE recreating, so we exercise restoration of the
            // Success state — not a race between save() and the recreate boundary.
            composeRule
                .awaitNodeWithContentDescription(context.getString(R.string.app_feedback_button_renamed, newName))
                .assertIsDisplayed()

            scenario.recreate()

            composeRule
                .awaitNodeWithContentDescription(context.getString(R.string.app_feedback_button_renamed, newName))
                .assertIsDisplayed()
        }
    }

    @Test
    fun editScreenExposesA11yContentDescriptions() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch(LandingActivity::class.java).use {
            openEditFor(sound)

            composeRule.awaitNodeWithContentDescription(string(R.string.app_addbutton_preview_audio)).assertHasClickAction()
        }
    }

    /**
     * Long-press the row → Rename, the only way into Edit now. Anchors on the destination's title so
     * every test starts from a settled naming screen: the row and the preview header both carry the
     * audio's name, so the name alone is not a unique anchor.
     */
    private fun openEditFor(sound: Sound) {
        composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
        composeRule.awaitNodeWithText(string(R.string.app_edit)).performClick()
        composeRule.awaitNodeWithText(editTitle()).assertIsDisplayed()
    }

    private fun nameField() = composeRule.onNode(hasSetTextAction())

    private fun editTitle() = string(R.string.app_addbutton_activity_title_edit)

    private fun string(resId: Int): String = context.getString(resId)

    private companion object {
        // The success overlay holds on screen before the flow pops; give it more room than the
        // default node-wait so a slow emulator frame budget doesn't read as a regression.
        const val SAVE_SETTLE_MS = 2 * WAIT_TIMEOUT_MS
    }
}
