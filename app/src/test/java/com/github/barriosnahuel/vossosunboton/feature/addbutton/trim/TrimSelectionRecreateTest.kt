/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import org.junit.Rule
import org.junit.Test

/**
 * A range the user dragged must survive an Activity recreate — rotating the phone mid-edit, or the
 * system killing the screen in the background, cannot quietly hand back the whole audio and let them
 * save three minutes they had already cut down.
 *
 * Exercised over the real saver rather than the whole add screen: the screen only shows the trim
 * editor once a real audio duration lands, and no JVM test can produce one (ADR 0028 § Consequences).
 */
internal class TrimSelectionRecreateTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a dragged range survives the screen being recreated`() {
        val restorationTester = StateRestorationTester(composeTestRule)
        var applyDrag: (() -> Unit)? = null

        restorationTester.setContent {
            var selection by rememberSaveable(stateSaver = TrimSelectionSaver) {
                mutableStateOf(TrimSelection.WHOLE)
            }
            applyDrag = { selection = selection.withStart(0.25f, CLIP_MS).withEnd(0.75f, CLIP_MS) }
            Text(text = "${selection.startMs(CLIP_MS)}-${selection.endMs(CLIP_MS)}")
        }

        applyDrag!!.invoke()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("15000-45000").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("15000-45000").assertIsDisplayed()
    }

    private companion object {
        const val CLIP_MS = 60_000
    }
}
