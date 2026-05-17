/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import java.util.concurrent.Executor

/**
 * Bridge between the Vault UI and `androidx.biometric:biometric`. Keeps the framework dependency
 * surface tiny so call-sites stay testable and the production wiring stays in one place.
 *
 * Auth scope is **per collection per invocation** by design (spec § 5): the prompt is fired every
 * time the user opens a private collection, the matching Add/Edit edit session, etc. No timed
 * session cache lives in this class — adding one would silently lower the security bar.
 *
 * Allowed authenticators are `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. The DEVICE_CREDENTIAL fallback
 * matters because the spec's "no biometric ≠ no privacy" stance still relies on the device lock
 * screen — when it exists, we prefer it over no gate at all. The "no protection" branch only kicks
 * in when neither is available; the UI surfaces a persistent warning in that case (§ 6).
 */
class BiometricGate(
    private val activity: FragmentActivity,
    private val biometricManager: BiometricManager = BiometricManager.from(activity),
    private val promptFactory: PromptFactory = DefaultPromptFactory,
) {
    /**
     * Snapshot of the device's biometric readiness, mapped to a closed enum so call-sites do not
     * leak [BiometricManager] constants into UI code.
     */
    fun status(): BiometricGateStatus =
        when (biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricGateStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricGateStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricGateStatus.NOT_ENROLLED
            else -> BiometricGateStatus.UNAVAILABLE
        }

    /**
     * Fires the biometric prompt and resolves [onResult] on the main thread.
     *
     * Prompt strings are passed in (not hardcoded) so the call-site controls localization and
     * tone — a Vault-card unlock uses different copy than a tagging-session unlock.
     */
    fun requestUnlock(
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (BiometricGateResult) -> Unit,
    ) {
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                // setNegativeButtonText is forbidden when DEVICE_CREDENTIAL is among the authenticators
                // (BiometricPrompt builds its own "Use PIN" affordance). The string is still captured for
                // tests that assert call-site copy intent.
                .also { ignored ->
                    negativeButtonText.length // keep ref reachable for future hardware-only path
                }.build()
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(BiometricGateResult.Granted)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    // User cancellation, lockout, "no device credential set up" — all collapse to
                    // a single Denied state. The UI never auto-retries; the user re-taps if they want.
                    onResult(BiometricGateResult.Denied(reason = errorCode))
                }

                override fun onAuthenticationFailed() {
                    // Soft fail: an unrecognized fingerprint / face. The framework keeps the prompt
                    // open and the user can retry — we do not surface this as a result yet.
                }
            }
        try {
            promptFactory.build(activity, callback).authenticate(info)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Throwable,
        ) {
            // Defensive: launching the prompt on an activity that died or has no fragment manager
            // can throw IllegalStateException. We log + surface Denied so the UI can recover.
            Tracker.log("vault.biometric_launch_failed=${e.javaClass.simpleName}")
            Tracker.track(RuntimeException("BiometricPrompt launch failed", e))
            onResult(BiometricGateResult.Denied(reason = -1))
        }
    }

    /** Test seam: lets tests replace [BiometricPrompt] construction with a fake. */
    fun interface PromptFactory {
        fun build(
            activity: FragmentActivity,
            callback: BiometricPrompt.AuthenticationCallback,
        ): BiometricPrompt
    }

    companion object {
        const val ALLOWED_AUTHENTICATORS: Int = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}

/** Closed mapping of device biometric readiness, used to drive UI affordances. */
enum class BiometricGateStatus {
    /** Strong biometric or device credential is enrolled and ready. */
    AVAILABLE,

    /** Device has no biometric hardware. May still have a PIN/password — see [NOT_ENROLLED]. */
    NO_HARDWARE,

    /** Hardware is present but the user hasn't enrolled biometrics nor set a screen lock. */
    NOT_ENROLLED,

    /** Catch-all for transient / unknown unavailability (security update pending, etc.). */
    UNAVAILABLE,
    ;

    val isProtected: Boolean get() = this == AVAILABLE
}

sealed interface BiometricGateResult {
    data object Granted : BiometricGateResult

    data class Denied(
        val reason: Int,
    ) : BiometricGateResult
}

private object DefaultPromptFactory : BiometricGate.PromptFactory {
    override fun build(
        activity: FragmentActivity,
        callback: BiometricPrompt.AuthenticationCallback,
    ): BiometricPrompt {
        val executor: Executor =
            androidx.core.content.ContextCompat
                .getMainExecutor(activity)
        return BiometricPrompt(activity, executor, callback)
    }
}
