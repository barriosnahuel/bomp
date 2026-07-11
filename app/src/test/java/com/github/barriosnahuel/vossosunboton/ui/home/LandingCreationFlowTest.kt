/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The creation/edit flows as graph destinations (ADR 0024 D4): opening them from each surface, and the
 * back semantics that motivated the migration — back must walk the flow back a step, never tear it down.
 *
 * The recorder destination renders its permission priming here (Robolectric grants no RECORD_AUDIO),
 * which is enough to assert *that the destination is on screen*; capture behavior itself is
 * `RecorderViewModelTest`'s job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class LandingCreationFlowTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val createdViewModels = mutableListOf<SoundsViewModel>()
    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private lateinit var originalController: PlayerController

    @Before
    fun setUp() {
        // Swap the controller wholesale rather than mockkObject-ing the factory: the naming and recorder
        // destinations both collect `playbackState` for their preview transport, and a partially-stubbed
        // singleton throws on the first un-stubbed call.
        originalController = PlayerControllerFactory.instance
        PlayerControllerFactory.instance =
            mockk(relaxed = true) {
                every { playbackState } returns playbackStateFlow
            }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        PlayerControllerFactory.instance = originalController
        unmockkAll()
    }

    @Test
    fun `the Hub's record row opens the recorder as a destination, not a separate screen`() {
        givenLandingWithASound()

        openHubAndRecord()

        // The recorder is on screen (its permission priming, un-granted under Robolectric) and Landing's
        // chrome is gone: it is a full destination, not an overlay.
        composeTestRule.onNodeWithText(RECORDER_PRIMING_CTA).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun `back from the recorder with nothing recorded returns to the tab`() {
        givenLandingWithASound()
        openHubAndRecord()

        composeTestRule.onNodeWithText(RECORDER_PRIMING_CTA).assertIsDisplayed()
        pressBack()

        // Landing's FAB is back — the pop landed on the tab, not on some intermediate step.
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).assertIsDisplayed()
        composeTestRule.onNodeWithText(RECORDER_PRIMING_CTA).assertDoesNotExist()
    }

    @Test
    fun `editing an audio from the list opens naming in edit mode, pre-filled with its name`() {
        givenLandingWithASound()

        openEditFor(SOUND_NAME)

        composeTestRule.onNodeWithText(SAVE_CHANGES).assertIsDisplayed()
        // The existing name is pre-populated: this is a rename, not a blank Create.
        composeTestRule.onNodeWithText(SOUND_NAME).assertIsDisplayed()
    }

    @Test
    fun `back from naming returns to the list instead of leaving the app`() {
        givenLandingWithASound()
        openEditFor(SOUND_NAME)
        composeTestRule.onNodeWithText(SAVE_CHANGES).assertIsDisplayed()

        pressBack()

        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).assertIsDisplayed()
        composeTestRule.onNodeWithText(SAVE_CHANGES).assertDoesNotExist()
    }

    private fun givenLandingWithASound() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent {
            // Captured from the composition so pressBack() can drive the same dispatcher NavDisplay
            // registers its onBack with — a test-only shortcut would not prove the graph handles back.
            backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            AppTheme { LandingScreen(viewModel) }
        }
        composeTestRule.waitForIdle()
        val sound = testSound(SOUND_NAME, file = "$SOUND_NAME.mp3")
        // Both: `_sounds` is what the list renders, `allSoundsCache` is where the naming destination
        // resolves the edited audio's id. Seeding only the former would render a row whose Edit action
        // opens a destination that cannot find its audio.
        viewModel.injectSounds(listOf(sound))
        viewModel.injectLibrary(listOf(sound))
        composeTestRule.waitForIdle()
    }

    private fun openHubAndRecord() {
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(HUB_RECORD_ROW).performClick()
        composeTestRule.waitForIdle()
    }

    private fun openEditFor(name: String) {
        composeTestRule.onNodeWithText(name).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(RENAME_ACTION).performClick()
        composeTestRule.waitForIdle()
    }

    /** Drives the real system back dispatcher, so `NavDisplay`'s onBack (not a test shortcut) decides. */
    private fun pressBack() {
        composeTestRule.runOnUiThread {
            backDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private var backDispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null

    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        // Bounded: a load that never lands fails here in seconds instead of hanging until CI's
        // no-output timeout.
        runBlocking { withTimeout(LOAD_TIMEOUT_MS) { vm.isInitialLoadComplete.first { it } } }
        return vm
    }

    // Drives the rendered list. Injected after the first composition so init's loadSounds cascade
    // (which repopulates it) has already settled.
    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }

    // Drives the global library the naming destination looks an edited audio up in.
    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectLibrary(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
        const val SOUND_NAME = "Existing Bomp"
        const val FAB_DESCRIPTION = "Add a Bomp"
        const val HUB_RECORD_ROW = "Record a Bomp"
        const val RENAME_ACTION = "Rename"
        const val SAVE_CHANGES = "Save changes"
        const val RECORDER_PRIMING_CTA = "Allow microphone"
    }
}
