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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Covers the non-blocking duplicate-name hint introduced in PR #2 of the stable-`Sound.id` series.
 * The hint surfaces when the user types a name that matches an existing Bomp; the save button
 * stays enabled because [SoundsRepository.save] upserts by id (ADR 0008), so two same-named
 * Bomps can legitimately coexist.
 *
 * The inline play/stop toggle is scoped to *this match*: tap → `startPlayingUri` (preview path,
 * resets on each start, doesn't pollute home position cache); tap again while playing →
 * `stopPlayingSound`; disposal → stops the controller only when our uri is the active one.
 */
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class AddButtonScreenDuplicateNameHintTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val hintText
        get() = context.getString(R.string.app_addbutton_duplicate_name_hint)
    private val playLabel
        get() = context.getString(R.string.app_addbutton_duplicate_name_hint_play_description)
    private val stopLabel
        get() = context.getString(R.string.app_addbutton_duplicate_name_hint_stop_description)

    private lateinit var fake: FakeAnalyticsTracker
    private lateinit var feature: FakeAddButtonFeature
    private lateinit var playbackStateFlow: MutableStateFlow<PlaybackState?>

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        feature = FakeAddButtonFeature()
        AddButtonFeatureProvider.setForTest(feature)
        playbackStateFlow = MutableStateFlow(null)
        // Mocked so the inline-play test never actually loads a MediaPlayer; the hint observes
        // `playbackState` directly to drive its play/stop toggle, so the field is exposed via a
        // `MutableStateFlow` tests can mutate to simulate live transitions.
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.playbackState } returns playbackStateFlow
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startPlayingUri(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        every { PlayerControllerFactory.instance.pause() } answers { nothing }
        every { PlayerControllerFactory.instance.resume() } answers { nothing }
        every { PlayerControllerFactory.instance.seekTo(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        runBlocking { SoundsRepository(context).clearForTest() }
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
        runBlocking { SoundsRepository(context).clearForTest() }
        unmockkAll()
    }

    @Test
    fun `hint appears in Create mode when the typed name matches an existing custom Bomp`() {
        seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
            fake.assertEmitted("duplicate_name_hint_shown")
        }
    }

    @Test
    fun `hint matches case-insensitively and trims whitespace`() {
        seedSound("Bell", "bell.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("  BELL  ")

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
        }
    }

    @Test
    fun `hint stays hidden in Create mode when the typed name has no match`() {
        seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("brand new")

            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithText(hintText).assertCountEquals(0)
            fake.assertNotEmitted("duplicate_name_hint_shown")
        }
    }

    @Test
    fun `hint stays hidden in Edit mode when the name still matches only the sound being edited`() {
        val self = seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(editIntent(self)).use {
            composeTestRule.waitForIdle()

            // Field is pre-populated with the sound's own name; the only match is the sound
            // itself, which is excluded by id (ADR 0008) — the hint must not fire.
            composeTestRule.onAllNodesWithText(hintText).assertCountEquals(0)
            fake.assertNotEmitted("duplicate_name_hint_shown")
        }
    }

    @Test
    fun `hint appears in Edit mode when the typed name matches a different existing Bomp`() {
        val self = seedSound(EXISTING_NAME, "existing.mp3")
        seedSound(OTHER_NAME, "other.mp3")

        ActivityScenario.launch<AddButtonActivity>(editIntent(self)).use {
            composeTestRule.waitForIdle()
            // Replace the pre-populated name with the other existing sound's name.
            composeTestRule.onNode(hasSetTextAction()).performTextReplacement(OTHER_NAME)

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
        }
    }

    @Test
    fun `save proceeds while the duplicate-name hint is showing (non-blocking)`() {
        seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            // The save action must fire even with the hint visible — the hint is purely informational.
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitForIdle()

            assertThat(feature.saveNewCalls).isEqualTo(1)
        }
    }

    @Test
    fun `tapping the inline play routes through startPlayingUri (preview path) and fires analytics`() {
        val match = seedSound(EXISTING_NAME, "existing.mp3")
        val expected = expectedUri(match)
        // Extract `instance` once: `verify(exactly = 0) { PlayerControllerFactory.instance.X }`
        // also asserts the getter chain (`getInstance$app`) is never called, which fails because
        // production code legitimately accesses it.
        val ctrl = PlayerControllerFactory.instance

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription(playLabel).performClick()
            composeTestRule.waitForIdle()

            fake.assertEmitted("duplicate_name_hint_play")
            verify { ctrl.startPlayingUri(any(), expected) }
            verify(exactly = 0) { ctrl.startPlayingSound(any(), any()) }
        }
    }

    @Test
    fun `tapping the inline button while this match is playing stops it (no restart)`() {
        val match = seedSound(EXISTING_NAME, "existing.mp3")
        val expected = expectedUri(match)
        val ctrl = PlayerControllerFactory.instance

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            // Simulate the controller transitioning into "playing this match" — the hint should
            // morph to the stop affordance and the next tap should hit `stopPlayingSound`.
            playbackStateFlow.value = PlaybackState(uri = expected, positionMs = 0, durationMs = 1_000, isPlaying = true)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithContentDescription(stopLabel).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription(stopLabel).performClick()
            composeTestRule.waitForIdle()

            verify { ctrl.stopPlayingSound() }
            // No second start: a re-tap while playing must stop, never re-issue play.
            verify(exactly = 0) { ctrl.startPlayingUri(any(), any()) }
            // Stopping is not analytics-tracked — only the user-initiated play tap is.
            fake.assertNotEmitted("duplicate_name_hint_play")
        }
    }

    @Test
    fun `icon stays as play when an unrelated uri is the one currently playing`() {
        seedSound(EXISTING_NAME, "existing.mp3")
        val unrelated = Uri.parse("content://test/other-audio.mp3")
        playbackStateFlow.value = PlaybackState(uri = unrelated, positionMs = 0, durationMs = 1_000, isPlaying = true)

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            // Hint sees a non-matching playbackState → its toggle is "play" (not stop), regardless
            // of whether something else is playing. This is the AudioPreview-coexistence case.
            composeTestRule.onNodeWithContentDescription(playLabel).assertIsDisplayed()
            composeTestRule.onAllNodesWithContentDescription(stopLabel).assertCountEquals(0)
        }
    }

    @Test
    fun `tapping play preempts an unrelated playing uri (AudioPreview coexistence)`() {
        val match = seedSound(EXISTING_NAME, "existing.mp3")
        val expected = expectedUri(match)
        val unrelated = Uri.parse("content://test/other-audio.mp3")
        playbackStateFlow.value = PlaybackState(uri = unrelated, positionMs = 0, durationMs = 1_000, isPlaying = true)
        val ctrl = PlayerControllerFactory.instance

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription(playLabel).performClick()
            composeTestRule.waitForIdle()

            // The hint calls `startPlayingUri` even though something else is already playing — the
            // controller's `startPlayingUri` contract handles preemption (PlayerController.kt:53-63).
            // This test pins the expectation that we *do* call the preemption-capable API.
            verify { ctrl.startPlayingUri(any(), expected) }
            verify(exactly = 0) { ctrl.stopPlayingSound() }
        }
    }

    @Test
    fun `leaving the screen while this match is playing stops it`() {
        val match = seedSound(EXISTING_NAME, "existing.mp3")
        val expected = expectedUri(match)

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            playbackStateFlow.value = PlaybackState(uri = expected, positionMs = 0, durationMs = 1_000, isPlaying = true)
            composeTestRule.waitForIdle()
        }
        // The Activity is destroyed on `use` exit → DisposableEffect.onDispose runs and, because
        // the controller's playbackState still owns our match's uri, fires `stopPlayingSound`.
        verify { PlayerControllerFactory.instance.stopPlayingSound() }
    }

    @Test
    fun `disposal does not stop an unrelated playback that owns the controller`() {
        seedSound(EXISTING_NAME, "existing.mp3")
        val unrelated = Uri.parse("content://test/other-audio.mp3")
        playbackStateFlow.value = PlaybackState(uri = unrelated, positionMs = 0, durationMs = 1_000, isPlaying = true)
        val ctrl = PlayerControllerFactory.instance

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
        }
        // Hint disposal sees `playbackState.uri != ours` and must not preempt the unrelated playback.
        verify(exactly = 0) { ctrl.stopPlayingSound() }
    }

    private fun seedSound(
        name: String,
        file: String,
    ): Sound {
        val sound = testSound(name = name, file = file)
        runBlocking { SoundsRepository(context).save(sound) }
        return sound
    }

    private fun expectedUri(sound: Sound): Uri = getFile(context, sound.file!!).toUri()

    private fun createIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, SAMPLE_URI)
        }

    private fun editIntent(sound: Sound): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            putExtra(LandingActivity.EXTRA_EDIT_SOUND, sound)
        }

    private class FakeAddButtonFeature : AddButtonFeature {
        var saveNewFeedback: Int = R.string.app_addbutton_feedback_saved_ok
        var saveNewCalls: Int = 0

        override fun saveNewButtonAsync(
            context: Context,
            name: String,
            uri: String,
            publicCollectionIds: Set<String>,
            privateCollectionIds: Set<String>,
        ): Deferred<Int> {
            saveNewCalls += 1
            return CompletableDeferred(saveNewFeedback)
        }

        override fun renameButtonAsync(
            context: Context,
            sound: Sound,
            newName: String,
        ): Deferred<Unit> = CompletableDeferred(Unit)
    }

    private companion object {
        val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
        const val EXISTING_NAME = "Existing Bomp"
        const val OTHER_NAME = "Other Bomp"
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
