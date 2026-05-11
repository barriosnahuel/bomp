/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test

internal class PlayerControllerTest : AbstractRobolectricTest() {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `on stopPlayingSound when media player is playing should stop it`() {
        val mockedMediaPlayer = givenAMediaPlayerCurrentlyPlayingASound()

        whenCallingPlayerControllerStop(mockedMediaPlayer)

        thenItShouldStopMediaPlayer(mockedMediaPlayer)
    }

    @Test
    fun `on startPlayingSound when nothing is playing should reset and start`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        controllerForTest(mp).startPlayingSound(context, sound)

        verifyOrder {
            mp.reset()
            mp.prepare()
            mp.start()
        }
    }

    @Test
    fun `on startPlayingSound when a sound is already playing should stop it first`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returns true

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        controllerForTest(mp).startPlayingSound(context, sound)

        verifyOrder {
            mp.stop()
            mp.reset()
            mp.start()
        }
    }

    @Test
    fun `on startPlayingSound when source setup fails should not start media player`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns false

        controllerForTest(mp).startPlayingSound(context, sound)

        verify(exactly = 0) { mp.start() }
    }

    @Test
    fun `on startPlayingSound when source setup fails should call onPlayerError`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns false

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)

        verify { listener.onPlayerError(sound) }
        verify(exactly = 0) { listener.onPlayerStart(any(), any()) }
    }

    @Test
    fun `on startPlayingSound when prepare throws should call onPlayerError and not start`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        every { mp.prepare() } throws java.io.IOException("fail")

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)

        verify { listener.onPlayerError(sound) }
        verify(exactly = 0) { mp.start() }
    }

    @Test
    fun `on startPlayingSound when start succeeds emits onPlayerStart only after start`() {
        // Locks in the no-flicker invariant from the inverse case: the listener must NOT be told
        // "playing" until start() has actually returned, so a future revert that fires onPlayerStart
        // before start() (the historical order) doesn't sneak past the failure-path test.
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)

        verifyOrder {
            mp.start()
            listener.onPlayerStart(sound, any())
        }
    }

    @Test
    fun `on startPlayingSound when start throws IllegalStateException should call onPlayerError and track non-fatal`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        every { mp.start() } throws IllegalStateException("MediaPlayer in invalid state")

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)

        verify { listener.onPlayerError(sound) }
        verify(exactly = 1) { Tracker.track(any()) }
        // Guards the "no flicker" invariant: when start() fails, the UI must never see "playing".
        verify(exactly = 0) { listener.onPlayerStart(any(), any()) }
    }

    @Test
    fun `on natural completion the listener receives onPlayerStop with completed = true`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val completionSlot = io.mockk.slot<MediaPlayer.OnCompletionListener>()
        every { mp.setOnCompletionListener(capture(completionSlot)) } answers { nothing }

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        completionSlot.captured.onCompletion(mp)

        verify { listener.onPlayerStop(sound, completed = true) }
    }

    @Test
    fun `on stopPlayingSound the listener receives onPlayerStop with completed = false`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = mockk<MediaPlayer>(relaxed = true)
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        every { mp.isPlaying } returnsMany listOf(false, true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        controller.stopPlayingSound()

        verify { listener.onPlayerStop(sound, completed = false) }
    }

    @Test
    fun `startPlayingUri emits a Stream PlaybackState with the given uri`() {
        val context = mockk<Context>(relaxed = true)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000
        val uri = Uri.parse("content://test/clip.mp3")

        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, uri)

        val state = controller.playbackState.value
        assertThat(state).isNotNull()
        assertThat(state!!.uri).isEqualTo(uri)
        assertThat(state.durationMs).isEqualTo(5_000)
        assertThat(state.isPlaying).isTrue()
    }

    @Test
    fun `startPlayingUri while a Sound is playing pre-empts it via onPlayerStop`() {
        // Concurrency invariant from ADR 0005: starting a Stream stops any currently-playing Sound.
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returnsMany listOf(false, true, false)
        every { mp.duration } returns 5_000
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        controller.startPlayingUri(context, Uri.parse("content://test/clip.mp3"))

        verify { listener.onPlayerStop(sound, completed = false) }
    }

    @Test
    fun `pause flips isPlaying to false in the current PlaybackState`() {
        val context = mockk<Context>(relaxed = true)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000
        val uri = Uri.parse("content://test/clip.mp3")

        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, uri)
        every { mp.isPlaying } returns true
        controller.pause()

        verify { mp.pause() }
        assertThat(controller.playbackState.value?.isPlaying).isFalse()
        assertThat(controller.playbackState.value?.uri).isEqualTo(uri)
    }

    @Test
    fun `resume flips isPlaying back to true after a pause`() {
        val context = mockk<Context>(relaxed = true)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000

        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, Uri.parse("content://test/clip.mp3"))
        every { mp.isPlaying } returns true
        controller.pause()
        controller.resume()

        // start() is called twice: once on startPlayingUri, once on resume.
        verify(exactly = 2) { mp.start() }
        assertThat(controller.playbackState.value?.isPlaying).isTrue()
    }

    @Test
    fun `stopPlayingSound clears a paused Stream PlaybackState even when the player is not currently playing`() {
        // Regression guard for the AudioPreview onDispose path: when the user pauses then closes the
        // screen, the player is in PAUSED state (isPlaying == false) but the StateFlow still holds
        // the preview. stopPlayingSound must clear it so a future preview doesn't see stale state.
        val context = mockk<Context>(relaxed = true)
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returnsMany listOf(false, true, false, false)
        every { mp.duration } returns 5_000

        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, Uri.parse("content://test/clip.mp3"))
        controller.pause()
        assertThat(controller.playbackState.value).isNotNull()

        controller.stopPlayingSound()

        assertThat(controller.playbackState.value).isNull()
    }

    @Test
    fun `seekTo delegates to mediaPlayer seekTo`() {
        val mp = givenAnIdleMediaPlayer()

        controllerForTest(mp).seekTo(1500)

        verify { mp.seekTo(1500) }
    }

    private fun givenAMediaPlayerCurrentlyPlayingASound(): MediaPlayer {
        val mockedMediaPlayer = mockk<MediaPlayer>()
        every { mockedMediaPlayer.isPlaying } returns true
        every { mockedMediaPlayer.stop() } answers { nothing }
        return mockedMediaPlayer
    }

    private fun givenAnIdleMediaPlayer(): MediaPlayer =
        mockk<MediaPlayer>(relaxed = true).also {
            every { it.isPlaying } returns false
        }

    /**
     * Constructs the controller with eager dispatchers so coroutine bodies in start*() complete
     * before the test method moves to its `verify {}` block. UnconfinedTestDispatcher resumes the
     * continuation on the calling thread, so withContext(ioDispatcher) does not actually schedule.
     */
    private fun controllerForTest(mp: MediaPlayer): PlayerControllerImpl {
        val dispatcher = UnconfinedTestDispatcher()
        return PlayerControllerImpl(
            mediaPlayer = mp,
            ioDispatcher = dispatcher,
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )
    }

    private fun whenCallingPlayerControllerStop(mockedMediaPlayer: MediaPlayer) {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance } returns controllerForTest(mockedMediaPlayer)
        PlayerControllerFactory.instance.stopPlayingSound()
    }

    private fun thenItShouldStopMediaPlayer(mockedMediaPlayer: MediaPlayer) {
        verifySequence {
            mockedMediaPlayer.isPlaying
            mockedMediaPlayer.stop()
        }
    }
}
