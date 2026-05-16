/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the inbound-URI security boundary documented in
 * CLAUDE.md § *Security boundaries → Inbound URI validation*. Each test launches
 * [AddButtonActivity] in Create mode with an intentionally-invalid `EXTRA_STREAM` URI, taps
 * Save, and asserts the rejection feedback Snackbar is shown — confirming the validator's
 * scheme / MIME / size branches reject end-to-end without crashing the app.
 *
 * Unit-level coverage in `AddButtonFeatureTest` exercises the validator function directly with
 * synthetic inputs. This instrumented test catches regressions in the integration: real
 * `ContentResolver` resolving real `content://` URIs against the real (debug) `FileProvider`,
 * the actual save coroutine path, and the screen's Snackbar wiring for the rejection result.
 */
@RunWith(AndroidJUnit4::class)
internal class InboundUriValidationFlowTest : AbstractUiTest() {
    @Test
    fun httpSchemeUriShowsRejectionSnackbar() {
        // `http://` is outside `AddButtonFeature.ALLOWED_SCHEMES` ({content, file}). The validator
        // rejects without touching ContentResolver, so this also confirms scheme is checked first.
        val httpUri: Uri = Uri.parse("http://example.com/audio.mp3")

        ActivityScenario.launch<AddButtonActivity>(launchIntent(httpUri)).use {
            typeNameAndSave("rejected_http")
            composeRule.awaitNodeWithText(rejectionFeedback()).assertIsDisplayed()
        }
    }

    @Test
    fun nonAudioMimeUriShowsRejectionSnackbar() {
        // A `.txt` file served through the FileProvider reports MIME `text/plain`. The validator
        // rejects because the MIME does not start with `audio/`.
        val textUri = TestData.seedTextFileUri(context)

        ActivityScenario.launch<AddButtonActivity>(launchIntent(textUri)).use {
            typeNameAndSave("rejected_mime")
            composeRule.awaitNodeWithText(rejectionFeedback()).assertIsDisplayed()
        }
    }

    @Test
    fun oversizedAudioUriShowsRejectionSnackbar() {
        // A sparse 52 MB `.mp3` is above the validator's 50 MB cap; MIME passes (`audio/mpeg`),
        // size fails. Sparse allocation keeps the seeding cost trivial.
        val bigUri = TestData.seedOversizedAudioUri(context)

        ActivityScenario.launch<AddButtonActivity>(launchIntent(bigUri)).use {
            typeNameAndSave("rejected_oversize")
            composeRule.awaitNodeWithText(rejectionFeedback()).assertIsDisplayed()
        }
    }

    private fun typeNameAndSave(name: String) {
        composeRule.awaitNode(hasSetTextAction()).performTextInput(name)
        composeRule.awaitNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
    }

    private fun rejectionFeedback(): String = context.getString(R.string.app_feedback_generic_error_contact_support)

    private fun launchIntent(uri: Uri): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
