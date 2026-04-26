/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundDao

/**
 * Helpers to seed and tear down sound data for instrumented UI tests.
 *
 * Custom sounds are written to `getExternalFilesDir(Music)/<file>` (matching what
 * [com.github.barriosnahuel.vossosunboton.commons.file.getFile] resolves to) and registered
 * via [SoundDao]. The audio payload comes from `androidTest/assets/test_sound.mp3` —
 * a ~200ms silent clip so playback tests run fast and predictably.
 */
internal object TestData {
    private const val TEST_ASSET = "test_sound.mp3"
    private const val PREFS_FILE = "my-prefs"

    private val dao = SoundDao()

    /**
     * Wipes all SharedPreferences used by the app and deletes every file in the Music
     * external dir. Safe to call between tests — leaves no state behind.
     */
    fun clearAll(context: Context) {
        context
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

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
        return (1..count).map { index ->
            val name = "custom_$index"
            val fileName = "$name.mp3"
            copyAssetToMusicDir(context, fileName)
            val sound = Sound(name, fileName)
            dao.save(context, sound)
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
        dao.savePin(context, soundName, true)
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
