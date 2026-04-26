/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [AddButtonActivity] in Create mode (case 1.5).
 *
 * See [AddButtonEditFlowTest] for the pattern: launching by intent and using English
 * string literals because the dynamic feature's R class is unreachable from `:app`.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonCreateFlowTest : AbstractUiTest() {
    @Test
    fun create_mode_without_uri_finishes_immediately() {
        ActivityScenario.launch<Activity>(launchIntent(uri = null)).use { scenario ->
            // The Activity shows a toast and calls finish() inside onCreate, so by the
            // time the launch returns, scenario.state is already DESTROYED. There's no
            // composable to settle and calling moveToState(RESUMED) on a destroyed
            // ActivityScenario throws IllegalStateException.
            assert(scenario.state == Lifecycle.State.DESTROYED) {
                "Expected Activity to be DESTROYED after missing-URI guard. Was: ${scenario.state}"
            }
        }
    }

    @Test
    fun create_mode_with_uri_renders_save_button_and_form() {
        ActivityScenario.launch<Activity>(launchIntent(uri = SAMPLE_URI)).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(SAVE).assertIsDisplayed()
            composeRule.onNodeWithText(NAME_FIELD_LABEL).assertIsDisplayed()
        }
    }

    @Test
    fun save_with_blank_name_shows_required_error() {
        ActivityScenario.launch<Activity>(launchIntent(uri = SAMPLE_URI)).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(SAVE).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(NAME_REQUIRED_ERROR).assertIsDisplayed()
        }
    }

    private fun launchIntent(uri: Uri?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            setClassName(context, ADD_BUTTON_ACTIVITY)
            if (uri != null) putExtra(Intent.EXTRA_STREAM, uri)
        }

    companion object {
        private const val ADD_BUTTON_ACTIVITY =
            "com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity"

        // Strings from feature_addbutton/src/main/res/values/strings.xml.
        private const val SAVE = "Save"
        private const val NAME_FIELD_LABEL = "Choose a funny name for your new button"
        private const val NAME_REQUIRED_ERROR = "Name is required"
        private val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
    }
}
