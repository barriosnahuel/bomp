/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Debug-only convenience: pre-populates a few of the bundled Explore samples as if the user had
 * already saved them to "My Sounds", so a clean debug install isn't an empty board (saves manually
 * re-adding real audios after every reinstall). Triggered by [EXTRA_SEED_MY_SOUNDS] on the launch
 * intent — see `scripts/install-debug-seeded.sh`.
 *
 * Lives in the debug sourceset and is reached through the `CustomBuildTypeApplication` seam, so
 * release builds never reference it (the release seam is a no-op). Mirrors the
 * `CustomBuildTypeApplication` / `DebugTools` / `StrictModeConfigurator` debug-tooling pattern.
 */
internal object DebugSoundSeeder {
    /** Boolean launch-intent extra that triggers seeding. Keep in sync with `install-debug-seeded.sh`. */
    const val EXTRA_SEED_MY_SOUNDS = "debug_seed_my_sounds"

    private const val DEFAULT_SEED_COUNT = 5
    private const val SEED_ID_PREFIX = "seed:"

    fun seedIfRequested(
        context: Context,
        intent: Intent,
    ) {
        if (!intent.getBooleanExtra(EXTRA_SEED_MY_SOUNDS, false)) {
            return
        }
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                val sources =
                    PackagedAudios.get(context).map { sample ->
                        SeedSource(sample.rawRes.toString(), sample.name) {
                            context.resources.openRawResource(sample.rawRes)
                        }
                    }
                seed(context, sources)
            }.onFailure { Timber.w(it, "Debug My Sounds seeding failed") }
        }
    }

    /**
     * Copies up to [limit] [sources] into the Music external dir and upserts each via
     * [SoundsRepository]. Identity is keyed on [SeedSource.key] (the sample's stable, filename-safe
     * id), so two samples sharing a display name never collide and re-running never duplicates
     * entries (a key already present as a custom sound is skipped). Decoupled from [PackagedAudios]
     * through [SeedSource] so it's unit-testable without the (uncommitted) bundled debug audio.
     */
    @VisibleForTesting
    suspend fun seed(
        context: Context,
        sources: List<SeedSource>,
        limit: Int = DEFAULT_SEED_COUNT,
    ) {
        val repo = SoundsRepository(context)
        val existingIds =
            repo.sounds
                .first()
                .filterNot { it.isBundled() }
                .map { it.id }
                .toSet()
        var seeded = 0
        sources.take(limit).forEach { source ->
            val id = "$SEED_ID_PREFIX${source.key}"
            if (id in existingIds) {
                return@forEach
            }
            val target = getFile(context, "seed-${source.key}.mp3")
            if (!target.exists()) {
                copy(source.open(), FileOutputStream(target))
            }
            repo.save(Sound(id, source.name, target.name))
            seeded++
        }
        Timber.i("Seeded %d debug My Sounds", seeded)
    }
}

/**
 * A seed candidate: a stable, filename-safe [key] (production uses the sample's `rawRes`), its
 * display [name], and a factory that opens the raw audio bytes.
 */
internal class SeedSource(
    val key: String,
    val name: String,
    val open: () -> InputStream,
)
