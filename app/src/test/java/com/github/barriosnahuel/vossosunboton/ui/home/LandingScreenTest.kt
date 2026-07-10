/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
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
    fun `overflow menu lists help, collections, share and about in order`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitForIdle()

        // "Help" shares its label ("See how it works") with the welcome footer, so it is addressed by
        // its stable tag rather than the ambiguous text; the other three labels are unique.
        val help = composeTestRule.onNodeWithTag(OVERFLOW_SEE_HOW_IT_WORKS_TAG)
        val collections = composeTestRule.onNodeWithText("Manage collections")
        val share = composeTestRule.onNodeWithText("Share app")
        val about = composeTestRule.onNodeWithText("About")

        // Each item is actually on screen (not merely composed) — guards the AGPLv3-required About entry
        // and Share against being pushed off the visible menu by future growth.
        help.assertIsDisplayed()
        collections.assertIsDisplayed()
        share.assertIsDisplayed()
        about.assertIsDisplayed()

        val helpTop = help.fetchSemanticsNode().boundsInRoot.top
        val collectionsTop = collections.fetchSemanticsNode().boundsInRoot.top
        val shareTop = share.fetchSemanticsNode().boundsInRoot.top
        val aboutTop = about.fetchSemanticsNode().boundsInRoot.top

        assertThat(helpTop).isLessThan(collectionsTop)
        assertThat(collectionsTop).isLessThan(shareTop)
        assertThat(shareTop).isLessThan(aboutTop)
    }

    @Test
    fun `add-a-Bomp FAB is shown on My Bomps`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertIsDisplayed()
    }

    @Test
    fun `add-a-Bomp FAB is hidden on the Vault tab`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.selectTab(AppTab.VAULT)
        composeTestRule.waitForIdle()

        // Vault's job is to listen — it stays FAB-less (design "regla por tab").
        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertDoesNotExist()
    }

    @Test
    fun `add-a-Bomp FAB is hidden on the Explore tab`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectHasBundledSounds(true)
        composeTestRule.waitForIdle()
        viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
        composeTestRule.waitForIdle()

        // Explore is a consumption surface — no create affordance there.
        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertDoesNotExist()
    }

    @Test
    fun `tapping the FAB opens the import Hub`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add a Bomp").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("How do you add one?").assertIsDisplayed()
    }

    @Test
    fun `open import Hub survives an Activity recreate`() {
        // § Stateful Composables: an opened sheet is durable progress — a recreate (rotation, theme,
        // system kill) must not silently rewind the user back to the list. `isHubVisible` is
        // rememberSaveable; StateRestorationTester emulates the save/restore round-trip.
        val viewModel = givenAViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add a Bomp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("How do you add one?").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("How do you add one?").assertIsDisplayed()
    }

    @Test
    fun `empty My Bomps hides the FAB and shows the Import CTA instead`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectSounds(emptyList())
        composeTestRule.waitForIdle()

        // Welcome-empty state owns the single imperative: the inline "Import" CTA, not the FAB
        // (design "estado vacío": fab=null + CTA Importar).
        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertDoesNotExist()
        composeTestRule.onNodeWithText("Import").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `non-empty My Bomps shows the FAB and no inline Import CTA`() {
        // Reverse of the empty-state swap: with audios present (a fresh install seeds the welcome
        // sticker, so the list is non-empty), the FAB is the create affordance and the empty-state
        // CTA must not appear.
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add a Bomp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import").assertDoesNotExist()
    }

    @Test
    fun `tapping the empty-state Import CTA opens the import Hub`() {
        val viewModel = givenAViewModel()

        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        viewModel.injectSounds(emptyList())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Import").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Same destination the FAB carries (design: "mismo sheet desde el CTA del estado vacío").
        composeTestRule.onNodeWithText("How do you add one?").assertIsDisplayed()
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

    @Suppress("UNCHECKED_CAST")
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
    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectLibrary(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            // Safe: allSoundsCache is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }

    // Drives the rendered list (`_sounds`) directly. A fresh install seeds it with the welcome
    // sticker, so emptying it is the only way to reach the welcome-empty state once init's load
    // has settled (inject AFTER waitForIdle, same cascade reasoning as injectLibrary).
    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            // Safe: _sounds is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }
}
