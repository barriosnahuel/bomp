/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.home.cancelAndJoinAll
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class ImmersiveListenScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fakeTracker = FakeAnalyticsTracker()
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(fakeTracker)
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Suppress("LongParameterList")
    private fun launchScreen(
        title: String = "La risa de la abuela",
        collectionLabel: String = "Caro",
        dateLabel: String? = "24 DEC 2024",
        positionMs: Int = 42_000,
        durationMs: Int? = 98_000,
        isPlaying: Boolean = false,
        onPlayPause: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                ImmersiveListenScreen(
                    title = title,
                    collectionLabel = collectionLabel,
                    dateLabel = dateLabel,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    progressFraction = 0.42f,
                    onPlayPause = onPlayPause,
                    onBack = onBack,
                )
            }
        }
    }

    @Test
    fun `renders the four facts a vault audio carries`() {
        launchScreen()
        // Collection sits in the fixed top bar; name/date/duration are in the scrollable body, so
        // scroll to them (a no-op when the screen already fits) before asserting.
        composeTestRule.onNodeWithText("CARO").assertIsDisplayed()
        composeTestRule.onNodeWithText("La risa de la abuela").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("24 DEC 2024").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("0:42").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1:38").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `renders the listen mode eyebrow and the listen-only footer`() {
        launchScreen()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_vault_immersive_mode_label).uppercase())
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_vault_immersive_listen_only_label).uppercase())
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `does not render a caption or subtitle the vault has no data for`() {
        // Regression for the spec: the Claude Design mock shows a hand-written caption and a free
        // text subtitle; the Vault stores neither, so listen mode must never render them.
        launchScreen()
        composeTestRule.onNodeWithText("mamá, abuela y yo").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cuando le contamos lo del casamiento").assertDoesNotExist()
    }

    @Test
    fun `transport shows pause while playing and play while paused`() {
        launchScreen(isPlaying = true)
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_pause)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `transport shows play while not playing`() {
        launchScreen(isPlaying = false)
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_play)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping the transport invokes onPlayPause`() {
        var tapped = false
        launchScreen(onPlayPause = { tapped = true })
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_play)).performScrollTo().performClick()
        assertTrue(tapped)
    }

    @Test
    fun `tapping back invokes onBack`() {
        var backed = false
        launchScreen(onBack = { backed = true })
        // Back lives in the fixed top bar (not the scrollable body), so no scroll needed.
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.app_vault_immersive_back)).performClick()
        assertTrue(backed)
    }

    @Test
    fun `renders without a date when dateLabel is null`() {
        launchScreen(dateLabel = null)
        composeTestRule.onNodeWithText("La risa de la abuela").performScrollTo().assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `host emits the vault_listen screen view on entry`() {
        val viewModel =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += viewModel

        composeTestRule.setContent {
            AppTheme { ImmersiveListenHost(viewModel = viewModel, soundId = "any-id", onBack = {}) }
        }
        composeTestRule.waitForIdle()

        fakeTracker.assertScreenView(CanonicalScreenName.VAULT_LISTEN)
    }
}
