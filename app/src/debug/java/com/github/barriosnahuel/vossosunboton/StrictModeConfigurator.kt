/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import com.github.barriosnahuel.vossosunboton.commons.android.error.Trackable
import timber.log.Timber
import java.util.concurrent.Executors

internal object StrictModeConfigurator {
    fun initializeWithDefaults(trackable: Trackable) {
        setupThreadPolicy(trackable)
        setupVirtualMachinePolicy(trackable)
    }
}

private fun setupThreadPolicy(trackable: Trackable) {
    val threadPolicyBuilder =
        StrictMode.ThreadPolicy
            .Builder()
            .detectAll()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        threadPolicyBuilder.penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
            reportViolation(trackable, violation)
        }
    }

    StrictMode.setThreadPolicy(threadPolicyBuilder.build())
}

private fun setupVirtualMachinePolicy(trackable: Trackable) {
    val vmPolicyBuilder =
        StrictMode.VmPolicy
            .Builder()
            .detectAll()

    // detectNonSdkApiUsage() is intentionally NOT in detectAll() upstream (opt-in only). Add it back so we keep
    // visibility on greylist API hits (Firebase reflection, Compose dispatchOnScrollChanged, etc. — all filtered
    // via KNOWN_THIRD_PARTY_VIOLATIONS).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        vmPolicyBuilder.detectNonSdkApiUsage()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        vmPolicyBuilder.penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
            reportViolation(trackable, violation)
        }
    }

    StrictMode.setVmPolicy(vmPolicyBuilder.build())
}

/**
 * Single decision point for both ThreadPolicy and VmPolicy violations:
 *  - filtered third-party noise → silent (not in logcat, not in Crashlytics)
 *  - everything else → uniform `Timber.tag("StrictMode").e(...)` line in logcat (DebugTree),
 *    breadcrumb in Crashlytics (ErrorTrackerTree), a non-fatal via `Tracker.track`, AND a process kill so the
 *    dev cannot miss it during local runs.
 *
 * `penaltyLog()` and `penaltyDeath()` are intentionally NOT set on the builders. `penaltyDeath()` would fire
 * before `penaltyListener` and bypass our filter, killing the process on every Compose scroll / Espresso
 * reflection / framework finalizer. The kill happens here, after the filter, so only genuinely unknown
 * violations crash the app. The trade-off: a new SDK / framework version that introduces a previously unseen
 * third-party violation will break debug runs and the instrumented suite until a matcher is added — by design.
 *
 * The kill is dispatched to the main thread because `penaltyListener` runs on a background executor whose
 * default uncaught-exception handler swallows throws. The main looper rethrows to the system handler, which
 * terminates the process.
 */
private fun reportViolation(
    trackable: Trackable,
    violation: Throwable,
) {
    if (!shouldReportToCrashlytics(violation)) return
    Timber.tag(LOG_TAG).e(violation, "policy violation: %s", violation.javaClass.simpleName)
    trackable.track(StrictModeException(violation))
    Handler(Looper.getMainLooper()).post { throw StrictModeException(violation) }
}

/**
 * Decide whether a StrictMode violation is worth surfacing. Returns `false` for known third-party violations
 * triaged in the Fase 0 audit (push-me-backlog/backlog/readme.md). The list is intentionally narrow: any violation
 * not matched here flows to logcat + Crashlytics so genuine new regressions still raise a flag.
 *
 * Disk-read / slow-call violations from Firebase init are not on this list because they are prevented at the
 * call-site via `StrictMode.allowThreadDiskReads()` in `AnalyticsTrackerProvider.createTracker`.
 */
private fun shouldReportToCrashlytics(violation: Throwable): Boolean {
    val frames = violation.stackTrace
    return KNOWN_THIRD_PARTY_VIOLATIONS.none { matcher ->
        frames.any { frame ->
            frame.className.startsWith(matcher.classNamePrefix) &&
                (matcher.methodNameContains == null || matcher.methodNameContains in frame.methodName)
        }
    }
}

/**
 * Match a stack frame by class-name prefix and (optionally) method name. The method-name filter exists for cases
 * where a class-name prefix would over-match — e.g. `android.os.StrictMode` appears in the stack of every
 * violation (the framework itself reports them), so filtering by class alone would silence the entire policy.
 */
private data class KnownThirdPartyViolation(
    val classNamePrefix: String,
    val library: String,
    val methodNameContains: String? = null,
)

private const val LOG_TAG = "StrictMode"

private val KNOWN_THIRD_PARTY_VIOLATIONS =
    listOf(
        // Firebase Analytics (play-services-measurement) reflects Ldalvik/system/VMStack;->getStackClass2()
        // during init via the auto-registered Firebase ContentProvider. NonSdkApi greylist hit, no public AOSP /
        // Firebase tracker found as of 2026-05.
        KnownThirdPartyViolation(
            classNamePrefix = "com.google.android.gms.internal.measurement",
            library = "Firebase Analytics",
        ),
        // Firebase Datatransport (CCT) opens untagged HTTP connections via OkHttp to upload Analytics events.
        // Not actionable from app code. Before switching to detectAll() the original configurator deliberately
        // excluded detectUntaggedSockets() for the same reason.
        KnownThirdPartyViolation(
            classNamePrefix = "com.google.android.datatransport",
            library = "Firebase Datatransport CCT (untagged sockets)",
        ),
        // Firebase Crashlytics fetches its settings endpoint over HTTP at startup; the request opens an untagged
        // socket via the platform OkHttp shim (`com.android.okhttp.*`). Same root cause as the Datatransport
        // matcher above but with a different observable top-of-app-frame, depending on which Firebase product
        // wins the race to send first on a given run.
        KnownThirdPartyViolation(
            classNamePrefix = "com.google.firebase",
            library = "Firebase Crashlytics / FCM (untagged sockets)",
        ),
        // Jetpack Compose UI 1.11.0 (BOM 2026.04.01) reflects Landroid/view/ViewTreeObserver;->dispatchOnScrollChanged()
        // on every scroll. No fix mentioned in Compose UI 1.9.x → 1.11.x release notes, no public tracker found
        // as of 2026-05. Re-evaluate when bumping the BOM.
        KnownThirdPartyViolation(
            classNamePrefix = "androidx.compose.ui.platform.AndroidComposeView",
            library = "Jetpack Compose UI",
        ),
        // Espresso reflects MessageQueue and InputManager methods to drive UI automation. Only fires during
        // connectedDebugAndroidTest — never in builds shipped to users — but the test JVM shares this configurator,
        // so it pollutes Crashlytics during local test runs unless filtered.
        KnownThirdPartyViolation(
            classNamePrefix = "androidx.test.espresso",
            library = "Espresso (androidTest only)",
        ),
        // Android framework finalizer leak: SurfaceControl.finalize() reports an unreleased InsetsSourceControl
        // during window teardown on certain API levels. Reproduced from `enableEdgeToEdge()` which we use in
        // LandingActivity and AddButtonActivity — the controls are owned by the framework, not by us.
        KnownThirdPartyViolation(
            classNamePrefix = "android.view.SurfaceControl",
            library = "Android framework (InsetsSourceControl finalize)",
        ),
        // Android framework triggers detectExplicitGc on every Activity teardown:
        // ActivityThread.performDestroyActivity → StrictMode.decrementExpectedActivityCount →
        // System.runFinalization → Runtime.gc. The framework hits its own detector. Match by method name
        // because the class prefix `android.os.StrictMode` appears in every violation's stack and would
        // silence the entire policy.
        KnownThirdPartyViolation(
            classNamePrefix = "android.os.StrictMode",
            library = "Android framework (Activity destroy GC)",
            methodNameContains = "decrementExpectedActivityCount",
        ),
    )

private class StrictModeException(
    violation: Throwable,
) : RuntimeException(violation)
