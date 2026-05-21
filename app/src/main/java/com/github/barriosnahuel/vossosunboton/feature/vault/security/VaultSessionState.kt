/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault.security

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped "Vault is open" flag.
 *
 * **Lifetime:** identical to the OS process. Survives Activity recreate (rotation, theme change),
 * survives the user switching tabs, dies on process kill (low memory or explicit force-stop). The
 * flag is NOT persisted to disk on purpose — the privacy promise is that "the next time you open
 * the app from a cold start you need your fingerprint again".
 *
 * **Why a singleton (vs a ViewModel field):** the flag must be readable from BOTH
 * [com.github.barriosnahuel.vossosunboton.feature.vault.VaultScreen] (in `LandingActivity`) and
 * [com.github.barriosnahuel.vossosunboton.feature.addbutton.AssignToCollectionsSection] (in
 * `AddButtonActivity`). The two activities have their own ViewModelStoreOwners, so a per-VM cache
 * would not be visible across them.
 *
 * **Why per-Vault (vs per-collection):** the first iteration cached per collection id. Usability
 * feedback flipped that — re-asking the biometric prompt every time the user navigated to a
 * different private collection (or back to the same one) felt punitive. The privacy boundary is
 * "this app, this session" — one prompt is enough to honour that.
 */
object VaultSessionState {
    private val opened = MutableStateFlow(false)

    /** Observable view of the open/closed state. Compose subscribers re-render when it flips. */
    val flow: StateFlow<Boolean> = opened.asStateFlow()

    /** `true` once the user has authenticated for the Vault during this process. */
    fun isVaultOpen(): Boolean = opened.value

    /** Records a successful biometric grant (or unprotected-device fallback) for the Vault. */
    fun markVaultOpen() {
        opened.value = true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun clearForTest() {
        opened.value = false
    }
}
