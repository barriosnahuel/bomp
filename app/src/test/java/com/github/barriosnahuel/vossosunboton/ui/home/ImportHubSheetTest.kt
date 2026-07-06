/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class ImportHubSheetTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `hub shows title and the record row`() {
        setHub()

        composeTestRule.onNodeWithText("How do you add one?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Record a Bomp").assertIsDisplayed()
    }

    @Test
    fun `hub orders the rows record then bring then import`() {
        setHub()

        val recordTop =
            composeTestRule
                .onNodeWithText("Record a Bomp")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val bringTop =
            composeTestRule
                .onNodeWithText("Bring audios from other apps")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val importTop =
            composeTestRule
                .onNodeWithText("Import audio from your device")
                .fetchSemanticsNode()
                .boundsInRoot.top

        assertThat(recordTop).isLessThan(bringTop)
        assertThat(bringTop).isLessThan(importTop)
    }

    @Test
    fun `tapping the record row invokes onRecord`() {
        var recorded = false
        setHub(onRecord = { recorded = true })

        composeTestRule.onNodeWithText("Record a Bomp").performClick()
        composeTestRule.waitForIdle() // the row animates the sheet closed before invoking onRecord

        assertThat(recorded).isTrue()
    }

    @Test
    fun `tapping the bring-from-apps row invokes onBringFromApps`() {
        var opened = false
        setHub(onBringFromApps = { opened = true })

        composeTestRule.onNodeWithText("Bring audios from other apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bring audios from other apps").performClick()
        composeTestRule.waitForIdle() // the row animates the sheet closed before invoking the callback

        assertThat(opened).isTrue()
    }

    @Test
    fun `tapping the import row invokes onImport`() {
        var imported = false
        setHub(onImport = { imported = true })

        composeTestRule.onNodeWithText("Import audio from your device").performClick()
        composeTestRule.waitForIdle() // the row animates the sheet closed before invoking onImport

        assertThat(imported).isTrue()
    }

    private fun setHub(
        onImport: () -> Unit = {},
        onRecord: () -> Unit = {},
        onBringFromApps: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                ImportHubSheet(
                    onImport = onImport,
                    onRecord = onRecord,
                    onBringFromApps = onBringFromApps,
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
