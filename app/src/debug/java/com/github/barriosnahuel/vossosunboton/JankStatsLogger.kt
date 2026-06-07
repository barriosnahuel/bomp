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
import timber.log.Timber

/**
 * Debug-only frame diagnostics. Installs [JankStats] on every Activity window and logs janky frames
 * to Logcat, attributing each to the screen it occurred on. Lets us see whether the entry-screen
 * jank (LandingActivity, ~24.8% slow frames in production) is concentrated at startup or during
 * steady-state interaction — complementing the on-device Macrobenchmark.
 *
 * Never ships to release: this class lives in the debug source set and the JankStats dependency is
 * `debugImplementation` only.
 *
 * Scope note: fine-grained per-phase marks (`list_scroll`, `playback`) need
 * [PerformanceMetricsState] seams at the exact scroll/playback call-sites in main/production code.
 * Those are intentionally deferred to the production JankStats work (investigation plan Fase 6);
 * here jank is attributed by screen, which already separates the startup regime from interaction.
 */
internal class JankStatsLogger : Application.ActivityLifecycleCallbacks {
    private val trackers = mutableMapOf<Activity, JankStats>()

    override fun onActivityResumed(activity: Activity) {
        val existing = trackers[activity]
        if (existing != null) {
            existing.isTrackingEnabled = true
            return
        }
        runCatching {
            val jankStats =
                JankStats.createAndTrack(activity.window) { frameData ->
                    if (frameData.isJank) {
                        Timber.d(
                            "Jank frame on %s: %d ms (states=%s)",
                            activity.localClassName,
                            frameData.frameDurationUiNanos / NANOS_PER_MILLI,
                            frameData.states,
                        )
                    }
                }
            // Register before the best-effort state tagging: if putState threw, the tracker would
            // be live but unrecorded, double-installing on the next resume.
            trackers[activity] = jankStats
            PerformanceMetricsState
                .getHolderForHierarchy(activity.window.decorView)
                .state
                ?.putState(STATE_SCREEN, activity::class.simpleName ?: activity.localClassName)
        }.onFailure { error ->
            // Debug-only dev diagnostic: Logcat is enough; don't surface a non-fatal for a tool.
            Timber.w(error, "Could not install jank diagnostics on %s", activity.localClassName)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        trackers[activity]?.isTrackingEnabled = false
    }

    override fun onActivityDestroyed(activity: Activity) {
        trackers.remove(activity)?.isTrackingEnabled = false
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val STATE_SCREEN = "screen"
