/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault.security

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/**
 * Process-scoped session cache for the Vault unlock. Spec § 5 originally specified
 * per-collection-per-invocation auth, but post-launch usability feedback flipped the policy:
 *
 *   1) First pass: per-collection, cached for the lifetime of the process.
 *   2) Second pass (this iteration): per-VAULT, cached for the lifetime of the process.
 *      One biometric on first entry to the Vault tab, no more prompts in the session.
 *
 * The state must:
 *  - Start closed (fresh process).
 *  - Persist `markVaultOpen` between reads inside the same process.
 *  - Reset between tests via `clearForTest()` (a process-singleton would otherwise leak across them).
 *  - Expose `isVaultOpen()` so any surface (Vault tab, Add/Edit private chips, immersive view) can
 *    short-circuit its own gate when the session is already authenticated.
 */
internal class VaultSessionStateTest : AbstractRobolectricTest() {
    @After
    fun tearDown() {
        VaultSessionState.clearForTest()
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (default-deny: a fresh process is never unlocked). */
    @Test
    fun `fresh state reports the vault as closed`() {
        assertThat(VaultSessionState.isVaultOpen()).isFalse()
    }

    /** OWASP MASVS-AUTH-2 (mark+check round-trip — the affirmative path of the session contract). */
    @Test
    fun `markVaultOpen flips isVaultOpen to true`() {
        VaultSessionState.markVaultOpen()
        assertThat(VaultSessionState.isVaultOpen()).isTrue()
    }

    @Test
    fun `clearForTest resets the vault to closed`() {
        VaultSessionState.markVaultOpen()
        VaultSessionState.clearForTest()
        assertThat(VaultSessionState.isVaultOpen()).isFalse()
    }
}
