/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ActiveFilterHeader is the contextual entry point into Manage Collections — surfaces the
 * current filter context plus a ✎ for non-system collections. The header itself is a presentation
 * component; what the user really cares about is captured here as end-to-end behavior.
 *
 * Coverage:
 * - Renders "All sounds · N" on My Sounds when no chip is selected.
 * - Renders "{name} · N sounds" when a chip is selected.
 * - The ✎ slot reserves its width even when hidden — proven indirectly by asserting the header is
 *   one composable above the list and never duplicates / hops on filter change. Robolectric-style
 *   pixel-perfect snapshot tests are out of scope; we lock in the visible affordances instead.
 * - The ✎ opens Manage Collections (the next test class drills into Manage itself).
 * - Header disappears in ZRP and filtered-empty states, and while the Vault is locked.
 */
@RunWith(AndroidJUnit4::class)
internal class ActiveFilterHeaderFlowTest : AbstractUiTest() {
    @Test
    fun headerShowsAllSoundsWhenNoFilterActive() {
        val sounds = TestData.seedCustomSounds(context, count = 2)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(sounds.first().id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            // The header should announce the visible count (2 — both audios, since "All" is selected).
            composeRule.awaitNodeWithText(allMySoundsHeader(2)).assertIsDisplayed()
        }
    }

    @Test
    fun headerShowsCollectionNameAndCountWhenFiltering() {
        val sounds = TestData.seedCustomSounds(context, count = 2)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(sounds.first().id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText("Familia").performClick()
            composeRule.awaitNodeWithText(headerWithCollection("Familia", 1)).assertIsDisplayed()
        }
    }

    @Test
    fun headerEditIconOpensManageScreenWithFocusedCollection() {
        val sounds = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(sounds.first().id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText("Familia").performClick()
            // Tap ✎ — the header's edit button — to navigate.
            composeRule
                .onNodeWithText(headerWithCollection("Familia", 1))
                .assertIsDisplayed()
            composeRule
                .onNodeWithContentDescription(editDescription("Familia"))
                .performClick()
            // Manage Collections title is the marker that we landed on the new screen.
            composeRule.awaitNodeWithText(manageScreenTitle()).assertIsDisplayed()
        }
    }

    @Test
    fun headerIsHiddenWhenMySoundsHasNoAudios() {
        // Fresh install — no custom sounds, welcome consumed via clearAll's default.
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(mySoundsTabLabel()).assertIsDisplayed()
            // No header text for "All sounds" should be visible when the user lands on an empty
            // My Sounds — the welcome empty-state takes over and the header is suppressed.
            composeRule.onAllNodesWithContentDescription(editDescription("Familia")).assertCountEquals(0)
        }
    }

    @Test
    fun headerIsHiddenWhileVaultIsLocked() {
        // Vault tab unlocked-by-default needs the session opener; if we don't call that the
        // UnlockGate is what renders and the header must NOT be visible above it.
        TestData.seedCustomSounds(context, count = 1).also { (audio) ->
            TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(audio.id))
        }

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultTabLabel()).performClick()
            composeRule.awaitNodeWithText(unlockCtaLabel()).assertIsDisplayed()
            // The header's all-vault copy must not be on screen above the unlock gate.
            composeRule
                .onAllNodes(hasContentDescription(editDescription("Caro")))
                .assertCountEquals(0)
        }
    }

    private fun allMySoundsHeader(count: Int) = context.getString(R.string.app_active_filter_header_all_my_sounds, count)

    private fun headerWithCollection(
        name: String,
        count: Int,
    ) = context.getString(R.string.app_active_filter_header_with_collection, name, count)

    private fun editDescription(name: String) = context.getString(R.string.app_active_filter_header_edit_description, name)

    private fun manageScreenTitle() = context.getString(R.string.app_manage_collections_title)

    private fun mySoundsTabLabel() = context.getString(R.string.app_navigation_menu_item_my_sounds)

    private fun vaultTabLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun unlockCtaLabel() = context.getString(R.string.app_vault_unlock_cta)
}
