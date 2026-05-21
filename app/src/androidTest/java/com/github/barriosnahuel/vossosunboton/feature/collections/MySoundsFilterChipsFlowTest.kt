/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
 * Regressions for the My Sounds filter chip row.
 *
 * The chip row regressed twice across this branch:
 *
 * 1. The row was conditional on `publicCollections.isNotEmpty()`, which silently hid the only
 *    "+ Nueva" entry-point on a fresh install — the round-2 launch fix made it always render.
 * 2. The system Baúl chip used to display `collection.name` (literal Spanish "Baúl") in the EN
 *    locale; the round-3 fix routes system collections through the localized resource. That
 *    locale variant gets its own test in `VaultSystemCollectionLocaleTest`; this file covers the
 *    creation + selection happy paths.
 *
 * The tests intentionally drive through the live `LandingActivity` instead of the chip-row
 * composable in isolation — the FilterChip / AssistChip pair is wired to a VM-owned state flow
 * (`activeMySoundsFilter` + persistence to DataStore), and the "+ Nueva" affordance hands off to
 * `CollectionSheetHost`, which is mounted higher up in the tree. Splicing those together
 * by hand in a unit test would duplicate production assumptions; ATF coverage from the live tree
 * is the bonus for going the instrumented route.
 */
@RunWith(AndroidJUnit4::class)
internal class MySoundsFilterChipsFlowTest : AbstractUiTest() {
    @Test
    fun filterChipsRenderOnMySoundsWhenPublicCollectionsExist() {
        val sounds = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = sounds.map { it.id })

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(allChipLabel()).assertIsDisplayed()
            composeRule.onNodeWithText("Familia").assertIsDisplayed()
            composeRule.onNodeWithText(newChipLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun newChipIsVisibleEvenWithoutCollections() {
        // Spec § launch-regression — the row used to vanish entirely on a fresh install. The
        // "+ Nueva" chip is the only path from a zero-collection state into having one, so it
        // must always render on My Sounds.
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(newChipLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun selectingACollectionChipNarrowsTheList() {
        val (one, two) = TestData.seedCustomSounds(context, count = 2)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(one.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(one.name).assertIsDisplayed()
            composeRule.onNodeWithText(two.name).assertIsDisplayed()

            composeRule.onNodeWithText("Familia").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText(one.name).assertIsDisplayed()
            composeRule.waitUntil(NARROW_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(two.name)).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    @Test
    fun tappingNewChipOpensCreatePublicCollectionSheet() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(newChipLabel()).performClick()
            composeRule.awaitNodeWithText(sheetTitle()).assertIsDisplayed()
        }
    }

    private fun allChipLabel() = context.getString(R.string.app_my_sounds_filter_all)

    private fun newChipLabel() = context.getString(R.string.app_my_sounds_filter_chip_new)

    private fun sheetTitle() = context.getString(R.string.app_collection_sheet_title_new_public)

    companion object {
        // Selection → loadSounds → _sounds emission → AnimatedVisibility exit. Five seconds is the
        // same envelope the existing flow tests use for snackbar disappearance.
        private const val NARROW_TIMEOUT_MS = 5_000L
    }
}
