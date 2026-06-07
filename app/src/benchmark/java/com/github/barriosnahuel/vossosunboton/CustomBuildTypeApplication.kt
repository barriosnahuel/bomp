/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Application
import android.content.Intent
import com.github.barriosnahuel.vossosunboton.commons.android.error.ErrorTrackerTree
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Build-type specifics for the `benchmark` variant: release-like (no StrictMode, no debug seeding
 * corpus), plus an on-demand synthetic-corpus seeder used **only** by the :macrobenchmark scroll
 * benchmark to control the list size (the scroll-jank-vs-N question, H2).
 *
 * The `benchmark` variant overrides the release source set's [CustomBuildTypeApplication]; the
 * synthetic seeding never reaches release or debug. Triggered via [SEED_COUNT_EXTRA] on the launch
 * intent — kept in sync with the benchmark in :macrobenchmark.
 */
internal abstract class CustomBuildTypeApplication : Application() {
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        Timber.plant(ErrorTrackerTree())

        super.onCreate()
    }

    /**
     * Seeds exactly [count][SEED_COUNT_EXTRA] synthetic sounds (idempotent: a no-op when the corpus
     * already has that many, so repeated benchmark iterations don't re-pay the cost). Fire-and-forget;
     * the benchmark waits for the list to render before measuring.
     */
    fun seedDebugSoundsIfRequested(intent: Intent) {
        val count = intent.getIntExtra(SEED_COUNT_EXTRA, 0)
        if (count <= 0) {
            return
        }
        seedScope.launch { seedSyntheticSounds(count) }
    }

    private suspend fun seedSyntheticSounds(count: Int) {
        val repo = SoundsRepository(this)
        val existing = repo.sounds.first().filter { it.id.startsWith(SYNTHETIC_ID_PREFIX) }
        if (existing.size == count) {
            return
        }
        existing.forEach { repo.delete(it) }
        repeat(count) { index ->
            repo.save(Sound("$SYNTHETIC_ID_PREFIX$index", "$SYNTHETIC_NAME_PREFIX $index", "$SYNTHETIC_ID_PREFIX$index.dat"))
        }
    }
}

/** Launch-intent extra carrying how many synthetic sounds to seed. Keep in sync with :macrobenchmark. */
const val SEED_COUNT_EXTRA = "benchmark_seed_count"

/** Display-name prefix the benchmark waits on to confirm seeding rendered. Keep in sync with :macrobenchmark. */
const val SYNTHETIC_NAME_PREFIX = "Benchmark sound"

private const val SYNTHETIC_ID_PREFIX = "benchmark:"
