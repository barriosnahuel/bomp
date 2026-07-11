/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.rule.GrantPermissionRule
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented integration coverage for ADR 0019 § Draft recovery: a persisted draft (its temp clip +
 * the [DataStoreRecorderDraftStore] metadata) reopens the recorder straight into Review. This is the
 * launcher-re-entry / process-death path — the bug where a captured-but-unsaved clip was silently lost
 * when the app was re-opened from the launcher instead of Recents. The recorder is a destination now,
 * so the resume runs through the real entry point: the Landing draft banner. Real DataStore +
 * FileProvider run on the emulator; only the mic (irrelevant here, no recording happens) is absent.
 */
internal class RecorderResumeDraftTest : AbstractUiTest() {
    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val draftStore by lazy { DataStoreRecorderDraftStore(context) }

    @After
    fun clearDraft() {
        runBlocking { draftStore.clearForTest() }
        RecorderTempFiles.purge(context)
    }

    @Test
    fun resumingADraftReopensTheRecorderInReview() {
        // One custom sound so My Sounds renders its normal list (not the first-run welcome surface),
        // which is what the draft banner sits above.
        TestData.seedCustomSounds(context, count = 1)
        val clip = RecorderTempFiles.newTempFile(context)
        context.resources.openRawResource(R.raw.app_branding_audio).use { input ->
            clip.outputStream().use { output -> input.copyTo(output) }
        }
        runBlocking {
            draftStore.save(clip, durationMs = 3_000)
            draftStore.draft.first { it != null }
        }

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_continue)).performClick()

            // Review affordances present → the persisted clip was restored, not lost.
            composeRule.awaitNodeWithText(string(R.string.app_recorder_use)).assertIsDisplayed()
            composeRule.awaitNodeWithText(string(R.string.app_recorder_rerecord)).assertIsDisplayed()
            // 3 s restored duration surfaces on the timer.
            composeRule.awaitNodeWithText("0:03").assertIsDisplayed()
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
