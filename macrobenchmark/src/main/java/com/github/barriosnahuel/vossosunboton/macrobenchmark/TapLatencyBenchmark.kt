/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tap-to-sound latency guardrail: how long from tapping a sound row until playback starts, on the
 * current engine. The project's hard UX budget is **≤100 ms**; this benchmark is the permanent
 * no-regression gate for it — most immediately for the Media3 playback-engine migration, which must
 * keep this number inside the budget.
 *
 * Measures the [TAP_TO_SOUND_SECTION] async trace section, which spans tap dispatch →
 * `onPlayerStart`/`onPlayerError` at the ViewModel boundary — engine-agnostic on purpose so the
 * number stays comparable across engine swaps. [StartupMode.COLD] kills the process every
 * iteration, so each measured tap is a first tap against a fresh player (the worst, and most
 * honest, case — a warm re-tap would exercise the much cheaper resume-in-place path instead).
 *
 * The tapped row is synthetic sound 0, the only seeded row backed by real audio bytes (see the
 * benchmark variant's `CustomBuildTypeApplication.seedPlayableAudioFile`), exercising the dominant
 * custom-file playback path. Run on a physical device — emulator audio/disk starvation makes
 * latency numbers meaningless (same reason the frozen-frame gate is disarmed under instrumentation).
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class TapLatencyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun tapToSoundFirstTap() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(TraceSectionMetric(TAP_TO_SOUND_SECTION, TraceSectionMetric.Mode.First)),
            iterations = DEFAULT_ITERATIONS,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.COLD,
        ) {
            // COLD kills the process between setupBlock and measureBlock, so the launch must happen
            // HERE — launch wall-time doesn't pollute the metric, which only reads the trace
            // section's own duration.
            startActivityAndWait { intent -> intent.putExtra(SEED_COUNT_EXTRA, TAP_SEED_COUNT) }
            // Seeding is synchronous in onCreate; fail loudly if the playable row never renders
            // rather than measuring nothing (a missing section reports as a silent zero-count).
            check(device.wait(Until.hasObject(By.text(PLAYABLE_SOUND_NAME)), SEED_RENDER_TIMEOUT_MS)) {
                "Playable seeded sound ($PLAYABLE_SOUND_NAME) didn't render within $SEED_RENDER_TIMEOUT_MS ms."
            }
            // Play is a dedicated icon button inside the card (tapping the card body does NOT play).
            // Scope to the seeded row's own button: the nearest Play control below the row title.
            val title =
                checkNotNull(device.findObject(By.text(PLAYABLE_SOUND_NAME))) {
                    "Playable seeded sound ($PLAYABLE_SOUND_NAME) not on screen at measure time."
                }
            val titleCenterY = title.visibleBounds.centerY()
            val playButton =
                device
                    .findObjects(By.desc(PLAY_BUTTON_DESC))
                    .filter { it.visibleBounds.centerY() > titleCenterY }
                    .minByOrNull { it.visibleBounds.centerY() }
            checkNotNull(playButton) {
                "No '$PLAY_BUTTON_DESC' button below the seeded row — is the device locale English?"
            }
            playButton.click()
            // Fixed settle instead of polling UI state: the metric reads the trace section's own
            // duration, so extra wall time here adds no noise — it only guarantees the async section
            // has closed (prepare+start finished) before the iteration's trace window ends.
            Thread.sleep(TAP_SETTLE_MS)
        }
}
