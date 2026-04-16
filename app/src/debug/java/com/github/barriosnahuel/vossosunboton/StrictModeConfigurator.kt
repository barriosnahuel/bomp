package com.github.barriosnahuel.vossosunboton

import android.os.Build
import android.os.StrictMode
import com.github.barriosnahuel.vossosunboton.commons.android.error.Trackable
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
            .detectCustomSlowCalls()
            .detectNetwork()
            .penaltyLog()

    threadPolicyBuilder.detectResourceMismatches()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        threadPolicyBuilder.penaltyListener(Executors.newSingleThreadExecutor()) {
            trackable.track(StrictModeException(it))
        }
    }

    StrictMode.setThreadPolicy(threadPolicyBuilder.build())
}

private fun setupVirtualMachinePolicy(trackable: Trackable) {
    val vmPolicyBuilder =
        StrictMode.VmPolicy
            .Builder()
            // Explicit detectors — intentionally excludes detectUntaggedSockets()
            // because Firebase SDKs trigger it, and it is not actionable from app code.
            .detectActivityLeaks()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectLeakedRegistrationObjects()
            .detectFileUriExposure()
            .penaltyLog()

    vmPolicyBuilder.detectCleartextNetwork()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vmPolicyBuilder.detectContentUriWithoutPermission()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        vmPolicyBuilder.detectNonSdkApiUsage()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        vmPolicyBuilder.detectUnsafeIntentLaunch()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        vmPolicyBuilder.penaltyListener(Executors.newSingleThreadExecutor()) {
            trackable.track(StrictModeException(it))
        }
    }

    StrictMode.setVmPolicy(vmPolicyBuilder.build())
}

private class StrictModeException(
    violation: Throwable,
) : RuntimeException(violation)
