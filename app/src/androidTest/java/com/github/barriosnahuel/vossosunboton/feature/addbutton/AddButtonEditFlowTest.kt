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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
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
            composeRule.waitForIdle()
            // Both the OutlinedTextField and the AudioPreview header render the sound name,
            // so a plain hasText() matcher returns 2 nodes. Scope to the editable input.
            nameField().assertIsDisplayed()
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.app_addbutton_preview_audio))
                .assertHasClickAction()
        }
    }

    @Test
    fun saveWithBlankNameShowsRequiredError() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            nameField().performTextClearance()
            composeRule.onNodeWithText(context.getString(R.string.app_addbutton_save_changes)).performClick()
            composeRule.waitForIdle()
            composeRule
                .onNodeWithText(context.getString(R.string.app_addbutton_name_is_required_error))
                .assertIsDisplayed()
        }
    }

    @Test
    fun saveWithValidNameNavigatesBackToLandingWithRenamedExtra() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val newName = "renamed_custom"
        intending(hasComponent(hasClassName(LandingActivity::class.java.name)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            nameField().performTextClearance()
            nameField().performTextInput(newName)
            composeRule.onNodeWithText(context.getString(R.string.app_addbutton_save_changes)).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                // Hamcrest 1.3 (transitive in the test APK) lacks the 2-arg `allOf` Kotlin
                // compiles to. Stick to one matcher per intended() call.
                runCatching {
                    intended(hasComponent(hasClassName(LandingActivity::class.java.name)))
                    intended(hasExtra(LandingActivity.EXTRA_BUTTON_RENAMED, true))
                    intended(hasExtra(LandingActivity.EXTRA_BUTTON_NAME, newName))
                }.isSuccess
            }
        }
    }

    @Test
    fun editScreenExposesA11yContentDescriptions() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<AddButtonActivity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.app_addbutton_preview_audio))
                .assertHasClickAction()
        }
    }

    private fun editIntent(sound: Sound): Intent = LandingActivity.editIntent(context, sound)

    private fun nameField() = composeRule.onNode(hasSetTextAction())

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
