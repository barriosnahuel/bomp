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
 * Calibration of the debug-only frozen-frame gate. Pure logic, no rendering — the headless,
 * deterministic half of the gate; the runtime kill is wired in [JankStatsLogger] (mirroring the
 * already-trusted `StrictModeConfigurator` one-liner). Each test pins one calibration mechanism so a
 * future tweak to thresholds/windows can't silently break the others.
 *
 * Timing model: each screen's startup window anchors on *that screen's* first observed frame; the
 * sustained-block window measures the gap between consecutive frozen frames. Tests call [prime] (a
 * fast frame at t=0) to anchor a screen's window where the real app would, then place frames relative
 * to it.
 */
class FrozenFrameGateTest {
    private val settleMs = 5_000L
    private val sustainedMs = 5_000L
    private val frozenMs = FROZEN_FRAME_THRESHOLD_MS // 700
    private val egregiousMs = 1_500L
    private val afterWindow = settleMs + 1_000L
    private val gate = newGate(allowlist = emptyList())

    private fun newGate(allowlist: List<KnownHeavyFrame>) =
        FrozenFrameGate(
            startupSettleMillis = settleMs,
            crashOnFrozenCount = 2,
            sustainedWindowMillis = sustainedMs,
            egregiousFrozenMillis = egregiousMs,
            allowlist = allowlist,
        )

    /** Feed one frame attributed to [screen]; named args inlined so call sites stay short. */
    private fun FrozenFrameGate.feed(
        start: Long,
        durMs: Long,
        screen: String = "LandingActivity",
    ) = onFrame(frameStartMillis = start, frameDurationMillis = durMs, states = listOf(StateInfo(JANK_SCREEN_STATE_KEY, screen)))

    /** A fast frame at t=0 — anchors [screen]'s startup window where the real app would, never counts. */
    private fun FrozenFrameGate.prime(screen: String = "LandingActivity") = feed(start = 0, durMs = 16, screen = screen)

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
    fun `a second frozen frame within the sustained window crashes`() {
        gate.prime()
        gate.feed(start = afterWindow, durMs = 900)
        assertThat(gate.feed(start = afterWindow + 200, durMs = 1_200)).isTrue() // both in the 700..egregious band
    }

    @Test
    fun `a single egregiously long frame crashes immediately, no second needed`() {
        gate.prime()
        assertThat(gate.feed(start = afterWindow, durMs = egregiousMs)).isTrue()
    }

    @Test
    fun `a frame just below the egregious threshold still needs a second frozen frame`() {
        gate.prime()
        assertThat(gate.feed(start = afterWindow, durMs = egregiousMs - 1)).isFalse() // 1st, ambiguous band
        assertThat(gate.feed(start = afterWindow + 100, durMs = egregiousMs - 1)).isTrue() // 2nd within window
    }

    @Test
    fun `an egregious frame within the startup window is still ignored`() {
        gate.prime() // anchors the window at t=0
        assertThat(gate.feed(start = 2_000, durMs = 4_000)).isFalse() // 2s in: inside the window, ignored despite its size
    }

    @Test
    fun `an allowlisted egregious frame never crashes`() {
        val allowlisted = newGate(listOf(KnownHeavyFrame(stateKey = JANK_SCREEN_STATE_KEY, stateValueContains = "HugeListActivity")))
        allowlisted.prime(screen = "HugeListActivity")
        assertThat(allowlisted.feed(start = afterWindow, durMs = 10_000, screen = "HugeListActivity")).isFalse()
    }

    @Test
    fun `frozen frames within a screen's startup window are ignored and never counted`() {
        // The first frame anchors the window at t=0; three frozen frames inside [0, settle) must not count.
        gate.feed(start = 0, durMs = 2_000)
        gate.feed(start = 1_000, durMs = 3_000)
        gate.feed(start = settleMs - 1, durMs = 2_500)
        // A single frozen frame after the window is still only the FIRST counted one → no crash.
        assertThat(gate.feed(start = afterWindow, durMs = 900)).isFalse()
    }

    @Test
    fun `the startup window is anchored on the screen's first observed frame even when it is fast`() {
        gate.prime() // fast frame at t=0 anchors the window, does not count
        gate.feed(start = 2_000, durMs = 900) // inside window — ignored
        assertThat(gate.feed(start = afterWindow, durMs = 900)).isFalse()
        assertThat(gate.feed(start = afterWindow + 50, durMs = 900)).isTrue()
    }

    @Test
    fun `the startup window boundary is exclusive - a frame at exactly settle is already armed`() {
        gate.prime()
        assertThat(gate.feed(start = settleMs - 1, durMs = 900)).isFalse() // inside, uncounted
        assertThat(gate.feed(start = settleMs, durMs = 900)).isFalse() // armed: 1st counted
        assertThat(gate.feed(start = settleMs + 10, durMs = 900)).isTrue() // 2nd counted, within sustained window
    }

    @Test
    fun `each screen gets its own startup window - a late screen's cold paint is excluded`() {
        // Regression guard: previously the window anchored on the FIRST frame of the whole process, so
        // an Activity opened late legitimately froze past it. Now each screen re-arms its own window.
        gate.feed(start = 0, durMs = 16, screen = "LandingActivity") // anchor Landing at t=0
        gate.feed(start = 20_000, durMs = 16, screen = "AddButtonActivity") // anchor AddButton at t=20s
        // 22s is 22s into the *process* but only 2s into AddButton's window → ignored.
        assertThat(gate.feed(start = 22_000, durMs = 2_000, screen = "AddButtonActivity")).isFalse()
    }

    @Test
    fun `a screen still gates after its own startup window elapses`() {
        gate.feed(start = 0, durMs = 16, screen = "LandingActivity")
        gate.feed(start = 20_000, durMs = 16, screen = "AddButtonActivity") // anchor AddButton at t=20s
        assertThat(gate.feed(start = 26_000, durMs = 900, screen = "AddButtonActivity")).isFalse() // 6s in → 1st
        assertThat(gate.feed(start = 26_200, durMs = 900, screen = "AddButtonActivity")).isTrue() // 2nd within sustained
    }

    @Test
    fun `two frozen frames farther apart than the sustained window do not accumulate`() {
        // Regression guard: previously the session-wide counter let two frozen frames anywhere in the
        // run crash the process. Now a gap beyond the sustained window restarts the count.
        gate.prime()
        gate.feed(start = afterWindow, durMs = 900) // 1st counted
        assertThat(gate.feed(start = afterWindow + sustainedMs + 1_000, durMs = 900)).isFalse() // gap > window → reset
    }

    @Test
    fun `the sustained-window boundary is inclusive - frames exactly a window apart still stack`() {
        gate.prime()
        gate.feed(start = afterWindow, durMs = 900) // 1st
        assertThat(gate.feed(start = afterWindow + sustainedMs, durMs = 900)).isTrue() // gap == window → 2nd stacks
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
    fun `an allowlisted frozen frame never counts toward the gate`() {
        val allowlisted = newGate(listOf(KnownHeavyFrame(stateKey = JANK_SCREEN_STATE_KEY, stateValueContains = "HugeListActivity")))
        allowlisted.prime(screen = "HugeListActivity")
        repeat(10) {
            assertThat(allowlisted.feed(start = afterWindow + it * 100L, durMs = 2_000, screen = "HugeListActivity")).isFalse()
        }
    }

    @Test
    fun `allowlisted frozen frames do not absorb the tolerance for real ones`() {
        val allowlisted = newGate(listOf(KnownHeavyFrame(stateKey = JANK_SCREEN_STATE_KEY, stateValueContains = "HugeListActivity")))
        allowlisted.prime()
        // One allowlisted frozen frame (ignored), then two REAL frozen frames → crash on the second real one.
        allowlisted.feed(start = afterWindow, durMs = 2_000, screen = "HugeListActivity")
        assertThat(allowlisted.feed(start = afterWindow + 100, durMs = 900)).isFalse()
        assertThat(allowlisted.feed(start = afterWindow + 200, durMs = 900)).isTrue()
    }

    @Test
    fun `frames with no screen attribution share one window`() {
        val unattributed = { start: Long, durMs: Long -> gate.onFrame(start, durMs, emptyList()) }
        unattributed(0, 16) // anchors the unattributed window
        assertThat(unattributed(afterWindow, 900)).isFalse() // 1st
        assertThat(unattributed(afterWindow + 100, 900)).isTrue() // 2nd within sustained
    }

    @Test
    fun `a key-only allowlist matcher ignores the state value`() {
        val matcher = KnownHeavyFrame(stateKey = JANK_SCREEN_STATE_KEY)
        assertThat(matcher.matches(listOf(StateInfo(JANK_SCREEN_STATE_KEY, "anything")))).isTrue()
        assertThat(matcher.matches(listOf(StateInfo("other", "anything")))).isFalse()
    }
}
