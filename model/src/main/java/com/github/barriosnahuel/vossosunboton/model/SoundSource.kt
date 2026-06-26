/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model

import kotlinx.serialization.Serializable

/**
 * Where a [Sound] came from. **Internal** provenance only — no UI branches on it yet (ADR 0019); it
 * exists so a recorded audio is distinguishable from an imported one without a second data migration
 * when a UI treatment is eventually decided.
 *
 * [IMPORTED] is the persisted default: every pre-recorder user audio arrived via the share sheet or
 * the import picker, so a payload written before this field decodes as [IMPORTED] (the desired
 * post-update state). [BUNDLED] is derivable (`file == null`) and set on packaged audio; [RECORDED]
 * is set by the in-app recorder save path. An unknown value coerces to [IMPORTED] (the repository's
 * `coerceInputValues = true`), so a forward-incompatible payload can never break decoding.
 */
@Serializable
enum class SoundSource {
    RECORDED,
    IMPORTED,
    BUNDLED,
}
