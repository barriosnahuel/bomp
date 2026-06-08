/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the LandingActivity cold-start path (the jank regime located in
 * Fase 3/4). No `androidx.baselineprofile` plugin: this is run by hand against the **`nonMinifiedRelease`**
 * build (release code, un-minified → real method names that reflect the real release startup path, so
 * AGP can remap the rules through R8 at release time) and the collected rules are committed to
 * `app/src/main/baseline-prof.txt`, which AGP bakes into the release APK. Regenerate when the startup
 * path changes meaningfully. How-to: CONTRIBUTING § Baseline Profile.
 *
 * **Run only via `scripts/generate-baseline-profile.sh`** (which installs `nonMinifiedRelease` first).
 * Do NOT run it through the unfiltered `:macrobenchmark:connectedBenchmarkAndroidTest` suite: that
 * targets the **minified** `benchmark` variant, so it would collect obfuscated rules that R8 can't
 * remap into release — a silently useless profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
        }
}
