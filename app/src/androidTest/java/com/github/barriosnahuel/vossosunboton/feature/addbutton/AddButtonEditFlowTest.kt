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
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [AddButtonActivity] in Edit mode (case 1.6).
 *
 * The Activity lives in the dynamic feature module, which the `:app` module cannot depend
 * on at compile-time without creating a cycle. We launch via [LandingActivity.editIntent]
 * (sets the class by name) and resolve `feature_addbutton_*` strings through [string]
 * (a runtime `Resources.getIdentifier` lookup), so a rename in the feature's `strings.xml`
 * fails the test loudly instead of silently passing on en_US.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonEditFlowTest : AbstractUiTest() {
    @Test
    fun edit_mode_renders_preview_card_and_existing_name() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            // Both the OutlinedTextField and the AudioPreview header render the sound name,
            // so a plain hasText() matcher returns 2 nodes. Scope to the editable input.
            nameField().assertIsDisplayed()
            composeRule
                .onNodeWithContentDescription(string("feature_addbutton_preview_audio", "feature_addbutton"))
                .assertHasClickAction()
        }
    }

    @Test
    fun save_with_blank_name_shows_required_error() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            nameField().performTextClearance()
            composeRule.onNodeWithText(string("feature_addbutton_save_changes", "feature_addbutton")).performClick()
            composeRule.waitForIdle()
            composeRule
                .onNodeWithText(string("feature_addbutton_name_is_required_error", "feature_addbutton"))
                .assertIsDisplayed()
        }
    }

    @Test
    fun save_with_valid_name_navigates_back_to_landing_with_renamed_extra() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        val newName = "renamed_custom"
        intending(hasComponent(hasClassName(LandingActivity::class.java.name)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            nameField().performTextClearance()
            nameField().performTextInput(newName)
            composeRule.onNodeWithText(string("feature_addbutton_save_changes", "feature_addbutton")).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                // Same hamcrest 1.3 `allOf` gap as elsewhere — assert the most discriminating
                // matchers in series. Renamed extra is unique to this navigation path.
                runCatching {
                    intended(hasComponent(hasClassName(LandingActivity::class.java.name)))
                    intended(hasExtra(LandingActivity.EXTRA_BUTTON_RENAMED, true))
                    intended(hasExtra(LandingActivity.EXTRA_BUTTON_NAME, newName))
                }.isSuccess
            }
        }
    }

    @Test
    fun edit_screen_exposes_a11y_content_descriptions() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            composeRule
                .onNodeWithContentDescription(string("feature_addbutton_preview_audio", "feature_addbutton"))
                .assertHasClickAction()
        }
    }

    private fun editIntent(sound: Sound): Intent = LandingActivity.editIntent(context, sound)

    private fun nameField() = composeRule.onNode(hasSetTextAction())

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
