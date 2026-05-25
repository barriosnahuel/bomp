/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Guards the listener-leak fix proven by the LeakCanary heap analysis: a destroyed [SoundsViewModel]
 * stayed reachable forever through `PlayerControllerFactory.instance` (a process-singleton) because it
 * registered itself as the playback listener in `init` and never detached. The regression this guards:
 * the user opens the app, navigates around (each visit builds and clears a `SoundsViewModel`), and every
 * cleared ViewModel — with its caches and collectors — is pinned in memory for the life of the process.
 */
internal class SoundsViewModelLifecycleTest : AbstractRobolectricTest() {
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        runBlocking {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            SoundsRepository(ctx).clearForTest()
            WelcomeStickerStore(ctx).clearForTest()
            CollectionsRepository(ctx).clearForTest()
            MySoundsFilterStore(ctx).clearForTest()
            VaultFilterStore(ctx).clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        unmockkAll()
    }

    @Test
    fun `detaches the playback listener when the ViewModel is cleared`() {
        val viewModel = givenAViewModel()
        verify { PlayerControllerFactory.instance.setOnStartStopListener(viewModel) }

        clearViaViewModelStore(viewModel)

        verify { PlayerControllerFactory.instance.removeOnStartStopListener(viewModel) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm =
            SoundsViewModel(
                context,
                ioDispatcher = UnconfinedTestDispatcher(),
                welcomeStore = WelcomeStickerStore(context),
                shareFeature = ShareFeature.instance,
            )
        createdViewModels += vm
        runBlocking { vm.isInitialLoadComplete.first { it } }
        return vm
    }

    /**
     * Drives the real ViewModel teardown: hosting [viewModel] in a [ViewModelStore] and clearing it is
     * what the framework does on `Activity.onDestroy`, and it invokes `onCleared()` — the hook under test.
     */
    private fun clearViaViewModelStore(viewModel: SoundsViewModel) {
        val store = ViewModelStore()
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T = viewModel as T
            },
        )[SoundsViewModel::class.java]
        store.clear()
    }
}
