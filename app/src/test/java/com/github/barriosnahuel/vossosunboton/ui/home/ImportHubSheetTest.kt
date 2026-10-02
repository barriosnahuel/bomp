/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    fun `hub offers exactly two paths, record then bring`() {
        setHub()

        val recordTop =
            composeTestRule
                .onNodeWithText("Record a Bomp")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val bringTop =
            composeTestRule
                .onNodeWithText("Bring in an audio you already have")
                .fetchSemanticsNode()
                .boundsInRoot.top

        assertThat(recordTop).isLessThan(bringTop)
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun `hub no longer offers the file-browser row`() {
        setHub()

        composeTestRule.onAllNodesWithText("Import audio from your device").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Find it on your phone").assertCountEquals(0)
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
    fun `tapping the bring row invokes onBringFromApps`() {
        var opened = false
        setHub(onBringFromApps = { opened = true })

        composeTestRule.onNodeWithText("Bring in an audio you already have").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bring in an audio you already have").performClick()
        composeTestRule.waitForIdle() // the row animates the sheet closed before invoking the callback

        assertThat(opened).isTrue()
    }

    private fun setHub(
        onRecord: () -> Unit = {},
        onBringFromApps: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                ImportHubSheet(
                    onRecord = onRecord,
                    onBringFromApps = onBringFromApps,
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
