/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule

/**
 * Base class for instrumented UI tests.
 *
 * Subclasses launch their own [androidx.test.core.app.ActivityScenario] and drive interactions
 * through [composeRule], Espresso, or UI Automator depending on what they need to assert.
 *
 * Accessibility:
 * - [AccessibilityChecks] is enabled class-wide and validates every Espresso/UI Automator
 *   interaction against the Google ATF (contrast, touch target, content description, etc.).
 * - Compose-driven interactions do NOT trigger ATF automatically — each screen test must
 *   include explicit `assertContentDescription*` / `assertHasClickAction` assertions for the
 *   nodes that matter for screen-reader navigation.
 */
internal abstract class AbstractUiTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    protected val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    open fun setUp() {
        Intents.init()
        TestData.clearAll(context)
    }

    @After
    open fun tearDown() {
        try {
            Intents.release()
        } finally {
            TestData.clearAll(context)
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun enableAccessibilityChecks() {
            // Compose drives a11y through the semantics tree, not the View's contentDescription.
            // ATF runs against Android Views and flags AndroidComposeView root as missing a label.
            // Suppress that specific false-positive so we can still catch real Espresso/UIAutomator
            // a11y violations elsewhere.
            val composeRootIsExempt =
                allOf(
                    AccessibilityCheckResultUtils.matchesViews(withClassName(equalTo("androidx.compose.ui.platform.AndroidComposeView"))),
                    AccessibilityCheckResultUtils.matchesCheckNames(equalTo("SpeakableTextPresentCheck")),
                )
            AccessibilityChecks
                .enable()
                .setRunChecksFromRootView(true)
                .setSuppressingResultMatcher(composeRootIsExempt)
        }
    }
}
