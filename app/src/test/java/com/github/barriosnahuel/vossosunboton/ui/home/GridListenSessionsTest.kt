/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** One test per session transition of the grid listen-session pair. */
internal class GridListenSessionsTest : AbstractRobolectricTest() {
    private val fake = FakeAnalyticsTracker()
    private val sessions = GridListenSessions { fake }

    private fun start(
        soundId: String = A,
        positionMs: Int = 0,
        tracked: Boolean = true,
        surface: String = CanonicalScreenName.MY_SOUNDS,
    ) = sessions.onStart(soundId, surface, durationMs = DURATION_MS, positionMs = positionMs, tracked = tracked)

    private fun playTo(vararg positionsMs: Int) = positionsMs.forEach { sessions.onProgress(it) }

    private fun names() = fake.events.map { it.name }.filterNot { it.startsWith("first_") }

    private fun ends() = fake.events.filter { it.name == "listen_session_end" }

    @Test
    fun `an audio starting opens one session on the surface it was played from`() {
        start(surface = CanonicalScreenName.SEARCH_SOUND)

        val event = fake.assertEmitted("listen_session_start")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.SEARCH_SOUND)
        fake.assertNotEmitted("listen_session_end")
    }

    @Test
    fun `completing the audio closes the session crediting it whole`() {
        start()
        playTo(100, 600, 1_100, 1_600, 1_950)

        sessions.onStop(A, completed = true)

        val end = ends().single()
        assertThat(end.params["listened_ms"]).isEqualTo(DURATION_MS)
        assertThat(end.params["duration_ms"]).isEqualTo(DURATION_MS)
        assertThat(end.params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `stopping the audio closes the session with only what was heard`() {
        start()
        playTo(100, 200, 300)

        sessions.onStop(A, completed = false)

        assertThat(ends().single().params["listened_ms"]).isEqualTo(300)
    }

    @Test
    fun `pausing and resuming the same audio stays one session`() {
        start()
        playTo(100, 200)
        sessions.onPause(A, positionMs = 250)
        start(positionMs = 250)
        playTo(350, 450)
        sessions.onStop(A, completed = false)

        assertThat(names()).containsExactly("listen_session_start", "listen_session_end").inOrder()
        assertThat(ends().single().params["listened_ms"]).isEqualTo(450)
    }

    @Test
    fun `progress ticks while paused belong to another playback and do not accrue`() {
        start()
        playTo(100, 200)
        sessions.onPause(A, positionMs = 200)
        // A preview or recorder review publishing its own positions through the same listener.
        playTo(300, 400, 500)
        sessions.close()

        assertThat(ends().single().params["listened_ms"]).isEqualTo(200)
    }

    @Test
    fun `another audio starting closes the previous session before opening its own`() {
        start(A)
        playTo(100, 200)
        sessions.onPause(A, positionMs = 200)
        start(B)

        assertThat(names())
            .containsExactly("listen_session_start", "listen_session_end", "listen_session_start")
            .inOrder()
        assertThat(ends().single().params["listened_ms"]).isEqualTo(200)
    }

    @Test
    fun `returning to a preempted audio opens a new session from where it was left`() {
        start(A)
        playTo(100, 200)
        sessions.onPause(A, positionMs = 200)
        start(B)
        sessions.onPause(B, positionMs = 0)
        start(A, positionMs = 200)
        playTo(300)
        sessions.close()

        assertThat(fake.events.count { it.name == "listen_session_start" }).isEqualTo(3)
        assertThat(ends().last().params["listened_ms"]).isEqualTo(100)
    }

    @Test
    fun `an untracked playback closes the open session and opens none`() {
        start(A)
        playTo(100)

        start(B, tracked = false)
        playTo(200, 300)
        sessions.onStop(B, completed = true)

        assertThat(names()).containsExactly("listen_session_start", "listen_session_end").inOrder()
        assertThat(ends().single().params["listened_ms"]).isEqualTo(100)
    }

    @Test
    fun `the same audio turning into a long-form listen closes its grid session`() {
        start(A)
        playTo(100)

        start(A, tracked = false)

        assertThat(names()).containsExactly("listen_session_start", "listen_session_end").inOrder()
    }

    @Test
    fun `replaying after completion is a new session`() {
        start()
        sessions.onStop(A, completed = true)
        start()

        assertThat(fake.events.count { it.name == "listen_session_start" }).isEqualTo(2)
    }

    @Test
    fun `leaving the screen closes the session once`() {
        start()
        playTo(100)

        sessions.close()
        sessions.close()
        sessions.onStop(A, completed = true)

        assertThat(ends()).hasSize(1)
    }

    @Test
    fun `callbacks for another audio do not touch the open session`() {
        start(A)
        playTo(100)

        sessions.onPause(B, positionMs = 900)
        sessions.onStop(B, completed = true)
        playTo(200)
        sessions.close()

        assertThat(ends().single().params["listened_ms"]).isEqualTo(200)
    }

    @Test
    fun `nothing is emitted when nothing played`() {
        sessions.onProgress(100)
        sessions.close()

        assertThat(fake.events).isEmpty()
    }

    private companion object {
        const val A = "custom:a"
        const val B = "custom:b"
        const val DURATION_MS = 2_000
    }
}
