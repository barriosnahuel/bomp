/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Status enum mapping tests for [BiometricGate].
 *
 * OWASP MASVS-AUTH-2 (device-level authentication policy) — covers the closed-enum surface that
 * UI consumers use to drive affordances; an unsupported status code must not surface as a
 * silently-trusted "AVAILABLE".
 */
internal class BiometricGateTest : AbstractRobolectricTest() {
    private fun gateWith(canAuthenticate: Int): BiometricGate {
        val activity = mockk<FragmentActivity>(relaxed = true)
        val manager = mockk<BiometricManager>()
        every { manager.canAuthenticate(any()) } returns canAuthenticate
        return BiometricGate(activity = activity, biometricManager = manager)
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (improper authentication state mapping). */
    @Test
    fun `BIOMETRIC_SUCCESS maps to AVAILABLE`() {
        val gate = gateWith(BiometricManager.BIOMETRIC_SUCCESS)
        assertThat(gate.status()).isEqualTo(BiometricGateStatus.AVAILABLE)
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (improper authentication state mapping). */
    @Test
    fun `NO_HARDWARE maps to NO_HARDWARE`() {
        val gate = gateWith(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
        assertThat(gate.status()).isEqualTo(BiometricGateStatus.NO_HARDWARE)
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (improper authentication state mapping). */
    @Test
    fun `HW_UNAVAILABLE maps to NO_HARDWARE`() {
        val gate = gateWith(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)
        assertThat(gate.status()).isEqualTo(BiometricGateStatus.NO_HARDWARE)
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (improper authentication state mapping). */
    @Test
    fun `NONE_ENROLLED maps to NOT_ENROLLED`() {
        val gate = gateWith(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        assertThat(gate.status()).isEqualTo(BiometricGateStatus.NOT_ENROLLED)
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (improper authentication state mapping — unknown codes are never AVAILABLE). */
    @Test
    fun `unrecognized return code falls through to UNAVAILABLE`() {
        val gate = gateWith(BiometricManager.BIOMETRIC_STATUS_UNKNOWN)
        assertThat(gate.status()).isEqualTo(BiometricGateStatus.UNAVAILABLE)
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (only AVAILABLE means protected — every other state is a gap to surface in UI). */
    @Test
    fun `isProtected is true only when status is AVAILABLE`() {
        assertThat(BiometricGateStatus.AVAILABLE.isProtected).isTrue()
        assertThat(BiometricGateStatus.NO_HARDWARE.isProtected).isFalse()
        assertThat(BiometricGateStatus.NOT_ENROLLED.isProtected).isFalse()
        assertThat(BiometricGateStatus.UNAVAILABLE.isProtected).isFalse()
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (prompt launch failure surfaces as Denied, never as silent grant). */
    @Test
    fun `requestUnlock surfaces a Denied result when PromptFactory throws`() {
        val activity = mockk<FragmentActivity>(relaxed = true)
        val manager = mockk<BiometricManager>(relaxed = true)
        val failingFactory =
            BiometricGate.PromptFactory { _: FragmentActivity, _: BiometricPrompt.AuthenticationCallback ->
                throw IllegalStateException("simulated activity-dead failure")
            }
        val gate = BiometricGate(activity = activity, biometricManager = manager, promptFactory = failingFactory)
        var capturedResult: BiometricGateResult? = null
        gate.requestUnlock(
            title = "t",
            subtitle = "s",
            negativeButtonText = "n",
        ) { result -> capturedResult = result }
        assertThat(capturedResult).isInstanceOf(BiometricGateResult.Denied::class.java)
    }
}
