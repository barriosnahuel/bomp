/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * How long a tap on the onboarding tour takes to show its response on screen
 * ([InteractionToNextPaintMetric]), with the tour's infinite pulse animation running vs. stopped on
 * the same device — does the pulse's continuous frame stream delay the response frame?
 *
 * The two tests differ only in `animator_duration_scale` (1 = pulse on, 0 = pulse off; the app reads
 * it as reduce-motion and holds the pulse static). It is a global device setting, so the original
 * value is restored after each test. Test Lab devices ship with it at 0, so the "on" variant is the
 * only way to see the pulse there.
 *
 * Every iteration: `pm clear` (a fresh first session), cold start, open the tour from the welcome
 * footer — all in setup, untraced — then the measured block walks the 3 steps with the primary CTA,
 * dwelling on each step first so the pulse has the pipeline in steady state when the tap lands.
 * Nodes are found by resource-id, so it runs in any locale. Physical device for numbers
 * (CONTRIBUTING.md § *Performance*); an emulator only proves it runs.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class InteractionToNextPaintBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var originalAnimatorScale: String = ""

    @Before
    fun rememberAnimatorScale() {
        originalAnimatorScale = device.executeShellCommand("settings get global $ANIMATOR_SCALE").trim()
    }

    @After
    fun restoreAnimatorScale() {
        // "null" means it was never set: delete it rather than writing a literal "null".
        if (originalAnimatorScale == "null" || originalAnimatorScale.isEmpty()) {
            device.executeShellCommand("settings delete global $ANIMATOR_SCALE")
        } else {
            device.executeShellCommand("settings put global $ANIMATOR_SCALE $originalAnimatorScale")
        }
    }

    @Test
    fun onboardingTourTapToNextPaintPulseOn() = measureTourTaps(animatorScale = "1")

    @Test
    fun onboardingTourTapToNextPaintPulseOff() = measureTourTaps(animatorScale = "0")

    private fun measureTourTaps(animatorScale: String) {
        // The metric reads FrameTimeline, which only exists from API 31.
        assumeTrue("FrameTimeline needs API 31+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(InteractionToNextPaintMetric(expectedTaps = FIRST_RUN_STEP_COUNT)),
            iterations = INP_ITERATIONS,
            compilationMode = CompilationMode.DEFAULT,
            // No startup mode: the cold start happens in setup, so the trace holds only the tour taps.
            startupMode = null,
            setupBlock = {
                // Kills the app and wipes its DataStore, so the welcome footer that opens the tour shows.
                device.executeShellCommand("pm clear $TARGET_PACKAGE")
                device.executeShellCommand("settings put global $ANIMATOR_SCALE $animatorScale")
                pressHome()
                startActivityAndWait()
                awaitRes(SEE_HOW_IT_WORKS_RES, "'see how it works' footer").click()
                awaitRes(ONBOARDING_PRIMARY_CTA_RES, "onboarding CTA at step 1")
            },
        ) {
            repeat(FIRST_RUN_STEP_COUNT) { step ->
                Thread.sleep(INP_DWELL_MS)
                val cta = awaitRes(ONBOARDING_PRIMARY_CTA_RES, "onboarding CTA at step ${step + 1}")
                val label = checkNotNull(cta.findObject(By.text(NON_EMPTY_TEXT))?.text) { "CTA at step ${step + 1} has no label." }
                cta.click()
                val isLast = step == FIRST_RUN_STEP_COUNT - 1
                // Proves the tap advanced (a dropped tap would measure nothing): past the last step the
                // tour closes; before it, the CTA's label changes.
                awaitChange("onboarding didn't advance past step ${step + 1}") {
                    val node = device.findObject(By.res(ONBOARDING_PRIMARY_CTA_RES))
                    if (isLast) node == null else node?.findObject(By.text(NON_EMPTY_TEXT))?.text?.let { it != label } ?: false
                }
            }
            // Lets the last response frame reach the screen before the trace stops.
            Thread.sleep(INP_SETTLE_MS)
        }
    }

    private fun awaitChange(
        failure: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + FIRST_RUN_UI_TIMEOUT_MS
        while (!runCatching { condition() }.getOrDefault(false)) {
            check(SystemClock.uptimeMillis() < deadline) { "$failure within $FIRST_RUN_UI_TIMEOUT_MS ms." }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun awaitRes(
        resourceId: String,
        what: String,
    ): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.res(resourceId)), FIRST_RUN_UI_TIMEOUT_MS)) {
            "$what ($resourceId) not found within $FIRST_RUN_UI_TIMEOUT_MS ms."
        }

    private companion object {
        const val ANIMATOR_SCALE = "animator_duration_scale"
        val NON_EMPTY_TEXT: Pattern = Pattern.compile(".+")
    }
}
