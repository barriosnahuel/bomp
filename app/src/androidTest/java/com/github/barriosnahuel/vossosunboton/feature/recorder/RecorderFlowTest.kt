/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.Manifest
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.rule.GrantPermissionRule
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented regression coverage for the recorder's lifecycle + disk-threading bugs that only
 * surface on a real device: the debug build's StrictMode penalty throws on disk-on-main, and these
 * paths never run under Robolectric. A fake [RecorderEngine] stands in for the mic — the bugs are
 * about host lifecycle, FileProvider, and StrictMode, not audio — while temp files, the FileProvider
 * URI, and StrictMode run for real on the emulator.
 *
 * The recorder is a destination inside Landing now, so each test walks the real entry point
 * (+ FAB → import Hub → "record") rather than launching an Activity.
 *
 * Guards (each a crash/regression caught manually on a Pixel):
 * - Stop → Review must not crash (FileProvider URI resolution off the main thread).
 * - A config-change recreate mid-recording must keep recording (the host's ON_STOP must not treat it
 *   as backgrounding). Real rotation additionally never recreates (manifest `configChanges`).
 * - Destroying mid-recording must not crash (temp cleanup off the main thread).
 *
 * Note: deliberately no global device rotation here — `UiDevice.setOrientation*` mutates shared
 * emulator state and destabilises the rest of the suite. `recreate()` exercises the same guard.
 */
internal class RecorderFlowTest : AbstractUiTest() {
    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private lateinit var engine: FakeRecorderEngine

    @Before
    override fun setUp() {
        super.setUp()
        engine = FakeRecorderEngine()
        RecorderEngineProvider.setForTest(engine)
        // The + FAB (the only way into the Hub, and from there the recorder) renders only when
        // My Sounds is non-empty; an empty list swaps it for the welcome CTA.
        TestData.seedCustomSounds(context, count = 1)
    }

    @After
    override fun tearDown() {
        RecorderEngineProvider.setForTest(null)
        RecorderTempFiles.purge(context)
        super.tearDown()
    }

    @Test
    fun recordingThenStoppingReachesReviewWithoutCrashing() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openRecorder()
            startRecording()
            // The clip must cross the 1 s minimum so Stop moves to Review (not "too short").
            SystemClock.sleep(MIN_CLIP_WAIT_MS)
            composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_stop)).performClick()

            // Reaching Review without a StrictMode DiskReadViolation crash is the regression guard.
            composeRule.awaitNodeWithText(string(R.string.app_recorder_use)).assertIsDisplayed()
        }
    }

    @Test
    fun recordingSurvivesAConfigChangeRecreate() {
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            openRecorder()
            startRecording()

            scenario.recreate()

            // Still recording (Stop still offered): a config-change recreate must not auto-stop via the
            // host's ON_STOP, and the NavEntry-scoped ViewModel + capture survive it.
            composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_stop)).assertIsDisplayed()
        }
    }

    @Test
    fun destroyingWhileRecordingDoesNotCrash() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            openRecorder()
            startRecording()
            // Closing destroys the Activity → every NavEntry ViewModel store is cleared → onCleared. A
            // DiskWriteViolation (File.delete on main) there would crash the process; surviving teardown
            // is the regression guard.
        }
    }

    private fun openRecorder() {
        composeRule.awaitNodeWithContentDescription(string(R.string.app_hub_fab_description)).performClick()
        composeRule.awaitNodeWithText(string(R.string.app_hub_record)).performClick()
    }

    private fun startRecording() {
        composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_record)).performClick()
        composeRule.awaitNodeWithContentDescription(string(R.string.app_recorder_cd_stop)).assertIsDisplayed()
    }

    private fun string(resId: Int): String = context.getString(resId)

    private companion object {
        const val MIN_CLIP_WAIT_MS = 1_300L
    }
}
