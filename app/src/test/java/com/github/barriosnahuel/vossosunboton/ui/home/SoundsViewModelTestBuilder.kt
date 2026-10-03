/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStore
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStoreProvider
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout

/** Headroom for the DataStore → repo → loadSounds chain on a loaded CI machine; a passing await returns as soon as the gate opens. */
internal const val INITIAL_LOAD_TIMEOUT_MS = 10_000L

/**
 * The one way tests build a real [SoundsViewModel]: constructs it and blocks until its init load
 * (`isInitialLoadComplete`) has landed, so state a test injects afterwards is never overwritten
 * mid-test by that load. Bounded by [INITIAL_LOAD_TIMEOUT_MS]: a load that never lands fails by name
 * instead of hanging CI.
 *
 * [trackIn] receives the VM **before** the await, so the test's `cancelAndJoinAll()` teardown still
 * stops it when the await times out. Defaults mirror the production constructor, except
 * [ioDispatcher] (the `UnconfinedTestDispatcher` every caller uses). Does not install an analytics
 * fake — that stays the test's choice.
 *
 * Grep-enforced: `SoundsViewModel(` in test sources only appears here, or on a line carrying
 * `// vm-await-ok` (scripts/check-adr-invariants.sh). See CONTRIBUTING.md § Awaiting multiple async inputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
internal fun buildLoadedSoundsViewModel(
    trackIn: MutableList<SoundsViewModel>? = null,
    application: Application = ApplicationProvider.getApplicationContext(),
    ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    searchDebounceMs: Long = 200L,
    welcomeStore: WelcomeStickerStore = WelcomeStickerStore(application),
    shareFeature: ShareFeature = ShareFeature.instance,
    draftStore: RecorderDraftStore = RecorderDraftStoreProvider.get(application),
): SoundsViewModel {
    val vm =
        SoundsViewModel(
            application,
            ioDispatcher = ioDispatcher,
            searchDebounceMs = searchDebounceMs,
            welcomeStore = welcomeStore,
            shareFeature = shareFeature,
            draftStore = draftStore,
        )
    trackIn?.add(vm)
    awaitInitialLoad(vm.isInitialLoadComplete)
    return vm
}

/**
 * Blocks until [gate] is `true`, or fails with an [AssertionError] naming the gate (the
 * [TimeoutCancellationException] is kept as its cause) after [timeoutMs].
 */
internal fun awaitInitialLoad(
    gate: StateFlow<Boolean>,
    timeoutMs: Long = INITIAL_LOAD_TIMEOUT_MS,
) {
    runBlocking {
        try {
            withTimeout(timeoutMs) { gate.first { it } }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("SoundsViewModel.isInitialLoadComplete did not open within $timeoutMs ms", e)
        }
    }
}
