/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Frame timing along a brand-new user's first session on LandingActivity — the context in which
 * low-tier devices report their slow frames in the field: no sounds of their own, the welcome audio
 * shown and played, the onboarding tour walked to the end (its demos run infinite pulse animations),
 * the Import Hub sheet it lands on opened and closed, then ≥ 10 s idle on the welcome-only list.
 *
 * Every iteration starts from `pm clear` (empty library, welcome + swipe hint not yet consumed) and a
 * cold start. `pm clear` also drops ProfileInstaller's markers, so it re-writes the profile in the
 * background in both modes, as on a real first launch (None also resets compilation per iteration).
 * Two compilation modes split first-run JIT cost (`None`, a fresh install) from steady rendering cost
 * (`Partial(Require)`, the committed Baseline Profile). Nodes are found by resource-id (Compose test
 * tags), never by text, so the journey runs in any device locale. Run it on a physical
 * low-tier device (Test Lab: CONTRIBUTING.md § *Performance*); the import step needs a microphone,
 * without one the Hub is skipped for the bring-in guide and the run fails loudly.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun firstRunJourneyNoCompilation() = measureJourney(CompilationMode.None())

    @Test
    fun firstRunJourneyBaselineProfile() = measureJourney(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measureJourney(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            iterations = FIRST_RUN_ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
            setupBlock = {
                // Wipes the DataStore files (sounds, welcome + hint flags) but keeps the compiled code,
                // so the compilation mode set up above still applies to the measured launch.
                device.executeShellCommand("pm clear $TARGET_PACKAGE")
                // None must stay a fresh install every iteration: the profile ProfileInstaller re-writes
                // after `pm clear` could otherwise be compiled by a background dexopt between iterations.
                if (compilationMode is CompilationMode.None) {
                    device.executeShellCommand("cmd package compile --reset $TARGET_PACKAGE")
                }
                pressHome()
            },
        ) {
            startActivityAndWait()

            // Welcome audio: the only row on a fresh install. The dwell lets its one-time swipe hint (the
            // card slides and settles back) finish before the tap; the play button's description flipping
            // to its "pause" label — whatever the locale — proves playback started.
            awaitRes(SOUND_PLAY_BUTTON_RES, "welcome audio's play button")
            Thread.sleep(FIRST_RUN_DWELL_MS)
            val playLabel =
                checkNotNull(labelOf(awaitRes(SOUND_PLAY_BUTTON_RES, "welcome audio's play button"))) { "Play button has no label." }
            awaitRes(SOUND_PLAY_BUTTON_RES, "welcome audio's play button").click()
            awaitChange("welcome audio didn't start playing") { changedLabel(SOUND_PLAY_BUTTON_RES, playLabel) }
            Thread.sleep(FIRST_RUN_DWELL_MS)

            // Onboarding tour from the welcome footer, walked through every step with its primary CTA.
            // Each step has its own CTA label, so a changed label (or, past the last step, no CTA) proves
            // the tap advanced — a dropped tap fails here, naming the step.
            awaitRes(SEE_HOW_IT_WORKS_RES, "'see how it works' footer").click()
            repeat(FIRST_RUN_STEP_COUNT) { step ->
                awaitRes(ONBOARDING_PRIMARY_CTA_RES, "onboarding CTA at step ${step + 1}")
                Thread.sleep(FIRST_RUN_DWELL_MS)
                val ctaLabel =
                    checkNotNull(
                        labelOf(awaitRes(ONBOARDING_PRIMARY_CTA_RES, "onboarding CTA at step ${step + 1}")),
                    ) { "CTA has no label." }
                awaitRes(ONBOARDING_PRIMARY_CTA_RES, "onboarding CTA at step ${step + 1}").click()
                val isLast = step == FIRST_RUN_STEP_COUNT - 1
                awaitChange("onboarding didn't advance past step ${step + 1}") {
                    // Past the last step the tour closes, so only there does a missing CTA mean "advanced".
                    if (isLast) res(ONBOARDING_PRIMARY_CTA_RES) == null else changedLabel(ONBOARDING_PRIMARY_CTA_RES, ctaLabel)
                }
            }

            // Finishing the tour lands on the Import Hub sheet. Without a microphone the app opens the
            // bring-in guide instead and this wait fails.
            awaitRes(IMPORT_HUB_SHEET_RES, "Import Hub sheet after the tour (no microphone on the device?)")
            Thread.sleep(FIRST_RUN_DWELL_MS)
            device.pressBack()
            check(device.wait(Until.gone(By.res(IMPORT_HUB_SHEET_RES)), FIRST_RUN_UI_TIMEOUT_MS)) {
                "Import Hub sheet didn't close on back within $FIRST_RUN_UI_TIMEOUT_MS ms."
            }

            Thread.sleep(FIRST_RUN_IDLE_MS)
        }

    private fun MacrobenchmarkScope.res(resourceId: String): UiObject2? = device.findObject(By.res(resourceId))

    // A node that is present and labelled differently — a null read (mid-recomposition) is not a change.
    private fun MacrobenchmarkScope.changedLabel(
        resourceId: String,
        previous: String,
    ): Boolean = res(resourceId)?.let(::labelOf)?.let { it != previous } ?: false

    // The tag sits on the clickable node; Compose exposes its label (icon description, button text) on
    // an unmerged child, so read it from the subtree.
    private fun labelOf(node: UiObject2): String? =
        node.findObject(By.desc(NON_EMPTY))?.contentDescription ?: node.findObject(By.text(NON_EMPTY))?.text

    // Polls instead of Until: the condition reads a node that recomposition may replace between reads.
    private fun MacrobenchmarkScope.awaitChange(
        failure: String,
        condition: MacrobenchmarkScope.() -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + FIRST_RUN_UI_TIMEOUT_MS
        while (!runCatching { condition() }.getOrDefault(false)) {
            check(SystemClock.uptimeMillis() < deadline) { "$failure within $FIRST_RUN_UI_TIMEOUT_MS ms." }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun MacrobenchmarkScope.awaitRes(
        resourceId: String,
        what: String,
    ): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.res(resourceId)), FIRST_RUN_UI_TIMEOUT_MS)) {
            "$what ($resourceId) not found within $FIRST_RUN_UI_TIMEOUT_MS ms."
        }
}

private val NON_EMPTY: Pattern = Pattern.compile(".+")
