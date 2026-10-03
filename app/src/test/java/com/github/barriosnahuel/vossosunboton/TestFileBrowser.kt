/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows

/**
 * Declares a system file browser (SAF) able to open audio, so "import a file" options render. Robolectric
 * resolves no activity for any intent unless declared, which by default makes every test a device without
 * one — call this from `@Before` in tests that tap those options. Uses the same deprecated setter as
 * ScreenLockSettingsTest: Robolectric has no lightweight replacement.
 */
@Suppress("DEPRECATION")
internal fun declareAudioFileBrowser() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = ActivityResultContracts.OpenDocument().createIntent(context, arrayOf("audio/*"))
    val resolveInfo =
        ResolveInfo().apply {
            activityInfo =
                ActivityInfo().apply {
                    packageName = "com.android.documentsui"
                    name = "com.android.documentsui.picker.PickActivity"
                }
        }
    Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
}

/** Undoes [declareAudioFileBrowser] for a test of the no-file-browser path inside a class that declares one. */
@Suppress("DEPRECATION")
internal fun withdrawAudioFileBrowser() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = ActivityResultContracts.OpenDocument().createIntent(context, arrayOf("audio/*"))
    Shadows.shadowOf(context.packageManager).removeResolveInfosForIntent(intent, "com.android.documentsui")
}
