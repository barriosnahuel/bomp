/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * The business pair of a listen session: how much of the audio was actually heard, and whether it
 * kept playing once the app left the foreground. Guards the events the Vault's long-listen surface
 * shipped without.
 */
internal class ImmersiveListenAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val fake = FakeAnalyticsTracker()

    private companion object {
        const val SAMPLE_MS = 100
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = TestLifecycleOwner()
    private var attached by mutableStateOf(true)
    private var position by mutableStateOf(0)
    private var playing by mutableStateOf(true)
    private var rotating = false

    private fun startSession(durationMs: Int? = 98_000) {
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (attached) {
                    TrackListenSession(
                        tracker = fake,
                        soundId = "sound-1",
                        isPlaying = playing,
                        positionMs = position,
                        durationMs = durationMs,
                        isChangingConfigurations = { rotating },
                    )
                }
            }
        }
    }

    /** Walks the position the way the player publishes it (~100 ms apart), not in one jump. */
    private fun playTo(targetMs: Int) {
        var at = position
        while (at < targetMs) {
            at = minOf(at + SAMPLE_MS, targetMs)
            val next = at
            composeTestRule.runOnIdle { position = next }
        }
        composeTestRule.waitForIdle()
    }

    private fun scrubTo(positionMs: Int) {
        composeTestRule.runOnIdle { position = positionMs }
        composeTestRule.waitForIdle()
    }

    private fun closeScreen() {
        composeTestRule.runOnIdle { attached = false }
        // runOnIdle waits for idle BEFORE the block; the teardown it triggers happens after it.
        composeTestRule.waitForIdle()
    }

    private fun leaveTheApp() {
        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `closing the screen reports how much audio was actually heard`() {
        startSession()
        playTo(1_500)

        closeScreen()

        val event = fake.assertEmitted("listen_session_end")
        assertThat(event.params["listened_ms"]).isEqualTo(1_500)
        assertThat(event.params["duration_ms"]).isEqualTo(98_000)
        assertThat(event.params["surface"]).isEqualTo("vault_listen")
    }

    @Test
    fun `a paused stretch does not count as audio heard`() {
        startSession()
        playTo(800)
        repeat(3) { composeTestRule.runOnIdle { position = 800 } }

        closeScreen()

        assertThat(fake.assertEmitted("listen_session_end").params["listened_ms"]).isEqualTo(800)
    }

    @Test
    fun `a session closed before any audio played reports zero, not the audio length`() {
        startSession()

        closeScreen()

        assertThat(fake.assertEmitted("listen_session_end").params["listened_ms"]).isEqualTo(0)
    }

    @Test
    fun `leaving the app while the session plays reports background listening`() {
        startSession()
        playTo(300)

        leaveTheApp()

        assertThat(fake.assertEmitted("listen_backgrounded").params["surface"])
            .isEqualTo("vault_listen")
    }

    @Test
    fun `leaving the app with the session paused reports nothing`() {
        playing = false
        startSession()

        leaveTheApp()

        fake.assertNotEmitted("listen_backgrounded")
    }

    @Test
    fun `backgrounding twice in one session reports it once`() {
        startSession()

        leaveTheApp()
        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        leaveTheApp()

        assertThat(fake.events.count { it.name == "listen_backgrounded" }).isEqualTo(1)
    }

    @Test
    fun `opening the screen opens the session exactly once`() {
        startSession()
        playTo(400)

        assertThat(fake.events.count { it.name == "listen_session_start" }).isEqualTo(1)
        assertThat(fake.assertEmitted("listen_session_start").params["surface"]).isEqualTo("vault_listen")
    }

    @Test
    fun `a rotation neither ends the session nor counts as leaving the app`() {
        startSession()
        playTo(300)
        rotating = true

        leaveTheApp()
        closeScreen()

        // A rotation tears the composition down and rebuilds it; counting it would inflate starts
        // against ends and invent a background listen that never happened.
        fake.assertNotEmitted("listen_session_end")
        fake.assertNotEmitted("listen_backgrounded")
    }

    @Test
    fun `dragging the waveform forward does not count as audio heard`() {
        startSession()
        playTo(1_000)
        scrubTo(80_000)

        closeScreen()

        assertThat(fake.assertEmitted("listen_session_end").params["listened_ms"]).isEqualTo(1_000)
    }
}
