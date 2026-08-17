/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.net.toUri
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The audio behind the add screen, resolved once: where it lives and how long it is.
 *
 * Both reads are off the main thread. Edit mode's URI resolution calls `getExternalFilesDir()`, which
 * trips a StrictMode DiskReadViolation if it runs synchronously during composition; the duration read
 * blocks up to its own timeout.
 *
 * [durationMs] is 0 until the metadata read lands (and stays 0 if it fails), which is what gates both
 * the preview card and the trim editor — a player that can't report a duration has nothing to preview
 * and nothing to cut. Hoisted here rather than read inside `AudioPreview` because the save path needs
 * the same number to turn a trim selection into millisecond bounds, and reading it twice would mean
 * two metadata extractions over one file.
 */
internal class PreviewMedia(
    val source: Uri?,
    val durationMs: Int,
)

@Composable
internal fun rememberPreviewMedia(
    context: Context,
    mode: AddButtonMode,
): PreviewMedia {
    val source: Uri? by produceState<Uri?>(initialValue = null, mode) {
        value =
            withContext(Dispatchers.IO) {
                when (val m = mode) {
                    is AddButtonMode.Create -> m.uri
                    is AddButtonMode.Edit -> m.sound.file?.let { getFile(context, it).toUri() }
                }
            }
    }
    val resolved = source
    val durationMs: Int by produceState(initialValue = 0, resolved) {
        val uri = resolved ?: return@produceState
        value =
            withContext(Dispatchers.IO) {
                // Failure leaves the duration at 0, which keeps the preview card and the trim editor
                // hidden — same UX as a player that can't prepare. Same static wrapper message as the
                // other extraction call-sites so Crashlytics groups them under one issue; the
                // breadcrumb disambiguates the path.
                runCatching { readDurationMs(context, uri) }
                    .onFailure {
                        // A disposed screen cancels the extraction — propagate, don't report it as a failure.
                        if (it is CancellationException) throw it
                        Tracker.log("addbutton.preview.uri=$uri")
                        Tracker.track(RuntimeException("Failed to extract duration metadata", it))
                    }.getOrDefault(0)
            }
    }
    return PreviewMedia(source = resolved, durationMs = durationMs)
}
