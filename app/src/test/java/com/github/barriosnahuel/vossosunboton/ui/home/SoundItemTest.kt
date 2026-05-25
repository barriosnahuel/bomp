/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class SoundItemTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `swipe right triggers pin callback`() {
        var pinCallCount = 0
        val sound = testSound("test sound", file = "test.mp3")

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = { pinCallCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("test sound").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertThat(pinCallCount).isEqualTo(1)
    }

    // Key regression test for rememberUpdatedState:
    // Without it, the second swipe calls the stale closure (isPinned=false) → re-pins instead of unpinning.
    @Test
    fun `swipe right twice calls onPinClick with updated sound state each time`() {
        val capturedIsPinned = mutableListOf<Boolean>()
        var sound by mutableStateOf(testSound("test sound", file = "test.mp3"))

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {
                        capturedIsPinned.add(sound.isPinned)
                        sound = sound.copy(isPinned = !sound.isPinned)
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("test sound").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test sound").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertThat(capturedIsPinned).hasSize(2)
        assertThat(capturedIsPinned[0]).isFalse()
        assertThat(capturedIsPinned[1]).isTrue()
    }

    @Test
    fun `swipe left triggers delete callback`() {
        var deleteCallCount = 0
        val sound = testSound("test sound", file = "test.mp3")

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = { deleteCallCount++ },
                    onPinClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("test sound").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertThat(deleteCallCount).isEqualTo(1)
    }

    @Test
    fun `when idle with duration shows total duration`() {
        val sound = testSound("bell", file = "bell.mp3")

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    durationMs = 62000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("1:02").assertExists()
    }

    @Test
    fun `when playing shows elapsed time not remaining`() {
        val sound = testSound("bell", file = "bell.mp3").copy(isPlaying = true)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = PlaybackProgress(positionMs = 30000, durationMs = 62000),
                    durationMs = 62000,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("0:30").assertExists()
    }

    @Test
    fun `pinned and unpinned card have the same height`() {
        var pinnedHeight = 0
        var unpinnedHeight = 0
        val sound = testSound("test sound", file = "test.mp3")

        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    Box(modifier = Modifier.onGloballyPositioned { pinnedHeight = it.size.height }) {
                        SoundItem(
                            sound = sound.copy(isPinned = true),
                            playbackProgress = null,
                            onPlayClick = {},
                            onSeek = {},
                            onShareClick = {},
                            onDelete = {},
                            onPinClick = {},
                        )
                    }
                    Box(modifier = Modifier.onGloballyPositioned { unpinnedHeight = it.size.height }) {
                        SoundItem(
                            sound = sound.copy(isPinned = false),
                            playbackProgress = null,
                            onPlayClick = {},
                            onSeek = {},
                            onShareClick = {},
                            onDelete = {},
                            onPinClick = {},
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertThat(pinnedHeight).isEqualTo(unpinnedHeight)
    }

    @Test
    fun `swipe right on bundled sound triggers pin callback`() {
        var pinCallCount = 0
        val sound = testSound("bundled sound", rawRes = 1)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = { pinCallCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("bundled sound").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertThat(pinCallCount).isEqualTo(1)
    }

    @Test
    fun `swipe left on bundled sound does not trigger delete callback`() {
        var deleteCallCount = 0
        val sound = testSound("bundled sound", rawRes = 1)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = { deleteCallCount++ },
                    onPinClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("bundled sound").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertThat(deleteCallCount).isEqualTo(0)
    }

    @Test
    fun `pin button is visible for bundled sound`() {
        val sound = testSound("bundled sound", rawRes = 1)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Pin to top").assertExists()
    }

    // Regression net for the dropdown decoupling: when both Edit and Delete are wired (custom
    // sound), long-press must show BOTH items. The welcome variant only wires Delete and the
    // matching test in WelcomeStickerScreenTest covers that single-item case.
    @Test
    fun `custom-sound long-press opens dropdown with both Edit and Delete`() {
        val sound = testSound("custom sound", file = "custom.mp3")

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = {},
                    onPinClick = {},
                    onEditClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("custom sound").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Rename").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }
}
