/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

// Smoke coverage for the Edit-mode preview-source resolution path with a non-null Sound.file.
// The actual StrictMode DiskReadViolation lives in dalvik's BlockGuard hooks (not simulated by
// Robolectric's JVM File ops), so the disk-on-main-thread guarantee is verified end-to-end by the
// instrumented AddButtonEditFlowTest on a real debug build with StrictModeConfigurator armed. This
// test proves the composable launches without throwing when the bug-prone branch is reachable.
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonScreenStrictModeTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `Edit mode with a non-null sound file launches without crashing`() {
        val intent =
            Intent(context, AddButtonActivity::class.java).apply {
                putExtra(LandingActivity.EXTRA_EDIT_SOUND, testSound("Existing sound", file = "existing.mp3"))
            }

        ActivityScenario.launch<AddButtonActivity>(intent).use { scenario ->
            composeTestRule.waitForIdle()
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }
}
