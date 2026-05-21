/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.ui.test.assertIsDisplayed
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
 * Smoke-level regressions for the Vault tab.
 *
 * The instrumented suite is the right place for these because the Vault renders against the real
 * `AppTheme` color roles (the round-3 fix removed a forced-dark subtree override) and because the
 * biometric gate's "no protection" branch — taken whenever the emulator has no fingerprint or
 * screen lock configured — produces a visually different UnlockGate than the prompt path. Both
 * branches are reachable here without configuring a biometric on the device:
 *
 * - `vaultTabAlwaysVisibleInBottomNav`: ensures the Vault item is reachable even on a brand-new
 *   install (no bundled audios, no custom audios). This invariant regressed once during round 2.
 * - `tappingVaultRevealsUnlockGate`: covers the unlocked CTA path and the inspirational ZRP that
 *   the user lands on after the per-session unlock — exercises the new "no dark theme subtree"
 *   rendering so a future hex-color regression surfaces here.
 * - `unlockingVaultRevealsInspirationalZeroResultState`: same flow as above but pre-seeds the
 *   session state so we land directly on the ZRP body. Belt + suspenders for the empty-state
 *   message; failing it means either the chip row, the FAB, or the ZRP text changed unexpectedly.
 */
@RunWith(AndroidJUnit4::class)
internal class VaultTabFlowTest : AbstractUiTest() {
    @Test
    fun vaultTabAlwaysVisibleInBottomNav() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun tappingVaultRevealsUnlockGate() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(unlockCta()).assertIsDisplayed()
        }
    }

    @Test
    fun unlockingVaultRevealsInspirationalZeroResultState() {
        // Pre-grant the per-session unlock so the test does not depend on the emulator having a
        // biometric configured — the gate falls through to "open directly" on unprotected devices
        // (spec § 6) and the user lands on the ZRP body the same way.
        TestData.markVaultOpen()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(zrpHeadlineLead()).assertIsDisplayed()
            composeRule.onNodeWithText(zrpEmphasis()).assertIsDisplayed()
        }
    }

    private fun vaultLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun unlockCta() = context.getString(R.string.app_vault_unlock_cta)

    private fun zrpHeadlineLead() = context.getString(R.string.app_vault_zrp_headline_lead)

    private fun zrpEmphasis() = context.getString(R.string.app_vault_zrp_headline_emphasis)
}
