/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.recorder.DataStoreRecorderDraftStore
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderTempFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration coverage for the draft-recovery banner wiring on My Bomps (ADR 0019 §
 * Draft recovery): a persisted draft surfaces the resume banner on the real Landing screen, resuming
 * opens the recorder destination on the restored clip, Discard tears it down, and the funnel events
 * (`recording_draft_banner_shown` / `_resumed` / `_discarded`) fire. The only test exercising
 * `SoundsViewModel.pendingDraft` → render → `discardDraft` end to end.
 */
@RunWith(AndroidJUnit4::class)
internal class RecorderDraftBannerFlowTest : AbstractUiTest() {
    // Resuming lands on the recorder destination, which renders the mic-permission priming screen
    // instead of Review when the permission is missing.
    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val draftStore by lazy { DataStoreRecorderDraftStore(context) }
    private val analytics = FakeAnalyticsTracker()

    @Before
    override fun setUp() {
        super.setUp()
        AnalyticsTrackerProvider.setForTest(analytics)
    }

    @After
    override fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        runBlocking { draftStore.clearForTest() }
        RecorderTempFiles.purge(context)
        super.tearDown()
    }

    @Test
    fun aPendingDraftShowsTheResumeBannerAndLogsTheImpression() {
        seedDraft()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_banner_message)).assertIsDisplayed()
            analytics.assertEmitted("recording_draft_banner_shown")
        }
    }

    @Test
    fun resumingFromTheBannerLogsAndOpensTheRecorder() {
        seedDraft()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_continue)).performClick()

            // No Activity hop any more: the banner pushes the recorder destination, which restores the
            // draft straight into Review (its "use this" affordance is the anchor).
            composeRule.awaitNodeWithText(string(R.string.app_recorder_use)).assertIsDisplayed()
            analytics.assertEmitted("recording_draft_resumed")
        }
    }

    @Test
    fun discardingFromTheBannerRemovesItAndLogsTheEvent() {
        seedDraft()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_discard)).performClick()

            // The store clear propagates through pendingDraft → the banner leaves composition.
            composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(string(R.string.app_recorder_draft_banner_message)).fetchSemanticsNodes().isEmpty()
            }
            analytics.assertEmitted("recording_draft_discarded")
        }
    }

    private fun seedDraft() {
        // One custom sound so My Bomps renders its normal list (not a first-run onboarding surface).
        TestData.seedCustomSounds(context, count = 1)
        // Real audio bytes: resuming now actually opens the recorder, whose Review decodes the clip's
        // waveform — an empty stub would exercise the decode-failure branch instead.
        val clip = RecorderTempFiles.newTempFile(context)
        context.resources.openRawResource(R.raw.app_branding_audio).use { input ->
            clip.outputStream().use { output -> input.copyTo(output) }
        }
        runBlocking {
            draftStore.save(clip, durationMs = 3_000)
            draftStore.draft.first { it != null }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
