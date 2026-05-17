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
 * Process-scoped session cache for unlocked Vault collections. Spec § 5 originally specified
 * per-collection-per-invocation auth, but post-launch usability feedback (PR review) flipped
 * the policy to "per-collection, cached for the lifetime of the process" — see decisions handoff
 * 2026-05-17.
 *
 * The cache must:
 * 1. Start empty (fresh process).
 * 2. Persist a `markUnlocked` between reads inside the same process.
 * 3. Reset between tests via `clearForTest()` (a process-singleton would otherwise leak across them).
 * 4. Expose `hasAnyUnlock()` so other surfaces (Add/Edit tagging) can short-circuit their own gate
 *    when the user has already authenticated in the session.
 */
internal class VaultSessionStateTest : AbstractRobolectricTest() {
    @After
    fun tearDown() {
        VaultSessionState.clearForTest()
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (default-deny: an unrecognised id must never read as unlocked). */
    @Test
    fun `fresh state reports nothing unlocked`() {
        assertThat(VaultSessionState.isUnlocked("any-id")).isFalse()
        assertThat(VaultSessionState.hasAnyUnlock()).isFalse()
    }

    /** OWASP MASVS-AUTH-2 (mark+check round-trip — the affirmative path of the session contract). */
    @Test
    fun `markUnlocked makes that id readable as unlocked`() {
        VaultSessionState.markUnlocked("baul")
        assertThat(VaultSessionState.isUnlocked("baul")).isTrue()
        assertThat(VaultSessionState.hasAnyUnlock()).isTrue()
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (markUnlocked must not leak across collection ids). */
    @Test
    fun `markUnlocked does not falsely unlock a different id`() {
        VaultSessionState.markUnlocked("baul")
        assertThat(VaultSessionState.isUnlocked("other")).isFalse()
    }

    @Test
    fun `clearForTest resets every unlocked id`() {
        VaultSessionState.markUnlocked("baul")
        VaultSessionState.markUnlocked("family")
        VaultSessionState.clearForTest()
        assertThat(VaultSessionState.hasAnyUnlock()).isFalse()
        assertThat(VaultSessionState.isUnlocked("baul")).isFalse()
        assertThat(VaultSessionState.isUnlocked("family")).isFalse()
    }
}
