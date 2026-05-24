/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for "private-only audios stay in the Vault" (spec § 1), now expressed through the
 * explicit `isVisibleInMySounds` flag (ADR 0012). My Sounds visibility no longer derives from
 * `inPrivate - inPublic`; instead the one-time `migrateVisibilityIfNeeded` seeds pre-existing
 * Vault-only audios to `isVisibleInMySounds = false` at VM init (each test re-runs it because
 * `TestData.clearAll` resets the store). The end-to-end contract is unchanged:
 * - `audioOnlyInAPrivateCollectionIsHiddenFromMySounds`: a Vault-only audio is hidden (migrated to
 *   `false`).
 * - `audioCrossTaggedInPublicAndPrivateStaysVisibleOnMySounds`: a cross-tagged audio is NOT
 *   private-only, so it keeps the `true` default and stays visible.
 *
 * The new dual-home capability (a private audio the user explicitly keeps visible) and the toggle
 * are covered deterministically by the JVM `SoundsViewModelVisibilityTest`.
 */
@RunWith(AndroidJUnit4::class)
internal class PrivateOnlyAudiosHiddenFromMySoundsTest : AbstractUiTest() {
    @Test
    fun audioOnlyInAPrivateCollectionIsHiddenFromMySounds() {
        val (publicAudio, privateAudio) = TestData.seedCustomSounds(context, count = 2)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(publicAudio.id))
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(privateAudio.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(publicAudio.name).assertIsDisplayed()
            // The private-only audio must NOT appear on My Sounds — even though it exists in the
            // library, the projection step filters it out.
            composeRule.waitUntil(LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(publicAudio.name)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(privateAudio.name).assertCountEquals(0)
        }
    }

    @Test
    fun audioCrossTaggedInPublicAndPrivateStaysVisibleOnMySounds() {
        val (crossTagged) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(crossTagged.id))
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(crossTagged.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Visible on My Sounds: cross-tagged audios surface from their public side.
            composeRule.awaitNodeWithText(crossTagged.name).assertIsDisplayed()
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 5_000L
    }
}
