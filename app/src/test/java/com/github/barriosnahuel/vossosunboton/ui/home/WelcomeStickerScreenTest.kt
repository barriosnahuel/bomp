/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.welcome.welcomeSticker
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class WelcomeStickerScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `welcome sticker variant renders play button and trailing label`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sound = welcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val playLabel = context.getString(R.string.app_play)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    durationMs = 14_000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {},
                    isWelcomeVariant = true,
                    borderOverride = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer),
                    trailingLabel = "-0:14",
                )
            }
        }

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(playLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("-0:14").assertIsDisplayed()
    }

    @Test
    fun `welcome variant exposes share but hides pin and edit affordances`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sound = welcomeSticker(context)
        val pinLabel = context.getString(R.string.app_pin)
        val unpinLabel = context.getString(R.string.app_unpin)
        val shareLabel = context.getString(R.string.app_share_chooser_title)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    durationMs = 14_000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {},
                    isWelcomeVariant = true,
                )
            }
        }

        // Pin / unpin icon is suppressed because onPinClick = null inside the welcome variant.
        assertThat(composeTestRule.onAllNodesWithContentDescription(pinLabel).fetchSemanticsNodes()).isEmpty()
        assertThat(composeTestRule.onAllNodesWithContentDescription(unpinLabel).fetchSemanticsNodes()).isEmpty()
        // Share stays visible — sharing the welcome message is positive word-of-mouth signal.
        composeTestRule.onNodeWithContentDescription(shareLabel).assertIsDisplayed()
    }
}
