/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The trim editor's own state machine: opening it from the collapsed call to action, previewing only
 * the kept range, and backing out to the whole audio.
 */
internal class TrimSectionTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private lateinit var fakeController: PlayerController
    private lateinit var originalController: PlayerController

    private var expanded by mutableStateOf(false)
    private var selection by mutableStateOf(TrimSelection.WHOLE)
    private val mounted = mutableStateOf(true)

    @Before
    fun setUp() {
        originalController = PlayerControllerFactory.instance
        fakeController = mockk(relaxed = true) { every { playbackState } returns playbackStateFlow }
        PlayerControllerFactory.instance = fakeController
    }

    @After
    fun tearDown() {
        PlayerControllerFactory.instance = originalController
    }

    @Test
    fun `the collapsed editor offers to trim and opens in place when tapped`() {
        setSection()

        composeTestRule.onNodeWithText(string(R.string.app_addbutton_trim_cta)).performClick()
        composeTestRule.waitForIdle()

        assertThat(expanded).isTrue()
        composeTestRule.onNodeWithText(string(R.string.app_addbutton_trim_title)).assertIsDisplayed()
    }

    @Test
    fun `an open editor states where the cut starts and how much it keeps`() {
        expanded = true
        selection = TrimSelection.WHOLE.withStart(0.25f, CLIP_MS).withEnd(0.75f, CLIP_MS)
        setSection()

        // 0:15 in, keeping 0:30 of the original minute.
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_trim_range, "0:15", "0:30"))
            .assertIsDisplayed()
    }

    @Test
    fun `previewing plays from the start of the kept range, not from the top of the audio`() {
        expanded = true
        selection = TrimSelection.WHOLE.withStart(0.5f, CLIP_MS)
        setSection()

        composeTestRule.onNodeWithContentDescription(string(R.string.app_addbutton_trim_preview_description)).performClick()
        composeTestRule.waitForIdle()

        verify { fakeController.startPlayingUri(any(), SOURCE, 30_000) }
    }

    @Test
    fun `the preview stops at the end of the kept range instead of running on to the end of the audio`() {
        expanded = true
        selection = TrimSelection.WHOLE.withEnd(0.5f, CLIP_MS)
        setSection()

        // The editor owns this playback — its range stop only applies to a preview it started.
        composeTestRule.onNodeWithContentDescription(string(R.string.app_addbutton_trim_preview_description)).performClick()

        // Mid-range: nothing to stop yet.
        playbackStateFlow.value = PlaybackState(uri = SOURCE, positionMs = 20_000, durationMs = CLIP_MS, isPlaying = true)
        composeTestRule.waitForIdle()
        verify(exactly = 0) { fakeController.pause() }

        // The 0:30 boundary goes by on the controller's own progress tick.
        playbackStateFlow.value = PlaybackState(uri = SOURCE, positionMs = 30_050, durationMs = CLIP_MS, isPlaying = true)
        composeTestRule.waitForIdle()

        verify { fakeController.pause() }
    }

    @Test
    fun `replaying after the range stop rewinds to the start instead of resuming at the tail`() {
        expanded = true
        selection = TrimSelection.WHOLE.withStart(0.25f, CLIP_MS).withEnd(0.5f, CLIP_MS)
        setSection()

        // Play, then let the range end go by so the controller is left loaded and paused at 0:30.
        composeTestRule.onNodeWithContentDescription(string(R.string.app_addbutton_trim_preview_description)).performClick()
        playbackStateFlow.value = PlaybackState(uri = SOURCE, positionMs = 30_050, durationMs = CLIP_MS, isPlaying = false)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.app_addbutton_trim_preview_description)).performClick()
        composeTestRule.waitForIdle()

        // A bare startPlayingUri would be short-circuited into a resume at 0:30 and stop on the spot.
        verify { fakeController.seekTo(15_000) }
        verify { fakeController.resume() }
    }

    @Test
    fun `the range stop leaves playback the editor did not start alone`() {
        expanded = true
        selection = TrimSelection.WHOLE.withEnd(0.5f, CLIP_MS)
        setSection()

        // The AudioPreview card above plays the same URI through the same controller. Nobody tapped the
        // editor's own play button, so its range end must not cut that playback short.
        playbackStateFlow.value = PlaybackState(uri = SOURCE, positionMs = 40_000, durationMs = CLIP_MS, isPlaying = true)
        composeTestRule.waitForIdle()

        verify(exactly = 0) { fakeController.pause() }
    }

    @Test
    fun `backing out to the whole audio closes the editor and forgets the range`() {
        expanded = true
        selection = TrimSelection.WHOLE.withStart(0.3f, CLIP_MS).withEnd(0.6f, CLIP_MS)
        setSection()

        composeTestRule.onNodeWithText(string(R.string.app_addbutton_trim_keep_whole)).performClick()
        composeTestRule.waitForIdle()

        assertThat(expanded).isFalse()
        assertThat(selection).isEqualTo(TrimSelection.WHOLE)
    }

    @Test
    fun `leaving the editor does not stop the audio - the card that owns the URI does that`() {
        expanded = true
        selection = TrimSelection.WHOLE.withEnd(0.5f, CLIP_MS)
        setSection()
        composeTestRule.onNodeWithContentDescription(string(R.string.app_addbutton_trim_preview_description)).performClick()
        playbackStateFlow.value = PlaybackState(uri = SOURCE, positionMs = 5_000, durationMs = CLIP_MS, isPlaying = true)
        composeTestRule.waitForIdle()

        mounted.value = false
        composeTestRule.waitForIdle()

        // AudioPreview's StopPreviewOnDispose covers this URI and can tell a real exit from an Activity
        // recreate. A second disposal here would double-stop on the way out and, lacking that guard,
        // would cut the audio on every rotation.
        verify(exactly = 0) { fakeController.stopPlayingSound() }
    }

    private fun setSection() {
        composeTestRule.setContent {
            AppTheme {
                if (!mounted.value) return@AppTheme
                TrimSection(
                    context = context,
                    source = SOURCE,
                    durationMs = CLIP_MS,
                    expanded = expanded,
                    selection = selection,
                    // Standing in for an envelope still decoding: the editor must be fully usable before
                    // the wave arrives, since the handles are driven by the duration, not by the peaks.
                    peaks = null,
                    onExpandedChange = { expanded = it },
                    onSelectionChange = { selection = it },
                )
            }
        }
    }

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        val SOURCE = "content://media/external/audio/media/7".toUri()
        const val CLIP_MS = 60_000
    }
}
