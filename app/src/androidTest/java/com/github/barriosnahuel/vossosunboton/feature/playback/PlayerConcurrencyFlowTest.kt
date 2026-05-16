/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end coverage for the Stream↔Sound preempt invariant from [ADR 0005]:
 * at most one of {Sound playback, Stream preview, nothing} runs at a time, and switching kinds
 * preempts (pauses Sound / clears Stream) before starting the new target.
 *
 * Unit-level coverage in `PlayerControllerTest` exercises the wrapper logic with a mocked
 * `MediaPlayer`. This instrumented test catches regressions in the layers that the unit test
 * cannot reach: real `MediaPlayer` state machine, real `PlayerControllerListener` wiring driven
 * by the live `SoundsViewModel`, and the UI updates that observe the controller's `StateFlow` and
 * listener callbacks.
 *
 * The test drives the *other* engine through the controller's API rather than launching a second
 * Activity (Home + AddButtonActivity simultaneously is awkward under ActivityScenario). The
 * MediaPlayer + listener chain is what the test is checking; the cross-Activity navigation is
 * tangential.
 */
@RunWith(AndroidJUnit4::class)
internal class PlayerConcurrencyFlowTest : AbstractUiTest() {
    @Test
    fun streamPreemptsSoundWhenPreviewStartsWhileBompIsPlaying() {
        TestData.seedCustomSounds(context, count = 1)
        val previewUri = TestData.seedPreviewAudio(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Bomp starts playing — UI swaps the play icon for pause.
            composeRule.awaitNodeWithContentDescription(playLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(pauseLabel()).assertIsDisplayed()

            // Drive the *other* engine: start a preview URI through the controller directly.
            // In production, this is how AudioPreview's `onClick` calls into the controller; here
            // we bypass the AddButtonActivity navigation and trigger the same code path.
            val controller = PlayerControllerFactory.instance
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller.startPlayingUri(context, previewUri)
            }

            // Stream is now the active engine — its StateFlow value is non-null and matches our URI.
            composeRule.waitUntil(timeoutMillis = PREEMPT_TIMEOUT_MS) {
                controller.playbackState.value?.uri == previewUri
            }
            // The Bomp's tile reverts to the play icon — the listener received `onPlayerPause(...)`
            // and the SoundsViewModel surfaced it to the UI.
            composeRule.awaitNodeWithContentDescription(playLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun soundPreemptsStreamWhenBompStartsWhilePreviewIsPlaying() {
        TestData.seedCustomSounds(context, count = 1)
        val previewUri = TestData.seedPreviewAudio(context)

        // Start the preview *before* launching Home so the controller is already running a Stream
        // when the Activity comes up and observes the seeded Bomp in the "not playing" state.
        val controller = PlayerControllerFactory.instance
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.startPlayingUri(context, previewUri)
        }
        composeRule.waitUntil(timeoutMillis = PREEMPT_TIMEOUT_MS) {
            controller.playbackState.value?.uri == previewUri
        }

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Bomp's tile shows the play icon (Stream is the active engine, not this Sound).
            composeRule.awaitNodeWithContentDescription(playLabel()).performClick()

            // Stream gets preempted — playbackState collapses to null.
            composeRule.waitUntil(timeoutMillis = PREEMPT_TIMEOUT_MS) {
                controller.playbackState.value == null
            }
            // The Bomp is now the active engine — UI shows the pause icon.
            composeRule.awaitNodeWithContentDescription(pauseLabel()).assertIsDisplayed()
        }
    }

    private fun playLabel(): String = context.getString(R.string.app_play)

    private fun pauseLabel(): String = context.getString(R.string.app_pause)

    companion object {
        // MediaPlayer.prepare on a 5s clip + StateFlow emit + UI recomposition + listener chain
        // must settle inside this window. Generous for cold-AVD codec spin-up.
        private const val PREEMPT_TIMEOUT_MS: Long = 5_000L
    }
}
