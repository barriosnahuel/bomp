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
 * Fase 3/4). No `androidx.baselineprofile` plugin: this is run by hand against the **non-minified
 * debug** build (real method names, so AGP can remap the rules through R8 at release time) and the
 * collected rules are committed to `app/src/main/baseline-prof.txt`, which AGP bakes into the release
 * APK. Regenerate when the startup path changes meaningfully. How-to: CONTRIBUTING § Baseline Profile.
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
