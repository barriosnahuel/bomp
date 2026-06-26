/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A captured-but-unsaved clip that survived the process: its temp [file] and recorded [durationMs]. */
data class RecorderDraft(
    val file: File,
    val durationMs: Long,
)

/**
 * Persists the *pending recording draft* — a clip the user captured but neither saved nor discarded —
 * so it survives backgrounding, a launcher re-entry (which `clearTop`s [RecordingActivity]), and a
 * process death (ADR 0019 § Draft recovery). The clip bytes already live in `cacheDir/recordings/`;
 * this only records which file is the draft and its duration, so the Landing banner can offer to
 * resume it. Interface + [DataStoreRecorderDraftStore] impl mirrors the `FirstFlagStore` precedent so
 * the ViewModels can take a deterministic fake.
 */
interface RecorderDraftStore {
    /** Reactive pending draft — emits the draft while its temp file exists, `null` otherwise. */
    val draft: Flow<RecorderDraft?>

    /** The pending draft right now, file-existence-validated. Clears stale metadata and returns null. */
    suspend fun current(): RecorderDraft?

    /** Records [file] (under `cacheDir/recordings/`) and its [durationMs] as the pending draft. */
    fun save(
        file: File,
        durationMs: Long,
    )

    /** Forgets the pending draft (after save/discard). Does not delete the file — the caller owns that. */
    fun clear()
}

/**
 * DataStore-backed [RecorderDraftStore]. The draft file lives in the OS-evictable cache, so
 * [draft]/[current] re-validate existence on every read and self-heal (clear the stale metadata) when
 * the OS reclaimed the bytes — the caller then sees "no draft" rather than a dangling resume.
 * File-existence checks run on [Dispatchers.IO] ([flowOn]) so a main-thread collector never trips
 * StrictMode.
 */
class DataStoreRecorderDraftStore(
    context: Context,
    // Process-lived so save/clear complete even when the Activity finishes right after (back-discard,
    // handoff). limitedParallelism(1) serialises them so a save() then clear() can't reorder on disk.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) : RecorderDraftStore {
    private val appContext = context.applicationContext
    private val store: DataStore<Preferences> = appContext.recorderDraftStore

    override val draft: Flow<RecorderDraft?> =
        store.data
            .map { prefs -> prefs.toDraft() }
            .flowOn(Dispatchers.IO)

    override suspend fun current(): RecorderDraft? =
        withContext(Dispatchers.IO) {
            val draft = store.data.first().toDraft()
            if (draft == null) clear()
            draft
        }

    override fun save(
        file: File,
        durationMs: Long,
    ) {
        scope.launch {
            store.edit {
                it[KEY_FILE] = file.name
                it[KEY_DURATION] = durationMs
            }
        }
    }

    override fun clear() {
        scope.launch { store.edit { it.clear() } }
    }

    private fun Preferences.toDraft(): RecorderDraft? {
        val name = this[KEY_FILE]
        val duration = this[KEY_DURATION]
        if (name == null || duration == null) return null
        val file = RecorderTempFiles.resolve(appContext, name)
        return if (file.exists()) RecorderDraft(file, duration) else null
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        withContext(Dispatchers.IO) { store.edit { it.clear() } }
    }

    companion object {
        const val DATASTORE_NAME = "recorder-draft"
        private val KEY_FILE = stringPreferencesKey("draft_file")
        private val KEY_DURATION = longPreferencesKey("draft_duration_ms")
    }
}

/**
 * Process-singleton [RecorderDraftStore] so the recorder and the Landing banner share ONE instance —
 * one write scope, one ordering domain (a save then clear from different ViewModels can't reorder),
 * and no per-ViewModel-construction scope leak. Manual DI (ADR 0002), mirroring `RecorderEngineProvider`.
 */
object RecorderDraftStoreProvider {
    @Volatile private var instance: RecorderDraftStore? = null

    fun get(context: Context): RecorderDraftStore =
        instance ?: synchronized(this) {
            instance ?: DataStoreRecorderDraftStore(context).also { instance = it }
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun setForTest(store: RecorderDraftStore?) {
        instance = store
    }
}

private val Context.recorderDraftStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreRecorderDraftStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Tracker.track(RuntimeException("Recorder draft DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
