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
 * The Activity lives in the dynamic feature module, which the `:app` module cannot depend
 * on at compile-time without creating a cycle. We launch via an intent that targets the
 * class by name and resolve `feature_addbutton_*` strings through [string] (a runtime
 * `Resources.getIdentifier` lookup), so a rename in the feature's `strings.xml` fails the
 * test loudly instead of silently passing on en_US.
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
            composeRule.onNodeWithText(string("feature_addbutton_save", "feature_addbutton")).assertIsDisplayed()
            composeRule.onNodeWithText(string("feature_addbutton_name", "feature_addbutton")).assertIsDisplayed()
        }
    }

    @Test
    fun save_with_blank_name_shows_required_error() {
        ActivityScenario.launch<Activity>(launchIntent(uri = SAMPLE_URI)).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(string("feature_addbutton_save", "feature_addbutton")).performClick()
            composeRule.waitForIdle()
            composeRule
                .onNodeWithText(string("feature_addbutton_name_is_required_error", "feature_addbutton"))
                .assertIsDisplayed()
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
        private val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
    }
}
