/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Take [AddButtonScreen] out of the composition from a subclass `@After`, which JUnit runs *before*
 * `AbstractRobolectricTest` un-mocks [com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker].
 *
 * Mandatory for any test that mounts the screen with an unresolvable audio URI: `AudioPreview`'s
 * metadata read blocks an IO thread until its 10 s timeout, and only disposal interrupts it
 * (`readDurationMs` is cancellation-cooperative). Left mounted past the test, that thread times out
 * after the mock is gone, reports to a real (uninitialised) Crashlytics, and the escaping
 * `IllegalStateException` fails an unrelated later test or kills the Gradle worker.
 *
 * Callers gate the screen behind [mounted] inside their `setContent`.
 */
internal fun disposeAddButtonScreen(
    rule: ComposeTestRule,
    mounted: MutableState<Boolean>,
) {
    // A test that failed while the clock was paused would otherwise hang waitForIdle here.
    rule.mainClock.autoAdvance = true
    mounted.value = false
    rule.waitForIdle()
}
