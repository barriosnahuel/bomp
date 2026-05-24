/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data

import kotlinx.serialization.Serializable

@Serializable
internal data class StoredSound(
    val id: String,
    val name: String,
    val file: String? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val durationMs: Int? = null,
    // ADR 0012. `encodeDefaults = false` (see SoundsRepository.json) keeps the `true` default off
    // disk, so payloads written before this field decode as `true` — the desired post-update state
    // for every already-visible audio. The one-time migration flips private-only audios to `false`.
    val isVisibleInMySounds: Boolean = true,
)
