/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Package visibility is enforced only on a real device (API 30+), never under Robolectric: this is the one
 * place that proves the manifest `<queries>` entry lets the recorder see the system file browser. Without
 * it the check reads false and mic-less devices lose "Import instead" even with a browser installed.
 */
@RunWith(AndroidJUnit4::class)
class FileBrowserVisibilityTest {
    @Test
    fun theSystemAudioFileBrowserIsVisibleToTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertThat(context.canBrowseFiles("audio/*")).isTrue()
    }
}
