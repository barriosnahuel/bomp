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
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class PlayerControllerTest : AbstractRobolectricTest() {
    @Before
    fun setUpAnalytics() {
        // This suite drives the real controller, whose engine reports transport use through the
        // analytics provider; without a fake it would build the real tracker and TestApplication has
        // no Firebase, leaving an escaping exception for an unrelated test to trip over.
        AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
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
    fun `on startPlayingSound when a different sound is playing should reset without an explicit stop`() {
        // Switching data source is the one place reset() is unavoidable (MediaPlayer needs to be
        // taken back to IDLE before a new setDataSource). We do NOT call stop() first — reset()
        // subsumes it and an extra transition through STOPPED is wasted work. The previous sound's
        // position is captured before reset() via mp.currentPosition (validated in the
        // "switching A then B then A seeks back to A's saved position" test below).
        val context = mockk<Context>(relaxed = true)
        val previous = Sound("previous", rawRes = 1)
        val next = Sound("next", rawRes = 2)
        val mp = givenAnIdleMediaPlayer()
        every { mp.currentPosition } returns 0
        every { mp.duration } returns 10_000

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.startPlayingSound(context, previous)
        every { mp.isPlaying } returns true
        controller.startPlayingSound(context, next)

        verify(exactly = 0) { mp.stop() }
        verifyOrder {
            mp.reset() // for previous
            mp.start() // previous started
            mp.reset() // for next (after preemption)
            mp.start() // next started
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
        verify(exactly = 0) { listener.onPlayerStart(any(), any(), any()) }
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
            listener.onPlayerStart(sound, any(), any())
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
        verify(exactly = 0) { listener.onPlayerStart(any(), any(), any()) }
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
    fun `pause on a playing Sound saves position and emits onPlayerPause without reset`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 10_000
        every { mp.currentPosition } returns 3_500
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        every { mp.isPlaying } returns true
        controller.pause()

        verify { mp.pause() }
        // Only the initial reset() to load the source — no second reset on pause.
        verify(exactly = 1) { mp.reset() }
        // A pause is NOT a stop: the listener gets onPlayerPause (position retained) so the UI can
        // keep the progress bar where it was, and onPlayerStop is never fired.
        verify { listener.onPlayerPause(sound, positionMs = 3_500, durationMs = 10_000) }
        verify(exactly = 0) { listener.onPlayerStop(sound, any()) }
    }

    @Test
    fun `tap a paused Sound resumes via start without reset and without re-prepare`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 10_000
        every { mp.currentPosition } returns 3_500
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        every { mp.isPlaying } returns true
        controller.pause()
        every { mp.isPlaying } returns false
        controller.startPlayingSound(context, sound)

        // Exactly one reset() — for the initial load. The resume re-tap goes through mp.start()
        // directly without resetting or re-preparing the data source.
        verify(exactly = 1) { mp.reset() }
        verify(exactly = 1) { mp.prepare() }
        // start() runs twice: initial play, then resume.
        verify(exactly = 2) { mp.start() }
        // onPlayerStart fires on resume too, carrying the saved position so the VM can initialise
        // _playbackProgress at 3500 ms instead of 0 (no slider flicker on resume).
        verify { listener.onPlayerStart(sound, durationMs = 10_000, positionMs = 3_500) }
    }

    @Test
    fun `switching from A to B and back to A seeks A to its saved position`() {
        val context = mockk<Context>(relaxed = true)
        val a = Sound("a", rawRes = 1)
        val b = Sound("b", rawRes = 2)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 10_000
        every { mp.currentPosition } returns 4_200 // A's position when it gets preempted by B

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.startPlayingSound(context, a)
        every { mp.isPlaying } returns true
        controller.startPlayingSound(context, b) // preempts A, saves currentPosition (4_200) under "a"
        controller.startPlayingSound(context, a) // preempts B, switches back to A

        // When A is reloaded, seekTo(4_200) is called between prepare() and start() so playback
        // resumes from where it paused, not from 0.
        verify { mp.seekTo(4_200) }
    }

    @Test
    fun `natural completion clears the saved position so the next play starts from zero`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        every { mp.currentPosition } returns 2_000
        every { mp.duration } returns 10_000
        val completionSlot = io.mockk.slot<MediaPlayer.OnCompletionListener>()
        every { mp.setOnCompletionListener(capture(completionSlot)) } answers { nothing }

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.startPlayingSound(context, sound)
        every { mp.isPlaying } returns true
        controller.pause() // saves position at 2_000
        every { mp.isPlaying } returns false
        completionSlot.captured.onCompletion(mp) // natural-end fires — should clear the saved position
        controller.startPlayingSound(context, sound) // fresh load: must re-prepare and start from 0

        // No seekTo because the saved position was cleared by completion.
        verify(exactly = 0) { mp.seekTo(any()) }
        // Two prepares confirm the second tap went through the full load path (no in-place resume).
        verify(exactly = 2) { mp.prepare() }
    }

    @Test
    fun `stopPlayingSound clears the saved position for the current sound target`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        every { mp.currentPosition } returns 1_500
        every { mp.duration } returns 10_000

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.startPlayingSound(context, sound)
        every { mp.isPlaying } returns true
        controller.pause()
        every { mp.isPlaying } returns false
        controller.stopPlayingSound() // explicit stop — should clear the saved position
        controller.startPlayingSound(context, sound)

        verify(exactly = 0) { mp.seekTo(any()) }
    }

    @Test
    fun `forgetSound on the current sound target stops resets and clears state`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 10_000
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        every { mp.isPlaying } returns true
        controller.forgetSound(sound)

        verify { mp.stop() }
        // Two resets: initial load + forget-path reset to return MediaPlayer to IDLE.
        verify(exactly = 2) { mp.reset() }
        verify { listener.onPlayerStop(sound, completed = false) }
    }

    @Test
    fun `forgetSound for a non-current sound clears its saved position without disturbing playback`() {
        // User plays A, switches to B (A's position is saved). Then the app deletes A — forgetSound(A)
        // must drop A's saved position so a future "A again" does NOT seekTo the stale value, and
        // it must NOT touch the MediaPlayer (B is currently loaded).
        val context = mockk<Context>(relaxed = true)
        val a = Sound("a", rawRes = 1)
        val b = Sound("b", rawRes = 2)
        val mp = givenAnIdleMediaPlayer()
        every { mp.currentPosition } returns 2_000
        every { mp.duration } returns 10_000

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.startPlayingSound(context, a)
        every { mp.isPlaying } returns true
        controller.startPlayingSound(context, b) // preempts A, saves A's position
        every { mp.isPlaying } returns true
        controller.forgetSound(a) // A is not the current target — should be a no-op on mp
        controller.startPlayingSound(context, a) // would seekTo if savedSoundPositions still had A

        // Three resets total: initial-A + A→B switch + B→A switch. No extra reset from forgetSound.
        verify(exactly = 3) { mp.reset() }
        // No seekTo because forgetSound(A) cleared the saved position before the third tap.
        verify(exactly = 0) { mp.seekTo(any()) }
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
    fun `startPlayingUri with a start position seeks there before starting and publishes it`() {
        // The recorder review resumes a scrubbed offset: a fresh start must seek before start() and
        // report the offset as the initial position (not 0) so the wave doesn't jump to the beginning.
        val context = mockk<Context>(relaxed = true)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000
        val uri = Uri.parse("content://test/clip.mp3")

        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, uri, startPositionMs = 2_000)

        verifyOrder {
            mp.seekTo(2_000)
            mp.start()
        }
        assertThat(controller.playbackState.value?.positionMs).isEqualTo(2_000)
    }

    @Test
    fun `startPlayingUri while a Sound is playing preempts the sound via onPlayerPause`() {
        // Concurrency invariant: starting a Stream while a Sound plays must surface the event to
        // the Sound listener so the Home UI updates. Because the Sound's position is captured for
        // a later resume, the event is onPlayerPause (not onPlayerStop) — the UI keeps the Sound's
        // progress bar at its position rather than collapsing it.
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000
        every { mp.currentPosition } returns 1_200
        val listener = mockk<PlayerControllerListener>(relaxed = true)

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        val controller = controllerForTest(mp)
        controller.setOnStartStopListener(listener)
        controller.startPlayingSound(context, sound)
        every { mp.isPlaying } returns true
        controller.startPlayingUri(context, Uri.parse("content://test/clip.mp3"))

        verify { listener.onPlayerPause(sound, positionMs = 1_200, durationMs = 5_000) }
        verify(exactly = 0) { listener.onPlayerStop(sound, any()) }
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
        every { mp.isPlaying } returns false
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
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000

        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, Uri.parse("content://test/clip.mp3"))
        every { mp.isPlaying } returns true
        controller.pause()
        every { mp.isPlaying } returns false
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

    @Test
    fun `seekTo publishes the new position so a paused scrub reflects in the UI`() {
        // The progress runnable only advances positionMs while playing, so without publishing here a
        // seek on a paused preview would leave the StateFlow (and the waveform) at the old position.
        val context = mockk<Context>(relaxed = true)
        val mp = givenAnIdleMediaPlayer()
        every { mp.duration } returns 5_000
        val controller = controllerForTest(mp)
        controller.startPlayingUri(context, Uri.parse("content://test/clip.mp3"))
        every { mp.isPlaying } returns true
        controller.pause()
        every { mp.isPlaying } returns false

        controller.seekTo(3_200)

        verify { mp.seekTo(3_200) }
        assertThat(controller.playbackState.value?.positionMs).isEqualTo(3_200)
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
    @OptIn(ExperimentalCoroutinesApi::class)
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
