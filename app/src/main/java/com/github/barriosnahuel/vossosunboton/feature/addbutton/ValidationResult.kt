/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import timber.log.Timber

/**
 * The gate every inbound audio URI passes before anything opens its stream: scheme allowlist, `audio/`
 * MIME, and a size cap (CLAUDE.md § Inbound URI validation).
 *
 * Lives on its own rather than inside `AddButtonFeature` because there is now more than one surface
 * that touches a URI the user handed us — the save pipeline, and the trimmer, which feeds the URI to
 * Media3's data source *before* the save runs. A second copy of these rules is how one of them
 * silently drifts into accepting an `http://` `EXTRA_STREAM` or an over-cap file.
 */
internal sealed class ValidationResult {
    data class Ok(
        val mime: String?,
    ) : ValidationResult()

    data object Rejected : ValidationResult()

    data object Unreadable : ValidationResult()
}

internal fun validateAudioUri(
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
        ValidationResult.Ok(mime)
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

/**
 * 50 MB cap — ≈4× a 5-min MP3 at 320 kbps; rejects pathological inputs while leaving headroom for long
 * voice notes and lossy stems. Constant lives here, not in resources, because tests need a stable
 * JVM-side reference.
 */
internal const val MAX_AUDIO_BYTES = 50L * 1024 * 1024
private const val AUDIO_MIME_PREFIX = "audio/"
private val ALLOWED_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE)

/**
 * Destination extension per validated MIME. Explicit map rather than
 * `MimeTypeMap.getExtensionFromMimeType` (which returns `mp4`, not `m4a`, for `audio/mp4`).
 * Unknown audio MIME falls back to [DEFAULT_AUDIO_EXT].
 */
internal const val DEFAULT_AUDIO_EXT = "mp3"
internal val AUDIO_EXT_BY_MIME =
    mapOf(
        "audio/mp4" to "m4a",
        "audio/aac" to "m4a",
        "audio/mpeg" to "mp3",
        "audio/opus" to "opus",
        "audio/ogg" to "ogg",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
    )
