/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel =
        SoundsViewModel(
            ApplicationProvider.getApplicationContext(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    private fun SoundsViewModel.injectHasBundledSounds(value: Boolean) {
        SoundsViewModel::class.java
            .getDeclaredField("_hasBundledSounds")
            .also { it.isAccessible = true }
            // Safe: _hasBundledSounds is always MutableStateFlow<Boolean> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<Boolean>).value = value }
    }
}
