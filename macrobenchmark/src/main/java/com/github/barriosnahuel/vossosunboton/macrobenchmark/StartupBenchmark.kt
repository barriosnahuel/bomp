/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing for LandingActivity (the app's launcher / entry screen).
 *
 * Reproduces the startup regime of the jank investigation: production Firebase Performance shows
 * ~573 ms cold start on an Android-11 OnePlus 8Pro vs 33–84 ms on current flagships. Run the same
 * benchmark on a flagship and on a throttled / older device to quantify that gap.
 *
 * Three compilation modes bracket the real-world spread:
 *  - [CompilationMode.None]: no AOT — worst case, closest to a fresh install / cold ART.
 *  - [CompilationMode.DEFAULT]: typical post-install state.
 *  - [CompilationMode.Partial] with [BaselineProfileMode.Require]: AOT-compiles the startup hot path
 *    from the committed `app/src/main/baseline-prof.txt`. Measures the actual shipped Baseline Profile.
 *
 * Numbers are read on-device by a human (plan Fase 3/4); this class only defines the measurement.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun startupDefaultCompilation() = measureStartup(CompilationMode.DEFAULT)

    @Test
    fun startupBaselineProfile() = measureStartup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measureStartup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
}
