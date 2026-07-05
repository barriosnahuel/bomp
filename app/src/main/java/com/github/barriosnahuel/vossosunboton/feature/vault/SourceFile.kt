/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.content.Context
import android.net.Uri
import java.io.File

/** A local file ready for the extractor; [deleteAfter] marks per-decode temp copies. */
internal class SourceFile(
    val file: File,
    val deleteAfter: Boolean,
)

/**
 * Copies a bundled raw resource to a per-decode temp file. No persistent cache on purpose: a
 * disk cache keyed by the resource int would go stale across app updates (aapt reassigns ids)
 * and locale changes (qualified raw overrides share the id); the in-memory envelope cache
 * already makes repeat decodes per process free.
 */
internal fun copyRawResToTempFile(
    context: Context,
    rawRes: Int,
): File = writeTempFile(context) { target -> context.resources.openRawResource(rawRes).use { it.copyTo(target) } }

/** Copies a content/file uri's stream to a per-decode temp file (deleted after the decode). */
internal fun copyUriToTempFile(
    context: Context,
    uri: Uri,
): File =
    writeTempFile(context) { target ->
        checkNotNull(context.contentResolver.openInputStream(uri)) { "unreadable uri" }.use { it.copyTo(target) }
    }

/** Creates the temp, runs [write] into it, and deletes it on ANY failure before rethrowing. */
private inline fun writeTempFile(
    context: Context,
    write: (java.io.OutputStream) -> Unit,
): File {
    val target = File.createTempFile("waveform_src_", null, context.cacheDir)
    var written = false
    try {
        target.outputStream().use { write(it) }
        written = true
    } finally {
        if (!written) target.delete()
    }
    return target
}
