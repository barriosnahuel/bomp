/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Whether a system file browser (SAF) can open [mimeType] files — the same intent every "import a file"
 * option launches. Some TV-like and managed builds ship without one; those options are hidden rather than
 * offered (ADR 0019 § Microphone-less devices). Needs the matching manifest `<queries>` entry, or it reads
 * false on API 30+ even when one exists.
 */
internal fun Context.canBrowseFiles(mimeType: String): Boolean =
    ActivityResultContracts
        .OpenDocument()
        .createIntent(this, arrayOf(mimeType))
        .resolveActivity(packageManager) != null
