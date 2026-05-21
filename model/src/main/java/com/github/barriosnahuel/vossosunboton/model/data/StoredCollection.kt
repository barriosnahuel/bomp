/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data

import kotlinx.serialization.Serializable

/**
 * Persistence-tier mirror of [com.github.barriosnahuel.vossosunboton.model.Collection]. Lives in
 * the same `:model` package as [StoredSound] so the repository can encode both with `Json`. All
 * fields have safe defaults so future schema additions decode against older JSON without a
 * migration — same posture as [StoredSound].
 */
@Serializable
internal data class StoredCollection(
    val id: String,
    val name: String,
    val access: StoredCollectionAccess = StoredCollectionAccess.PUBLIC,
    val requiredMetadata: StoredCollectionRequiredMetadata = StoredCollectionRequiredMetadata.NONE,
    val playbackUI: StoredCollectionPlaybackUI = StoredCollectionPlaybackUI.DEFAULT,
    val shareability: StoredCollectionShareability = StoredCollectionShareability.BOMPEABLE,
    val autoCategorize: StoredCollectionAutoCategorize = StoredCollectionAutoCategorize.OFF,
    val isSystem: Boolean = false,
    val createdAt: Long? = null,
    /**
     * IDs of the [StoredSound]s belonging to this collection. Stored inside the collection record
     * (vs a separate junction list) so a single decoded `List<StoredCollection>` snapshot already
     * answers both "what collections exist" and "which audios belong to each" without a second
     * read. Audio deletion sweeps stale ids via [SoundsRepository.delete].
     */
    val audioIds: List<String> = emptyList(),
)

@Serializable
internal enum class StoredCollectionAccess { PUBLIC, PRIVATE }

@Serializable
internal enum class StoredCollectionRequiredMetadata { NONE, PHOTO_AND_DATE }

@Serializable
internal enum class StoredCollectionPlaybackUI { DEFAULT, IMMERSIVE, KITCHEN_MODE }

@Serializable
internal enum class StoredCollectionShareability { BOMPEABLE, LISTEN_ONLY }

@Serializable
internal enum class StoredCollectionAutoCategorize { OFF, KEYWORDS_ON_DEVICE }
