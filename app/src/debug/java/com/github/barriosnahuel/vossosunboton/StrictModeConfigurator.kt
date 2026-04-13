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
            .detectAll()
            .penaltyLog()

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
