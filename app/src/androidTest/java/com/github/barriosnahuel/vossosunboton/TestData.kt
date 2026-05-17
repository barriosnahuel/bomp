/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Helpers to seed and tear down sound data for instrumented UI tests.
 *
 * Custom sounds are written to `getExternalFilesDir(Music)/<file>` (matching what
 * [com.github.barriosnahuel.vossosunboton.commons.file.getFile] resolves to) and registered
 * via [SoundsRepository]. The audio payload comes from `androidTest/assets/test_sound.mp3` —
 * a 5s silent clip. The duration matters: shorter clips finish so fast that the
 * `isPlaying = true → false` window can close before Compose commits the playing-state
 * frame on a starved main thread (post-install codec spin-up + jank), turning
 * `tapPlaySwapsPlayIconToPause` into a flake. 5s gives the test a comfortable margin
 * over any realistic prepare()/recompose latency.
 */
internal object TestData {
    private const val TEST_ASSET = "test_sound.mp3"

    // 52 MB — comfortably above the AddButtonFeature.MAX_AUDIO_BYTES cap (50 MB) without being
    // brittle to a couple-MB tweak of the cap. Sparse-file allocation makes the actual disk usage
    // negligible.
    private const val OVERSIZE_BYTES: Long = 52L * 1024 * 1024

    private fun repo(context: Context) = SoundsRepository(context)

    private fun collectionsRepo(context: Context) = CollectionsRepository(context, onError = { /* swallow in tests */ })

    /**
     * Wipes the sounds DataStore and deletes every file in the Music external dir. Also marks the
     * welcome sticker as already-consumed so it doesn't appear at row 0 of MY_SOUNDS during tests
     * that aren't specifically exercising the welcome flow — existing instrumented tests assume
     * "exactly one share/play button" semantics. The welcome sticker has its own dedicated
     * Robolectric coverage in `WelcomeStickerScreenTest` and `SoundsViewModelTest`.
     *
     * Safe to call between tests — leaves no state behind.
     */
    fun clearAll(context: Context) {
        runBlocking {
            repo(context).clearForTest()
            collectionsRepo(context).clearForTest()
            // Hide welcome by default in instrumented tests; opt-in via [enableWelcomeSticker].
            val welcome = WelcomeStickerStore(context)
            welcome.clearForTest()
            welcome.consume()
        }
        // Process-scoped singleton — survives across tests in the same instrumentation process,
        // so reset it explicitly to keep biometric-gate tests deterministic.
        VaultSessionState.clearForTest()

        context
            .getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.listFiles()
            ?.forEach { it.delete() }
        previewAudioDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Seeds a playable preview-audio file in the SUT's `cacheDir/preview-audio/` directory and
     * returns a `content://` URI minted by the debug-only [AndroidTestFileProvider]. Each call
     * produces a fresh file (random suffix) so tests can run concurrently or repeatedly without
     * collisions. The payload is the same 5s silent clip used by [seedCustomSounds], giving
     * MediaPlayer enough margin to prepare/start without flaking on a starved main thread.
     *
     * Pair the returned URI with `Intent.FLAG_GRANT_READ_URI_PERMISSION` on the launching intent
     * so the SUT process can open the stream when AddButtonActivity (or any consumer) resolves it.
     */
    fun seedPreviewAudio(context: Context): Uri {
        val dir = previewAudioDir(context).apply { mkdirs() }
        val file = dir.resolve("preview-audio-${UUID.randomUUID()}.mp3")
        InstrumentationRegistry.getInstrumentation().context.assets.open(TEST_ASSET).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return providerUri(context, file)
    }

    /**
     * Seeds a `content://` URI for a non-audio file (`.txt`). The `FileProvider`'s
     * [android.content.ContentResolver.getType] returns `text/plain` for `.txt` (per
     * [java.net.URLConnection.guessContentTypeFromName]), exercising the MIME-rejection branch
     * of `AddButtonFeature.validateContentUri`. Payload is 4 bytes — size is irrelevant for the
     * MIME check.
     */
    fun seedTextFileUri(context: Context): Uri {
        val dir = previewAudioDir(context).apply { mkdirs() }
        val file = dir.resolve("preview-bogus-${UUID.randomUUID()}.txt")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return providerUri(context, file)
    }

    /**
     * Seeds an oversized (`>` 50 MB) `content://` URI as a sparse file via [RandomAccessFile.setLength],
     * so the seeding cost is O(1) on the filesystem regardless of nominal size. The
     * `FileProvider`'s `openAssetFileDescriptor(...).length` reports the sparse length, which
     * `AddButtonFeature.resolveSize` reads — exercising the size-cap rejection branch. Extension
     * stays `.mp3` so the MIME check passes and the validator reaches the size step.
     */
    fun seedOversizedAudioUri(context: Context): Uri {
        val dir = previewAudioDir(context).apply { mkdirs() }
        val file = dir.resolve("preview-oversize-${UUID.randomUUID()}.mp3")
        RandomAccessFile(file, "rw").use { it.setLength(OVERSIZE_BYTES) }
        return providerUri(context, file)
    }

    private fun providerUri(
        context: Context,
        file: java.io.File,
    ): Uri = FileProvider.getUriForFile(context, "${context.packageName}.androidtest.fileprovider", file)

    private fun previewAudioDir(context: Context) = context.cacheDir.resolve("preview-audio")

    /**
     * Seeds [count] custom sounds named `custom_1`, `custom_2`, ... each backed by a copy
     * of the bundled test asset. Returns the list of [Sound]s that were saved.
     */
    fun seedCustomSounds(
        context: Context,
        count: Int = 1,
    ): List<Sound> {
        require(count > 0) { "count must be positive" }
        val r = repo(context)
        return (1..count).map { index ->
            val name = "custom_$index"
            val fileName = "$name.mp3"
            copyAssetToMusicDir(context, fileName)
            // Deterministic id derived from the seeded name keeps assertions stable across runs
            // and matches the production constraint that ids are minted once at save time.
            val sound = Sound(id = "custom:$name", name = name, file = fileName)
            runBlocking { r.save(sound) }
            sound
        }
    }

    /**
     * Marks the [sound] as pinned. The sound must already exist (custom or bundled). Takes the
     * full [Sound] so the id-keyed [SoundsRepository.savePin] (ADR 0008) gets both fields.
     */
    fun pin(
        context: Context,
        sound: Sound,
    ) {
        runBlocking { repo(context).savePin(sound.id, sound.name, true) }
    }

    /**
     * Opt-in for instrumented tests that exercise the welcome flow. Clears the
     * `consumed = true` default applied by [clearAll] so the welcome sticker appears at row 0
     * of MY_SOUNDS again. Call AFTER [clearAll] in `@Before`, or whenever a fresh-install state
     * is needed.
     */
    fun enableWelcomeSticker(context: Context) {
        runBlocking { WelcomeStickerStore(context).clearForTest() }
    }

    /**
     * Seeds a public collection named [name] containing [audioIds]. Returns the new collection id
     * so the caller can pass it to [SoundsViewModel.selectMySoundsFilter] or assert against a chip.
     *
     * Idempotent across tests because `clearAll` wipes the underlying DataStore.
     */
    fun seedPublicCollection(
        context: Context,
        name: String,
        audioIds: List<String> = emptyList(),
    ): String = seedCollection(context, name, CollectionProfile.GENERIC_PUBLIC, audioIds)

    /**
     * Seeds a private collection named [name] containing [audioIds]. The system Baúl is seeded by
     * the repo on first read; if your test needs it visible without creating other private
     * collections, call [touchPrivateCollections] in `@Before` so the seed lands.
     */
    fun seedPrivateCollection(
        context: Context,
        name: String,
        audioIds: List<String> = emptyList(),
    ): String = seedCollection(context, name, CollectionProfile.VAULT, audioIds)

    /**
     * Forces the repo to seed the system Baúl collection without creating any other collection.
     * Useful for tests that need to assert against the system chip after [clearAll] reset the store.
     */
    fun touchPrivateCollections(context: Context): List<Collection> =
        runBlocking { collectionsRepo(context).collections.first() }

    /**
     * Marks the per-session Vault unlock as already granted so a test that doesn't exercise the
     * biometric gate can land directly on the Vault body. Pair with [VaultSessionState.clearForTest]
     * in `@After` (already wired into [clearAll]).
     */
    fun markVaultOpen() {
        VaultSessionState.markVaultOpen()
    }

    private fun seedCollection(
        context: Context,
        name: String,
        profile: CollectionProfile,
        audioIds: List<String>,
    ): String =
        runBlocking {
            val repo = collectionsRepo(context)
            val created = repo.create(name = name, profile = profile)
            audioIds.forEach { audioId -> repo.addAudio(created.id, audioId) }
            created.id
        }

    private fun copyAssetToMusicDir(
        context: Context,
        destFileName: String,
    ) {
        val musicDir =
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: error("External files dir for Music is unavailable")
        musicDir.mkdirs()
        val dest = musicDir.resolve(destFileName)
        InstrumentationRegistry.getInstrumentation().context.assets.open(TEST_ASSET).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
