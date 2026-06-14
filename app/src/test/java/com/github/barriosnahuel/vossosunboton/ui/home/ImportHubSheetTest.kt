/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun `hub shows title and the import row`() {
        setHub()

        composeTestRule.onNodeWithText("How do you add one?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import audio from your device").assertIsDisplayed()
    }

    @Test
    fun `tapping the import row invokes onImport`() {
        var imported = false
        setHub(onImport = { imported = true })

        composeTestRule.onNodeWithText("Import audio from your device").performClick()
        composeTestRule.waitForIdle() // the row animates the sheet closed before invoking onImport

        assertThat(imported).isTrue()
    }

    @Test
    fun `record row is present, badged Soon, and disabled`() {
        setHub()

        composeTestRule.onNodeWithText("Record your first Bomp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Soon").assertIsDisplayed()
        // Inert by design: the merged row node carries the disabled semantic so TalkBack announces it.
        composeTestRule.onNodeWithText("Record your first Bomp").assertIsNotEnabled()
    }

    @Test
    fun `hub does not offer the see-how-it-works row yet`() {
        // PR4 guard: the onboarding entry point ("Ver cómo funciona") only joins the Hub once
        // onboarding exists. Until then it must not leak into this sheet.
        setHub()

        composeTestRule
            .onNodeWithText("how it works", substring = true, ignoreCase = true)
            .assertDoesNotExist()
    }

    private fun setHub(
        onImport: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                ImportHubSheet(onImport = onImport, onDismiss = onDismiss)
            }
        }
        composeTestRule.waitForIdle()
    }
}
