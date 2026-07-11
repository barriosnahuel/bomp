/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

/**
 * The surface that opened a Create flow. Tags `screen_view {add_sound}` and the `sound_add` event,
 * and [RECORD] additionally selects the `SoundSource.RECORDED` provenance persisted with the audio.
 *
 * Lives outside [AddButtonActivity] because the Activity is now only the share-sheet trampoline
 * (ADR 0024 D4): the internal sources are named by graph destinations that must not have to reach
 * into the trampoline to spell their own funnel.
 */
object AddSoundSource {
    const val SHARE = "share"
    const val IMPORT = "import"
    const val RECORD = "record"
}
