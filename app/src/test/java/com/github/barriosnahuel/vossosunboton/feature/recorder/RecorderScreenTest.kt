/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

internal class RecorderScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ready state shows the record affordance`() {
        composeTestRule.setContent { AppTheme { recorder(RecorderState.Ready) } }

        composeTestRule.onNodeWithText("Tap to record").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Start recording").assertIsDisplayed()
    }

    @Test
    fun `tapping the hero in ready invokes onRecordTap`() {
        var tapped = false
        composeTestRule.setContent { AppTheme { recorder(RecorderState.Ready, onRecordTap = { tapped = true }) } }

        composeTestRule.onNodeWithContentDescription("Start recording").performClick()

        assertThat(tapped).isTrue()
    }

    @Test
    fun `review state offers use and re-record`() {
        val review = RecorderState.Review(Uri.parse("content://clip"), durationMs = 4_000)
        composeTestRule.setContent { AppTheme { recorder(review) } }

        composeTestRule.onNodeWithText("Use this").assertIsDisplayed()
        composeTestRule.onNodeWithText("Re-record").assertIsDisplayed()
    }

    /**
     * OWASP MASVS-PLATFORM-1 / CWE-284 (Improper Access Control — a denied mic permission must never
     * dead-end; the user can still create via import). Guards the denial escape affordance.
     */
    @Test
    fun `a denied microphone offers an import escape`() {
        var imported = false
        composeTestRule.setContent {
            AppTheme {
                MicPermissionDenied(onOpenSettings = {}, onImportInstead = { imported = true }, onClose = {})
            }
        }

        composeTestRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import instead").performClick()

        assertThat(imported).isTrue()
    }

    @Test
    fun `review timer follows the playback position while previewing`() {
        val review = RecorderState.Review(Uri.parse("content://clip"), durationMs = 5_000)
        composeTestRule.setContent {
            AppTheme { recorder(review, isPreviewPlaying = true, previewPositionMs = 2_000) }
        }

        // Progress, not the static final duration (the stuck-timer bug).
        composeTestRule.onNodeWithText("0:02").assertIsDisplayed()
    }

    @Test
    fun `review timer shows the clip duration when not previewing`() {
        val review = RecorderState.Review(Uri.parse("content://clip"), durationMs = 5_000)
        composeTestRule.setContent {
            AppTheme { recorder(review, isPreviewPlaying = false, previewPositionMs = 0) }
        }

        composeTestRule.onNodeWithText("0:05").assertIsDisplayed()
    }

    @Test
    fun `review waveform reflects the playback progress`() {
        val review = RecorderState.Review(Uri.parse("content://clip"), durationMs = 5_000)
        composeTestRule.setContent {
            AppTheme { recorder(review, isPreviewPlaying = true, previewPositionMs = 2_000) }
        }

        composeTestRule
            .onNodeWithContentDescription("Playback progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))
    }

    @Composable
    private fun recorder(
        state: RecorderState,
        isPreviewPlaying: Boolean = false,
        previewPositionMs: Long = 0L,
        peaks: FloatArray? = null,
        onRecordTap: () -> Unit = {},
    ) {
        RecorderScreen(
            state = state,
            isPreviewPlaying = isPreviewPlaying,
            previewPositionMs = previewPositionMs,
            peaks = peaks,
            onRecordTap = onRecordTap,
            onStopTap = {},
            onPreviewToggle = {},
            onUseClip = {},
            onReRecord = {},
            onClose = {},
        )
    }
}
