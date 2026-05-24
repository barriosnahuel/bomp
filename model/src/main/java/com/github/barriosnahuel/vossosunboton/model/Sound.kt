/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model

import android.os.Parcelable
import androidx.annotation.RawRes
import kotlinx.parcelize.Parcelize

/**
 * Represents a sound (a.k.a. a button of this app).
 *
 * [id] is the stable internal identity: `"bundled:$rawRes"` for packaged sounds (stable
 * cross-install) and a random UUID for user-created ones, minted once at save time. Identity
 * comparisons — repository upsert, playback/progress caches, Compose list keys — use [id]; [name]
 * is for display and textual search only, and two sounds may legitimately share a name. See
 * ADR 0008.
 */
@Parcelize
data class Sound(
    val id: String,
    val name: String,
    val file: String?,
    @field:RawRes @param:RawRes val rawRes: Int,
    val isPlaying: Boolean,
    val isFavorite: Boolean = false,
    val dateAdded: Long? = null,
    val isPinned: Boolean = false,
    /**
     * Whether this sound appears in the My Sounds "Todo" botonera. Explicit per-sound state
     * (ADR 0012): the only input to that surface's visibility. Independent of collection membership
     * — a sound can be visible here *and* live in a private Vault collection at the same time.
     * Defaults to `true`; a sound created straight into the Vault flow is the only born-`false` case.
     */
    val isVisibleInMySounds: Boolean = true,
) : Parcelable {
    constructor(id: String, name: String, file: String?) : this(id, name, file, 0, false, false)
    constructor(
        name: String,
        @RawRes rawRes: Int = 0,
    ) : this("bundled:$rawRes", name, null, rawRes, false, false)

    /**
     * Indicates whether or not this sound is bundled in the app or is a user's custom sound.
     */
    fun isBundled() = file == null
}
