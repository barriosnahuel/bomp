/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

internal class RecorderDraftBannerTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows the unsaved-recording prompt`() {
        composeTestRule.setContent { AppTheme { RecorderDraftBanner(onContinue = {}, onDiscard = {}) } }

        composeTestRule.onNodeWithText("You have an unsaved recording").assertIsDisplayed()
    }

    @Test
    fun `tapping continue invokes onContinue`() {
        var continued = false
        composeTestRule.setContent { AppTheme { RecorderDraftBanner(onContinue = { continued = true }, onDiscard = {}) } }

        composeTestRule.onNodeWithText("Continue").performClick()

        assertThat(continued).isTrue()
    }

    @Test
    fun `tapping discard invokes onDiscard`() {
        var discarded = false
        composeTestRule.setContent { AppTheme { RecorderDraftBanner(onContinue = {}, onDiscard = { discarded = true }) } }

        composeTestRule.onNodeWithText("Discard").performClick()

        assertThat(discarded).isTrue()
    }
}
