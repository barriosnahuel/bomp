/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Forces the "there are bundled audios" state, which is what makes the Explore tab exist in the
 * bottom bar. Reflection because the flag is derived from the packaged catalogue, and a CI checkout
 * has no `model/src/debug/res/raw/` to derive it from.
 */
@Suppress("UNCHECKED_CAST")
internal fun SoundsViewModel.injectHasBundledSounds(value: Boolean) {
    SoundsViewModel::class.java
        .getDeclaredField("_hasBundledSounds")
        .also { it.isAccessible = true }
        // Safe: _hasBundledSounds is always MutableStateFlow<Boolean> — type parameter erased at runtime
        .let { (it.get(this) as MutableStateFlow<Boolean>).value = value }
}
