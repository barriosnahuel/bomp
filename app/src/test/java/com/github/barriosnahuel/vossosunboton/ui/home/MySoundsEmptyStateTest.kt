/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class MySoundsEmptyStateTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `MySoundsEmptyState renders headline and body copy`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val headline = context.getString(R.string.app_my_sounds_empty_headline)
        val body = context.getString(R.string.app_my_sounds_empty_body)

        composeTestRule.setContent {
            MaterialTheme { MySoundsEmptyState() }
        }

        composeTestRule.onNodeWithText(headline).assertIsDisplayed()
        composeTestRule.onNodeWithText(body).assertIsDisplayed()
    }
}
