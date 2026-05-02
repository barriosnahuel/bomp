/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data

import kotlinx.serialization.Serializable

@Serializable
internal data class StoredSound(
    val name: String,
    val file: String? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val durationMs: Int? = null,
)
