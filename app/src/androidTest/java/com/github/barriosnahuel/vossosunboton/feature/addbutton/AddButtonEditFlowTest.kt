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
import androidx.compose.ui.test.hasText
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
import org.hamcrest.CoreMatchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [AddButtonActivity] in Edit mode (case 1.6).
 *
 * The AddButtonActivity class lives in the dynamic feature module, which the `:app` module
 * cannot depend on at compile-time without creating a cycle. Tests therefore launch via an
 * [Intent] targeting the class by name (using [LandingActivity.editIntent]) and reference
 * `feature_addbutton_*` strings by their English literal values — the AVD locale is `en_US`
 * by default.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonEditFlowTest : AbstractUiTest() {
    @Test
    fun edit_mode_renders_preview_card_and_existing_name() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            composeRule.onNode(hasText(sound.name)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(PREVIEW_AUDIO).assertHasClickAction()
        }
    }

    @Test
    fun save_with_blank_name_shows_required_error() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            composeRule.onNode(hasText(sound.name)).performTextClearance()
            composeRule.onNodeWithText(SAVE_CHANGES).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(NAME_REQUIRED_ERROR).assertIsDisplayed()
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
            composeRule.onNode(hasText(sound.name)).performTextClearance()
            composeRule.onNode(hasSetTextAction()).performTextInput(newName)
            composeRule.onNodeWithText(SAVE_CHANGES).performClick()
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                runCatching {
                    intended(
                        allOf(
                            hasComponent(hasClassName(LandingActivity::class.java.name)),
                            hasExtra(LandingActivity.EXTRA_BUTTON_RENAMED, true),
                            hasExtra(LandingActivity.EXTRA_BUTTON_NAME, newName),
                        ),
                    )
                }.isSuccess
            }
        }
    }

    @Test
    fun edit_screen_exposes_a11y_content_descriptions() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()

        ActivityScenario.launch<Activity>(editIntent(sound)).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(PREVIEW_AUDIO).assertHasClickAction()
        }
    }

    private fun editIntent(sound: Sound): Intent = LandingActivity.editIntent(context, sound)

    companion object {
        // String literals from feature_addbutton/src/main/res/values/strings.xml.
        // The androidTest source set in :app cannot import the dynamic feature's R class
        // without creating a circular dependency.
        private const val PREVIEW_AUDIO = "Preview audio"
        private const val SAVE_CHANGES = "Save changes"
        private const val NAME_REQUIRED_ERROR = "Name is required"
        private const val WAIT_TIMEOUT_MS = 5_000L
    }
}
