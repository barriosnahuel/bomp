/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTransport
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Whether a listen was driven from the media notification / lock screen instead of the app's own
 * screen — the pocket-listening signal. The engine already knows the difference (`publishedPlaying`
 * mismatches mean an external command); these guard that it reports it once per gesture, that our
 * own commands are never mistaken for external ones, and that the OS pausing us on its own is not
 * counted as the Bomper doing something.
 *
 * Drives [ListenSessionEngine] directly through its injected transport callback: that is what the
 * callback exists for, and it keeps the analytics singleton (which needs a Firebase that tests do
 * not have) out of the test entirely.
 */
internal class ListenSessionTransportAnalyticsTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val reported = mutableListOf<String>()
    private val playerListener = slot<Player.Listener>()
    private lateinit var player: Player
    private lateinit var engine: ListenSessionEngine

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun givenAPlayingSession() {
        player =
            mockk<Player>(relaxed = true) {
                every { addListener(capture(playerListener)) } just Runs
                every { isPlaying } returns false
                every { currentPosition } returns 0L
                every { duration } returns 10_000L
            }
        engine =
            ListenSessionEngine(
                playerProvider = { player },
                handler = Handler(Looper.getMainLooper()),
                listenerProvider = { null },
                playbackState = MutableStateFlow(null),
                bridge = FakeMediaSessionBridge(),
                onSystemTransport = { _, action -> reported += action },
            )
        engine.startUri(context, Uri.parse("file:///audio.mp3"), startPositionMs = 0)
        // STATE_READY is what publishes the start, so the engine now holds "playing" as published.
        playerListener.captured.onPlaybackStateChanged(Player.STATE_READY)
        reported.clear()
    }

    private fun givenAnExternalPause() {
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY
        playerListener.captured.onIsPlayingChanged(false)
    }

    private fun systemSeek() {
        playerListener.captured.onPositionDiscontinuity(
            mockk(relaxed = true),
            mockk(relaxed = true),
            Player.DISCONTINUITY_REASON_SEEK,
        )
    }

    @Test
    fun `pausing from the media notification reports transport use from the system`() {
        givenAPlayingSession()

        givenAnExternalPause()

        assertThat(reported).containsExactly(AnalyticsTransport.PAUSE)
    }

    @Test
    fun `resuming from the media notification reports transport use from the system`() {
        givenAPlayingSession()
        givenAnExternalPause()
        reported.clear()

        every { player.playWhenReady } returns true
        playerListener.captured.onIsPlayingChanged(true)

        assertThat(reported).containsExactly(AnalyticsTransport.PLAY)
    }

    @Test
    fun `losing audio focus is the OS reacting, not the Bomper using a control`() {
        givenAPlayingSession()

        // Another app took the focus: playback pauses, but nobody touched a control.
        playerListener.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        givenAnExternalPause()

        assertThat(reported).isEmpty()
    }

    @Test
    fun `unplugging the headphones is not transport use either`() {
        givenAPlayingSession()

        playerListener.captured.onPlayWhenReadyChanged(
            false,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
        )
        givenAnExternalPause()

        assertThat(reported).isEmpty()
    }

    @Test
    fun `scrubbing the lock-screen seekbar reports a seek from the system`() {
        givenAPlayingSession()

        systemSeek()

        assertThat(reported).containsExactly(AnalyticsTransport.SEEK)
    }

    @Test
    fun `our own seek is not mistaken for a lock-screen scrub`() {
        givenAPlayingSession()

        // Dragging the waveform inside the app goes through the engine's seekTo; the discontinuity
        // it causes is the same callback the lock-screen seekbar triggers.
        engine.seekTo(4_000)
        systemSeek()

        assertThat(reported).isEmpty()
    }

    @Test
    fun `one drag of the lock-screen seekbar is one seek, not one per movement`() {
        givenAPlayingSession()

        // A system seekbar fires a discontinuity per movement: on device a single gesture produced
        // bursts 17-96 ms wide, which inflates a system scrub against the same gesture on our own
        // waveform (that one reports once, on release).
        repeat(6) { systemSeek() }

        assertThat(reported).containsExactly(AnalyticsTransport.SEEK)
    }

    @Test
    fun `a second drag after the gesture window is its own seek`() {
        givenAPlayingSession()
        systemSeek()

        // 500 ms: shorter than the two real drags we measured 976 ms apart, longer than the window.
        ShadowSystemClock.advanceBy(Duration.ofMillis(500))
        systemSeek()

        assertThat(reported).hasSize(2)
    }

    @Test
    fun `pausing from our own screen is never reported as a system pause`() {
        givenAPlayingSession()
        // The real player delivers the state callback synchronously inside pause(), before we have
        // marked the new state as published. On device this made one screen pause emit both origins
        // 5 ms apart, inflating exactly the split the event exists to measure.
        every { player.pause() } answers {
            every { player.playWhenReady } returns false
            every { player.playbackState } returns Player.STATE_READY
            playerListener.captured.onIsPlayingChanged(false)
        }

        engine.pause()

        assertThat(reported).isEmpty()
    }

    @Test
    fun `resuming from our own screen is never reported as a system play`() {
        givenAPlayingSession()
        givenAnExternalPause()
        reported.clear()
        every { player.play() } answers {
            every { player.playWhenReady } returns true
            playerListener.captured.onIsPlayingChanged(true)
        }
        every { player.isPlaying } returns false

        engine.resume()

        assertThat(reported).isEmpty()
    }
}
