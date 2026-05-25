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
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Debug-only convenience: pre-populates a clean install with a realistic board — custom-named
 * audios, public collections and a couple of Vault-only audios — so a fresh debug build (and the
 * marketing screenshots) read like a real Bomper's collection instead of an empty board or the raw
 * bundled-sample names. Triggered by [EXTRA_SEED_MY_SOUNDS] on the launch intent — see
 * `scripts/install-debug-seeded.sh`. The seed corpus is locale-aware ([DebugSeedCorpus]).
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
            val raws = PackagedAudios.get(context)
            if (raws.isEmpty()) {
                Timber.w("Debug seed skipped: no bundled audio present in the raw resource dir")
                return@launch
            }
            val corpus = DebugSeedCorpus.forLocale(Locale.getDefault())
            // Reuse the bundled audio bytes (cycling) as the payload for each realistically-named
            // seed item — the corpus only defines names/membership, not audio.
            val sources =
                corpus.items.mapIndexed { index, item ->
                    val raw = raws[index % raws.size]
                    SeedSource(
                        key = item.key,
                        name = item.name,
                        publicCollections = item.publicCollections,
                        privateCollections = item.privateCollections,
                        inSystemVault = item.inSystemVault,
                        visibleInMySounds = item.visibleInMySounds,
                    ) { context.resources.openRawResource(raw.rawRes) }
                }
            runCatching {
                seed(
                    context = context,
                    sources = sources,
                    limit = sources.size,
                    publicCollections = corpus.publicCollections,
                    privateCollections = corpus.privateCollections,
                )
            }.onFailure { Timber.w(it, "Debug My Sounds seeding failed") }
        }
    }

    /**
     * Copies up to [limit] [sources] into the Music external dir and upserts each via
     * [SoundsRepository], then ensures [publicCollections] + [privateCollections] exist and applies
     * each source's collection membership and Vault-only visibility. Identity is keyed on
     * [SeedSource.key] (a stable, filename-safe id), so two audios sharing a display name never
     * collide and re-running never duplicates entries — audio upserts skip ids already present,
     * collection creation skips names already present, and `addAudio` is idempotent. Decoupled from
     * [PackagedAudios] through [SeedSource] so it stays unit-testable without the (uncommitted)
     * bundled debug audio.
     */
    @VisibleForTesting
    @Suppress("LongParameterList") // Debug-only seeding facade; each arg maps to one corpus axis.
    suspend fun seed(
        context: Context,
        sources: List<SeedSource>,
        limit: Int = DEFAULT_SEED_COUNT,
        publicCollections: List<String> = emptyList(),
        privateCollections: List<String> = emptyList(),
    ) {
        val soundsRepo = SoundsRepository(context)
        val collectionsRepo = CollectionsRepository(context)
        val planned = sources.take(limit)

        val collectionsByName =
            ensureCollections(collectionsRepo, publicCollections, privateCollections)
        // The system Baúl is auto-seeded on the first collections read above; resolve it by flag so
        // we never hardcode its id.
        val systemVaultId = collectionsByName.values.firstOrNull { it.isSystem && it.isPrivate }?.id

        val existingIds =
            soundsRepo.sounds
                .first()
                .filterNot { it.isBundled() }
                .map { it.id }
                .toSet()

        var seeded = 0
        planned.forEach { source ->
            val id = "$SEED_ID_PREFIX${source.key}"
            if (id !in existingIds) {
                val target = getFile(context, "seed-${source.key}.mp3")
                if (!target.exists()) {
                    copy(source.open(), FileOutputStream(target))
                }
                soundsRepo.save(Sound(id, source.name, target.name))
                if (!source.visibleInMySounds) {
                    soundsRepo.saveVisibility(id, source.name, isVisibleInMySounds = false)
                }
                seeded++
            }
            // Membership is idempotent, so we apply it even when the audio already existed.
            (source.publicCollections + source.privateCollections).forEach { name ->
                collectionsByName[name]?.let { collectionsRepo.addAudio(it.id, id) }
            }
            if (source.inSystemVault) {
                systemVaultId?.let { collectionsRepo.addAudio(it, id) }
            }
        }
        Timber.i("Seeded %d debug My Sounds", seeded)
    }

    /**
     * Creates any missing [public] / [private] collections (skipping names that already exist —
     * including the system Baúl) and returns the resulting name→[Collection] index, so callers can
     * resolve ids for membership.
     */
    private suspend fun ensureCollections(
        repo: CollectionsRepository,
        public: List<String>,
        private: List<String>,
    ): Map<String, Collection> {
        val existingNames =
            repo.collections
                .first()
                .map { it.name }
                .toSet()
        public.filterNot { it in existingNames }.forEach {
            runCatching { repo.create(it, CollectionProfile.GENERIC_PUBLIC) }
        }
        private.filterNot { it in existingNames }.forEach {
            runCatching { repo.create(it, CollectionProfile.VAULT) }
        }
        return repo.collections.first().associateBy { it.name }
    }
}

/**
 * A seed candidate: a stable, filename-safe [key]; its display [name]; the public and private
 * collection names it joins; whether it also lives in the system Baúl ([inSystemVault]); whether it
 * shows in My Sounds ([visibleInMySounds]); and a factory that opens the raw audio bytes.
 */
internal class SeedSource(
    val key: String,
    val name: String,
    val publicCollections: List<String> = emptyList(),
    val privateCollections: List<String> = emptyList(),
    val inSystemVault: Boolean = false,
    val visibleInMySounds: Boolean = true,
    val open: () -> InputStream,
)
