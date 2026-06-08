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
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
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
 * benchmark-variant `CustomBuildTypeApplication`), then scrolls the list. Uses [UiScrollable] (not
 * `findObject` + `fling`): it re-resolves the scrollable node on every gesture, so a recomposition
 * between gestures can't invalidate a cached handle (the StaleObjectException that sank the earlier
 * approach). Numbers are read on-device by a human (plan Fase 3).
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
            // WARM keeps the process alive and relaunches LandingActivity each iteration. It's a
            // singleTask Activity, so the relaunch re-enters the live instance via onNewIntent (no
            // recreate) and the LazyColumn keeps its scroll position — setupBlock resets it to the top
            // below. Seeding is idempotent, so the relaunch re-sends the extra cheaply (no-op once seeded).
            startupMode = StartupMode.WARM,
            setupBlock = {
                startActivityAndWait { intent -> intent.putExtra(SEED_COUNT_EXTRA, itemCount) }
                // Seeding is synchronous (done in onCreate before the list loads), so once a seeded item
                // renders the full exact-N corpus is present. Fail loudly if it never does, rather than
                // measuring an empty/wrong list.
                check(device.wait(Until.hasObject(By.textContains(SYNTHETIC_NAME_PREFIX)), SEED_RENDER_TIMEOUT_MS)) {
                    "Seeded corpus ($SYNTHETIC_NAME_PREFIX) didn't render within $SEED_RENDER_TIMEOUT_MS ms."
                }
                // Reset to the top so every iteration measures the same top-to-bottom fling, not the
                // already-scrolled list left by the previous iteration (singleTask, see above).
                UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(SCROLL_RESET_SWIPES)
            },
        ) {
            val list = UiScrollable(UiSelector().scrollable(true))
            // Fail loudly if the list never appears; idle frames must not pass as a result.
            check(list.waitForExists(SEED_RENDER_TIMEOUT_MS)) {
                "No scrollable sound list on LandingActivity within $SEED_RENDER_TIMEOUT_MS ms."
            }
            // Keep flings clear of the system back-gesture edges.
            list.setSwipeDeadZonePercentage(SWIPE_DEAD_ZONE)
            repeat(SCROLL_GESTURES) {
                list.flingForward()
                // Settle after each fling so the deceleration frames (often the jankiest) land inside
                // the measured window and consecutive flings don't cut each other short.
                device.waitForIdle()
            }
        }
}
