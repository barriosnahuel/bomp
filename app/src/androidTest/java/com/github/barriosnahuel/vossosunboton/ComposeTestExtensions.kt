/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText

/**
 * Default timeout for [awaitNode] / [awaitNodeWithText] / [awaitNodeWithContentDescription].
 *
 * Five seconds is the same value tests have used inline since the suite landed; long enough to
 * cover the DataStore-read → StateFlow-emit → Compose-render chain on a cold-started emulator,
 * short enough that a real failure surfaces before the JUnit timeout.
 */
internal const val WAIT_TIMEOUT_MS: Long = 5_000L

/**
 * Wait until at least one node matching [matcher] is in the semantics tree, then return the
 * single-node interaction. Use this whenever a click/assertion targets a node whose existence
 * depends on ViewModel/DataStore-emitted state — `composeRule.waitForIdle()` waits only for
 * Compose recompositions to settle, not for upstream coroutines to finish seeding the screen.
 *
 * Returns the same [SemanticsNodeInteraction] that `onNode(matcher)` would have returned, so
 * callers can chain `.performClick()`, `.assertIsDisplayed()`, etc. directly.
 */
internal fun ComposeTestRule.awaitNode(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = WAIT_TIMEOUT_MS,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
    return onNode(matcher)
}

/**
 * [awaitNode] specialised for `hasText(text)` — the most common flake shape in this suite.
 */
internal fun ComposeTestRule.awaitNodeWithText(
    text: String,
    timeoutMillis: Long = WAIT_TIMEOUT_MS,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }
    return onNodeWithText(text)
}

/**
 * [awaitNode] specialised for [androidx.compose.ui.test.hasContentDescription] — pairs with
 * the IconButton-heavy parts of the UI (bottom nav, top bar, swipe-action accents).
 */
internal fun ComposeTestRule.awaitNodeWithContentDescription(
    label: String,
    timeoutMillis: Long = WAIT_TIMEOUT_MS,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
    }
    return onNodeWithContentDescription(label)
}
