/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class LandingScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Cancel each VM's `viewModelScope` to stop the reactive `repo.sounds` collector added
        // in the post-PR-#1130 fix; otherwise it survives the test boundary.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        unmockkAll()
    }

    @Test
    fun `Explore nav item is not shown when no bundled sounds are available`() {
        val viewModel = givenAViewModel()
        viewModel.injectHasBundledSounds(false)

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Explore").assertDoesNotExist()
    }

    @Test
    fun `Explore nav item is shown when bundled sounds are available`() {
        val viewModel = givenAViewModel()
        viewModel.injectHasBundledSounds(true)

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Explore").assertIsDisplayed()
    }

    @Test
    fun `Search FAB is hidden when sound list is empty`() {
        val viewModel = givenAViewModel()
        viewModel.injectSounds(emptyList())

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Search").assertDoesNotExist()
    }

    @Test
    fun `Search FAB is hidden when sound list has 6 items`() {
        val viewModel = givenAViewModel()
        viewModel.injectSounds(stubSounds(count = 6))

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Search").assertDoesNotExist()
    }

    @Test
    fun `Search FAB is shown when sound list has 7 items`() {
        val viewModel = givenAViewModel()
        viewModel.injectSounds(stubSounds(count = 7))

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        // Wait for init's loadSounds to populate state — DataStore IO suspends off
        // UnconfinedTestDispatcher, so without this wait, reflection-based injection
        // races with the in-flight load and gets overwritten.
        runBlocking { vm.isInitialLoadComplete.first { it } }
        return vm
    }

    private fun SoundsViewModel.injectHasBundledSounds(value: Boolean) {
        SoundsViewModel::class.java
            .getDeclaredField("_hasBundledSounds")
            .also { it.isAccessible = true }
            // Safe: _hasBundledSounds is always MutableStateFlow<Boolean> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<Boolean>).value = value }
    }

    private fun SoundsViewModel.injectSounds(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            // Safe: _sounds is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }

    private fun stubSounds(count: Int): List<Sound> = List(count) { Sound(name = "stub-$it", file = "/tmp/stub-$it.mp3") }
}
