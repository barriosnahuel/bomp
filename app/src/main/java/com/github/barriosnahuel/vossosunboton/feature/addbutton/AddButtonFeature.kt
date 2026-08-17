/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.SoundSource
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

interface AddButtonFeature {
    fun saveNewButtonAsync(
        context: @NotNull Context,
        name: String,
        uri: String,
        publicCollectionIds: Set<String> = emptySet(),
        privateCollectionIds: Set<String> = emptySet(),
        source: SoundSource = SoundSource.IMPORTED,
    ): Deferred<Int>

    fun renameButtonAsync(
        context: @NotNull Context,
        sound: Sound,
        newName: String,
    ): Deferred<Unit>

    companion object {
        val instance: AddButtonFeature by lazy { AddButtonFeatureImpl() }
    }
}

private class AddButtonFeatureImpl : AddButtonFeature {
    override fun saveNewButtonAsync(
        context: Context,
        name: String,
        uri: String,
        publicCollectionIds: Set<String>,
        privateCollectionIds: Set<String>,
        source: SoundSource,
    ): Deferred<Int> {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        // Mint the stable id once, at the creation site — `SoundsRepository.save` is a pure upsert
        // keyed by the id it is given, so identity must originate here, not in the repository.
        // See ADR 0008.
        val id = UUID.randomUUID().toString()

        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.async(Dispatchers.IO) {
            var feedbackMessage = R.string.app_addbutton_feedback_save_failed
            val parsed = Uri.parse(uri)
            // Derive the destination extension from the validated MIME so the saved file's name matches
            // its actual container (a recorded clip is AAC/MP4, an imported .opus stays .opus) — share
            // MIME resolution keys on the extension. Unknown MIME falls back to mp3.
            val mime =
                when (val result = validateAudioUri(context, parsed)) {
                    is ValidationResult.Ok -> result.mime
                    ValidationResult.Rejected -> return@async R.string.app_feedback_generic_error_contact_support
                    ValidationResult.Unreadable -> return@async R.string.app_addbutton_feedback_uri_unreadable
                }
            val extension = AUDIO_EXT_BY_MIME[mime] ?: DEFAULT_AUDIO_EXT
            val fileName = "$sanitizedName-${System.currentTimeMillis()}.$extension"
            // getFile() resolves context.getExternalFilesDir(...), which performs disk I/O —
            // keep it inside the IO dispatcher so StrictMode does not flag it on the main thread.
            val targetFile = getFile(context, fileName)
            try {
                FileOutputStream(targetFile).use { fileOutputStream ->
                    context.contentResolver.openInputStream(parsed).use { inputStream ->
                        if (inputStream == null) {
                            Tracker.log("addbutton.uri=$uri")
                            Tracker.track(
                                RuntimeException("Inbound URI returned null inputStream after validation"),
                            )
                            return@async R.string.app_addbutton_feedback_uri_unreadable
                        } else {
                            copy(inputStream, fileOutputStream)
                            val repo = SoundsRepository(context)
                            repo.save(Sound(id, name, fileName).copy(source = source))
                            extractDurationMs(context, targetFile, fileName)?.let { repo.saveDuration(id, name, it) }
                            // Apply collection tags now so the audio shows up in the right place
                            // immediately. Failures don't roll back the save (the audio still
                            // exists, just untagged) but ARE reported as non-fatal so we can spot
                            // it in BigQuery. Either set is a no-op when empty.
                            if (publicCollectionIds.isNotEmpty() || privateCollectionIds.isNotEmpty()) {
                                val collections = CollectionsRepository(context, onError = Tracker::track)
                                runCatching {
                                    collections.setAudioCollections(id, publicCollectionIds, CollectionAccess.PUBLIC)
                                    collections.setAudioCollections(id, privateCollectionIds, CollectionAccess.PRIVATE)
                                }.onFailure {
                                    Tracker.log("addbutton.tag_apply_failed=${it.javaClass.simpleName}")
                                    Tracker.track(RuntimeException("Failed to tag new audio to collections", it))
                                }
                            }

                            feedbackMessage = R.string.app_addbutton_feedback_saved_ok
                        }
                    }
                }
            } catch (e: FileNotFoundException) {
                Tracker.log("addbutton.fileName=$fileName")
                Tracker.track(RuntimeException("Can't create new audio's path", e))
            } catch (e: IOException) {
                Tracker.log("addbutton.fileName=$fileName")
                Tracker.track(RuntimeException("Can't copy original audio", e))
            }

            feedbackMessage
        }
    }

    /** Best-effort duration probe of the just-copied file; a failure is non-fatal (the audio saves). */
    private suspend fun extractDurationMs(
        context: Context,
        file: File,
        fileName: String,
    ): Int? =
        runCatching {
            readDurationMs(context, Uri.fromFile(file))
        }.onFailure {
            if (it is CancellationException) throw it
            Tracker.log("addbutton.fileName=$fileName")
            Tracker.track(RuntimeException("Failed to extract duration metadata", it))
        }.getOrNull()

    override fun renameButtonAsync(
        context: Context,
        sound: Sound,
        newName: String,
    ): Deferred<Unit> {
        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.async(Dispatchers.IO) {
            SoundsRepository(context).rename(sound.id, newName)
        }
    }
}
