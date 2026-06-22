/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Canonical Firebase user property names emitted by [AnalyticsTracker.setUserProperty]. Every `lifetime_*` property
 * also doubles as a key into the [CounterStore], so the same constant must be used on both sides — that's exactly
 * what this object enforces. Names must satisfy Firebase's constraints (≤ 24 chars, allowed charset, no reserved
 * prefix); `AnalyticsUserPropertyNameTest` fails the build otherwise — an over-length name is dropped silently and
 * never reaches BigQuery. `AnalyticsCoverageMatrixTest` enumerates these and fails when a new entry is added without
 * a matching call-site test.
 */
object AnalyticsUserProperty {
    /** Snapshot of the user-created sound count. Updated on add/delete. */
    const val CURRENT_SOUNDS = "current_sounds"

    /** Snapshot of the pinned sound count. Updated on togglePin. */
    const val CURRENT_PINNED = "current_pinned"

    /** Snapshot — number of public (My Sounds) user collections. System collections excluded. */
    const val CURRENT_COLLECTIONS_PUBLIC = "current_public_colls"

    /** Snapshot — number of Vault (private) user collections. Seeded "Baúl" system collection excluded. */
    const val CURRENT_COLLECTIONS_PRIVATE = "current_private_colls"

    /** Snapshot — number of audios that belong to at least one user collection. Engagement signal. */
    const val CURRENT_AUDIOS_IN_COLLECTIONS = "current_audios_in_colls"

    // Four mutually-exclusive snapshots that classify every user audio by where it lives. They sum
    // to the total user-created (non-bundled) audio count, so the dashboard reads as a pie of how
    // people organize. Axes: scope (public / vault) × organization (default / custom). Cross-tagged
    // audios (in both a public and a private collection) count as public — the public surface
    // preserves their visibility (spec § 3.1).

    /** Public audios with no collection at all — the raw My Sounds list (the public "default"). */
    const val CURRENT_PUBLIC_DEFAULT = "current_public_default"

    /** Public audios filed into at least one public collection (includes cross-tagged). */
    const val CURRENT_PUBLIC_CUSTOM = "current_public_custom"

    /** Private-only audios whose only private collection is the seeded Baúl (the Vault "default"). */
    const val CURRENT_VAULT_DEFAULT = "current_vault_default"

    /** Private-only audios filed into at least one user-created (non-system) private collection. */
    const val CURRENT_VAULT_CUSTOM = "current_vault_custom"

    /** Monotonic counter — total times the user shared a sound. */
    const val LIFETIME_SHARES = "lifetime_shares"

    /** Monotonic counter — total times the user played a sound. */
    const val LIFETIME_PLAYS = "lifetime_plays"

    /** Monotonic counter — total collections the user has created over the lifetime of the install. */
    const val LIFETIME_COLLECTION_CREATES = "lifetime_coll_creates"

    /** Monotonic counter — total collections the user has deleted over the lifetime of the install. */
    const val LIFETIME_COLLECTION_DELETES = "lifetime_coll_deletes"

    /** Monotonic counter — total collection renames over the lifetime of the install. */
    const val LIFETIME_COLLECTION_RENAMES = "lifetime_coll_renames"

    /** Monotonic counter — total assign + unassign toggles via the assign-to-collection sheet. */
    const val LIFETIME_COLLECTION_ASSIGNS = "lifetime_coll_assigns"

    /** Monotonic counter — total successful Vault biometric unlocks. */
    const val LIFETIME_VAULT_UNLOCKS = "lifetime_vault_unlocks"

    val ALL: List<String> =
        listOf(
            CURRENT_SOUNDS,
            CURRENT_PINNED,
            CURRENT_COLLECTIONS_PUBLIC,
            CURRENT_COLLECTIONS_PRIVATE,
            CURRENT_AUDIOS_IN_COLLECTIONS,
            CURRENT_PUBLIC_DEFAULT,
            CURRENT_PUBLIC_CUSTOM,
            CURRENT_VAULT_DEFAULT,
            CURRENT_VAULT_CUSTOM,
            LIFETIME_SHARES,
            LIFETIME_PLAYS,
            LIFETIME_COLLECTION_CREATES,
            LIFETIME_COLLECTION_DELETES,
            LIFETIME_COLLECTION_RENAMES,
            LIFETIME_COLLECTION_ASSIGNS,
            LIFETIME_VAULT_UNLOCKS,
        )
}
