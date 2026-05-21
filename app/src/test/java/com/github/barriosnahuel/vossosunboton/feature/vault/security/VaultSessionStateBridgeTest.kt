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
 * Cross-surface contract: once the Vault has been opened during this process, ANY private surface
 * (the Vault tab list, the Add/Edit private chips, an immersive playback view) short-circuits its
 * own gate and trusts the session. The test pins that contract without spinning up the Compose
 * harness for each surface.
 */
internal class VaultSessionStateBridgeTest : AbstractRobolectricTest() {
    @After
    fun tearDown() {
        VaultSessionState.clearForTest()
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (cross-surface contract: a single open vault enables all private surfaces). */
    @Test
    fun `opening the vault enables every downstream surface that checks the session`() {
        // Pre-condition: nothing open.
        assertThat(VaultSessionState.isVaultOpen()).isFalse()

        // The Vault tab marks open on biometric grant.
        VaultSessionState.markVaultOpen()

        // The Add/Edit private chips, the immersive view, and any future private surface all see
        // the same flipped flag instead of asking again.
        assertThat(VaultSessionState.isVaultOpen()).isTrue()
    }
}
