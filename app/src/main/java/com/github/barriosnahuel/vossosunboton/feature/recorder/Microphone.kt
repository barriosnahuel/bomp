/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Whether the device has a microphone at all — hardware presence, independent of the `RECORD_AUDIO`
 * grant. The manifest declares the mic optional, so a mic-less device installs the app and every path
 * into capture must check this first: docs/adr/0019-in-app-bomp-recorder.md § Microphone-less devices.
 */
internal fun Context.hasMicrophone(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

/**
 * Whether a system file browser can open [mimeType] files — the same intent the import escape launches.
 * Needs the matching manifest `<queries>` entry, or it reads false on API 30+ even when one exists.
 */
internal fun Context.canBrowseFiles(mimeType: String): Boolean =
    ActivityResultContracts
        .OpenDocument()
        .createIntent(this, arrayOf(mimeType))
        .resolveActivity(packageManager) != null
