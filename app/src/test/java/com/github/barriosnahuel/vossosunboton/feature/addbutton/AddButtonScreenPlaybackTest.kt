/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.SoundSource
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Guards the contract that the moment `save()` flips to [SaveOutcome.Success] — i.e. the success
 * morph starts — any in-flight preview playback is explicitly stopped. Without this the only stop
 * is AudioPreview's `DisposableEffect.onDispose`, which fires when the Activity tears down ~600 ms
 * later (overlay hold + `finish()`), so audio keeps bleeding into the confirmation moment.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonScreenPlaybackTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private lateinit var fakeController: PlayerController
    private lateinit var originalController: PlayerController

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())
        AddButtonFeatureProvider.setForTest(AlwaysSucceedFeature())
        originalController = PlayerControllerFactory.instance
        fakeController =
            mockk(relaxed = true) {
                every { playbackState } returns playbackStateFlow
            }
        PlayerControllerFactory.instance = fakeController
    }

    @After
    fun tearDown() {
        PlayerControllerFactory.instance = originalController
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
    }

    @Test
    fun `Create flow stops preview playback when save transitions to the success morph`() {
        // Simulate the user mid-preview: the URI-bound playbackState matches the share-incoming Uri.
        playbackStateFlow.value =
            PlaybackState(uri = SAMPLE_URI, positionMs = 250, durationMs = 5_000, isPlaying = true)

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            // Pause the clock so we can observe the post-save state BEFORE the overlay's auto-finish
            // (~600 ms) tears down the Activity — without this the AudioPreview DisposableEffect could
            // also call stopPlayingSound() during teardown and the assertion would not isolate the fix.
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            // Advance just enough for save() to flip to Success — well under the 600 ms hold.
            composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

            verify(exactly = 1) { fakeController.stopPlayingSound() }

            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `Edit flow stops preview playback when save transitions to the success morph`() {
        playbackStateFlow.value =
            PlaybackState(uri = EXISTING_URI, positionMs = 100, durationMs = 3_000, isPlaying = true)

        ActivityScenario.launch<AddButtonActivity>(editIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
                .performClick()
            composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

            verify(exactly = 1) { fakeController.stopPlayingSound() }

            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `Create flow does not stop preview when save fails so the user can retry without losing the audio`() {
        // Failure path stays on the form (snackbar with Retry). If the preview were pre-empted on
        // failure the user would have to re-tap play to verify the audio before retrying — which
        // is the exact UX trap the success-path fix is meant to avoid carrying over to errors.
        playbackStateFlow.value =
            PlaybackState(uri = SAMPLE_URI, positionMs = 200, durationMs = 4_000, isPlaying = true)
        AddButtonFeatureProvider.setForTest(
            object : AddButtonFeature {
                override fun saveNewButtonAsync(
                    context: Context,
                    name: String,
                    uri: String,
                    publicCollectionIds: Set<String>,
                    privateCollectionIds: Set<String>,
                    source: SoundSource,
                ): Deferred<Int> = CompletableDeferred(R.string.app_addbutton_feedback_save_failed)

                override fun renameButtonAsync(
                    context: Context,
                    sound: Sound,
                    newName: String,
                ): Deferred<Unit> = CompletableDeferred(Unit)
            },
        )

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

            verify(exactly = 0) { fakeController.stopPlayingSound() }

            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `save does not preempt playback when nothing is playing`() {
        // Idle controller: no preview was running. The fix must not pre-empt anything that isn't
        // ours — guards against future regressions where a global stop would interfere with an
        // unrelated playback owned by another surface.
        playbackStateFlow.value = null

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

            verify(exactly = 0) { fakeController.stopPlayingSound() }

            composeTestRule.mainClock.autoAdvance = true
        }
    }

    private fun createIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, SAMPLE_URI)
        }

    private fun editIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            putExtra(LandingActivity.EXTRA_EDIT_SOUND, testSound(EXISTING_NAME, file = "existing.mp3"))
        }

    private class AlwaysSucceedFeature : AddButtonFeature {
        override fun saveNewButtonAsync(
            context: Context,
            name: String,
            uri: String,
            publicCollectionIds: Set<String>,
            privateCollectionIds: Set<String>,
            source: SoundSource,
        ): Deferred<Int> = CompletableDeferred(R.string.app_addbutton_feedback_saved_ok)

        override fun renameButtonAsync(
            context: Context,
            sound: Sound,
            newName: String,
        ): Deferred<Unit> = CompletableDeferred(Unit)
    }

    private companion object {
        val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
        val EXISTING_URI: Uri = Uri.parse("file:///tmp/existing.mp3")
        const val NEW_NAME = "Test sound"
        const val EXISTING_NAME = "Existing sound"
        const val CLOCK_STEP_MS = 100L
    }
}
