/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.R

/**
 * Tab navigation for tests, through the bottom bar the user actually taps. The graph owns the active
 * tab (ADR 0024), so a test that pokes the ViewModel to "be on the Vault" asserts against a state no
 * user can produce — it would keep passing with the graph left on the wrong tab.
 */
internal fun ComposeTestRule.selectTab(tab: AppTab) {
    tabNode(tab).performClick()
    waitForIdle()
}

/** Where the user is, as rendered: the bar reflects the graph's active tab. */
internal fun ComposeTestRule.assertOnTab(tab: AppTab) {
    tabNode(tab).assertIsSelected()
}

// Matched by role, not by text alone: a tab's label is also the title of the screen it opens
// (e.g. "Vault"), so a text-only matcher hits two nodes as soon as that tab is the active one.
private fun ComposeTestRule.tabNode(tab: AppTab): SemanticsNodeInteraction =
    onNode(hasText(tabLabel(tab)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

internal fun tabLabel(tab: AppTab): String {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return context.getString(
        when (tab) {
            AppTab.MY_SOUNDS -> R.string.app_navigation_menu_item_my_sounds
            AppTab.VAULT -> R.string.app_navigation_menu_item_vault
            AppTab.EXPLORE_SOUNDS -> R.string.app_navigation_menu_item_explore_sounds
        },
    )
}
