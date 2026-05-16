/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import androidx.core.content.FileProvider

/**
 * Debug-variant-only [FileProvider] for instrumented tests. Lives in the SUT manifest (not the
 * test APK) so AddButtonActivity can resolve `content://` URIs minted by androidTest code.
 *
 * Distinct subclass — not the production `androidx.core.content.FileProvider` — because the
 * manifest merger keys `<provider>` elements by `android:name`, and we need a parallel
 * authority (`...androidtest.fileprovider`) alongside the production one (`...fileprovider`).
 *
 * Paths come from the `<meta-data android:name="android.support.FILE_PROVIDER_PATHS">` element
 * on this provider's `<provider>` declaration in the debug manifest — same mechanism the
 * production FileProvider uses, single source of truth.
 */
internal class AndroidTestFileProvider : FileProvider()
