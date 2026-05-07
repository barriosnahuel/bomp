/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.runBlocking

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

    private fun repo(context: Context) = SoundsRepository(context)

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
            // Hide welcome by default in instrumented tests; opt-in via [enableWelcomeSticker].
            val welcome = WelcomeStickerStore(context)
            welcome.clearForTest()
            welcome.consume()
        }

        context
            .getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.listFiles()
            ?.forEach { it.delete() }
    }

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
            val sound = Sound(name, fileName)
            runBlocking { r.save(sound) }
            sound
        }
    }

    /**
     * Marks [soundName] as pinned. The sound must already exist (custom or bundled).
     */
    fun pin(
        context: Context,
        soundName: String,
    ) {
        runBlocking { repo(context).savePin(soundName, true) }
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
