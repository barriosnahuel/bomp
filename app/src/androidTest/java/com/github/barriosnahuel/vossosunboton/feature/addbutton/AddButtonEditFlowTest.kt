/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
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
    fun saveWithValidNameNavigatesBackToLandingWithRenamedExtra() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val newName = "renamed_custom"
        // Stub the LandingActivity launch so the framework does not actually start it (ActivityScenario only owns
        // AddButtonActivity, and resolving LandingActivity here would race against AddButtonActivity finishing).
        intending(hasComponent(hasClassName(LandingActivity::class.java.name)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            composeRule.awaitNode(hasSetTextAction()).performTextClearance()
            nameField().performTextInput(newName)
            composeRule.onNodeWithText(context.getString(R.string.app_addbutton_save_changes)).performClick()
            // Inspect Intents.getIntents() directly instead of Intents.intended(): when a resumed activity exists,
            // intended() routes the matcher through Espresso.onView(isRoot()).check(...), which triggers the
            // class-wide ATF run from AbstractUiTest. The view tree is mid-transition (AddButtonActivity finishing,
            // LandingActivity launch stubbed), so the Espresso check throws AssertionError, runCatching swallows
            // it, and the wait times out — even though the intent was already in the recorded list.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                Intents.getIntents().any { intent ->
                    intent.component?.className == LandingActivity::class.java.name &&
                        intent.getBooleanExtra(LandingActivity.EXTRA_BUTTON_RENAMED, false) &&
                        intent.getStringExtra(LandingActivity.EXTRA_BUTTON_NAME) == newName
                }
            }
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
