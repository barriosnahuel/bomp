/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model

/**
 * Builds a [Sound] with a deterministic [Sound.id] for tests — `custom:<name>` for user sounds,
 * `bundled:<rawRes>` for packaged ones — so identity-keyed assertions stay stable across runs.
 * For the rare test that needs two same-named sounds with distinct ids, call the [Sound]
 * constructor directly with explicit ids.
 */
internal fun testSound(
    name: String,
    file: String? = null,
    rawRes: Int = 0,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    dateAdded: Long? = null,
    isPinned: Boolean = false,
    isVisibleInMySounds: Boolean = true,
): Sound =
    Sound(
        id = if (file != null) "custom:$name" else "bundled:$rawRes",
        name = name,
        file = file,
        rawRes = rawRes,
        isPlaying = isPlaying,
        isFavorite = isFavorite,
        dateAdded = dateAdded,
        isPinned = isPinned,
        isVisibleInMySounds = isVisibleInMySounds,
    )
