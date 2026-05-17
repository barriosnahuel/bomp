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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.CollectionAutoCategorize
import com.github.barriosnahuel.vossosunboton.model.CollectionPlaybackUI
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.CollectionRequiredMetadata
import com.github.barriosnahuel.vossosunboton.model.CollectionShareability
import com.github.barriosnahuel.vossosunboton.model.data.StoredCollection
import com.github.barriosnahuel.vossosunboton.model.data.StoredCollectionAccess
import com.github.barriosnahuel.vossosunboton.model.data.StoredCollectionAutoCategorize
import com.github.barriosnahuel.vossosunboton.model.data.StoredCollectionPlaybackUI
import com.github.barriosnahuel.vossosunboton.model.data.StoredCollectionRequiredMetadata
import com.github.barriosnahuel.vossosunboton.model.data.StoredCollectionShareability
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
import timber.log.Timber
import java.util.UUID

/**
 * Single source of truth for [Collection] metadata + audio↔collection links. Backed by a
 * dedicated Jetpack DataStore Preferences file (`collections.preferences_pb`), JSON-encoded.
 *
 * **Backup posture**: this file is intentionally excluded from `app_backup_rules.xml` and
 * `app_data_extraction_rules.xml`. Spec v2.4.0 § 5 declares both public and private collections
 * local-only, and the Vault (private collections) is sensitive content (CLAUDE.md § Security
 * boundaries → Backup hygiene). When sync ships in a later Pro spec, the exclude can be revisited.
 *
 * **System seed**: the first read after install lazily seeds the "Baúl" private system collection
 * via [ensureSystemBaul]. The seed is idempotent — once persisted, it is recognized by its stable
 * id [BAUL_SYSTEM_ID] and never duplicated.
 */
class CollectionsRepository(
    private val context: Context,
    /**
     * Reports recoverable failures (corruption recovered, malformed JSON, IO on the read flow).
     * Defaults to a Timber warning so `:model` stays decoupled from Crashlytics; production
     * callers in `:app` pass `Tracker::track` to surface non-fatals.
     */
    private val onError: (Throwable) -> Unit = { Timber.w(it) },
) {
    private val storedFlow: Flow<List<StoredCollection>> =
        context.collectionsStore.data
            .catch { e ->
                onError(RuntimeException("Failed reading collections DataStore", e))
                emit(emptyPreferences())
            }.map { prefs -> decodeSafely(prefs[COLLECTIONS_KEY]) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * Reactive snapshot of all collections (public + private). Each emission is the full list.
     * Subscribers filter by [Collection.isPrivate] / [Collection.isPublic] as needed.
     *
     * On the first subscription per install, the Baúl seed is committed before the flow emits so
     * the UI never observes an empty Vault tab during normal use. Errors during seed (e.g. disk
     * full) surface via [onError] and the flow emits the un-seeded list — the next mutate retries.
     */
    val collections: Flow<List<Collection>> =
        storedFlow.map { list ->
            if (list.none { it.id == BAUL_SYSTEM_ID }) {
                ensureSystemBaul()
                // Re-read after seeding to avoid emitting the un-seeded snapshot. The cost is one
                // extra `first()` per install; mutate() does its own atomic read.
                decodeStored().map(::toDomain)
            } else {
                list.map(::toDomain)
            }
        }

    suspend fun create(
        name: String,
        profile: CollectionProfile,
    ): Collection {
        val trimmed = name.trim()
        validateName(trimmed)
        ensureNameAvailable(trimmed, profile.access)
        val collection =
            Collection(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                profile = profile,
                isSystem = false,
                createdAt = System.currentTimeMillis(),
            )
        mutate { current -> current + toStored(collection) }
        return collection
    }

    /**
     * Renames [id] to [newName]. No-op if the collection is missing. Throws on blank/too-long
     * names, on duplicate names inside the same scope (case-insensitive), or on system
     * collections (the system Baúl's name is canonical and surfaced via a localized resource
     * string by the UI — letting the user persist a different name would create a UI/data drift
     * because the UI override would still display the resource value).
     */
    suspend fun rename(
        id: String,
        newName: String,
    ) {
        val trimmed = newName.trim()
        validateName(trimmed)
        mutate { current ->
            val existing = current.firstOrNull { it.id == id } ?: return@mutate current
            require(!existing.isSystem) { "Cannot rename a system collection (id=$id)" }
            val accessScope = existing.access
            val nameTaken =
                current.any {
                    it.id != id &&
                        it.access == accessScope &&
                        it.name.equals(trimmed, ignoreCase = true)
                }
            require(!nameTaken) { "A collection named '$trimmed' already exists in this scope" }
            current.map { if (it.id == id) it.copy(name = trimmed) else it }
        }
    }

    /**
     * Deletes [id]. Refuses to delete a system collection — caller must check [Collection.isSystem]
     * up-front; the throw is a programming-error guard, not a UX path.
     * Audios are NOT deleted from disk; they only lose the tag.
     */
    suspend fun delete(id: String) {
        mutate { current ->
            val target = current.firstOrNull { it.id == id } ?: return@mutate current
            require(!target.isSystem) { "Cannot delete a system collection (id=$id)" }
            current.filterNot { it.id == id }
        }
    }

    /** Adds [audioId] to [collectionId]. Idempotent — duplicates are dropped. */
    suspend fun addAudio(
        collectionId: String,
        audioId: String,
    ) {
        mutate { current ->
            current.map { c ->
                if (c.id == collectionId && audioId !in c.audioIds) {
                    c.copy(audioIds = c.audioIds + audioId)
                } else {
                    c
                }
            }
        }
    }

    /** Removes [audioId] from [collectionId]. Idempotent. */
    suspend fun removeAudio(
        collectionId: String,
        audioId: String,
    ) {
        mutate { current ->
            current.map { c ->
                if (c.id == collectionId && audioId in c.audioIds) {
                    c.copy(audioIds = c.audioIds - audioId)
                } else {
                    c
                }
            }
        }
    }

    /**
     * Replaces the full set of [collectionIds] an audio belongs to within [scope]. Lets the
     * Add/Edit screen commit the user's chip selections atomically without read-then-diff:
     * collections outside [scope] keep their links untouched, so the public chip group and the
     * private chip group can be edited independently and committed separately.
     */
    suspend fun setAudioCollections(
        audioId: String,
        collectionIds: Set<String>,
        scope: CollectionAccess,
    ) {
        mutate { current ->
            current.map { c ->
                val storedScope = toStoredAccess(scope)
                if (c.access != storedScope) {
                    c
                } else {
                    val shouldContain = c.id in collectionIds
                    val currentlyContains = audioId in c.audioIds
                    when {
                        shouldContain && !currentlyContains -> c.copy(audioIds = c.audioIds + audioId)
                        !shouldContain && currentlyContains -> c.copy(audioIds = c.audioIds - audioId)
                        else -> c
                    }
                }
            }
        }
    }

    /** Sweeps [audioId] out of every collection. Call when an audio is permanently deleted. */
    suspend fun forgetAudio(audioId: String) {
        mutate { current ->
            current.map { c -> if (audioId in c.audioIds) c.copy(audioIds = c.audioIds - audioId) else c }
        }
    }

    private suspend fun ensureSystemBaul() {
        mutate { current ->
            if (current.any { it.id == BAUL_SYSTEM_ID }) {
                current
            } else {
                current +
                    StoredCollection(
                        id = BAUL_SYSTEM_ID,
                        name = BAUL_SEED_NAME,
                        access = StoredCollectionAccess.PRIVATE,
                        requiredMetadata = StoredCollectionRequiredMetadata.NONE,
                        playbackUI = StoredCollectionPlaybackUI.IMMERSIVE,
                        shareability = StoredCollectionShareability.LISTEN_ONLY,
                        autoCategorize = StoredCollectionAutoCategorize.OFF,
                        isSystem = true,
                        createdAt = System.currentTimeMillis(),
                    )
            }
        }
    }

    private suspend fun ensureNameAvailable(
        name: String,
        access: CollectionAccess,
    ) {
        val storedScope = toStoredAccess(access)
        val list = decodeStored()
        val taken = list.any { it.access == storedScope && it.name.equals(name, ignoreCase = true) }
        require(!taken) { "A collection named '$name' already exists in this scope" }
    }

    private suspend fun decodeStored(): List<StoredCollection> =
        withContext(Dispatchers.IO) {
            decodeSafely(context.collectionsStore.data.first()[COLLECTIONS_KEY])
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        withContext(Dispatchers.IO) {
            context.collectionsStore.edit { it.clear() }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun setRawJsonForTest(raw: String) {
        withContext(Dispatchers.IO) {
            context.collectionsStore.edit { prefs -> prefs[COLLECTIONS_KEY] = raw }
        }
    }

    private suspend inline fun mutate(crossinline transform: (List<StoredCollection>) -> List<StoredCollection>) {
        withContext(Dispatchers.IO) {
            context.collectionsStore.edit { prefs ->
                val current = decodeSafely(prefs[COLLECTIONS_KEY])
                val next = transform(current)
                prefs[COLLECTIONS_KEY] = json.encodeToString(COLLECTIONS_SERIALIZER, next)
            }
        }
    }

    private fun decodeSafely(raw: String?): List<StoredCollection> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(COLLECTIONS_SERIALIZER, raw)
        } catch (e: SerializationException) {
            onError(RuntimeException("Malformed collections_json payload, recovering with empty list", e))
            emptyList()
        } catch (e: IllegalArgumentException) {
            onError(RuntimeException("Invalid collections_json structure, recovering with empty list", e))
            emptyList()
        }
    }

    private fun toDomain(stored: StoredCollection): Collection =
        Collection(
            id = stored.id,
            name = stored.name,
            profile =
                CollectionProfile(
                    access = stored.access.toDomain(),
                    requiredMetadata = stored.requiredMetadata.toDomain(),
                    playbackUI = stored.playbackUI.toDomain(),
                    shareability = stored.shareability.toDomain(),
                    autoCategorize = stored.autoCategorize.toDomain(),
                ),
            isSystem = stored.isSystem,
            createdAt = stored.createdAt,
            audioIds = stored.audioIds,
        )

    private fun toStored(collection: Collection): StoredCollection =
        StoredCollection(
            id = collection.id,
            name = collection.name,
            access = toStoredAccess(collection.profile.access),
            requiredMetadata =
                when (collection.profile.requiredMetadata) {
                    CollectionRequiredMetadata.NONE -> StoredCollectionRequiredMetadata.NONE
                    CollectionRequiredMetadata.PHOTO_AND_DATE -> StoredCollectionRequiredMetadata.PHOTO_AND_DATE
                },
            playbackUI =
                when (collection.profile.playbackUI) {
                    CollectionPlaybackUI.DEFAULT -> StoredCollectionPlaybackUI.DEFAULT
                    CollectionPlaybackUI.IMMERSIVE -> StoredCollectionPlaybackUI.IMMERSIVE
                    CollectionPlaybackUI.KITCHEN_MODE -> StoredCollectionPlaybackUI.KITCHEN_MODE
                },
            shareability =
                when (collection.profile.shareability) {
                    CollectionShareability.BOMPEABLE -> StoredCollectionShareability.BOMPEABLE
                    CollectionShareability.LISTEN_ONLY -> StoredCollectionShareability.LISTEN_ONLY
                },
            autoCategorize =
                when (collection.profile.autoCategorize) {
                    CollectionAutoCategorize.OFF -> StoredCollectionAutoCategorize.OFF
                    CollectionAutoCategorize.KEYWORDS_ON_DEVICE -> StoredCollectionAutoCategorize.KEYWORDS_ON_DEVICE
                },
            isSystem = collection.isSystem,
            createdAt = collection.createdAt,
            audioIds = emptyList(),
        )

    private fun toStoredAccess(access: CollectionAccess): StoredCollectionAccess =
        when (access) {
            CollectionAccess.PUBLIC -> StoredCollectionAccess.PUBLIC
            CollectionAccess.PRIVATE -> StoredCollectionAccess.PRIVATE
        }

    private fun StoredCollectionAccess.toDomain(): CollectionAccess =
        when (this) {
            StoredCollectionAccess.PUBLIC -> CollectionAccess.PUBLIC
            StoredCollectionAccess.PRIVATE -> CollectionAccess.PRIVATE
        }

    private fun StoredCollectionRequiredMetadata.toDomain(): CollectionRequiredMetadata =
        when (this) {
            StoredCollectionRequiredMetadata.NONE -> CollectionRequiredMetadata.NONE
            StoredCollectionRequiredMetadata.PHOTO_AND_DATE -> CollectionRequiredMetadata.PHOTO_AND_DATE
        }

    private fun StoredCollectionPlaybackUI.toDomain(): CollectionPlaybackUI =
        when (this) {
            StoredCollectionPlaybackUI.DEFAULT -> CollectionPlaybackUI.DEFAULT
            StoredCollectionPlaybackUI.IMMERSIVE -> CollectionPlaybackUI.IMMERSIVE
            StoredCollectionPlaybackUI.KITCHEN_MODE -> CollectionPlaybackUI.KITCHEN_MODE
        }

    private fun StoredCollectionShareability.toDomain(): CollectionShareability =
        when (this) {
            StoredCollectionShareability.BOMPEABLE -> CollectionShareability.BOMPEABLE
            StoredCollectionShareability.LISTEN_ONLY -> CollectionShareability.LISTEN_ONLY
        }

    private fun StoredCollectionAutoCategorize.toDomain(): CollectionAutoCategorize =
        when (this) {
            StoredCollectionAutoCategorize.OFF -> CollectionAutoCategorize.OFF
            StoredCollectionAutoCategorize.KEYWORDS_ON_DEVICE -> CollectionAutoCategorize.KEYWORDS_ON_DEVICE
        }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Collection name must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) {
            "Collection name must be at most $MAX_NAME_LENGTH chars (was ${name.length})"
        }
    }

    companion object {
        /** Reserved id for the system-seeded "Baúl" — stable across installs, recognised everywhere. */
        const val BAUL_SYSTEM_ID = "system:baul"

        /** Backup XML cross-reference (analogous to [SoundsRepository.BACKUP_FILE_PATH]). */
        const val BACKUP_FILE_PATH = "datastore/$DATASTORE_NAME.preferences_pb"

        const val MAX_NAME_LENGTH = 80

        /**
         * Seed name for the system Baúl collection. Spanish ("Baúl") even on en master because the
         * spec § 1 keeps the brand-original term across locales — the tab label itself can change
         * per locale (resolved via [com.github.barriosnahuel.vossosunboton.R.string.app_navigation_menu_item_vault]).
         */
        private const val BAUL_SEED_NAME = "Baúl"

        private val COLLECTIONS_KEY = stringPreferencesKey("collections_json")
        private val COLLECTIONS_SERIALIZER = ListSerializer(StoredCollection.serializer())
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                coerceInputValues = true
            }
    }
}

private const val DATASTORE_NAME = "collections"

/**
 * File-private DataStore delegate. Corruption handler logs via Timber for the same `:model`
 * decoupling reason documented on the `bompsStore` delegate.
 */
private val Context.collectionsStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Timber.e(exception, "Collections DataStore corruption recovered with empty prefs")
            emptyPreferences()
        },
)
