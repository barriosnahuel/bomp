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
import kotlinx.coroutines.flow.update

/**
 * Process-scoped cache of "this private collection was unlocked at least once during this session".
 *
 * **Lifetime:** identical to the OS process. Survives Activity recreate (rotation, theme change),
 * survives the user switching tabs, dies on process kill (low memory or explicit force-stop). The
 * cache is NOT persisted to disk on purpose — the privacy promise is that "the next time you open
 * the app from a cold start you need your fingerprint again", which matches the user's mental model
 * of session.
 *
 * **Why a singleton (vs a ViewModel field):** the cache must be readable from BOTH
 * [com.github.barriosnahuel.vossosunboton.feature.vault.VaultScreen] (in `LandingActivity`) and
 * [com.github.barriosnahuel.vossosunboton.feature.addbutton.AssignToCollectionsSection] (in
 * `AddButtonActivity`). The two activities have their own ViewModelStoreOwners, so a per-VM cache
 * would not be visible across them.
 *
 * **Default deny:** an id never seen returns `false`. There is no race between mark and read because
 * the [StateFlow] is conflated and the mark is synchronous.
 */
object VaultSessionState {
    private val unlockedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Observable view of the unlocked-ids set. Useful for Compose subscribers that re-render on changes. */
    val flow: StateFlow<Set<String>> = unlockedIds.asStateFlow()

    /**
     * Returns `true` if [collectionId] was unlocked at any earlier point in this process. The id
     * is the canonical [com.github.barriosnahuel.vossosunboton.model.Collection.id], not the name.
     */
    fun isUnlocked(collectionId: String): Boolean = collectionId in unlockedIds.value

    /** Records that [collectionId] has been authenticated. Idempotent. */
    fun markUnlocked(collectionId: String) {
        unlockedIds.update { it + collectionId }
    }

    /**
     * `true` if at least one collection has been unlocked in this session. Used by the Add/Edit
     * tagging surface to skip its own biometric reveal CTA — once any private collection has been
     * unlocked, the user has already proven session ownership and the tagging chips can surface
     * without a redundant prompt.
     */
    fun hasAnyUnlock(): Boolean = unlockedIds.value.isNotEmpty()

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun clearForTest() {
        unlockedIds.value = emptySet()
    }
}
