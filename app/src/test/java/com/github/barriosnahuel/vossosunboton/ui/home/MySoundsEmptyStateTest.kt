/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.google.common.truth.Truth.assertThat
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
            MaterialTheme { MySoundsEmptyState(onImportClick = {}, onShowOnboarding = {}) }
        }

        composeTestRule.onNodeWithText(headline).assertIsDisplayed()
        composeTestRule.onNodeWithText(body).assertIsDisplayed()
    }

    @Test
    fun `MySoundsEmptyState Import CTA invokes the callback when tapped`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cta = context.getString(R.string.app_my_sounds_empty_cta)
        var imports = 0

        composeTestRule.setContent {
            MaterialTheme { MySoundsEmptyState(onImportClick = { imports++ }, onShowOnboarding = {}) }
        }

        composeTestRule.onNodeWithText(cta).assertIsDisplayed()
        composeTestRule.onNodeWithText(cta).assertHasClickAction()
        composeTestRule.onNodeWithText(cta).performClick()

        assertThat(imports).isEqualTo(1)
    }

    @Test
    fun `MySoundsEmptyState secondary invokes onShowOnboarding when tapped`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val secondary = context.getString(R.string.app_my_sounds_empty_secondary)
        var tours = 0

        composeTestRule.setContent {
            MaterialTheme { MySoundsEmptyState(onImportClick = {}, onShowOnboarding = { tours++ }) }
        }

        composeTestRule.onNodeWithText(secondary).assertHasClickAction()
        composeTestRule.onNodeWithText(secondary).performClick()

        assertThat(tours).isEqualTo(1)
    }
}
