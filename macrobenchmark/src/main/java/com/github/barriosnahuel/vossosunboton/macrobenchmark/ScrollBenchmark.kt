/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while scrolling the sound list on LandingActivity, across three controlled list sizes
 * (few / medium / many). Production data flags the interaction regime as the worst bucket (~39% slow
 * frames + the only frozen frames during mid-length sessions); comparing the sizes shows whether that
 * jank scales with item count (hypothesis H2 — per-item composition cost), separate from the startup
 * regime measured by [StartupBenchmark].
 *
 * Each run seeds a synthetic corpus of exactly N sounds via a launch-intent extra (handled by the
 * benchmark-variant `CustomBuildTypeApplication`), then waits for the list to render before scrolling.
 * Scrolls the first scrollable node ([By.scrollable]) so it needs no `testTag` in the app. Numbers are
 * read on-device by a human (plan Fase 3).
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollSmallList() = measureScroll(SMALL_LIST)

    @Test
    fun scrollMediumList() = measureScroll(MEDIUM_LIST)

    @Test
    fun scrollLargeList() = measureScroll(LARGE_LIST)

    private fun measureScroll(itemCount: Int) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = DEFAULT_ITERATIONS,
            compilationMode = CompilationMode.DEFAULT,
            // WARM recreates LandingActivity each iteration so the list starts at the top. Seeding is
            // idempotent, so the relaunch re-sends the extra cheaply (no-op once N already seeded).
            startupMode = StartupMode.WARM,
            setupBlock = {
                startActivityAndWait { intent -> intent.putExtra(SEED_COUNT_EXTRA, itemCount) }
                // Seeding is synchronous (done in onCreate before the list loads), so once any seeded
                // item renders the full exact-N corpus is present — safe to start measuring.
                device.wait(Until.hasObject(By.textContains(SYNTHETIC_NAME_PREFIX)), SEED_RENDER_TIMEOUT_MS)
            },
        ) {
            // Fail loudly: a missing scrollable node must not silently record idle frames as a result.
            val list =
                requireNotNull(device.findObject(By.scrollable(true))) {
                    "No scrollable node on LandingActivity — wire a stable testTag if this fails on-device."
                }
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(SCROLL_GESTURES) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
}
