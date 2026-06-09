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
 * Debug-only frame diagnostics + a hard gate on frozen frames: installs [JankStats] per Activity,
 * attributes each frame to its screen, logs janky frames, and crashes on a frozen frame
 * ([FrozenFrameGate]) so a main-thread block fails loud like a StrictMode violation. Decision +
 * rationale: `docs/adr/0016-jankstats-frozen-frame-crash-gate.md`.
 *
 * Never ships to release (debug source set, `debugImplementation` dep). The crash fires in manual /
 * real-device debug but is **suppressed under instrumentation** ([armFrozenFrameCrash]) — the test
 * emulator's own starvation fakes frozen frames a real device doesn't. Install failures surface via
 * [Tracker] (a dead diagnostic must not look like "no jank"). Per-phase marks (`list_scroll`, …) are
 * deferred to the production JankStats work; here jank is attributed by screen.
 */
internal class JankStatsLogger : Application.ActivityLifecycleCallbacks {
    private val trackers = mutableMapOf<Activity, JankStats>()

    /** One gate across all activities, so the frozen counter is the whole-session count. */
    private val frozenFrameGate = FrozenFrameGate()

    /** Crash only outside instrumentation: the test emulator's starvation fakes frozen frames a real device won't (ADR 0016). */
    private val armFrozenFrameCrash = !isRunningUnderInstrumentation()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        runCatching {
            val jankStats =
                JankStats.createAndTrack(activity.window) { frameData ->
                    val durationMs = frameData.frameDurationUiNanos / NANOS_PER_MILLI
                    // Feed every frame (not only janky ones) so each screen's window anchors on its first paint.
                    if (armFrozenFrameCrash &&
                        frozenFrameGate.onFrame(frameData.frameStartNanos / NANOS_PER_MILLI, durationMs, frameData.states)
                    ) {
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
                ?.putState(JANK_SCREEN_STATE_KEY, activity::class.simpleName ?: activity.localClassName)
            Timber.d(
                "Jank diagnostics installed on %s (frozen-frame crash gate %s)",
                activity.localClassName,
                if (armFrozenFrameCrash) "armed" else "log-only — under instrumentation",
            )
        }.onFailure { error ->
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
        // Stop tracking before dropping the ref: a teardown frame could otherwise still reach the gate.
        trackers.remove(activity)?.isTrackingEnabled = false
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
     * The gate fired: record a non-fatal (static message; per-frame context as breadcrumbs) then kill —
     * the `StrictModeConfigurator.reportViolation` shape. The throw is posted to the main looper because
     * the JankStats callback runs on a background thread and would swallow a direct throw.
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

/** True under AndroidX instrumentation. Reflection because `androidx.test` is androidTest-only (absent in a manual debug launch). */
private fun isRunningUnderInstrumentation(): Boolean =
    runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess

/** Static, searchable crash title (`"JankStats: frozen frame…"`); per-frame context rides as breadcrumbs, like StrictMode. */
private class FrozenFrameException :
    RuntimeException(
        "JankStats: frozen frame exceeded ${FROZEN_FRAME_THRESHOLD_MS}ms (debug jank gate)",
    )
