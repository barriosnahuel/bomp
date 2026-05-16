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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the AudioPreview card in [AddButtonActivity] Create mode. Pairs
 * with the FileProvider infrastructure added in this stack so the preview is exercised against
 * a real `content://` URI and a real `MediaPlayer`, not a mocked path. Together with
 * [AddButtonPreviewRecreateTest] and the cross-screen [PlayerConcurrencyFlowTest], this is the
 * instrumented complement to the unit-level coverage in `PlayerControllerTest`.
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonPreviewFlowTest : AbstractUiTest() {
    @Test
    fun previewCardRendersWhenLaunchedWithPlayableUri() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(TestData.seedPreviewAudio(context))).use {
            // The IconButton's contentDescription is the same string in play and pause states
            // (the icon swaps; the a11y label stays "Preview audio"). Awaiting it confirms the
            // Card mounted, which only happens after MediaMetadataRetriever resolves durationMs > 0.
            composeRule.awaitNodeWithContentDescription(previewLabel()).assertIsDisplayed()
            // Slider exists alongside the play button — independent confirmation of the Card layout.
            composeRule.awaitNode(sliderMatcher).assertIsDisplayed()
        }
    }

    @Test
    fun tapPlayInPreviewSwapsToPauseAndAdvancesSlider() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(TestData.seedPreviewAudio(context))).use {
            // Slider is disabled until isPlaying flips true — this is the only deterministic
            // semantic flag for the play/pause state, since the IconButton's contentDescription
            // does not change between the two states.
            composeRule.awaitNode(sliderMatcher).assertIsNotEnabled()
            composeRule.awaitNodeWithContentDescription(previewLabel()).performClick()
            composeRule.waitUntil(timeoutMillis = PLAYBACK_SETTLE_MS) {
                composeRule.sliderProgress() > 0f
            }
            composeRule.onNode(sliderMatcher).assertIsEnabled()
            assertTrue(
                "slider must advance after tapping play; got ${composeRule.sliderProgress()}",
                composeRule.sliderProgress() > 0f,
            )
        }
    }

    @Test
    fun tapPauseHoldsPositionTapPlayResumesFromIt() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(TestData.seedPreviewAudio(context))).use {
            val playButton = composeRule.awaitNodeWithContentDescription(previewLabel())

            // Start playback and let the slider advance past a measurable threshold.
            playButton.performClick()
            composeRule.waitUntil(timeoutMillis = PLAYBACK_SETTLE_MS) {
                composeRule.sliderProgress() >= PROGRESS_THRESHOLD
            }
            val progressBeforePause = composeRule.sliderProgress()

            // Pause: same IconButton, second tap goes through the isPlaying branch and calls pause().
            playButton.performClick()
            // After pause settles, the slider must be disabled again (enabled = isPlaying = false).
            composeRule.waitUntil(timeoutMillis = PAUSE_SETTLE_MS) {
                !composeRule.sliderIsEnabled()
            }
            val progressWhenPaused = composeRule.sliderProgress()

            // Resume: third tap routes to controller.resume() because isOurPreview is still true.
            playButton.performClick()
            composeRule.waitUntil(timeoutMillis = PLAYBACK_SETTLE_MS) {
                composeRule.sliderProgress() > progressWhenPaused
            }
            val progressAfterResume = composeRule.sliderProgress()

            assertTrue(
                "progress before pause must be > 0 (was $progressBeforePause)",
                progressBeforePause > 0f,
            )
            assertTrue(
                "progress when paused must be >= progress before pause minus jitter " +
                    "(before=$progressBeforePause paused=$progressWhenPaused)",
                progressWhenPaused >= progressBeforePause - PROGRESS_JITTER,
            )
            assertTrue(
                "progress after resume must exceed paused position " +
                    "(paused=$progressWhenPaused resumed=$progressAfterResume)",
                progressAfterResume > progressWhenPaused,
            )
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
        // The Slider is the only node in the AudioPreview that exposes `ProgressBarRangeInfo`,
        // so a `keyIsDefined` matcher uniquely identifies it without needing a testTag.
        private val sliderMatcher: SemanticsMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

        // MediaPlayer prepare on a 5s clip + Compose recomposition + StateFlow propagation must
        // all settle inside this window. Long enough for cold-AVD codec spin-up; short enough
        // that a genuine regression surfaces before JUnit times out the test.
        private const val PLAYBACK_SETTLE_MS: Long = 5_000L

        // Pause path settles faster — just a StateFlow emit + recomposition; no MediaPlayer.prepare().
        private const val PAUSE_SETTLE_MS: Long = 2_000L

        // Slider must advance past this fraction (5% ≈ 250ms on a 5s clip) before we capture the
        // pause-baseline. Below ~1% the StateFlow emit cadence (~100ms) is the dominant variance.
        private const val PROGRESS_THRESHOLD: Float = 0.05f

        // Tolerance for the paused-position assertion: PlayerController may emit one trailing
        // StateFlow update with an in-flight position read between pause() being called and the
        // underlying MediaPlayer actually stopping. The captured "paused" value is therefore
        // allowed to round up slightly past the pre-pause read; we only assert it doesn't drift
        // backward by more than this jitter, which would indicate a real bug.
        private const val PROGRESS_JITTER: Float = 0.02f
    }
}

private fun ComposeTestRule.sliderProgress(): Float {
    val node =
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode()
    return node.config.getOrElseNullable(SemanticsProperties.ProgressBarRangeInfo) { null }?.current ?: 0f
}

private fun ComposeTestRule.sliderIsEnabled(): Boolean {
    val node = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNode()
    return !node.config.contains(SemanticsProperties.Disabled)
}
