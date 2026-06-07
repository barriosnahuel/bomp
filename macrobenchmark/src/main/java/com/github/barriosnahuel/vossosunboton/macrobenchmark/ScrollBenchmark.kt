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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while scrolling the sound list on LandingActivity.
 *
 * Targets the interaction regime of the jank investigation, which production data flags as the worst
 * bucket (~39% slow frames + the only frozen frames during mid-length sessions) — separate from the
 * startup regime measured by [StartupBenchmark].
 *
 * Scrolls via the first scrollable node ([By.scrollable]) so it needs no `testTag` wiring in the
 * app. If the soundboard later exposes a stable test tag, target it directly for robustness.
 * Numbers are read on-device by a human (plan Fase 3).
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollSoundsList() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = DEFAULT_ITERATIONS,
            compilationMode = CompilationMode.DEFAULT,
            // WARM recreates LandingActivity each iteration so the list starts at the top. Without it
            // the singleTask activity is reused with the process alive, leaving the list bottomed-out
            // after iteration 1 — later flings would hit an idle screen and fake good frame numbers.
            startupMode = StartupMode.WARM,
            setupBlock = { startActivityAndWait() },
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
