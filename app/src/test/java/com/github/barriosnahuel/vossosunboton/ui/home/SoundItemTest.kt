package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class SoundItemTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `swipe right triggers pin callback`() {
        var pinCallCount = 0
        val sound = Sound("test sound", file = "test.mp3")

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
        var sound by mutableStateOf(Sound("test sound", file = "test.mp3"))

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
        val sound = Sound("test sound", file = "test.mp3")

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
    fun `bundled sound does not trigger any callback on swipe`() {
        var pinCallCount = 0
        var deleteCallCount = 0
        val sound = Sound("bundled sound", rawRes = 1)

        composeTestRule.setContent {
            MaterialTheme {
                SoundItem(
                    sound = sound,
                    playbackProgress = null,
                    onPlayClick = {},
                    onSeek = {},
                    onShareClick = {},
                    onDelete = { deleteCallCount++ },
                    onPinClick = { pinCallCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("bundled sound").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("bundled sound").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertThat(pinCallCount).isEqualTo(0)
        assertThat(deleteCallCount).isEqualTo(0)
    }
}
