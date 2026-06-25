/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Activity
import android.app.Instrumentation
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.recorder.DataStoreRecorderDraftStore
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderTempFiles
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecordingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration coverage for the draft-recovery banner wiring on My Bomps (ADR 0019 §
 * Draft recovery): a persisted draft surfaces the resume banner on the real Landing screen, Discard
 * tears it down, and the funnel events (`recording_draft_banner_shown` / `_resumed` / `_discarded`)
 * fire. The only test exercising `SoundsViewModel.pendingDraft` → render → `discardDraft` end to end.
 */
@RunWith(AndroidJUnit4::class)
internal class RecorderDraftBannerFlowTest : AbstractUiTest() {
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
        intending(hasComponent(hasClassName(RecordingActivity::class.java.name)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(string(R.string.app_recorder_draft_continue)).performClick()
            composeRule.waitForIdle()

            analytics.assertEmitted("recording_draft_resumed")
            intended(hasComponent(hasClassName(RecordingActivity::class.java.name)))
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
        val clip = RecorderTempFiles.newTempFile(context).apply { createNewFile() }
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
