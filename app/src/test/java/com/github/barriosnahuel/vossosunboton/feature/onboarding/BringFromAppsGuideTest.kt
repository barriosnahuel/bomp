/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.onboarding

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Smoke, action, and empty-result-notice coverage for the [BringFromAppsGuide] single-step guide. Runs
 * with system animations off so the reused IMPORT demo's looping animation never keeps the Compose clock
 * busy past `waitForIdle`, mirroring `OnboardingTourTest`.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class BringFromAppsGuideTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @Test
    fun `renders the import lesson and its terminal CTA`() {
        setGuide()

        // The default Robolectric window is a short portrait one, where the lesson scrolls above the pinned bar.
        composeTestRule.onNodeWithText("Bring in the voices you already have.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Got it").assertIsDisplayed()
    }

    @Test
    fun `tapping Got it invokes onClose`() {
        var closed = false
        setGuide(onClose = { closed = true })

        composeTestRule.onNodeWithText("Got it").performClick()
        composeTestRule.waitForIdle()

        assertThat(closed).isTrue()
    }

    @Test
    fun `footer names the file browser and what it can reach`() {
        setGuide()

        composeTestRule.onNodeWithText("Look on your phone").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloads, music, recordings or Drive.").assertIsDisplayed()
    }

    @Test
    fun `tapping Look on your phone invokes onBrowseFiles`() {
        var browsed = 0
        setGuide(onBrowseFiles = { browsed++ })

        composeTestRule.onNodeWithText("Look on your phone").performClick()
        composeTestRule.waitForIdle()

        assertThat(browsed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `a landscape window keeps the CTA and the footer action reachable`() {
        setGuide()

        composeTestRule.onNodeWithText("Got it").assertIsDisplayed()
        composeTestRule.onNodeWithText("Look on your phone").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h300dp-land")
    fun `a very short window scrolls the actions with the lesson instead of pinning them`() {
        setGuide()

        // performScrollTo needs a scrollable ancestor, so it would throw if the bar were still pinned.
        composeTestRule.onNodeWithText("Got it").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Look on your phone").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `no notice shows until the host raises it`() {
        setGuide()

        composeTestRule.onAllNodesWithText(EMPTY_RESULT_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `the empty-result notice shows its message without an action`() {
        setGuide(showEmptyResultNotice = true)

        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()
        // Only the bar's two actions are clickable: the snackbar adds no "see how" loop back to this screen.
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun `the notice reports itself shown once it leaves the screen`() {
        var shown = 0
        setGuide(showEmptyResultNotice = true, onEmptyResultNoticeShown = { shown++ })
        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(NOTICE_OUTLIVED_MS)
        composeTestRule.waitForIdle()

        assertThat(shown).isEqualTo(1)
        composeTestRule.onAllNodesWithText(EMPTY_RESULT_MESSAGE).assertCountEquals(0)
    }

    private fun setGuide(
        onClose: () -> Unit = {},
        onBrowseFiles: () -> Unit = {},
        showEmptyResultNotice: Boolean = false,
        onEmptyResultNoticeShown: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                BringFromAppsGuide(
                    onClose = onClose,
                    onBrowseFiles = onBrowseFiles,
                    showEmptyResultNotice = showEmptyResultNotice,
                    onEmptyResultNoticeShown = onEmptyResultNoticeShown,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val EMPTY_RESULT_MESSAGE = "Wasn't it there? Voice notes come in by sharing them to Bomp."

        // Past SnackbarDuration.Long (10 s) plus its exit animation.
        const val NOTICE_OUTLIVED_MS = 12_000L
    }
}
