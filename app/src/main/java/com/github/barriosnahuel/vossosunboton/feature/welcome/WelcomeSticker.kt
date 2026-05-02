/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.welcome

import android.content.Context
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound

/**
 * Builds the welcome [Sound] used by the Sticker Cero feature. The instance is bundled-flavor
 * (`file == null`, `rawRes != 0`) so it shares the standard playback + share paths, but it is
 * NEVER persisted via `SoundsRepository` — visibility is gated by [WelcomeStickerStore] only.
 *
 * The audio resource is locale-qualified: `res/raw/app_welcome_sticker.mp3` is the English master,
 * `res/raw-es/app_welcome_sticker.mp3` overrides it for Spanish-locale devices. Android resolves
 * the right one automatically based on `Configuration.locale`.
 */
internal fun welcomeSticker(context: Context): Sound =
    Sound(
        name = context.getString(R.string.app_welcome_sticker_title),
        rawRes = R.raw.app_welcome_sticker,
    )

internal fun isWelcomeStickerName(
    name: String,
    context: Context,
): Boolean = name == context.getString(R.string.app_welcome_sticker_title)
