/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import androidx.metrics.performance.StateInfo

/**
 * Decides whether a frozen frame should crash the process — the assertable half of the debug-only
 * jank gate (mirrors `StrictModeConfigurator`'s report decision). Pure logic, no Android/rendering
 * dependency, so the calibration (startup window, tolerance, allowlist) is exhaustively unit-tested
 * headless; [JankStatsLogger] owns the runtime wiring (the frame source and the actual kill).
 *
 * Why frozen-only, why a hard gate: slow-frame jank is a non-deterministic spectrum (GC, thermal,
 * emulator slowness), so gating on it would be flaky — worse than no gate. A frozen frame (UI
 * duration > [FROZEN_FRAME_THRESHOLD_MS] ms) is so far above normal jitter it almost always means a
 * real main-thread block — the egregious class that *can* be a hard gate, like StrictMode crashing
 * on disk-on-main. Slow-frame regressions stay statistical (the Macrobenchmark `FrameTimingMetric`),
 * not a per-frame crash.
 *
 * Three calibration mechanisms keep it from being flaky:
 *  - **Startup window** — frozen frames within [startupSettleMillis] of the first observed frame are
 *    ignored (not even counted). Cold-start composition + Firebase init legitimately exceed the
 *    threshold on slow devices; without this every cold start on a slow emulator would crash.
 *  - **Tolerance** — the gate fires on the [crashOnFrozenCount]-th frozen frame, so a single one-off
 *    environmental hiccup never crashes; only a repeated block does.
 *  - **Allowlist** — known-legit-heavy frames matched by their attributed state (the frozen-frame
 *    analogue of StrictMode's `KnownThirdPartyViolation` escape hatch) never count.
 *
 * Session-scoped: one instance per process (held by the single [JankStatsLogger]); the frozen
 * counter is the whole-session count, not per-Activity.
 */
internal class FrozenFrameGate(
    private val startupSettleMillis: Long = STARTUP_SETTLE_MILLIS,
    private val crashOnFrozenCount: Int = CRASH_ON_FROZEN_COUNT,
    private val allowlist: List<KnownHeavyFrame> = KNOWN_HEAVY_FRAMES,
) {
    private var firstFrameStartMillis: Long? = null
    private var frozenCount = 0
    private var fired = false

    /**
     * Feed one observed frame. Returns `true` iff this frame should crash the process.
     *
     * Fed for *every* frame (not only janky ones) so [firstFrameStartMillis] anchors on the genuine
     * first rendered frame rather than the first slow one.
     *
     * @param frameStartMillis the frame's start timestamp (`FrameData.frameStartNanos` / 1e6); the
     *   first value seen anchors the startup window.
     * @param frameDurationMillis the frame's UI duration (`FrameData.frameDurationUiNanos` / 1e6).
     * @param states the frame's attributed states (`FrameData.states`).
     */
    fun onFrame(
        frameStartMillis: Long,
        frameDurationMillis: Long,
        states: List<StateInfo>,
    ): Boolean {
        if (fired) return false // already posted the kill; don't re-fire (avoids duplicate non-fatals before death)

        val firstFrame = firstFrameStartMillis ?: frameStartMillis.also { firstFrameStartMillis = it }

        if (frameDurationMillis < FROZEN_FRAME_THRESHOLD_MS) return false // slow/normal frame — never gates
        if (frameStartMillis - firstFrame < startupSettleMillis) return false // startup window — legitimately heavy
        if (allowlist.any { matcher -> matcher.matches(states) }) return false // known-legit-heavy render

        frozenCount++
        return (frozenCount >= crashOnFrozenCount).also { shouldCrash -> if (shouldCrash) fired = true }
    }
}

/**
 * Match a frame by its attributed JankStats state (key + optional value substring) — the frozen-frame
 * analogue of StrictMode's `KnownThirdPartyViolation`. A matched frame is a known-legit-heavy render
 * (e.g. the very first paint of a deliberately huge list) and never counts toward the gate.
 *
 * Escape hatch, kept intentionally narrow: any frozen frame NOT matched here still gates, so a genuine
 * new main-thread block raises a flag. Same triage spirit as the StrictMode list — fix the code first;
 * allowlist only genuinely-legit heavy renders.
 */
internal data class KnownHeavyFrame(
    val stateKey: String,
    val stateValueContains: String? = null,
) {
    fun matches(states: List<StateInfo>): Boolean =
        states.any { state ->
            state.key == stateKey && (stateValueContains == null || stateValueContains in state.value)
        }
}

/** Frames whose UI duration reaches this are "frozen" (Firebase / Android Vitals definition). */
internal const val FROZEN_FRAME_THRESHOLD_MS = 700L

/** Crash on the Nth frozen frame of the session; the first absorbs a one-off environmental hiccup. */
private const val CRASH_ON_FROZEN_COUNT = 2

/**
 * Ignore frozen frames within this window of the first observed frame: cold-start composition +
 * Firebase init legitimately exceed the threshold on slow devices/emulators. Generous on purpose —
 * the gate catches steady-state regressions, not cold start (that is the Macrobenchmark's job).
 */
private const val STARTUP_SETTLE_MILLIS = 5_000L

// frozen-frame-allowlist: empty today — no screen has a known-legit frozen render. Add entries as the
// gate surfaces deliberate heavy frames that are not main-thread bugs (same triage spirit as the
// StrictMode KNOWN_THIRD_PARTY_VIOLATIONS list — fix the code first; allowlist only third-party/legit).
private val KNOWN_HEAVY_FRAMES = emptyList<KnownHeavyFrame>()
