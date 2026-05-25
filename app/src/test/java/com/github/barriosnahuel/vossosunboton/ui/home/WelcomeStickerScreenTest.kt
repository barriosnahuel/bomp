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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.welcome.welcomeSticker
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
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

    @Test
    fun `welcome variant supports swipe-left to dismiss`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sound = welcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        var deleteCallCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    durationMs = 14_000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = { deleteCallCount++ },
                    onPinClick = {},
                    isWelcomeVariant = true,
                )
            }
        }

        composeTestRule.onNodeWithText(title).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertThat(deleteCallCount).isEqualTo(1)
    }

    @Test
    fun `welcome variant swipe-right is a no-op (no callback fired)`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sound = welcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        var deleteCallCount = 0
        var pinCallCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    durationMs = 14_000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = { deleteCallCount++ },
                    onPinClick = { pinCallCount++ },
                    isWelcomeVariant = true,
                )
            }
        }

        composeTestRule.onNodeWithText(title).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertThat(deleteCallCount).isEqualTo(0)
        assertThat(pinCallCount).isEqualTo(0)
    }

    @Test
    fun `welcome variant long-press opens dropdown with only the Delete item`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sound = welcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val deleteLabel = context.getString(R.string.app_delete)
        val editLabel = context.getString(R.string.app_edit)

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

        composeTestRule.onNodeWithText(title).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(deleteLabel).assertIsDisplayed()
        // Edit must NOT appear — the welcome is a system anchor, not an editable user sound.
        assertThat(composeTestRule.onAllNodesWithText(editLabel).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `welcome variant Delete dropdown item invokes the dismiss callback`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sound = welcomeSticker(context)
        val title = context.getString(R.string.app_welcome_sticker_title)
        val deleteLabel = context.getString(R.string.app_delete)
        var deleteCallCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    durationMs = 14_000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = { deleteCallCount++ },
                    onPinClick = {},
                    isWelcomeVariant = true,
                )
            }
        }

        composeTestRule.onNodeWithText(title).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(deleteLabel).performClick()
        composeTestRule.waitForIdle()

        assertThat(deleteCallCount).isEqualTo(1)
    }
}
