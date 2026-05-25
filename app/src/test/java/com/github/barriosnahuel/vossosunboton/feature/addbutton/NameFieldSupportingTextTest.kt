/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.testSound
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Direct tests for the supporting-text slot under the New Bomp name field. The mutual exclusion
 * between the validation error and the duplicate-name hint lives here because the two states are
 * unreachable simultaneously through the production save flow (typing clears `nameError`); a
 * direct Composable test pins the precedence so a future supporting-text refactor can't silently
 * surface both at once.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class NameFieldSupportingTextTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val hintText
        get() = context.getString(R.string.app_addbutton_duplicate_name_hint)

    @Test
    fun `hint is hidden when an error is also present so messages do not fight for the slot`() {
        val match = testSound(name = "Bell", file = "bell.mp3")
        val errorMessage = context.getString(R.string.app_addbutton_name_is_required_error)

        composeTestRule.setContent {
            MaterialTheme {
                NameFieldSupportingText(
                    error = errorMessage,
                    nameLength = 0,
                    maxNameLength = 50,
                    duplicateMatch = match,
                    context = context,
                    tracker = FakeAnalyticsTracker(),
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(hintText).assertCountEquals(0)
    }

    @Test
    fun `hint is shown when there is no error and a duplicate match is present`() {
        val match = testSound(name = "Bell", file = "bell.mp3")

        composeTestRule.setContent {
            MaterialTheme {
                NameFieldSupportingText(
                    error = null,
                    nameLength = 4,
                    maxNameLength = 50,
                    duplicateMatch = match,
                    context = context,
                    tracker = FakeAnalyticsTracker(),
                )
            }
        }

        composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
    }
}
