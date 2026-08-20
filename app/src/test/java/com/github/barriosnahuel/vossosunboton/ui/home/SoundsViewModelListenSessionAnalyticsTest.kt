/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * What a long listen reports when it starts. Lives apart from [SoundsViewModelAnalyticsTest] so that
 * class stays under detekt's LargeClass budget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SoundsViewModelListenSessionAnalyticsTest : AbstractRobolectricTest() {
    private lateinit var fake: FakeAnalyticsTracker
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        runBlocking { SoundsRepository(ApplicationProvider.getApplicationContext()).clearForTest() }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startListenSession(any(), any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        runBlocking {
            vm.isInitialLoadComplete.first { it }
            delay(50)
        }
        return vm
    }

    @Test
    fun `startListenSession emits sound_play on the listening surface, not the tab underneath`() {
        val viewModel = givenAViewModel()
        viewModel.setActiveTab(AppTab.VAULT)
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.startListenSession(sound)

        // Regression: this reported `vault`, which made a long listen indistinguishable from a
        // Vault list tap in every play query.
        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.VAULT_LISTEN)
    }

    @Test
    fun `startListenSession does not open a session — a resume would count as a second one`() {
        val viewModel = givenAViewModel()
        viewModel.setActiveTab(AppTab.VAULT)
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.startListenSession(sound)

        // The session pair belongs to the listening screen: a resume after a pause re-enters this
        // method, so emitting here would report one session per play tap.
        fake.assertNotEmitted("listen_session_start")
    }
}
