/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import androidx.metrics.performance.StateInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Calibration of the debug-only frozen-frame gate. Pure logic, no rendering — this is the headless,
 * deterministic half of the gate; the runtime kill is wired in [JankStatsLogger] (and mirrors the
 * already-trusted `StrictModeConfigurator` one-liner). Each test fixes one calibration mechanism so a
 * future tweak to thresholds/tolerance/window can't silently break the others.
 *
 * Timing model: the *first* [FrozenFrameGate.onFrame] call anchors the startup window at its
 * `frameStartMillis`; SETTLE_MS later the gate arms. In a real app the first observed frame is near
 * process start, so tests call [prime] first (a fast frame at t=0) to reproduce that anchor before
 * placing frames before/after the window.
 */
class FrozenFrameGateTest {
    private val settleMs = 5_000L
    private val frozenMs = FROZEN_FRAME_THRESHOLD_MS // 700
    private val afterWindow = settleMs + 1_000L
    private val gate = newGate(allowlist = emptyList())

    private fun newGate(allowlist: List<KnownHeavyFrame>) =
        FrozenFrameGate(startupSettleMillis = settleMs, crashOnFrozenCount = 2, allowlist = allowlist)

    private fun screen(name: String = "LandingActivity") = listOf(StateInfo("screen", name))

    /** Feed one frame; named args inlined here so the per-test call sites stay short and readable. */
    private fun FrozenFrameGate.feed(
        start: Long,
        durMs: Long,
        name: String = "LandingActivity",
    ) = onFrame(frameStartMillis = start, frameDurationMillis = durMs, states = screen(name))

    /** A fast frame at t=0 — anchors the startup window where the real app would, and never counts. */
    private fun FrozenFrameGate.prime() = feed(start = 0, durMs = 16)

    @Test
    fun `a slow frame below the frozen threshold never gates, however many`() {
        gate.prime()
        repeat(50) {
            assertThat(gate.feed(start = afterWindow + it, durMs = frozenMs - 1)).isFalse()
        }
    }

    @Test
    fun `a frame exactly at the threshold counts as frozen`() {
        gate.prime()
        gate.feed(start = afterWindow, durMs = frozenMs)
        assertThat(gate.feed(start = afterWindow + 100, durMs = frozenMs)).isTrue()
    }

    @Test
    fun `a single frozen frame after the startup window does not crash`() {
        gate.prime()
        assertThat(gate.feed(start = afterWindow, durMs = 900)).isFalse()
    }

    @Test
    fun `the second frozen frame after the startup window crashes`() {
        gate.prime()
        gate.feed(start = afterWindow, durMs = 900)
        assertThat(gate.feed(start = afterWindow + 200, durMs = 1_500)).isTrue()
    }

    @Test
    fun `frozen frames within the startup window are ignored and never counted`() {
        // The first frame anchors the window at t=0; three frozen frames inside [0, settle) must not count.
        gate.feed(start = 0, durMs = 2_000)
        gate.feed(start = 1_000, durMs = 3_000)
        gate.feed(start = settleMs - 1, durMs = 2_500)
        // A single frozen frame after the window is still only the FIRST counted one → no crash.
        assertThat(gate.feed(start = afterWindow, durMs = 900)).isFalse()
    }

    @Test
    fun `the window is anchored on the first observed frame even when it is fast`() {
        gate.prime() // fast frame at t=0 anchors the window, does not count
        gate.feed(start = 2_000, durMs = 900) // inside window — ignored
        assertThat(gate.feed(start = afterWindow, durMs = 900)).isFalse()
        assertThat(gate.feed(start = afterWindow + 50, durMs = 900)).isTrue()
    }

    @Test
    fun `the window boundary is exclusive - a frame at exactly settle is already armed`() {
        gate.prime()
        // frameStart - firstFrame == settle is NOT < settle, so the boundary frame is armed (counts).
        assertThat(gate.feed(start = settleMs - 1, durMs = 900)).isFalse() // inside, uncounted
        assertThat(gate.feed(start = settleMs, durMs = 900)).isFalse() // armed: 1st counted
        assertThat(gate.feed(start = settleMs + 10, durMs = 900)).isTrue() // 2nd counted
    }

    @Test
    fun `an allowlisted frozen frame never counts toward the gate`() {
        val allowlisted = newGate(listOf(KnownHeavyFrame(stateKey = "screen", stateValueContains = "HugeListActivity")))
        allowlisted.prime()
        repeat(10) {
            assertThat(allowlisted.feed(start = afterWindow + it * 100L, durMs = 2_000, name = "HugeListActivity")).isFalse()
        }
    }

    @Test
    fun `allowlisted frozen frames do not absorb the tolerance for real ones`() {
        val allowlisted = newGate(listOf(KnownHeavyFrame(stateKey = "screen", stateValueContains = "HugeListActivity")))
        allowlisted.prime()
        // One allowlisted frozen frame (ignored), then two REAL frozen frames → crash on the second real one.
        allowlisted.feed(start = afterWindow, durMs = 2_000, name = "HugeListActivity")
        assertThat(allowlisted.feed(start = afterWindow + 100, durMs = 900)).isFalse()
        assertThat(allowlisted.feed(start = afterWindow + 200, durMs = 900)).isTrue()
    }

    @Test
    fun `the gate fires at most once, even on a storm of later frozen frames`() {
        gate.prime()
        gate.feed(start = afterWindow, durMs = 900) // 1st frozen — no crash
        assertThat(gate.feed(start = afterWindow + 100, durMs = 900)).isTrue() // 2nd frozen — fires
        repeat(20) {
            assertThat(gate.feed(start = afterWindow + 200 + it, durMs = 5_000)).isFalse() // already fired — silent
        }
    }

    @Test
    fun `a key-only allowlist matcher ignores the state value`() {
        val matcher = KnownHeavyFrame(stateKey = "screen")
        assertThat(matcher.matches(listOf(StateInfo("screen", "anything")))).isTrue()
        assertThat(matcher.matches(listOf(StateInfo("other", "anything")))).isFalse()
    }
}
