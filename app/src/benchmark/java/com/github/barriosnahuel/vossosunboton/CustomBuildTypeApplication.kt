/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.app.Application
import android.content.Intent
import com.github.barriosnahuel.vossosunboton.commons.android.error.ErrorTrackerTree
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.FileOutputStream

/**
 * Build-type specifics for the `benchmark` variant: release-like (no StrictMode, no debug seeding
 * corpus), plus an on-demand synthetic-corpus seeder used **only** by the :macrobenchmark benchmarks —
 * scroll list-size control (the scroll-jank-vs-N question, H2) and a playable row for the
 * tap-to-sound latency guardrail.
 *
 * This overrides the release source set's `CustomBuildTypeApplication` for the benchmark variant;
 * the synthetic seeding never reaches release or debug. Architecture + rationale: ADR 0015.
 */
internal abstract class CustomBuildTypeApplication : Application() {
    override fun onCreate() {
        Timber.plant(ErrorTrackerTree())

        super.onCreate()
    }

    /**
     * Seeds exactly [count][SEED_COUNT_EXTRA] synthetic sounds (each stamped with a duration so rows
     * render like a real audio), via the single atomic `replaceSyntheticCorpus`.
     *
     * Runs **synchronously** (`runBlocking`) from `LandingActivity.onCreate`. The benchmark launch
     * intent carries no data URI, so `handleDeeplink` early-returns without touching the lazy
     * ViewModel — meaning the seed write completes before composition instantiates the VM and reads
     * the store. The measured scroll therefore always sees exactly N, never a stale or half-seeded
     * list. (A reactive `repo.sounds` collector in the VM is a second safety net if a read ever beat
     * the write.) Benchmark build only (release-like, no StrictMode); inert for the startup benchmark.
     */
    fun seedDebugSoundsIfRequested(intent: Intent) {
        val count = intent.getIntExtra(SEED_COUNT_EXTRA, 0)
        if (count <= 0) {
            return
        }
        val sounds =
            (0 until count).map { index ->
                Sound("$SYNTHETIC_ID_PREFIX$index", "$SYNTHETIC_NAME_PREFIX $index", "$SYNTHETIC_FILE_PREFIX$index.dat")
            }
        runBlocking {
            SoundsRepository(this@CustomBuildTypeApplication)
                .replaceSyntheticCorpus(SYNTHETIC_ID_PREFIX, sounds, SYNTHETIC_DURATION_MS)
        }
        seedPlayableAudioFile()
    }

    /**
     * Backs synthetic sound 0 with real audio bytes (the bundled welcome mp3) so `TapLatencyBenchmark`
     * can tap it and measure a real prepare+start through the dominant custom-file path. The rest of
     * the corpus stays file-less on purpose: the scroll render never reads the file (ADR 0015), and
     * playback fidelity only needs the one tapped row. Idempotent; still synchronous in `onCreate`.
     */
    private fun seedPlayableAudioFile() {
        val playableFile = getFile(this, "${SYNTHETIC_FILE_PREFIX}0.dat")
        if (playableFile.exists()) {
            return
        }
        resources.openRawResource(R.raw.app_welcome_sticker).let { input ->
            copy(input, FileOutputStream(playableFile))
        }
    }
}

/** Launch-intent extra carrying how many synthetic sounds to seed. Keep in sync with :macrobenchmark. */
const val SEED_COUNT_EXTRA = "benchmark_seed_count"

/** Display-name prefix the benchmark waits on to confirm seeding rendered. Keep in sync with :macrobenchmark. */
const val SYNTHETIC_NAME_PREFIX = "Benchmark sound"

private const val SYNTHETIC_ID_PREFIX = "benchmark:"

/**
 * File-name prefix for synthetic rows. Deliberately NOT the id prefix: a `:` is rejected by the
 * emulated external storage (FUSE) where sound files live, and the playable row's file is real.
 */
private const val SYNTHETIC_FILE_PREFIX = "benchmark_"

/** A plausible audio length so each seeded row renders a duration like a real sound. */
private const val SYNTHETIC_DURATION_MS = 3_000
