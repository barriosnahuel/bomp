/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * Deterministically tears down every [SoundsViewModel] a test built: cancels each
 * `viewModelScope` and **joins** until its child coroutines fully unwind.
 *
 * A bare `viewModelScope.cancel()` is fire-and-forget — it flips the scope's `Job` to
 * "cancelling" and returns without waiting. The `repo.sounds.drop(1).collect { loadSounds() }`
 * collector each VM starts in `init` then stays parked on the
 * process-singleton `bompsStore` DataStore and can outlive the test: the next test's
 * `clearForTest()` / `save(...)` writes emit through it, re-running `loadSounds` and leaking
 * analytics events (e.g. `milestone_sounds_3`, `search_zero_results`) into the next test's
 * `FakeAnalyticsTracker`. `cancelAndJoin()` waits for the collector to actually stop, closing
 * that cross-test race.
 *
 * Shared by the four `ui/home` test classes that build a `SoundsViewModel`.
 */
internal fun List<SoundsViewModel>.cancelAndJoinAll() {
    runBlocking {
        forEach {
            it.viewModelScope.coroutineContext.job
                .cancelAndJoin()
        }
    }
}
