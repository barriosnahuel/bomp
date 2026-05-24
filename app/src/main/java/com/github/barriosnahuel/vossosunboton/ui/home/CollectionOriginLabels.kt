/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.model.Collection

/**
 * Resolves the origin tags for one audio from its collection memberships — the single source of the
 * rule shared by every surface that shows tags (My Sounds and the Vault list via `SoundsList`, plus
 * the search overlay via [searchResultCollectionLabels]).
 *
 * Each membership id in [collectionsByAudio] for [audioId] is resolved against [collectionsById]:
 * the system Vault collection substitutes its localized [systemCollectionLabel] for the stored
 * literal name; any other collection uses its own name; a stale id (collection deleted) is dropped.
 *
 * Privacy: a private collection's name is content that lives behind the Vault gate. While the Vault
 * session is locked ([vaultOpen] = false) those labels are suppressed, so a cross-tagged
 * (public AND private) audio — which the upstream membership gate keeps visible on public surfaces —
 * shows only its public tags and never leaks a private collection's name without authentication.
 * Once the Vault is unlocked, every membership renders.
 *
 * Pure (no Android/Compose deps) so it can be unit-tested without composing any surface.
 */
internal fun collectionOriginLabels(
    audioId: String,
    collectionsByAudio: Map<String, List<String>>,
    collectionsById: Map<String, Collection>,
    systemCollectionLabel: String,
    vaultOpen: Boolean,
): List<String> =
    collectionsByAudio[audioId].orEmpty().mapNotNull { id ->
        val collection = collectionsById[id]
        when {
            collection == null -> null
            !vaultOpen && collection.isPrivate -> null
            collection.isSystem -> systemCollectionLabel
            else -> collection.name
        }
    }
