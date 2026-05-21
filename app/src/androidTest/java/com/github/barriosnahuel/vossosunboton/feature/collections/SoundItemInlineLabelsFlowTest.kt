/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-3 UX refinement: when a filter chip is selected, the cards beneath stop showing the
 * inline collection label in their meta row — the chip + ActiveFilterHeader already carry that
 * context, repeating it on each card is noise. With no chip active ("All"), the inline label
 * stays because it's the only differentiator across audios that belong to different collections.
 */
@RunWith(AndroidJUnit4::class)
internal class SoundItemInlineLabelsFlowTest : AbstractUiTest() {
    @Test
    fun cardShowsCollectionLabelInlineWhenNoFilterIsActive() {
        val (audio) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(audio.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(audio.name).assertIsDisplayed()
            // "Familia · " is the inline-label-plus-separator form unique to the card meta when no
            // filter is active. The chip says "Familia" (no separator); the header says
            // "All sounds · N" (no "Familia" at all). So exactly 1 node matches.
            composeRule
                .onAllNodes(hasText("Familia · ", substring = true))
                .assertCountEquals(1)
        }
    }

    @Test
    fun cardHidesCollectionLabelInlineWhenAFilterIsActive() {
        val (audio) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(audio.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText("Familia").performClick()
            composeRule.awaitNodeWithText(audio.name).assertIsDisplayed()
            // With the filter active, the header is "Familia · 1 sounds" — that's the ONE match
            // for "Familia · ". The card meta row drops its inline label, so it does NOT add a
            // second match. If a regression brings back the inline label, count goes to 2.
            composeRule
                .onAllNodes(hasText("Familia · ", substring = true))
                .assertCountEquals(1)
        }
    }
}
