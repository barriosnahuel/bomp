/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data.manager

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.commons.file.deleteFile
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.StoredSound
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber

/**
 * Single source of truth for sound metadata persistence. Backed by Jetpack DataStore Preferences
 * with a single JSON-encoded payload (see [StoredSound]).
 *
 * Brand-aligned name `bomps` doubles as the on-disk filename (`dataDir/datastore/bomps.preferences_pb`)
 * referenced from [BACKUP_FILE_PATH] so Auto Backup includes the right file. If the constant changes,
 * the backup XML breaks silently — `BackupRulesTest` enforces the contract.
 */
@Suppress("TooManyFunctions") // Persistence facade: many small save*/migrate methods, one concern.
class SoundsRepository(
    private val context: Context,
    /**
     * Reports recoverable failures (corrupted file replaced, malformed JSON, IOException on the
     * read flow). Defaults to a Timber warning so the model module stays decoupled from
     * Crashlytics — production callers in `:app` should pass `Tracker::track` to surface
     * non-fatals in the dashboard.
     */
    private val onError: (Throwable) -> Unit = { Timber.w(it) },
    /**
     * Invoked when a read recovered legacy/corrupt `sounds_json` data (a pre-stable-id payload that
     * was healed, or any element that had to be dropped). May fire on more than one read until the
     * healed data is persisted, so callers must gate it (e.g. `markFiredOnce`) before emitting
     * telemetry. Decoupled from analytics exactly as [onError] is from Crashlytics; `:app` wires the
     * one-shot `legacy_sounds_recovered` event so the population stays observable (ADR 0018). Default
     * no-op.
     */
    private val onLegacyRecovery: () -> Unit = {},
) {
    private val storedSounds: Flow<List<StoredSound>> =
        context.bompsStore.data
            .catch { e ->
                onError(RuntimeException("Failed reading sounds DataStore", e))
                emit(emptyPreferences())
            }.map { prefs -> decodeSafely(prefs[SOUNDS_KEY]) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * NOTE: each collector currently triggers an independent DataStore read + JSON parse + bundled
     * merge. Today the only collector is [SoundsViewModel] via `.first()`, so this is fine. If
     * Compose ever calls `collectAsState()` on this flow from multiple sites, wrap with
     * `.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)` at a process-lived scope.
     */
    val sounds: Flow<List<Sound>> =
        storedSounds
            .map { stored -> mergeWithBundled(stored, PackagedAudios.get(context)) }
            .flowOn(Dispatchers.IO)

    val durations: Flow<Map<String, Int>> =
        storedSounds
            .map { list -> list.mapNotNull { s -> s.durationMs?.let { s.id to it } }.toMap() }
            .distinctUntilChanged()

    suspend fun save(sound: Sound) {
        validateName(sound.name)
        mutate { current ->
            current.filterNot { it.id == sound.id } + sound.toStored(previous = current.firstOrNull { it.id == sound.id })
        }
    }

    /**
     * Benchmark seeding primitive: in a single atomic write, replaces every sound whose id starts
     * with [idPrefix] with [sounds], each stamped with [durationMs] (sounds and their cached duration
     * share one DataStore key, so this populates both — rows render their duration like a real audio).
     *
     * One write means a deterministic exact-N state: idempotent, shrinkable, and a single
     * recomposition (no per-item churn polluting frame timing). Non-prefixed sounds are untouched.
     * The only caller is the `benchmark` build type's seeder; never used by production flows — see
     * ADR 0015, with the benchmark-only call-site enforced by `scripts/check-adr-invariants.sh`.
     */
    suspend fun replaceSyntheticCorpus(
        idPrefix: String,
        sounds: List<Sound>,
        durationMs: Int,
    ) {
        sounds.forEach { validateName(it.name) }
        mutate { current ->
            current.filterNot { it.id.startsWith(idPrefix) } +
                sounds.map { it.toStored(previous = null).copy(durationMs = durationMs) }
        }
    }

    suspend fun savePin(
        id: String,
        name: String,
        isPinned: Boolean,
    ) {
        validateName(name)
        mutate { current ->
            val existing = current.firstOrNull { it.id == id }
            if (existing != null) {
                current.map { if (it.id == id) it.copy(isPinned = isPinned) else it }
            } else if (isPinned) {
                // Bundled sound being pinned for the first time: persist a stub so the flag survives.
                // We deliberately skip writing a stub for `isPinned = false` to avoid useless growth.
                current + StoredSound(id = id, name = name, isPinned = true)
            } else {
                current
            }
        }
    }

    /**
     * Persists [Sound.isVisibleInMySounds] (ADR 0012). Mirrors [savePin]: a plain replace on the
     * existing row (custom sounds always have one), or — only for the non-default `false` — a stub
     * so the flag survives even for an id without a stored row yet.
     */
    suspend fun saveVisibility(
        id: String,
        name: String,
        isVisibleInMySounds: Boolean,
    ) {
        validateName(name)
        mutate { current ->
            val existing = current.firstOrNull { it.id == id }
            if (existing != null) {
                current.map { if (it.id == id) it.copy(isVisibleInMySounds = isVisibleInMySounds) else it }
            } else if (!isVisibleInMySounds) {
                current + StoredSound(id = id, name = name, isVisibleInMySounds = false)
            } else {
                current
            }
        }
    }

    /**
     * One-time migration seeding [Sound.isVisibleInMySounds] for audios created before the explicit
     * flag (ADR 0012). Pre-flag, "shown in My Sounds" was derived as `inPrivate - inPublic`;
     * [privateOnlyIds] is exactly that set (the caller computes it from the collections snapshot).
     * Those audios flip to `false`; everything else keeps the `true` default. Guarded by a one-shot
     * preference key so it runs at most once per install — and so a user who *later* makes a
     * private-only audio visible again is not re-hidden on the next launch.
     */
    suspend fun migrateVisibilityIfNeeded(privateOnlyIds: Set<String>) {
        withContext(Dispatchers.IO) {
            val alreadyMigrated = context.bompsStore.data.first()[VISIBILITY_MIGRATED_KEY] ?: false
            if (alreadyMigrated) return@withContext
            context.bompsStore.edit { prefs ->
                val current = decodeSafely(prefs[SOUNDS_KEY])
                val next =
                    current.map { if (it.id in privateOnlyIds) it.copy(isVisibleInMySounds = false) else it }
                prefs[SOUNDS_KEY] = json.encodeToString(SOUNDS_SERIALIZER, next)
                prefs[VISIBILITY_MIGRATED_KEY] = true
            }
        }
    }

    suspend fun saveDuration(
        id: String,
        name: String,
        durationMs: Int,
    ) {
        validateName(name)
        mutate { current ->
            val existing = current.firstOrNull { it.id == id }
            if (existing != null) {
                current.map { if (it.id == id) it.copy(durationMs = durationMs) else it }
            } else {
                // Bundled sound being played: persist a stub so the cached duration survives.
                current + StoredSound(id = id, name = name, durationMs = durationMs)
            }
        }
    }

    suspend fun rename(
        id: String,
        newName: String,
    ) {
        validateName(newName)
        mutate { current ->
            current.map { if (it.id == id) it.copy(name = newName) else it }
        }
    }

    suspend fun delete(sound: Sound): Boolean =
        withContext(Dispatchers.IO) {
            val file =
                checkNotNull(sound.file) {
                    "Requested to delete a user's custom button without a persisted file in file system"
                }
            val deletedFromDisk = deleteFile(context, file)
            if (deletedFromDisk) {
                mutate { current -> current.filterNot { it.id == sound.id } }
            }
            deletedFromDisk
        }

    /**
     * Test-only escape hatch to wipe the underlying DataStore between tests. Public visibility
     * is required because the DataStore delegate is process-singleton: tests in `:app` (which
     * cannot use `internal` model symbols) need a way to reset state, and deleting the
     * `.preferences_pb` file directly does not work since DataStore caches state in memory.
     *
     * `@VisibleForTesting(otherwise = NONE)` instructs Android Lint to fail the build if any
     * non-test caller appears.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        withContext(Dispatchers.IO) {
            context.bompsStore.edit { it.clear() }
        }
    }

    /**
     * Test-only escape hatch to corrupt the JSON payload, used to assert `decodeSafely`'s
     * fallback behavior. Same Lint protection as [clearForTest].
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun setRawJsonForTest(raw: String) {
        withContext(Dispatchers.IO) {
            context.bompsStore.edit { prefs -> prefs[SOUNDS_KEY] = raw }
        }
    }

    /**
     * Read-modify-write inside a single `edit` block — guarantees atomicity even
     * if multiple coroutines mutate concurrently. Never read the Flow externally,
     * mutate the list, then write back: the gap between read and write is where
     * concurrent writers lose data.
     */
    private suspend inline fun mutate(crossinline transform: (List<StoredSound>) -> List<StoredSound>) {
        withContext(Dispatchers.IO) {
            context.bompsStore.edit { prefs ->
                val current = decodeSafely(prefs[SOUNDS_KEY])
                val next = transform(current)
                prefs[SOUNDS_KEY] = json.encodeToString(SOUNDS_SERIALIZER, next)
            }
        }
    }

    /**
     * Decodes the `sounds_json` payload, recovering legacy/partially-corrupt data instead of wiping
     * the list (ADR 0008 → 0018).
     *
     * Fast path (every post-migration install — the overwhelming majority): a clean payload decodes
     * in a single streaming pass with no intermediate JSON tree. The element-by-element recovery
     * (tree parse + per-element heal/decode + de-dup) runs only when the fast path proves the payload
     * is not clean — it threw, or it decoded but carries a blank `id` (which only legacy/corrupt data
     * produces). So new users never pay the recovery cost, and old users still get fully healed.
     */
    private fun decodeSafely(raw: String?): List<StoredSound> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val decoded = json.decodeFromString(SOUNDS_SERIALIZER, raw)
            // A blank id only comes from legacy/corrupt data → recover; otherwise the fast result wins.
            if (decoded.none { it.id.isBlank() }) decoded else recoverElementwise(raw)
        } catch (ignored: SerializationException) {
            recoverElementwise(raw)
        } catch (ignored: IllegalArgumentException) {
            recoverElementwise(raw)
        }
    }

    /**
     * Slow path: parse the list to a tree once, heal + decode each element independently, drop any
     * that cannot be recovered (a single bad record can never wipe the rest — the failure mode that
     * lost a real user's audio), and de-duplicate derived ids keep-first. Only a corrupt root (not
     * valid JSON, or a non-array) degrades to an empty list with an `onError`; per-element drops are
     * silent recovery.
     */
    private fun recoverElementwise(raw: String): List<StoredSound> {
        val array = parseSoundsArray(raw) ?: return emptyList()
        val seenIds = HashSet<String>()
        val recovered = array.mapNotNull { decodeElement(it) }.filter { seenIds.add(it.id) }
        // Reaching here means the fast path rejected the payload (threw or had a blank id) yet the
        // root is a valid array — i.e. legacy/corrupt data we just healed. Signal it (gated by the
        // caller) so the legacy population stays observable; a malformed/non-array root returned a
        // null array above without firing.
        onLegacyRecovery()
        return recovered
    }

    /** Parses [raw] to a [JsonArray], or null (with an `onError`) if it is not valid JSON or not an array. */
    private fun parseSoundsArray(raw: String): JsonArray? {
        val root =
            try {
                json.parseToJsonElement(raw)
            } catch (e: SerializationException) {
                onError(RuntimeException("Malformed sounds_json payload, recovering with empty list", e))
                return null
            }
        return root as? JsonArray ?: run {
            onError(
                RuntimeException(
                    "Invalid sounds_json structure, recovering with empty list",
                    IllegalArgumentException("sounds_json root is not a JSON array"),
                ),
            )
            null
        }
    }

    /**
     * Decodes one array element into a [StoredSound], or null if it is unrecoverable (a non-object,
     * an object with no usable `id` and no `name` to derive one from, or an object that still fails
     * strict decode — e.g. a non-string `id` or a missing required field). Returning null drops just
     * that element; callers keep the rest.
     */
    private fun decodeElement(element: JsonElement): StoredSound? {
        val healed = (element as? JsonObject)?.let { healLegacyId(it) } ?: return null
        return try {
            json.decodeFromJsonElement(StoredSound.serializer(), healed)
        } catch (ignored: SerializationException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

    /**
     * Heals a record written before [StoredSound.id] became required (ADR 0008 → 0018): when `id` is
     * missing or blank, derives `"custom:<name>"` from the element's pre-0008 name identity (ADR 0007)
     * — a stable, deterministic key reconstructed from the only stable field a legacy record has (not
     * the production custom-id format, which is a random UUID minted at save time). Returns the object
     * unchanged when it already has a usable `id`, or null when there is no `name` to derive one from.
     * Healed ids persist on the next [mutate]; see ADR 0018 for the bundled-stub limitation.
     */
    private fun healLegacyId(obj: JsonObject): JsonObject? {
        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull
        if (!id.isNullOrBlank()) return obj
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull
        return if (name.isNullOrBlank()) null else JsonObject(obj + ("id" to JsonPrimitive("custom:$name")))
    }

    private fun mergeWithBundled(
        stored: List<StoredSound>,
        bundled: List<Sound>,
    ): List<Sound> {
        val byId = stored.associateBy { it.id }
        val customSounds =
            stored
                .filter { it.file != null }
                .map { it.toCustomSound(context) }
        val bundledSounds =
            bundled.map { sound ->
                val storedFlags = byId[sound.id]
                if (storedFlags != null && storedFlags.isPinned) {
                    sound.copy(isPinned = true)
                } else {
                    sound
                }
            }
        return customSounds + bundledSounds
    }

    private fun StoredSound.toCustomSound(context: Context): Sound {
        val soundFile = file?.let { getFile(context, it) }
        val dateAdded = soundFile?.takeIf { it.exists() }?.lastModified()
        return Sound(
            id = id,
            name = name,
            file = file,
            rawRes = 0,
            isPlaying = false,
            isFavorite = isFavorite,
            dateAdded = dateAdded,
            isPinned = isPinned,
            isVisibleInMySounds = isVisibleInMySounds,
            source = source,
        )
    }

    private fun Sound.toStored(previous: StoredSound?): StoredSound =
        StoredSound(
            id = id,
            name = name,
            file = file,
            isFavorite = isFavorite || previous?.isFavorite == true,
            isPinned = isPinned || previous?.isPinned == true,
            durationMs = previous?.durationMs,
            // Visibility is owned by `saveVisibility`, not `save`: preserve the stored value on an
            // upsert so a generic `save` can never silently reset a hidden audio back to visible.
            // A brand-new audio (previous == null) takes the Sound's value (default `true`).
            isVisibleInMySounds = previous?.isVisibleInMySounds ?: isVisibleInMySounds,
            // Provenance is immutable per audio (ADR 0019): preserve the stored value on an upsert so
            // a generic `save` can't reset a RECORDED audio to the IMPORTED default. New audio takes
            // the Sound's value (recorder passes RECORDED; import keeps the default).
            source = previous?.source ?: source,
        )

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Sound name must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) {
            "Sound name must be at most $MAX_NAME_LENGTH chars (was ${name.length})"
        }
    }

    companion object {
        /**
         * On-disk filename of the DataStore. Appended to `dataDir/datastore/`. Public because
         * `app_backup_rules.xml` and `app_data_extraction_rules.xml` reference it; `BackupRulesTest`
         * asserts the XML uses exactly this filename so renames cannot silently break Auto Backup.
         */
        const val BACKUP_FILE_PATH = "datastore/$DATASTORE_NAME.preferences_pb"

        private val SOUNDS_KEY = stringPreferencesKey("sounds_json")

        /** One-shot guard for [migrateVisibilityIfNeeded] (ADR 0012). */
        private val VISIBILITY_MIGRATED_KEY = booleanPreferencesKey("visibility_migrated_v1")
        private val SOUNDS_SERIALIZER = ListSerializer(StoredSound.serializer())
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                coerceInputValues = true
            }
    }
}

private const val DATASTORE_NAME = "bomps"
private const val MAX_NAME_LENGTH = 200

/**
 * File-private DataStore delegate. The corruption handler logs through Timber instead of the
 * `onError` callback because the delegate is a top-level extension and cannot capture
 * `SoundsRepository` constructor parameters. In practice, corruption is rare enough that losing
 * the Crashlytics breadcrumb here (vs. the in-class catch blocks) is an acceptable trade-off
 * for keeping `:model` decoupled from Crashlytics.
 *
 * NOTE on cold-restart testing: the delegate is process-singleton, so unit tests cannot truly
 * simulate a cold app restart — re-instantiating `SoundsRepository(context)` reuses the same
 * underlying `DataStore`. End-to-end restore-from-Drive coverage lives in the manual Auto Backup
 * smoke step documented in the migration plan (`adb shell bmgr backupnow`).
 */
private val Context.bompsStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Timber.e(exception, "Bomps DataStore corruption recovered with empty prefs")
            emptyPreferences()
        },
)
