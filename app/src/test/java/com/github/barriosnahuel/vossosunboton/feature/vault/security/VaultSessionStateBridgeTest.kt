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
 * Cross-surface bridging contract: when ANY private collection has been unlocked during this
 * process, the Add/Edit tagging surface should treat the user as session-authenticated and
 * skip its own reveal CTA. The test guards the contract that
 * [com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonScreen] relies on without
 * having to spin up an actual Compose harness for the section.
 */
internal class VaultSessionStateBridgeTest : AbstractRobolectricTest() {
    @After
    fun tearDown() {
        VaultSessionState.clearForTest()
    }

    /** OWASP MASVS-AUTH-2 / CWE-287 (cross-surface contract: hasAnyUnlock reflects any prior session unlock). */
    @Test
    fun `unlocking the Baul makes hasAnyUnlock return true for downstream surfaces`() {
        // VaultScreen marks the unlocked collection on biometric grant. AssignToCollectionsSection
        // queries hasAnyUnlock at composition to start its private block already revealed.
        VaultSessionState.markUnlocked("system:baul")
        assertThat(VaultSessionState.hasAnyUnlock()).isTrue()
    }

    /**
     * OWASP MASVS-AUTH-2 (the Add/Edit surface uses its own session sentinel marker — the bridge
     * contract is "any unlocked id makes hasAnyUnlock true", regardless of what id was used).
     */
    @Test
    fun `the Add Edit surface sentinel also flips hasAnyUnlock`() {
        VaultSessionState.markUnlocked("addbutton-session")
        assertThat(VaultSessionState.hasAnyUnlock()).isTrue()
        assertThat(VaultSessionState.isUnlocked("addbutton-session")).isTrue()
    }
}
