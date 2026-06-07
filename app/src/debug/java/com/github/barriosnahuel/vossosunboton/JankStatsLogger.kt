/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import timber.log.Timber

/**
 * Debug-only frame diagnostics. Installs [JankStats] on every Activity window and logs janky frames
 * to Logcat, attributing each to the screen it occurred on, so we can see whether the entry-screen
 * jank (LandingActivity, ~24.8% slow frames in production) is concentrated at startup or during
 * steady-state interaction — complementing the on-device Macrobenchmark.
 *
 * Never ships to release: this class lives in the debug source set and the JankStats dependency is
 * `debugImplementation` only.
 *
 * Failure surfacing (deliberately mirrors the repo's fail-loud StrictMode philosophy):
 *  - A failed install is surfaced via [Tracker] (a non-fatal), not swallowed to a log nobody reads —
 *    otherwise a dead tool looks identical to "no jank". A successful install is logged too, so the
 *    absence of jank lines is a positive signal rather than ambiguous silence.
 *  - We do NOT crash on jank the way StrictMode crashes on a violation: jank is a ratio/spectrum and
 *    debug builds jank routinely, so per-frame crashing would be noise. The assertable regression
 *    gate lives in the Macrobenchmark instead; this tool only locates. Frozen frames (the
 *    pathological ones) are bumped to warn so they stand out.
 *
 * Scope note: fine-grained per-phase marks (`list_scroll`, `playback`) need [PerformanceMetricsState]
 * seams at the exact scroll/playback call-sites in main/production code, intentionally deferred to
 * the production JankStats work (investigation plan Fase 6); here jank is attributed by screen.
 */
internal class JankStatsLogger : Application.ActivityLifecycleCallbacks {
    private val trackers = mutableMapOf<Activity, JankStats>()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        runCatching {
            val jankStats =
                JankStats.createAndTrack(activity.window) { frameData ->
                    if (frameData.isJank) {
                        logJankFrame(activity, frameData.frameDurationUiNanos / NANOS_PER_MILLI, frameData.states)
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
}

private const val NANOS_PER_MILLI = 1_000_000L

/** Frames slower than this are "frozen" (Firebase/Android definition); logged at warn so they pop. */
private const val FROZEN_FRAME_THRESHOLD_MS = 700L

private const val STATE_SCREEN = "screen"
