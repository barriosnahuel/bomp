/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.metrics.performance.StateInfo
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import timber.log.Timber

/**
 * Debug-only frame diagnostics + a hard gate on frozen frames. Installs [JankStats] on every Activity
 * window, attributes each frame to the screen it occurred on, logs janky frames to Logcat (to locate
 * the LandingActivity entry-screen jank, ~24.8% slow frames in production), and — for the pathological
 * frozen frames — crashes the process so a main-thread block fails loudly instead of waiting for
 * Firebase Performance to flag it weeks after release.
 *
 * Never ships to release: this class lives in the debug source set and the JankStats dependency is
 * `debugImplementation` only. The gate therefore fires in manual debug use AND in the instrumented
 * suite (same runtime home as `StrictModeConfigurator`) — making the existing pre-PR instrumented run
 * a jank gate for free, no new tests and no human watching Logcat.
 *
 * Two failure modes, deliberately mirroring the repo's fail-loud StrictMode philosophy:
 *  - **Install failure** is surfaced via [Tracker] (a non-fatal), not swallowed to a log nobody reads —
 *    otherwise a dead tool looks identical to "no jank". A successful install is logged too, so the
 *    absence of jank lines is a positive signal rather than ambiguous silence.
 *  - **A frozen frame past the gate** ([FrozenFrameGate]) crashes the process, exactly as StrictMode
 *    crashes on a violation. Only *frozen* frames gate — slow-frame jank is a non-deterministic
 *    spectrum and stays log-only / statistical (the Macrobenchmark `FrameTimingMetric` is its
 *    regression gate). The startup window + 2nd-frozen tolerance + allowlist that keep the gate from
 *    being flaky all live in [FrozenFrameGate]; this class only wires the frame source and the kill.
 *
 * Scope note: fine-grained per-phase marks (`list_scroll`, `playback`) need [PerformanceMetricsState]
 * seams at the exact scroll/playback call-sites in main/production code, intentionally deferred to
 * the production JankStats work (investigation plan Fase 6); here jank is attributed by screen.
 */
internal class JankStatsLogger : Application.ActivityLifecycleCallbacks {
    private val trackers = mutableMapOf<Activity, JankStats>()

    /** Session-scoped: one gate across all activities, so the frozen counter is the whole-session count. */
    private val frozenFrameGate = FrozenFrameGate()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        runCatching {
            val jankStats =
                JankStats.createAndTrack(activity.window) { frameData ->
                    val durationMs = frameData.frameDurationUiNanos / NANOS_PER_MILLI
                    // Feed every frame (not only janky ones) so the gate's startup window anchors on the
                    // genuine first rendered frame rather than the first slow one.
                    if (frozenFrameGate.onFrame(frameData.frameStartNanos / NANOS_PER_MILLI, durationMs, frameData.states)) {
                        crashOnFrozenFrame(activity, durationMs, frameData.states)
                    }
                    if (frameData.isJank) {
                        logJankFrame(activity, durationMs, frameData.states)
                    }
                }
            trackers[activity] = jankStats
            PerformanceMetricsState
                .getHolderForHierarchy(activity.window.decorView)
                .state
                ?.putState(STATE_SCREEN, activity::class.simpleName ?: activity.localClassName)
            Timber.d("Jank diagnostics installed on %s", activity.localClassName)
        }.onFailure { error ->
            // Surface, don't swallow: a silently dead diagnostic is indistinguishable from "no jank".
            Tracker.track(RuntimeException("could not install jank diagnostics", error))
        }
    }

    override fun onActivityResumed(activity: Activity) {
        trackers[activity]?.isTrackingEnabled = true
    }

    override fun onActivityPaused(activity: Activity) {
        trackers[activity]?.isTrackingEnabled = false
    }

    override fun onActivityDestroyed(activity: Activity) {
        trackers.remove(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    private fun logJankFrame(
        activity: Activity,
        durationMs: Long,
        states: List<*>,
    ) {
        if (durationMs >= FROZEN_FRAME_THRESHOLD_MS) {
            Timber.w("Frozen frame on %s: %d ms (states=%s)", activity.localClassName, durationMs, states)
        } else {
            Timber.d("Jank frame on %s: %d ms (states=%s)", activity.localClassName, durationMs, states)
        }
    }

    /**
     * The gate fired: record a non-fatal with a static, searchable message, then kill the process —
     * the exact shape of `StrictModeConfigurator.reportViolation`. Per-frame context (screen, duration)
     * rides as breadcrumbs, never in the wrapper message, so the Crashlytics title stays stable.
     *
     * The kill is posted to the main looper because throwing from inside the JankStats callback would
     * be caught by its own dispatch; a fresh main-loop message escapes to the system uncaught handler,
     * which terminates the process. Same reasoning as the StrictMode `penaltyListener` kill.
     */
    private fun crashOnFrozenFrame(
        activity: Activity,
        durationMs: Long,
        states: List<StateInfo>,
    ) {
        Tracker.log("jank.screen=${activity.localClassName}")
        Tracker.log("jank.frozenFrameMs=$durationMs")
        Tracker.log("jank.states=$states")
        Tracker.track(FrozenFrameException())
        Handler(Looper.getMainLooper()).post { throw FrozenFrameException() }
    }
}

private const val NANOS_PER_MILLI = 1_000_000L

private const val STATE_SCREEN = "screen"

/**
 * Wraps the frozen-frame gate failure so the message starts with the searchable `"JankStats: frozen
 * frame"` identifier (the analogue of [StrictModeException]'s `"StrictMode:"` prefix). The message is
 * static — a compile-time constant — so the Crashlytics title never flickers; dynamic context (screen,
 * duration) travels as `Tracker.log` breadcrumbs attached just before the `track` call.
 */
private class FrozenFrameException :
    RuntimeException(
        "JankStats: frozen frame exceeded ${FROZEN_FRAME_THRESHOLD_MS}ms (debug jank gate)",
    )
