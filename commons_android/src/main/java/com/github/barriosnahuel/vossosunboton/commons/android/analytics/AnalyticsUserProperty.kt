/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Canonical Firebase user property names emitted by [AnalyticsTracker.setUserProperty]. The two `lifetime_*`
 * properties also double as keys into the [CounterStore], so the same constant must be used on both sides — that's
 * exactly what this object enforces. The regression net (`AnalyticsCoverageMatrixTest`) enumerates these and fails
 * when a new entry is added without a matching call-site test.
 */
object AnalyticsUserProperty {
    /** Snapshot of the user-created sound count. Updated on add/delete. */
    const val CURRENT_SOUNDS = "current_sounds"

    /** Snapshot of the pinned sound count. Updated on togglePin. */
    const val CURRENT_PINNED = "current_pinned"

    /** Snapshot — number of public (My Sounds) user collections. System collections excluded. */
    const val CURRENT_COLLECTIONS_PUBLIC = "current_collections_public"

    /** Snapshot — number of Vault (private) user collections. Seeded "Baúl" system collection excluded. */
    const val CURRENT_COLLECTIONS_PRIVATE = "current_collections_private"

    /** Snapshot — number of audios that belong to at least one user collection. Engagement signal. */
    const val CURRENT_AUDIOS_IN_COLLECTIONS = "current_audios_in_collections"

    /**
     * Snapshot — number of audios that live ONLY in private (Vault) collections (tagged to a
     * private collection and zero public ones). Distinct from [CURRENT_AUDIOS_IN_COLLECTIONS]:
     * this is the true size of the user's Vault, the private-content engagement signal.
     */
    const val CURRENT_VAULT_AUDIOS = "current_vault_audios"

    /** Monotonic counter — total times the user shared a sound. */
    const val LIFETIME_SHARES = "lifetime_shares"

    /** Monotonic counter — total times the user played a sound. */
    const val LIFETIME_PLAYS = "lifetime_plays"

    /** Monotonic counter — total collections the user has created over the lifetime of the install. */
    const val LIFETIME_COLLECTION_CREATES = "lifetime_collection_creates"

    /** Monotonic counter — total collections the user has deleted over the lifetime of the install. */
    const val LIFETIME_COLLECTION_DELETES = "lifetime_collection_deletes"

    /** Monotonic counter — total collection renames over the lifetime of the install. */
    const val LIFETIME_COLLECTION_RENAMES = "lifetime_collection_renames"

    /** Monotonic counter — total assign + unassign toggles via the assign-to-collection sheet. */
    const val LIFETIME_COLLECTION_ASSIGNS = "lifetime_collection_assigns"

    /** Monotonic counter — total successful Vault biometric unlocks. */
    const val LIFETIME_VAULT_UNLOCKS = "lifetime_vault_unlocks"

    val ALL: List<String> =
        listOf(
            CURRENT_SOUNDS,
            CURRENT_PINNED,
            CURRENT_COLLECTIONS_PUBLIC,
            CURRENT_COLLECTIONS_PRIVATE,
            CURRENT_AUDIOS_IN_COLLECTIONS,
            CURRENT_VAULT_AUDIOS,
            LIFETIME_SHARES,
            LIFETIME_PLAYS,
            LIFETIME_COLLECTION_CREATES,
            LIFETIME_COLLECTION_DELETES,
            LIFETIME_COLLECTION_RENAMES,
            LIFETIME_COLLECTION_ASSIGNS,
            LIFETIME_VAULT_UNLOCKS,
        )
}
