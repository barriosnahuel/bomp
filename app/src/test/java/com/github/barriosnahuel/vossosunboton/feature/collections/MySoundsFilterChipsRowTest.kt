/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import org.junit.Rule
import org.junit.Test

internal class MySoundsFilterChipsRowTest : AbstractRobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * Regression for v2.4.0 launch bug: the filter chip row was hidden when the user had no public
     * collections yet. With no public collections + a hidden row, the user had no surface to even
     * create their first one — the "+ Nueva" affordance was unreachable. The row must always
     * render the "+ Nueva" chip on My Sounds; "Todo" stays selected by default.
     */
    @Test
    fun rowRendersTodoAndNewChipsEvenWithNoPublicCollections() {
        composeRule.setContent {
            MySoundsFilterChipsRow(
                publicCollections = emptyList(),
                activeFilterId = null,
                onFilterSelected = {},
                onCreateRequested = {},
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.app_my_sounds_filter_all)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.app_my_sounds_filter_chip_new)).assertIsDisplayed()
    }
}
