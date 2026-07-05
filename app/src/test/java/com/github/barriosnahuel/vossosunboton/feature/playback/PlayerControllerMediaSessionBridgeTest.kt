/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutionException

/**
 * System-surface contract of the listen-session engine (ADR 0022 + spec 002d): sessions are
 * published/retracted through [MediaSessionBridge] exactly while one is active, media items carry
 * the title the system notification renders, and player-initiated transitions (media notification,
 * media keys, audio-focus loss command the [Player] directly) reconcile back into the same
 * consumer events without double emission. The real MediaSession/service path is covered by the
 * instrumented suite.
 */
internal class PlayerControllerMediaSessionBridgeTest : AbstractRobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val playerListener = slot<Player.Listener>()
    private val mediaItem = slot<MediaItem>()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `starting a sound session publishes it to the system with the audio name as title`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)

        controller.startListenSession(context, customSound())

        assertThat(bridge.playerCreatedCount).isEqualTo(1)
        assertThat(bridge.startedCount).isEqualTo(1)
        assertThat(
            mediaItem.captured.mediaMetadata.title
                .toString(),
        ).isEqualTo("custom audio")
    }

    @Test
    fun `starting a uri session titles it with the recording session string`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)

        controller.startUriListenSession(context, Uri.parse("file:///tmp/review.m4a"))

        assertThat(
            mediaItem.captured.mediaMetadata.title
                .toString(),
        ).isEqualTo(context.getString(R.string.app_playback_recording_session_title))
    }

    @Test
    fun `a second session reuses the session player instead of recreating it`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)

        controller.startListenSession(context, customSound())
        controller.startListenSession(context, Sound("other-id", "other audio", "other.mp3"))

        assertThat(bridge.playerCreatedCount).isEqualTo(1)
        assertThat(bridge.startedCount).isEqualTo(2)
    }

    @Test
    fun `stopping a session retracts it from the system and clears the loaded item`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)
        controller.startListenSession(context, customSound())

        controller.stopPlayingSound()

        assertThat(bridge.endedCount).isEqualTo(1)
        verify { player.clearMediaItems() }
    }

    @Test
    fun `natural completion retracts the session from the system`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)
        controller.startListenSession(context, customSound())

        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertThat(bridge.endedCount).isEqualTo(1)
    }

    @Test
    fun `a session error retracts the session from the system`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)
        controller.startListenSession(context, customSound())

        playerListener.captured.onPlayerError(
            PlaybackException("boom", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
        )

        assertThat(bridge.endedCount).isEqualTo(1)
    }

    @Test
    fun `an external pause emits onPlayerPause exactly once`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        // A media-notification / media-key pause acts on the Player directly: the engine only
        // learns about it through the callback.
        every { player.playWhenReady } returns false
        every { player.currentPosition } returns 4_000L
        playerListener.captured.onIsPlayingChanged(false)

        verify(exactly = 1) { listener.onPlayerPause(sound, positionMs = 4_000, durationMs = 10_000) }
    }

    @Test
    fun `a controller-initiated pause is not re-emitted when the player callback lands`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        controller.pause()
        every { player.playWhenReady } returns false
        playerListener.captured.onIsPlayingChanged(false)

        verify(exactly = 1) { listener.onPlayerPause(any(), any(), any()) }
    }

    @Test
    fun `an external resume emits onPlayerStart again`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        every { player.playWhenReady } returns false
        playerListener.captured.onIsPlayingChanged(false)

        playerListener.captured.onIsPlayingChanged(true)

        verify(exactly = 2) { listener.onPlayerStart(sound, durationMs = 10_000, positionMs = any()) }
    }

    @Test
    fun `a transient re-buffer does not masquerade as a pause`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        every { player.playWhenReady } returns true
        every { player.playbackState } returns Player.STATE_BUFFERING
        playerListener.captured.onIsPlayingChanged(false)

        verify(exactly = 0) { listener.onPlayerPause(any(), any(), any()) }
    }

    @Test
    fun `isPlaying callbacks before the first ready leave the start event to ready`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        val sound = customSound()
        controller.startListenSession(context, sound)

        playerListener.captured.onIsPlayingChanged(true)
        verify(exactly = 0) { listener.onPlayerStart(any(), any(), any()) }

        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        verify(exactly = 1) { listener.onPlayerStart(sound, durationMs = 10_000, positionMs = 0) }
    }

    @Test
    fun `an external pause of a uri session flips playbackState to paused`() {
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(player = player)
        controller.startUriListenSession(context, Uri.parse("file:///tmp/review.m4a"))
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        assertThat(controller.playbackState.value?.isPlaying).isTrue()

        every { player.playWhenReady } returns false
        every { player.currentPosition } returns 2_500L
        playerListener.captured.onIsPlayingChanged(false)

        assertThat(controller.playbackState.value?.isPlaying).isFalse()
        assertThat(controller.playbackState.value?.positionMs).isEqualTo(2_500)
    }

    @Test
    fun `an in-place resume re-publishes the session to the system`() {
        val player = givenAnIdleSessionPlayer()
        val bridge = FakeMediaSessionBridge()
        val controller = controllerForTest(player = player, bridge = bridge)
        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        controller.pause()

        controller.resume()

        // The paused target can outlive the service (task swiped away): resume must restart it.
        assertThat(bridge.startedCount).isEqualTo(2)
    }

    @Test
    fun `reaching the natural end is not reconciled as an external pause`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        // At natural end isPlaying flips false before/after STATE_ENDED; the stop belongs to
        // onEnded, never to the pause reconciliation.
        every { player.playbackState } returns Player.STATE_ENDED
        every { player.playWhenReady } returns false
        playerListener.captured.onIsPlayingChanged(false)

        verify(exactly = 0) { listener.onPlayerPause(any(), any(), any()) }
    }

    @Test
    fun `regaining playback after a transient suppression re-arms progress without re-emitting start`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        // Drain the ticker armed by READY: it self-terminates while the player reports not playing.
        shadowOf(Looper.getMainLooper()).idle()

        // Transient audio-focus loss: isPlaying drops with playWhenReady still true (suppression)…
        every { player.playWhenReady } returns true
        playerListener.captured.onIsPlayingChanged(false)
        // …then focus returns and playback resumes on its own.
        playerListener.captured.onIsPlayingChanged(true)

        verify(exactly = 1) { listener.onPlayerStart(any(), any(), any()) }
        verify(exactly = 0) { listener.onPlayerPause(any(), any(), any()) }
        // The ticker must be alive again after the regain.
        every { player.isPlaying } returns true
        every { player.currentPosition } returns 1_234L
        shadowOf(Looper.getMainLooper()).idle()
        verify { listener.onProgressUpdate(1_234) }
    }

    @Test
    fun `an external seek publishes the new position of a sound session`() {
        val player = givenAnIdleSessionPlayer()
        val listener = mockk<PlayerControllerListener>(relaxed = true)
        val controller = controllerForTest(player = player)
        controller.setOnStartStopListener(listener)
        controller.startListenSession(context, customSound())
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        playerListener.captured.onPositionDiscontinuity(
            positionInfoAt(0L),
            positionInfoAt(7_000L),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        verify { listener.onProgressUpdate(7_000) }
    }

    @Test
    fun `an external seek publishes the new position of a uri session`() {
        val player = givenAnIdleSessionPlayer()
        val controller = controllerForTest(player = player)
        controller.startUriListenSession(context, Uri.parse("file:///tmp/review.m4a"))
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)

        playerListener.captured.onPositionDiscontinuity(
            positionInfoAt(0L),
            positionInfoAt(7_000L),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        assertThat(controller.playbackState.value?.positionMs).isEqualTo(7_000)
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-926 (exported media session rejects controller-injected media items). */
    @Test
    fun `the media session callback rejects controller-supplied media items`() {
        val future =
            CuratedContentSessionCallback.onAddMediaItems(
                mockk(),
                mockk(),
                mutableListOf(MediaItem.fromUri("https://attacker.example/audio.mp3")),
            )

        val failure = assertThrows(ExecutionException::class.java) { future.get() }
        assertThat(failure).hasCauseThat().isInstanceOf(UnsupportedOperationException::class.java)
    }

    private fun customSound(): Sound = Sound("custom-id", "custom audio", "custom.mp3")

    private fun positionInfoAt(positionMs: Long): Player.PositionInfo =
        Player.PositionInfo(null, 0, null, null, 0, positionMs, positionMs, -1, -1)

    private fun givenAnIdleSessionPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.addListener(capture(playerListener)) } just Runs
        every { player.setMediaItem(capture(mediaItem)) } just Runs
        every { player.isPlaying } returns false
        every { player.currentPosition } returns 0L
        every { player.duration } returns 10_000L
        return player
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun controllerForTest(
        player: Player,
        bridge: FakeMediaSessionBridge = FakeMediaSessionBridge(),
    ): PlayerControllerImpl {
        val dispatcher = UnconfinedTestDispatcher()
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returns false
        return PlayerControllerImpl(
            mediaPlayer = mp,
            ioDispatcher = dispatcher,
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            sessionPlayerProvider = { player },
            sessionBridge = bridge,
        )
    }
}
