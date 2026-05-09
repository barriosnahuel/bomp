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
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
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

        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.async(Dispatchers.IO) {
            var feedbackMessage = R.string.app_addbutton_feedback_save_failed
            val parsed = Uri.parse(uri)
            when (validateAudioUri(context, parsed)) {
                ValidationResult.Ok -> Unit
                ValidationResult.Rejected -> return@async R.string.app_feedback_generic_error_contact_support
                ValidationResult.Unreadable -> return@async R.string.app_addbutton_feedback_uri_unreadable
            }
            // getFile() resolves context.getExternalFilesDir(...), which performs disk I/O —
            // keep it inside the IO dispatcher so StrictMode does not flag it on the main thread.
            val targetFile = getFile(context, fileName)
            try {
                FileOutputStream(targetFile).use { fileOutputStream ->
                    context.contentResolver.openInputStream(parsed).use { inputStream ->
                        if (inputStream == null) {
                            Tracker.track(
                                RuntimeException("Inbound URI returned null inputStream after validation: $uri"),
                            )
                            return@async R.string.app_addbutton_feedback_uri_unreadable
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
                                }.onFailure {
                                    Tracker.track(
                                        RuntimeException("Failed to extract duration metadata for $fileName", it),
                                    )
                                }.getOrNull()
                            if (durationMs != null) {
                                repo.saveDuration(name, durationMs)
                            }

                            feedbackMessage = R.string.app_addbutton_feedback_saved_ok
                        }
                    }
                }
            } catch (e: FileNotFoundException) {
                Tracker.track(RuntimeException("Can't create new button's path: $fileName", e))
            } catch (e: IOException) {
                Tracker.track(RuntimeException("Can't copy original audio: $fileName", e))
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
        // Scheme check first: a rejected scheme (e.g. http://) shouldn't trigger ContentResolver work
        // that may itself blow up on the unsupported URI and confuse the Unreadable vs Rejected metric.
        if (uri.scheme !in ALLOWED_SCHEMES) {
            Timber.w("Rejected inbound URI: scheme=%s", uri.scheme)
            return ValidationResult.Rejected
        }
        return validateContentUri(context, uri)
    }

    private fun validateContentUri(
        context: Context,
        uri: Uri,
    ): ValidationResult {
        val resolver = context.contentResolver
        val mimeResult = runCatching { resolver.getType(uri) }
        if (mimeResult.isFailure) {
            Tracker.track(
                RuntimeException("ContentResolver.getType failed for inbound URI", mimeResult.exceptionOrNull()),
            )
            return ValidationResult.Unreadable
        }
        val mime = mimeResult.getOrNull()
        val size = resolveSize(resolver, uri)
        val rejection =
            when {
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

        data object Unreadable : ValidationResult()
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
