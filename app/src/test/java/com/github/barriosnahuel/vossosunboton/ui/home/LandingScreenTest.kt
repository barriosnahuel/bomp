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
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class LandingScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Deterministically stop the reactive `repo.sounds` collector each VM starts in `init`.
        // A bare `cancel()` is fire-and-forget: the collector can outlive the
        // test, parked on the process-singleton DataStore. `cancelAndJoinAll()` joins until it
        // unwinds — see ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        unmockkAll()
    }

    @Test
    fun `Explore nav item is not shown when no bundled sounds are available`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        // Inject AFTER the first composition + collections-collector cascade so the value
        // sticks. The `collectionsRepo.collections.collect { ... loadSounds() }` collector in
        // SoundsViewModel re-runs loadSounds() on its first emission, which overwrites
        // `_hasBundledSounds` to the repo's real value (true under the debug build's bundled
        // audios). Injecting after waitForIdle ensures that cascade has already settled.
        viewModel.injectHasBundledSounds(false)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Explore").assertDoesNotExist()
    }

    @Test
    fun `Explore nav item is shown when bundled sounds are available`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectHasBundledSounds(true)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Explore").assertIsDisplayed()
    }

    @Test
    fun `Search action is shown in the top app bar even when the library is empty`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectLibrary(emptyList())
        composeTestRule.waitForIdle()

        // Search now lives in the top app bar as persistent chrome — present on every tab regardless
        // of library size (it replaced the count-gated Search FAB).
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun `overflow menu offers the share-app action`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Share Bomp").assertIsDisplayed()
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

    // Drives the global library (allSoundsCache) to a known value. The debug build's loadSounds
    // primes allSoundsCache with the bundled catalog, so injecting AFTER waitForIdle (once that
    // cascade settles, same reasoning as injectHasBundledSounds) is the only way to empty it.
    private fun SoundsViewModel.injectLibrary(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            // Safe: allSoundsCache is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }
}
