/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.ui.home.AppTab
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for [VaultScreen] reached via the Vault bottom-nav tab. Lives under
 * `LandingActivity` so we exercise the real navigation wiring + the
 * `CollectionsRepository.ensureSystemBaul` seed path through the full activity lifecycle.
 *
 * OWASP MASVS-AUTH-1 (UI consequence of biometric policy): on a Robolectric device with no
 * configured biometric, [VaultScreen] must NOT silently grant access — it shows the unprotected
 * warning chip and lets the user open the collection only because the spec § 6 explicitly allows
 * it (with the warning being the audit trail).
 */
internal class VaultScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<LandingActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        runBlocking {
            SoundsRepository(context).clearForTest()
            CollectionsRepository(context).clearForTest()
            WelcomeStickerStore(context).clearForTest()
            MySoundsFilterStore(context).clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun vaultTabRendersBaulCardAfterSelection() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val viewModel =
                androidx.lifecycle.ViewModelProvider(
                    activity,
                    SoundsViewModel.Factory,
                )[SoundsViewModel::class.java]
            viewModel.selectTab(AppTab.VAULT)
        }
        val baulLabel = context.getString(R.string.app_vault_baul_name)
        composeRule.onNodeWithText(baulLabel).assertIsDisplayed()
    }
}
