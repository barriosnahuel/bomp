/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Routing contract of the long-form listen-session engine (ADR 0022): sessions run on the injected
 * Media3 [Player], list/preview playback stays on [MediaPlayer], and the ADR 0005 one-active-playback
 * invariant holds across both engines. The real ExoPlayer path is covered by the instrumented suite;
 * these tests pin the controller's routing with a scripted fake.
 */
internal class PlayerControllerListenSessionTest : AbstractRobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val playerListener = slot<Player.Listener>()

    @Before
    fun setUpTracker() {
        // This suite drives the listener branches that now report transport use; without a fake the
        // provider would build the real tracker, and TestApplication has no Firebase.
        AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `startListenSession loads prepares and plays on the session player without seeking`() {
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(player = player)

        controller.startListenSession(context, customSound())

        verifyOrder {
            player.setMediaItem(any())
            player.prepare()
            player.play()
        }
        verify(exactly = 0) { player.seekTo(any()) }
    }

    @Test
    fun `session ready emits onPlayerStart with the player duration`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()

        controller.startListenSession(context, sound)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        verify { listener.onPlayerStart(sound, durationMs = 10_000, positionMs = 0) }
    }

    @Test
    fun `a re-buffer after the first ready does not re-emit onPlayerStart`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)

        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_BUFFERING)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        verify(exactly = 1) { listener.onPlayerStart(any(), any(), any()) }
    }

    @Test
    fun `startListenSession preempts a playing row sound pausing it with position`() {
        val mp = givenAMediaPlayerPlaying(positionMs = 1_234)
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(mp = mp, player = player)
        controller.setOnStartStopListener(listener)
        val rowSound = bundledSound("row")
        controller.startPlayingSound(context, rowSound)

        controller.startListenSession(context, customSound())

        verify { listener.onPlayerPause(match { it.id == rowSound.id }, positionMs = 1_234, durationMs = 10_000) }
        verify { mp.reset() }
    }

    @Test
    fun `starting a row sound stops an active session definitively`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(mp = mp, player = player)
        controller.setOnStartStopListener(listener)
        val sessionSound = customSound()
        controller.startListenSession(context, sessionSound)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        controller.startPlayingSound(context, bundledSound("row"))

        verify { player.stop() }
        verify { listener.onPlayerStop(match { it.id == sessionSound.id }, completed = false) }
    }

    @Test
    fun `pause routes to the session engine and reports a positional pause`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(mp = mp, player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)
        every { player.isPlaying } returns true
        every { player.currentPosition } returns 2_500L

        controller.pause()

        verify { player.pause() }
        verify { listener.onPlayerPause(match { it.id == sound.id }, positionMs = 2_500, durationMs = 10_000) }
        verify(exactly = 0) { mp.pause() }
    }

    @Test
    fun `startPlayingSound of the paused session target resumes the session in place`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(mp = mp, player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)
        every { player.isPlaying } returns false
        every { player.currentPosition } returns 2_500L

        controller.startPlayingSound(context, sound.copy(isPlaying = false))

        verify { player.play() }
        verify { listener.onPlayerStart(match { it.id == sound.id }, durationMs = 10_000, positionMs = 2_500) }
        verify(exactly = 0) { mp.prepare() }
    }

    @Test
    fun `session natural completion emits onPlayerStop completed true`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        playerListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        verify { listener.onPlayerStop(match { it.id == sound.id }, completed = true) }
    }

    @Test
    fun `seekTo routes to the session player while a session is active`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(mp = mp, player = player)
        controller.startListenSession(context, customSound())

        controller.seekTo(7_000)

        verify { player.seekTo(7_000L) }
        verify(exactly = 0) { mp.seekTo(any()) }
    }

    @Test
    fun `stopPlayingSound stops the session and emits a definitive stop`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)

        controller.stopPlayingSound()

        verify { player.stop() }
        verify { listener.onPlayerStop(match { it.id == sound.id }, completed = false) }
    }

    @Test
    fun `forgetSound of the session sound stops the session`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)

        controller.forgetSound(sound)

        verify { player.stop() }
        verify { listener.onPlayerStop(match { it.id == sound.id }, completed = false) }
    }

    @Test
    fun `uri session honors startPositionMs and feeds playbackState on ready`() {
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(player = player)
        val uri = Uri.parse("file:///tmp/review.m4a")

        controller.startUriListenSession(context, uri, startPositionMs = 500)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        verify { player.seekTo(500L) }
        val state = controller.playbackState.value
        assertThat(state).isNotNull()
        assertThat(state!!.uri).isEqualTo(uri)
        assertThat(state.durationMs).isEqualTo(10_000)
        assertThat(state.isPlaying).isTrue()
    }

    @Test
    fun `starting a preview uri on the media player stops an active session`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(mp = mp, player = player)
        controller.setOnStartStopListener(listener)
        val sessionSound = customSound()
        controller.startListenSession(context, sessionSound)

        controller.startPlayingUri(context, Uri.parse("file:///tmp/preview.mp3"))

        verify { player.stop() }
        verify { listener.onPlayerStop(match { it.id == sessionSound.id }, completed = false) }
    }

    @Test
    fun `session error reports through the listener and clears the session`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)

        playerListener.captured.onPlayerError(
            PlaybackException("boom", null, PlaybackException.ERROR_CODE_UNSPECIFIED),
        )

        verify { listener.onPlayerError(match { it.id == sound.id }) }
        verify { Tracker.track(any()) }
        // A follow-up seek must fall through to the MediaPlayer path — the session is gone.
        controller.seekTo(100)
        verify(exactly = 0) { player.seekTo(100L) }
    }

    @Test
    fun `replay after natural completion starts a fresh session not a MediaPlayer fallback`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(mp = mp, player = player)
        val sound = customSound()
        controller.startListenSession(context, sound)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        controller.startListenSession(context, sound)

        verify(exactly = 2) { player.prepare() }
        verify(exactly = 0) { player.seekTo(any()) }
        verify(exactly = 0) { mp.prepare() }
    }

    @Test
    fun `resume routes to the paused session`() {
        val mp = givenAnIdleMediaPlayer()
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(mp = mp, player = player)
        val sound = customSound()
        controller.startListenSession(context, sound)
        every { player.isPlaying } returns false

        controller.resume()

        verify { player.play() }
        verify(exactly = 0) { mp.start() }
    }

    @Test
    fun `starting a second sound session preempts the first with a definitive stop`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val first = customSound()
        controller.startListenSession(context, first)

        controller.startListenSession(context, Sound("other-id", "other audio", "other.mp3"))

        verify { listener.onPlayerStop(match { it.id == first.id }, completed = false) }
    }

    @Test
    fun `forgetSound of a non-session sound does not stop the active session`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())

        controller.forgetSound(bundledSound("unrelated"))

        verify(exactly = 0) { player.stop() }
        verify(exactly = 0) { listener.onPlayerStop(any(), any()) }
    }

    @Test
    fun `pause before ready cancels the pending start so ready emits no start event`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())

        controller.pause()
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        verify { player.pause() }
        verify(exactly = 0) { listener.onPlayerStart(any(), any(), any()) }
    }

    private fun customSound(): Sound = Sound("custom-id", "custom audio", "custom.mp3")

    private fun bundledSound(name: String): Sound = Sound(name, rawRes = 1)

    private fun givenAnIdleSessionPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.addListener(capture(playerListener)) } just Runs
        every { player.isPlaying } returns false
        every { player.currentPosition } returns 0L
        every { player.duration } returns 10_000L
        return player
    }

    private fun givenAnIdleMediaPlayer(): MediaPlayer {
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returns false
        return mp
    }

    private fun givenAMediaPlayerPlaying(positionMs: Int): MediaPlayer {
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returns true
        every { mp.currentPosition } returns positionMs
        every { mp.duration } returns 10_000
        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true
        return mp
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun controllerForTest(
        mp: MediaPlayer = givenAnIdleMediaPlayer(),
        player: Player,
    ): PlayerControllerImpl {
        val dispatcher = UnconfinedTestDispatcher()
        return PlayerControllerImpl(
            mediaPlayer = mp,
            ioDispatcher = dispatcher,
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            sessionPlayerProvider = { player },
            sessionBridge = FakeMediaSessionBridge(),
        )
    }
}
