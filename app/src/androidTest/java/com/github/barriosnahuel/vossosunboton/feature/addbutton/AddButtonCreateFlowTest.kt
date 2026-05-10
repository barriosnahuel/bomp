/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [AddButtonActivity] in Create mode (case 1.5).
 *
 * Launches via an intent so the test stays compatible with the (former) dynamic-feature
 * package layout: only the FQN moves if the Activity ever changes packages.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonCreateFlowTest : AbstractUiTest() {
    @Test
    fun createModeWithoutUriFinishesImmediately() {
        // The Activity shows a toast and finish()es in onCreate. Reading scenario.state
        // on the destroyed scenario triggers a runOnMainSync that crashes the runner on
        // some AVDs, so this test only verifies launch + close complete without throwing.
        ActivityScenario.launch<AddButtonActivity>(launchIntent(uri = null)).use { /* no-op */ }
    }

    @Test
    fun createModeWithUriRendersSaveButtonAndForm() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(uri = SAMPLE_URI)).use {
            composeRule.awaitNodeWithText(context.getString(R.string.app_addbutton_save)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.app_addbutton_name)).assertIsDisplayed()
        }
    }

    @Test
    fun saveWithBlankNameShowsRequiredError() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(uri = SAMPLE_URI)).use {
            composeRule.awaitNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeRule
                .awaitNodeWithText(context.getString(R.string.app_addbutton_name_is_required_error))
                .assertIsDisplayed()
        }
    }

    private fun launchIntent(uri: Uri?): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            if (uri != null) putExtra(Intent.EXTRA_STREAM, uri)
        }

    companion object {
        private val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
    }
}
