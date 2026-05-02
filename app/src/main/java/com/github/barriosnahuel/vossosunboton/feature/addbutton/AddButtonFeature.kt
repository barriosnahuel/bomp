/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

interface AddButtonFeature {
    fun saveNewButtonAsync(
        context: @NotNull Context,
        name: String,
        uri: String,
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
    ): Deferred<Int> {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "$sanitizedName-${System.currentTimeMillis()}.mp3"
        val targetFile = getFile(context, fileName)

        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.async(Dispatchers.IO) {
            var feedbackMessage = R.string.app_feedback_generic_error_contact_support
            val parsed = Uri.parse(uri)
            if (validateAudioUri(context, parsed) != ValidationResult.Ok) {
                return@async feedbackMessage
            }
            try {
                FileOutputStream(targetFile).use { fileOutputStream ->
                    context.contentResolver.openInputStream(parsed).use { inputStream ->
                        if (inputStream == null) {
                            Timber.e("Input stream obtained from the specified content URI is null: %s", uri)
                        } else {
                            copy(inputStream, fileOutputStream)
                            val repo = SoundsRepository(context)
                            repo.save(Sound(name, fileName))
                            val durationMs =
                                runCatching {
                                    val retriever = MediaMetadataRetriever()
                                    try {
                                        retriever.setDataSource(targetFile.absolutePath)
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()
                                    } finally {
                                        retriever.release()
                                    }
                                }.getOrNull()
                            if (durationMs != null) {
                                repo.saveDuration(name, durationMs)
                            }

                            feedbackMessage = R.string.app_addbutton_feedback_saved_ok
                        }
                    }
                }
            } catch (e: FileNotFoundException) {
                Timber.e("Can't create new button's path")
            } catch (e: IOException) {
                Timber.e("Can't copy original audio")
            }

            feedbackMessage
        }
    }

    override fun renameButtonAsync(
        context: Context,
        sound: Sound,
        newName: String,
    ): Deferred<Unit> {
        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.async(Dispatchers.IO) {
            SoundsRepository(context).rename(sound.name, newName)
        }
    }

    private fun validateAudioUri(
        context: Context,
        uri: Uri,
    ): ValidationResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val size = resolveSize(resolver, uri)
        val rejection =
            when {
                uri.scheme !in ALLOWED_SCHEMES -> "scheme=${uri.scheme}"
                mime == null || !mime.startsWith(AUDIO_MIME_PREFIX) -> "mime=$mime"
                size == null || size > MAX_AUDIO_BYTES -> "size=$size"
                else -> null
            }
        return if (rejection == null) {
            ValidationResult.Ok
        } else {
            Timber.w("Rejected inbound URI: %s", rejection)
            ValidationResult.Rejected
        }
    }

    private fun resolveSize(
        resolver: ContentResolver,
        uri: Uri,
    ): Long? {
        val fromAfd =
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.length.takeIf { len -> len >= 0 }
                }
            }.getOrNull()
        return fromAfd ?: runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    private sealed class ValidationResult {
        data object Ok : ValidationResult()

        data object Rejected : ValidationResult()
    }

    private companion object {
        /**
         * 50 MB cap — ≈4× a 5-min MP3 at 320 kbps; rejects pathological inputs while leaving
         * headroom for long voice notes and lossy stems. Constant lives here, not in resources,
         * because tests need a stable JVM-side reference.
         */
        const val MAX_AUDIO_BYTES = 50L * 1024 * 1024
        const val AUDIO_MIME_PREFIX = "audio/"
        val ALLOWED_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE)
    }
}
