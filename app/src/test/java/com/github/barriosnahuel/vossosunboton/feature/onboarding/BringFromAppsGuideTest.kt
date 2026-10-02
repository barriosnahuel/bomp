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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

        composeTestRule.onNodeWithText("Find it on your phone").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloads, music, recordings or Drive.").assertIsDisplayed()
    }

    @Test
    fun `the file browser is framed as an alternative, behind a question above it`() {
        setGuide()

        val questionBottom =
            composeTestRule
                .onNodeWithText("Already have it downloaded?")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val actionTop =
            composeTestRule
                .onNodeWithText("Find it on your phone")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertThat(questionBottom).isAtMost(actionTop)
    }

    @Test
    fun `tapping Find it on your phone invokes onBrowseFiles`() {
        var browsed = 0
        setGuide(onBrowseFiles = { browsed++ })

        composeTestRule.onNodeWithText("Find it on your phone").performClick()
        composeTestRule.waitForIdle()

        assertThat(browsed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `a landscape window keeps the CTA and the footer action reachable`() {
        setGuide()

        composeTestRule.onNodeWithText("Got it").assertIsDisplayed()
        composeTestRule.onNodeWithText("Find it on your phone").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h300dp-land")
    fun `a very short window scrolls the actions with the lesson instead of pinning them`() {
        setGuide()

        // performScrollTo needs a scrollable ancestor, so it would throw if the bar were still pinned.
        composeTestRule.onNodeWithText("Got it").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Find it on your phone").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `no notice shows until the host raises it`() {
        setGuide()

        composeTestRule.onAllNodesWithText(EMPTY_RESULT_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `the empty-result notice shows its message without an action`() {
        setGuide(emptyResultNoticeId = 1)

        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()
        // Only the bar's two actions are clickable: the snackbar adds no "see how" loop back to this screen.
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun `the notice reports its id once it leaves the screen`() {
        val shownIds = mutableListOf<Int>()
        setGuide(emptyResultNoticeId = 1, onEmptyResultNoticeShown = { shownIds += it })
        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(NOTICE_OUTLIVED_MS)
        composeTestRule.waitForIdle()

        assertThat(shownIds).containsExactly(1)
        composeTestRule.onAllNodesWithText(EMPTY_RESULT_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `a new empty result restarts a notice still on screen`() {
        val shownIds = mutableListOf<Int>()
        var noticeId by mutableIntStateOf(1)
        composeTestRule.setContent {
            AppTheme {
                BringFromAppsGuide(
                    onClose = {},
                    onBrowseFiles = {},
                    emptyResultNoticeId = noticeId,
                    onEmptyResultNoticeShown = { shownIds += it },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(HALF_A_NOTICE_MS)

        noticeId = 2
        // A write from the test thread reaches composition only once the global snapshot is applied.
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeBy(HALF_A_NOTICE_MS + 2_000L)

        // Past the first notice's own 10 s, the second one is still up: it got a fresh timer, not the leftovers.
        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(NOTICE_OUTLIVED_MS)
        composeTestRule.waitForIdle()
        assertThat(shownIds).containsExactly(2)
    }

    @Test
    @Config(qualifiers = "w640dp-h300dp-land")
    fun `in a very short window the notice leaves room to scroll the footer out from under it`() {
        setGuide(emptyResultNoticeId = 1)

        composeTestRule.onNodeWithText("Find it on your phone").performScrollTo()
        composeTestRule.onRoot().performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()

        val footerBottom =
            composeTestRule
                .onNodeWithText("Find it on your phone")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val noticeTop =
            composeTestRule
                .onNodeWithText(EMPTY_RESULT_MESSAGE)
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertThat(footerBottom).isAtMost(noticeTop)
    }

    private fun setGuide(
        onClose: () -> Unit = {},
        onBrowseFiles: () -> Unit = {},
        emptyResultNoticeId: Int = 0,
        onEmptyResultNoticeShown: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                BringFromAppsGuide(
                    onClose = onClose,
                    onBrowseFiles = onBrowseFiles,
                    emptyResultNoticeId = emptyResultNoticeId,
                    onEmptyResultNoticeShown = onEmptyResultNoticeShown,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val EMPTY_RESULT_MESSAGE = "Wasn't it there? In WhatsApp, press and hold the note, tap Share and pick Bomp."

        // Past SnackbarDuration.Long (10 s) plus its exit animation.
        const val NOTICE_OUTLIVED_MS = 12_000L
        const val HALF_A_NOTICE_MS = 5_000L
    }
}
