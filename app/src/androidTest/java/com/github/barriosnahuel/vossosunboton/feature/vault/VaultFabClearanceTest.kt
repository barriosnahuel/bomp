/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the Vault FAB / last-row overlap bug.
 *
 * The Vault tab overlays an [androidx.compose.material3.ExtendedFloatingActionButton] on top of
 * the audio list (always-visible "+ Nueva colección" entry-point). Without an explicit bottom
 * contentPadding on the underlying [androidx.compose.foundation.lazy.LazyColumn], a small Vault
 * (few enough audios that the list doesn't need to scroll) renders its last row flush with the
 * bottom of the LazyColumn — directly under the FAB, clipping the row by ~50%.
 *
 * The production fix bumps the LazyColumn's bottom contentPadding to leave clearance for the FAB
 * (88dp = 56dp ExtendedFAB height + Spacing.LG margin + Spacing.MD breathing room). This test
 * locks the invariant in: with a Vault that has audios but doesn't need to scroll, the bottom
 * edge of the last audio row sits above the top edge of the FAB.
 */
@RunWith(AndroidJUnit4::class)
internal class VaultFabClearanceTest : AbstractUiTest() {
    @Test
    fun lastVaultAudioStaysAboveTheExtendedFab() {
        // Three audios is enough to land the last row near the bottom of the viewport on a
        // typical phone-sized AVD, without going past the screen height (which would force the
        // LazyColumn to scroll and naturally hide the row under the FAB anyway — defeating the
        // regression). If the AVD changes resolution and the count needs revisiting, the test
        // will fail on the count assertion below first, not on the bounds check.
        val sounds = TestData.seedCustomSounds(context, count = 3)
        TestData.seedPrivateCollection(
            context,
            name = "Cumple",
            audioIds = sounds.map { it.id },
        )
        TestData.markVaultOpen()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultTabLabel()).performClick()
            // Wait for the body to render — the audio names are unique across the seeded list.
            val lastSound = sounds.last()
            composeRule.awaitNodeWithText(lastSound.name).assertIsDisplayed()

            val rowBounds =
                composeRule
                    .onNodeWithText(lastSound.name)
                    .getBoundsInRoot()
            val fabBounds =
                composeRule
                    .onNodeWithContentDescription(fabContentDescription())
                    .getBoundsInRoot()

            // Two checks: the row is fully on-screen (top >= 0) AND its bottom edge stays above
            // the FAB's top edge. The first guards against a future regression that scrolls the
            // row off the top; the second is the one we're paying for here.
            assertThat(rowBounds.top.value).isAtLeast(0f)
            assertThat(rowBounds.bottom.value).isLessThan(fabBounds.top.value)
        }
    }

    private fun vaultTabLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun fabContentDescription() = context.getString(R.string.app_vault_fab_new)
}
