/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [AddButtonActivity] in Edit mode (case 1.6).
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonEditFlowTest : AbstractUiTest() {
    @Test
    fun editModeRendersPreviewCardAndExistingName() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            // Both the OutlinedTextField and the AudioPreview header render the sound name,
            // so a plain hasText() matcher returns 2 nodes. Scope to the editable input.
            composeRule.awaitNode(hasSetTextAction()).assertIsDisplayed()
            // AudioPreview gates its Card render on `isReady`, which is flipped from a
            // LaunchedEffect after `withContext(Dispatchers.IO) { player.prepare() }` finishes —
            // an IO round-trip that `waitForIdle()` does not await.
            composeRule
                .awaitNodeWithContentDescription(context.getString(R.string.app_addbutton_preview_audio))
                .assertHasClickAction()
        }
    }

    @Test
    fun saveWithBlankNameShowsRequiredError() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            composeRule.onNodeWithText(context.getString(R.string.app_addbutton_save_changes)).performClick()
            composeRule
                .awaitNodeWithText(context.getString(R.string.app_addbutton_name_is_required_error))
                .assertIsDisplayed()
        }
    }

    @Test
    fun saveWithValidNameShowsConfirmationAndFinishes() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val newName = "renamed_custom"

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use { scenario ->
            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            nameField().performTextInput(newName)
            composeRule.onNodeWithText(context.getString(R.string.app_addbutton_save_changes)).performClick()
            // Confirmation lives inside the success overlay (no snackbar). Its semantics carry the
            // localised announcement with the new name interpolated.
            composeRule
                .awaitNodeWithContentDescription(context.getString(R.string.app_feedback_button_renamed, newName))
                .assertIsDisplayed()
            // Overlay finishes the entry+hold+exit window and then the Activity must finish so back
            // stack returns to the caller.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertThat(scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun editScreenExposesA11yContentDescriptions() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            composeRule
                .awaitNodeWithContentDescription(context.getString(R.string.app_addbutton_preview_audio))
                .assertHasClickAction()
        }
    }

    private fun editIntent(sound: Sound): Intent = LandingActivity.editIntent(context, sound)

    private fun nameField() = composeRule.onNode(hasSetTextAction())
}
