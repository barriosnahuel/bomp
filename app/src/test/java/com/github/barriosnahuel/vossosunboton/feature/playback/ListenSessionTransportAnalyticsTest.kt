/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Whether a listen was driven from the media notification / lock screen instead of the app's own
 * screen — the pocket-listening signal. The engine already knows the difference (`publishedPlaying`
 * mismatches mean an external command); these guard that it reports it, and that the OS pausing us
 * on its own is never counted as the Bomper doing something.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ListenSessionTransportAnalyticsTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fake = FakeAnalyticsTracker()
    private val playerListener = slot<Player.Listener>()

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(fake)
    }

    @After
    fun tearDown() {
        // Stop and cancel before unmockkAll: this controller is real, so a pending progress callback or
        // player event outliving the test would reach for the (now torn-down) Tracker stub and the
        // analytics provider, and the escaping exception surfaces in whatever test runs next.
        runCatching { controller.stopPlayingSound() }
        controllerScope?.cancel()
        controllerScope = null
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    private lateinit var controller: PlayerControllerImpl
    private var controllerScope: CoroutineScope? = null

    private fun givenAPlayingSession(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.addListener(capture(playerListener)) } just Runs
        every { player.isPlaying } returns false
        every { player.currentPosition } returns 0L
        every { player.duration } returns 10_000L
        val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        controllerScope = scope
        controller =
            PlayerControllerImpl(
                mediaPlayer = mockk<MediaPlayer>(relaxed = true).also { every { it.isPlaying } returns false },
                ioDispatcher = dispatcher,
                scope = scope,
                sessionPlayerProvider = { player },
                sessionBridge = FakeMediaSessionBridge(),
            )
        controller.startListenSession(context, Sound("custom-id", "custom audio", "custom.mp3"))
        // STATE_READY is what publishes the start, so the engine now holds "playing" as published.
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        fake.reset()
        return player
    }

    private fun givenAnExternalPause(player: Player) {
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY
        playerListener.captured.onIsPlayingChanged(false)
    }

    @Test
    fun `pausing from the media notification reports transport use from the system`() {
        val player = givenAPlayingSession()

        givenAnExternalPause(player)

        val event = fake.assertEmitted("listen_transport")
        assertThat(event.params["action"]).isEqualTo("pause")
        assertThat(event.params["origin"]).isEqualTo("system")
    }

    @Test
    fun `resuming from the media notification reports transport use from the system`() {
        val player = givenAPlayingSession()
        givenAnExternalPause(player)
        fake.reset()

        every { player.playWhenReady } returns true
        playerListener.captured.onIsPlayingChanged(true)

        val event = fake.assertEmitted("listen_transport")
        assertThat(event.params["action"]).isEqualTo("play")
        assertThat(event.params["origin"]).isEqualTo("system")
    }

    @Test
    fun `losing audio focus is the OS reacting, not the Bomper using a control`() {
        val player = givenAPlayingSession()

        // Another app took the focus: playback pauses, but nobody touched a control.
        playerListener.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        givenAnExternalPause(player)

        fake.assertNotEmitted("listen_transport")
    }

    @Test
    fun `unplugging the headphones is not transport use either`() {
        val player = givenAPlayingSession()

        playerListener.captured.onPlayWhenReadyChanged(
            false,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
        )
        givenAnExternalPause(player)

        fake.assertNotEmitted("listen_transport")
    }

    @Test
    fun `scrubbing the lock-screen seekbar reports a seek from the system`() {
        givenAPlayingSession()

        playerListener.captured.onPositionDiscontinuity(
            mockk(relaxed = true),
            mockk(relaxed = true),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        val event = fake.assertEmitted("listen_transport")
        assertThat(event.params["action"]).isEqualTo("seek")
        assertThat(event.params["origin"]).isEqualTo("system")
    }

    @Test
    fun `our own seek is not mistaken for a lock-screen scrub`() {
        givenAPlayingSession()

        // Dragging the waveform inside the app goes through the engine's seekTo; the discontinuity
        // it causes is the same callback the lock-screen seekbar triggers.
        controller.seekTo(4_000)
        playerListener.captured.onPositionDiscontinuity(
            mockk(relaxed = true),
            mockk(relaxed = true),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        assertThat(fake.events.count { it.name == "listen_transport" && it.params["origin"] == "system" })
            .isEqualTo(0)
    }

    private fun systemSeek() {
        playerListener.captured.onPositionDiscontinuity(
            mockk(relaxed = true),
            mockk(relaxed = true),
            Player.DISCONTINUITY_REASON_SEEK,
        )
    }

    @Test
    fun `one drag of the lock-screen seekbar is one seek, not one per movement`() {
        givenAPlayingSession()

        // A system seekbar fires a discontinuity per movement: on device a single gesture produced
        // bursts 17-96 ms wide, which inflates a system scrub against the same gesture on our own
        // waveform (that one reports once, on release).
        repeat(6) { systemSeek() }

        assertThat(fake.events.count { it.name == "listen_transport" }).isEqualTo(1)
    }

    @Test
    fun `a second drag after the gesture window is its own seek`() {
        givenAPlayingSession()
        systemSeek()

        // 500 ms: shorter than the two real drags we measured 976 ms apart, longer than the window.
        ShadowSystemClock.advanceBy(Duration.ofMillis(500))
        systemSeek()

        assertThat(fake.events.count { it.name == "listen_transport" }).isEqualTo(2)
    }

    @Test
    fun `pausing from our own screen is never reported as a system pause`() {
        val player = givenAPlayingSession()
        // The real player delivers the state callback synchronously inside pause(), before we have
        // marked the new state as published. On device this made one screen pause emit both origins
        // 5 ms apart, inflating exactly the split the event exists to measure.
        every { player.pause() } answers {
            every { player.playWhenReady } returns false
            every { player.playbackState } returns Player.STATE_READY
            playerListener.captured.onIsPlayingChanged(false)
        }

        controller.pause()

        fake.assertNotEmitted("listen_transport")
    }

    @Test
    fun `resuming from our own screen is never reported as a system play`() {
        val player = givenAPlayingSession()
        givenAnExternalPause(player)
        fake.reset()
        every { player.play() } answers {
            every { player.playWhenReady } returns true
            playerListener.captured.onIsPlayingChanged(true)
        }
        every { player.isPlaying } returns false

        controller.resume()

        fake.assertNotEmitted("listen_transport")
    }
}
