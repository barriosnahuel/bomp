/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Activity-recreate coverage for [AddButtonActivity] Create mode, satisfying the
 * CLAUDE.md § Stateful Composables obligation: every Composable with durable [rememberSaveable]
 * state needs at least one `scenario.recreate()` test.
 *
 * Scope:
 *  - The typed draft name (`rememberSaveable(stateSaver = TextFieldValue.Saver)`).
 *  - The blank-name error (`rememberSaveable { mutableStateOf<String?>(null) }`).
 *  - The AudioPreview card re-renders after recreate (durationMs is re-extracted via
 *    `LaunchedEffect(source)`; not rememberSaveable by design).
 *
 * Explicitly OUT of scope: PlayerController playback state across recreate. AudioPreview's
 * `DisposableEffect.onDispose` calls `controller.stopPlayingSound()` when the composable
 * leaves composition, so playback stops on Activity destroy by design. A test asserting
 * "playback survives recreate" would either fail (against current behavior) or require a
 * production change. If preserving playback across recreate is desired UX, it warrants its
 * own decision + ADR.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonPreviewRecreateTest : AbstractUiTest() {
    @Test
    fun typedDraftNameSurvivesRecreate() {
        val typed = "mid_create_draft"

        ActivityScenario.launch<AddButtonActivity>(launchIntent(TestData.seedPreviewAudio(context))).use { scenario ->
            composeRule.awaitNode(hasSetTextAction()).performTextInput(typed)
            // Wait for the AudioPreview card to render before recreating so we cover restoration
            // alongside a fully-laid-out screen, not a half-mounted one.
            composeRule.awaitNodeWithContentDescription(previewLabel()).assertIsDisplayed()

            scenario.recreate()

            composeRule.awaitNode(hasSetTextAction()).assertTextContains(typed)
        }
    }

    @Test
    fun blankNameRequiredErrorSurvivesRecreate() {
        val errorText = context.getString(R.string.app_addbutton_name_is_required_error)

        ActivityScenario.launch<AddButtonActivity>(launchIntent(TestData.seedPreviewAudio(context))).use { scenario ->
            composeRule.awaitNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeRule.awaitNodeWithText(errorText).assertIsDisplayed()

            scenario.recreate()

            composeRule.awaitNodeWithText(errorText).assertIsDisplayed()
        }
    }

    @Test
    fun audioPreviewCardReRendersAfterRecreate() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(TestData.seedPreviewAudio(context))).use { scenario ->
            // Pre-recreate: card mounts after Media3 metadata extraction resolves the duration.
            composeRule.awaitNodeWithContentDescription(previewLabel()).assertIsDisplayed()
            composeRule.awaitNode(sliderMatcher).assertIsDisplayed()

            scenario.recreate()

            // Post-recreate: LaunchedEffect(source) re-runs, durationMs re-extracts, card re-mounts.
            composeRule.awaitNodeWithContentDescription(previewLabel()).assertIsDisplayed()
            composeRule.awaitNode(sliderMatcher).assertIsDisplayed()
        }
    }

    private fun previewLabel(): String = context.getString(R.string.app_addbutton_preview_audio)

    private fun launchIntent(uri: Uri): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    companion object {
        private val sliderMatcher: SemanticsMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    }
}
