/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.barriosnahuel.vossosunboton.BuildConfig
import java.io.File

/**
 * The recorder's transient capture files. A clip lives in `cacheDir/recordings/` (OS-evictable, never
 * external) until the user saves it — then the save pipeline copies it into `Music/` and this temp is
 * dead weight the OS can reclaim. Exposed to the save flow as a FileProvider `content://` URI (same
 * authority `ShareFeature` uses) so it passes the `AddButtonFeature` inbound validator (scheme +
 * `audio/mp4` MIME) — the recorder does not fork the persistence/validation path (ADR 0019).
 */
object RecorderTempFiles {
    private const val DIR = "recordings"
    private const val PREFIX = "recording-"
    private const val EXTENSION = ".m4a"
    private val authority = BuildConfig.APPLICATION_ID + ".fileprovider"

    /** The capture directory (`cacheDir/recordings/`), created if absent. */
    fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /** Resolves a capture [fileName] (as persisted in a draft) back to its file under [dir]. */
    fun resolve(
        context: Context,
        fileName: String,
    ): File = File(dir(context), fileName)

    /** A fresh, empty target file under `cacheDir/recordings/` (directory created if absent). */
    fun newTempFile(context: Context): File = File(dir(context), "$PREFIX${System.currentTimeMillis()}$EXTENSION")

    /** Content URI for [file] under the shared FileProvider authority. */
    fun contentUriFor(
        context: Context,
        file: File,
    ): Uri = FileProvider.getUriForFile(context, authority, file)

    /**
     * Deletes leftover captures on recorder entry — clips a prior session handed off (already copied by
     * the save pipeline) or abandoned. [keep] (the restored draft's file, ADR 0019 § Draft recovery) is
     * spared; pass `null` for a fresh entry that should retain nothing.
     */
    fun purge(
        context: Context,
        keep: File? = null,
    ) {
        dir(context).listFiles()?.forEach { if (it != keep) it.delete() }
    }
}
