/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Regression guard for the Vault tab under the Nav3 graph: switching My Bomps → Vault must render
 * the vault content immediately — never flash the ZRP while an async reload settles. The ZRP is
 * legitimate only when the Vault truly has no audios.
 */
internal class LandingVaultTabTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        runBlocking {
            SoundsRepository(context).clearForTest()
            CollectionsRepository(context).clearForTest()
            val repo = SoundsRepository(context)
            repo.save(testSound("vaulted-audio", file = "vaulted.mp3"))
            val collections = CollectionsRepository(context)
            // Reading the flow once seeds the system Baúl before addAudio (no-op otherwise).
            collections.collections.first()
            val vaulted = repo.sounds.first().first { it.name == "vaulted-audio" }
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, vaulted.id)
        }
        VaultSessionState.markVaultOpen()
    }

    @After
    fun tearDown() {
        VaultSessionState.clearForTest()
        unmockkAll()
    }

    @Test
    fun `switching to the Vault tab shows its audios without flashing the empty state`() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithText(context.getString(R.string.app_navigation_menu_item_vault))
                .performClick()
            composeTestRule.waitForIdle()

            // Immediately after the swap — no awaiting an async reload — the vault audio must be
            // there and the ZRP must not. A transient empty vaultAudios at composition = the bug.
            val zrpNodes =
                composeTestRule
                    .onAllNodesWithText(context.getString(R.string.app_vault_zrp_headline_lead))
                    .fetchSemanticsNodes()
            assertThat(zrpNodes).isEmpty()
            composeTestRule.onNodeWithText("vaulted-audio").assertIsDisplayed()
        }
    }
}
