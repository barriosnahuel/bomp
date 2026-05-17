/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Grouping for one or more [Sound]s. Identity is the immutable [id].
 *
 * Two access scopes coexist: [CollectionAccess.PUBLIC] (visible on the My Sounds filter row,
 * `bompeable`) and [CollectionAccess.PRIVATE] (locked under the Vault tab, biometric-gated
 * per-collection, listen-only). The audio↔collection relation is N:M — a sound can belong to
 * any number of public and/or private collections.
 *
 * The system-seeded "Baúl" collection has [isSystem] = true and [CollectionAccess.PRIVATE]; the
 * UI prevents deletion of system collections.
 */
@Parcelize
data class Collection(
    val id: String,
    val name: String,
    val profile: CollectionProfile,
    val isSystem: Boolean = false,
    val createdAt: Long? = null,
    /**
     * Ids of the [Sound]s assigned to this collection. Exposed as a list (not a set) for
     * stable iteration order — UIs that render audios in "added order" need it.
     */
    val audioIds: List<String> = emptyList(),
) : Parcelable {
    val isPrivate: Boolean get() = profile.access == CollectionAccess.PRIVATE
    val isPublic: Boolean get() = profile.access == CollectionAccess.PUBLIC
    val audioCount: Int get() = audioIds.size
}

/**
 * Five orthogonal axes from the spec § 3.1. Each preset (Vault, Recetas, Genérica pública) is just
 * a combination of these values. The MVP fully implements [access] and [playbackUI]; the remaining
 * axes are persisted so future presets can ship without a data migration.
 */
@Parcelize
data class CollectionProfile(
    val access: CollectionAccess = CollectionAccess.PUBLIC,
    val requiredMetadata: CollectionRequiredMetadata = CollectionRequiredMetadata.NONE,
    val playbackUI: CollectionPlaybackUI = CollectionPlaybackUI.DEFAULT,
    val shareability: CollectionShareability = CollectionShareability.BOMPEABLE,
    val autoCategorize: CollectionAutoCategorize = CollectionAutoCategorize.OFF,
) : Parcelable {
    companion object {
        /** Default for new public collections — opens to the filter chip row on My Sounds. */
        val GENERIC_PUBLIC =
            CollectionProfile(
                access = CollectionAccess.PUBLIC,
                requiredMetadata = CollectionRequiredMetadata.NONE,
                playbackUI = CollectionPlaybackUI.DEFAULT,
                shareability = CollectionShareability.BOMPEABLE,
                autoCategorize = CollectionAutoCategorize.OFF,
            )

        /** Default for the Vault tab — listen-only, biometric-gated, immersive UI. */
        val VAULT =
            CollectionProfile(
                access = CollectionAccess.PRIVATE,
                requiredMetadata = CollectionRequiredMetadata.NONE,
                playbackUI = CollectionPlaybackUI.IMMERSIVE,
                shareability = CollectionShareability.LISTEN_ONLY,
                autoCategorize = CollectionAutoCategorize.OFF,
            )
    }
}

enum class CollectionAccess { PUBLIC, PRIVATE }

enum class CollectionRequiredMetadata { NONE, PHOTO_AND_DATE }

enum class CollectionPlaybackUI { DEFAULT, IMMERSIVE, KITCHEN_MODE }

enum class CollectionShareability { BOMPEABLE, LISTEN_ONLY }

enum class CollectionAutoCategorize { OFF, KEYWORDS_ON_DEVICE }
