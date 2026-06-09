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
 * dependency, so the calibration (startup window, sustained-block window, allowlist) is exhaustively
 * unit-tested headless; [JankStatsLogger] owns the runtime wiring (the frame source and the kill).
 *
 * **What "frozen" means here, and why not the Vitals metric.** A frame counts as frozen when its
 * **UI-thread** duration (`FrameData.frameDurationUiNanos`) reaches [FROZEN_FRAME_THRESHOLD_MS]. This
 * is deliberately *not* the Android Vitals "frozen frame > 700 ms" definition, which is *total* frame
 * duration including GPU/composition. The gate targets a **main-thread block** — the egregious,
 * crash-worthy bug class — and a block shows up as long UI-thread time, not GPU time. GPU-bound
 * slowness (overdraw, heavy shaders) is real but is not a main-thread block; crashing for it would be
 * a false positive, so it stays out of scope (covered statistically by the Macrobenchmark
 * `FrameTimingMetric`). Slow-frame jank (JankStats' `isJank`, ~2× the refresh interval) is far too
 * frequent to gate on and stays log-only.
 *
 * Two crash tiers, by how unambiguous the block is:
 *  - **Tier 1 — a single egregious frame** (≥ [egregiousFrozenMillis]) crashes immediately. Outside the
 *    startup window, on real hardware, a multi-second UI-thread frame is a genuine block, not jitter, so
 *    a one-shot block on a screen you visit once still fails loud.
 *  - **Tier 2 — the ambiguous [FROZEN_FRAME_THRESHOLD_MS]..[egregiousFrozenMillis] band** crashes on the
 *    [crashOnFrozenCount]-th frozen frame inside a [sustainedWindowMillis] window. A lone frozen frame
 *    here is absorbed as a one-off environmental hiccup (GC, thermal); only a *sustained / repeated*
 *    block crashes. The window also bounds the counter so frozen frames can't pile up across a session.
 *
 * Three filters apply before either tier:
 *  - **Per-screen startup window** — frozen frames within [startupSettleMillis] of the *first frame
 *    of a given screen* are ignored. Cold-start composition + Firebase init legitimately exceed the
 *    threshold; the window is re-armed per screen (keyed by [JANK_SCREEN_STATE_KEY]), so an Activity
 *    opened late in the session gets the same grace as the first one — not only the first process frame.
 *  - **Allowlist** — known-legit-heavy frames matched by their attributed state (the frozen-frame
 *    analogue of StrictMode's `KnownThirdPartyViolation` escape hatch) never count, in either tier.
 *  - **Frozen threshold** — frames under [FROZEN_FRAME_THRESHOLD_MS] are normal jank, never gated.
 *
 * Session-scoped: one instance per process (held by the single [JankStatsLogger]). All mutable state
 * is touched only from the JankStats frame-callback thread (a single shared `HandlerThread`), so the
 * non-synchronized fields are safe; do not call [onFrame] from another thread.
 */
internal class FrozenFrameGate(
    private val startupSettleMillis: Long = STARTUP_SETTLE_MILLIS,
    private val crashOnFrozenCount: Int = CRASH_ON_FROZEN_COUNT,
    private val sustainedWindowMillis: Long = SUSTAINED_FROZEN_WINDOW_MILLIS,
    private val egregiousFrozenMillis: Long = EGREGIOUS_FROZEN_FRAME_THRESHOLD_MS,
    private val allowlist: List<KnownHeavyFrame> = KNOWN_HEAVY_FRAMES,
) {
    private val screenFirstFrameMillis = mutableMapOf<String, Long>()
    private var lastFrozenFrameMillis: Long? = null
    private var frozenInWindowCount = 0
    private var fired = false

    /**
     * Feed one observed frame. Returns `true` iff this frame should crash the process.
     *
     * Fed for *every* frame (not only janky ones) so each screen's window anchors on its genuine
     * first rendered frame rather than its first slow one.
     *
     * @param frameStartMillis the frame's start timestamp (`FrameData.frameStartNanos` / 1e6).
     * @param frameDurationMillis the frame's UI duration (`FrameData.frameDurationUiNanos` / 1e6).
     * @param states the frame's attributed states (`FrameData.states`).
     */
    fun onFrame(
        frameStartMillis: Long,
        frameDurationMillis: Long,
        states: List<StateInfo>,
    ): Boolean {
        if (fired) return false // already posted the kill; don't re-fire (avoids duplicate non-fatals before death)

        // Stamp this screen's first-seen time on its first frame of ANY duration — must precede the
        // frozen early-return below, otherwise the window would anchor on the screen's first *frozen*
        // frame and never exclude its legitimately-heavy cold paint.
        val screen = screenKey(states)
        val screenFirstFrame = screenFirstFrameMillis.getOrPut(screen) { frameStartMillis }

        if (frameDurationMillis < FROZEN_FRAME_THRESHOLD_MS) return false // slow/normal frame — never gates
        if (frameStartMillis - screenFirstFrame < startupSettleMillis) return false // this screen's settle window
        if (allowlist.any { matcher -> matcher.matches(states) }) return false // known-legit-heavy render

        // Tier 1 — a single egregiously long frame. Outside the startup window, on real hardware (the gate
        // only arms there), a frame this long is a genuine main-thread block, not environmental jitter: GC
        // pauses are sub-second and thermal throttling spreads slowness over many frames, it doesn't freeze
        // one for seconds. So a one-shot block on a screen you visit once still fails loud — no need to
        // catch it a second time within a window.
        if (frameDurationMillis >= egregiousFrozenMillis) {
            fired = true
            return true
        }

        // Tier 2 — the ambiguous 700 ms..egregious band, where a lone frozen frame is too often an
        // environmental hiccup to justify a crash. Only frozen frames close in time stack toward a crash:
        // a sustained/repeated block, not two unrelated hiccups far apart. A gap larger than the window
        // restarts the count.
        val previousFrozen = lastFrozenFrameMillis
        if (previousFrozen == null || frameStartMillis - previousFrozen > sustainedWindowMillis) {
            frozenInWindowCount = 0
        }
        lastFrozenFrameMillis = frameStartMillis
        frozenInWindowCount++
        return (frozenInWindowCount >= crashOnFrozenCount).also { shouldCrash -> if (shouldCrash) fired = true }
    }

    private fun screenKey(states: List<StateInfo>): String =
        states.firstOrNull { it.key == JANK_SCREEN_STATE_KEY }?.value ?: UNATTRIBUTED_SCREEN
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

/**
 * The JankStats state key under which [JankStatsLogger] attributes each frame to its screen, and the
 * key [FrozenFrameGate] reads to scope its per-screen startup window. Single source of truth so the
 * producer and consumer can't drift (a silent drift would break every per-screen window + allowlist).
 */
internal const val JANK_SCREEN_STATE_KEY = "screen"

/** Frames whose UI-thread duration reaches this are "frozen" (a main-thread block, not the GPU). */
internal const val FROZEN_FRAME_THRESHOLD_MS = 700L

/**
 * A *single* frozen frame this long crashes immediately (tier 1): on real hardware, outside the startup
 * window, a multi-second UI-thread frame is a genuine block, not jitter (GC pauses are sub-second). The
 * 700 ms..this band is ambiguous and uses the 2-in-a-window tolerance instead.
 */
private const val EGREGIOUS_FROZEN_FRAME_THRESHOLD_MS = 1_500L

/** Frozen frames with no `screen` attribution (e.g. before the state attaches) share this window. */
private const val UNATTRIBUTED_SCREEN = ""

/** Crash on the Nth frozen frame inside a sustained window; the first absorbs a one-off hiccup. */
private const val CRASH_ON_FROZEN_COUNT = 2

/**
 * Ignore frozen frames within this window of a screen's first frame: cold-start composition +
 * Firebase init legitimately exceed the threshold on slow devices/emulators. Generous on purpose —
 * the gate catches steady-state regressions, not cold start (that is the Macrobenchmark's job).
 */
private const val STARTUP_SETTLE_MILLIS = 5_000L

/**
 * Two frozen frames farther apart than this are treated as independent one-off hiccups, not a
 * sustained block — the second restarts the count instead of firing the gate. Bounds the counter so
 * frozen frames don't accumulate across an entire session / instrumented suite.
 */
private const val SUSTAINED_FROZEN_WINDOW_MILLIS = 5_000L

// frozen-frame-allowlist: empty today — no screen has a known-legit frozen render. Add entries as the
// gate surfaces deliberate heavy frames that are not main-thread bugs (same triage spirit as the
// StrictMode KNOWN_THIRD_PARTY_VIOLATIONS list — fix the code first; allowlist only third-party/legit).
private val KNOWN_HEAVY_FRAMES = emptyList<KnownHeavyFrame>()
