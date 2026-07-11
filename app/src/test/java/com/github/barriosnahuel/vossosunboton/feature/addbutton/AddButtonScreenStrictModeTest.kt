/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

// Smoke coverage for the Edit-mode preview-source resolution path with a non-null Sound.file.
// The actual StrictMode DiskReadViolation lives in dalvik's BlockGuard hooks (not simulated by
// Robolectric's JVM File ops), so the disk-on-main-thread guarantee is verified end-to-end by the
// instrumented AddButtonEditFlowTest on a real debug build with StrictModeConfigurator armed. This
// test proves the composable renders without throwing when the bug-prone branch is reachable.
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonScreenStrictModeTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val screenMounted = mutableStateOf(true)

    @After
    fun disposeScreen() = disposeAddButtonScreen(composeTestRule, screenMounted)

    @Test
    fun `Edit mode with a non-null sound file renders without crashing`() {
        val host = composeTestRule.activity
        composeTestRule.setContent {
            AppTheme {
                if (screenMounted.value) {
                    AddButtonScreen(
                        context = host,
                        mode = AddButtonMode.Edit(testSound("Existing sound", file = "existing.mp3")),
                        onSaved = {},
                        onNavigateUp = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save_changes)).assertIsDisplayed()
    }
}
