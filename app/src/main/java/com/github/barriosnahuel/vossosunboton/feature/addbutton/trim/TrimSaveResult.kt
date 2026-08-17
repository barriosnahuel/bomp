/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import android.content.Context
import android.net.Uri
import com.github.barriosnahuel.vossosunboton.feature.addbutton.ValidationResult
import com.github.barriosnahuel.vossosunboton.feature.addbutton.validateAudioUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the save flow should actually persist.
 *
 * [fellBack] is `true` only when a cut was asked for and could not be produced — the save then carries
 * the untouched original and the success overlay says so. A save with no trim requested is not a
 * fallback: nothing failed.
 */
internal class TrimSaveResult(
    val uri: Uri,
    val trimmed: Boolean,
    val fellBack: Boolean,
)

/**
 * Turns a [selection] into the URI the save pipeline receives: the exported cut, or [source] itself.
 *
 * Short-circuits when there is nothing to cut — no duration yet, or a selection still covering the
 * whole clip — so opening the trim editor and changing nothing costs no transcode. Every failure path
 * inside [AudioTrimmer] resolves to [source], so this never denies the user their save:
 * docs/adr/0028-add-flow-audio-trim.md.
 *
 * The cut is a NEW inbound-URI surface — Media3's data source opens the URI here, before the save
 * pipeline's own validation ever runs — so it re-runs the same validator first (CLAUDE.md § Inbound
 * URI validation). Without it an `http://` `EXTRA_STREAM` would be fetched by the exporter, and an
 * over-cap file would enter through its trimmed derivative. A rejected source falls through untrimmed
 * and the save pipeline then refuses it with its own user-facing message.
 */
internal suspend fun applyTrim(
    context: Context,
    source: Uri,
    durationMs: Int,
    selection: TrimSelection,
): TrimSaveResult {
    val untouched = TrimSaveResult(uri = source, trimmed = false, fellBack = false)
    // Short-circuiting left to right matters: the ContentResolver work (off the main thread, and done
    // before Transformer ever sees the URI) only runs once we know there is a cut to make.
    val shouldCut =
        durationMs > 0 &&
            !selection.isWholeClip(durationMs) &&
            withContext(Dispatchers.IO) { validateAudioUri(context, source) } is ValidationResult.Ok
    if (!shouldCut) return untouched

    return when (
        val outcome =
            AudioTrimmer.trim(
                context = context,
                source = source,
                startMs = selection.startMs(durationMs),
                endMs = selection.endMs(durationMs),
            )
    ) {
        is TrimOutcome.Trimmed -> TrimSaveResult(uri = outcome.uri, trimmed = true, fellBack = false)
        is TrimOutcome.FallbackToOriginal -> TrimSaveResult(uri = source, trimmed = true, fellBack = true)
    }
}
