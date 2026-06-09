/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import androidx.metrics.performance.StateInfo

/**
 * Decides whether a frozen frame should crash the process — the pure, headless-tested half of the
 * debug jank gate; [JankStatsLogger] owns the runtime wiring and the kill.
 *
 * Two crash tiers (a single egregious frame, or a repeated one in the ambiguous band) behind three
 * pre-filters (per-screen startup window, allowlist, frozen threshold); thresholds are the constants
 * below. Why frozen-only, why these tiers/thresholds, why not `isJank` / total frame duration, why
 * disarmed under instrumentation: `docs/adr/0016-jankstats-frozen-frame-crash-gate.md`.
 *
 * Session-scoped (one per process). State is touched only from the single JankStats `HandlerThread`,
 * so the unsynchronized fields are safe — do not call [onFrame] off that thread.
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
     * Feed one observed frame; returns `true` iff it should crash the process. Fed for *every* frame so
     * each screen's startup window anchors on its first rendered frame, not its first slow one.
     *
     * @param frameStartMillis frame start (`FrameData.frameStartNanos` / 1e6).
     * @param frameDurationMillis UI-thread duration (`FrameData.frameDurationUiNanos` / 1e6).
     * @param states attributed states (`FrameData.states`).
     */
    fun onFrame(
        frameStartMillis: Long,
        frameDurationMillis: Long,
        states: List<StateInfo>,
    ): Boolean {
        if (fired) return false // fire once: the kill is already posted

        // Stamp each screen's first frame (any duration) before the frozen early-returns, so its window
        // anchors on the real first paint, not its first frozen one.
        val screen = screenKey(states)
        val screenFirstFrame = screenFirstFrameMillis.getOrPut(screen) { frameStartMillis }

        if (frameDurationMillis < FROZEN_FRAME_THRESHOLD_MS) return false // not frozen
        if (frameStartMillis - screenFirstFrame < startupSettleMillis) return false // this screen's startup settle
        if (allowlist.any { matcher -> matcher.matches(states) }) return false // known-legit-heavy render

        // Tier 1: a single egregious frame is an unambiguous block → crash now (ADR 0016).
        if (frameDurationMillis >= egregiousFrozenMillis) {
            fired = true
            return true
        }

        // Tier 2: ambiguous band — only a sustained block crashes (Nth frozen within the window); a gap
        // wider than the window restarts the count, so far-apart hiccups don't accumulate.
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

/** Allowlist matcher: a frame state key + optional value substring. Usage/triage: ADR 0016. */
internal data class KnownHeavyFrame(
    val stateKey: String,
    val stateValueContains: String? = null,
) {
    fun matches(states: List<StateInfo>): Boolean =
        states.any { state ->
            state.key == stateKey && (stateValueContains == null || stateValueContains in state.value)
        }
}

/** Shared key [JankStatsLogger] writes and [FrozenFrameGate] reads — single source so they can't drift. */
internal const val JANK_SCREEN_STATE_KEY = "screen"

/** Frames whose UI-thread duration reaches this are "frozen" (a main-thread block, not the GPU). */
internal const val FROZEN_FRAME_THRESHOLD_MS = 700L

/** A single frozen frame this long crashes immediately (tier 1); the 700 ms..this band needs two. */
private const val EGREGIOUS_FROZEN_FRAME_THRESHOLD_MS = 1_500L

/** Frozen frames with no `screen` attribution (e.g. before the state attaches) share this window. */
private const val UNATTRIBUTED_SCREEN = ""

/** Crash on the Nth frozen frame inside a sustained window; the first absorbs a one-off hiccup. */
private const val CRASH_ON_FROZEN_COUNT = 2

/** Frozen frames within this of a screen's first frame are ignored (cold start legitimately exceeds 700 ms). */
private const val STARTUP_SETTLE_MILLIS = 5_000L

/** Two frozen frames farther apart than this don't stack — independent hiccups, not a sustained block. */
private const val SUSTAINED_FROZEN_WINDOW_MILLIS = 5_000L

// frozen-frame-allowlist: empty today. Add KnownHeavyFrame entries for known-legit heavy renders; triage in ADR 0016.
private val KNOWN_HEAVY_FRAMES = emptyList<KnownHeavyFrame>()
