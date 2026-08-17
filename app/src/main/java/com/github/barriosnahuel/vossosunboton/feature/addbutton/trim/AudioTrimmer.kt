/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.github.barriosnahuel.vossosunboton.BuildConfig
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** What the save flow got back from the cutter. Two outcomes only: docs/adr/0028-add-flow-audio-trim.md. */
internal sealed interface TrimOutcome {
    /** The cut succeeded; [uri] points at the trimmed clip and replaces the original in the save. */
    data class Trimmed(
        val uri: Uri,
    ) : TrimOutcome

    /**
     * The cut did not happen. The save proceeds with the untouched original audio and the user is
     * told so — [reason] is a stable, low-cardinality slug for the Crashlytics breadcrumb.
     */
    data class FallbackToOriginal(
        val reason: String,
    ) : TrimOutcome
}

/**
 * Exports the kept range of an audio to a new clip via Media3 `Transformer`.
 *
 * Transformer transmuxes when the input codec already matches AAC and transcodes when it doesn't, so
 * one call covers MP3 / M4A / OPUS / OGG / WAV with no per-format branching and no call from our own
 * code into the AEP-prohibited `MediaExtractor` + `MediaMuxer` pair (ADR 0022). Output is always AAC in MP4
 * (`.m4a`), the extension the save pipeline already maps.
 *
 * **Never throws for a failed cut.** Any failure — unsupported codec, OEM `MediaCodec` bug, no disk
 * space, timeout — comes back as [TrimOutcome.FallbackToOriginal] so the caller saves the original
 * audio whole. Losing the user's audio to a trim failure is the one outcome this feature must not
 * have.
 */
internal object AudioTrimmer {
    /**
     * Cuts [source] down to `[startMs, endMs)` and returns a FileProvider URI for the result.
     *
     * Call from any dispatcher: the export itself is driven on the main thread because `Transformer`
     * requires a `Looper` and calls its listener back on the thread it was built on (the encode runs
     * on Transformer's own background threads). Cancelling the calling scope cancels the export.
     */
    suspend fun trim(
        context: Context,
        source: Uri,
        startMs: Int,
        endMs: Int,
    ): TrimOutcome =
        withContext(Dispatchers.IO) {
            val output =
                runCatching { TrimTempFiles.newTempFile(context) }
                    .getOrElse { return@withContext fallback("temp_file", it) }
            // Boxed rather than elvis'd: a successful export and a timeout would BOTH surface as a bare
            // null out of withTimeoutOrNull, and every success would then be recorded as a timeout.
            val outcome =
                try {
                    withTimeoutOrNull(EXPORT_TIMEOUT_MS) {
                        withContext(Dispatchers.Main) { ExportOutcome(export(context, source, startMs, endMs, output)) }
                    }
                } catch (cancellation: CancellationException) {
                    // Backing out or rotating mid-export: the half-written file has no owner, and the
                    // next export's purge is only a best-effort sweep that may never come.
                    output.delete()
                    throw cancellation
                }
            val failure = if (outcome == null) TIMEOUT_REASON else outcome.failure
            if (failure != null) {
                output.delete()
                return@withContext fallback(failure, null)
            }
            if (!output.isFile || output.length() <= 0L) {
                output.delete()
                return@withContext fallback("empty_output", null)
            }
            runCatching { TrimOutcome.Trimmed(TrimTempFiles.contentUriFor(context, output)) }
                .getOrElse {
                    output.delete()
                    fallback("uri_resolution", it)
                }
        }

    /**
     * Runs one export to completion. Returns `null` on success, or a stable failure slug.
     * Main-thread only — see [trim]'s contract.
     */
    @OptIn(UnstableApi::class) // Transformer's whole API surface is still @UnstableApi in media3 1.11.
    private suspend fun export(
        context: Context,
        source: Uri,
        startMs: Int,
        endMs: Int,
        output: File,
    ): String? =
        suspendCancellableCoroutine { continuation ->
            val clipped =
                MediaItem
                    .Builder()
                    .setUri(source)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration
                            .Builder()
                            .setStartPositionMs(startMs.toLong())
                            .setEndPositionMs(endMs.toLong())
                            .build(),
                    ).build()
            // A shared audio can technically carry a video track (a .mp4 with an audio MIME); dropping
            // it keeps the output a pure audio clip instead of an unplayable one-track-mismatch file.
            val edited = EditedMediaItem.Builder(clipped).setRemoveVideo(true).build()
            val transformer =
                Transformer
                    .Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                Tracker.track(RuntimeException("Audio trim export failed", exportException))
                                if (continuation.isActive) continuation.resume("export_error_${exportException.errorCode}")
                            }
                        },
                    ).build()
            // cancel() must run on the thread the Transformer was built on; invokeOnCancellation fires
            // on whichever thread cancelled the scope, so hop back to the main looper explicitly.
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { runCatching { transformer.cancel() } }
            }
            runCatching { transformer.start(edited, output.absolutePath) }
                .onFailure { if (continuation.isActive) continuation.resume("start_${it.javaClass.simpleName}") }
        }

    /** Distinguishes "exported cleanly" (`failure == null`) from "the whole export timed out". */
    private class ExportOutcome(
        val failure: String?,
    )

    private fun fallback(
        reason: String,
        cause: Throwable?,
    ): TrimOutcome {
        if (cause is CancellationException) throw cause
        Tracker.log("addbutton.trim_fallback=$reason")
        if (cause != null) Tracker.track(RuntimeException("Audio trim could not produce a clip", cause))
        return TrimOutcome.FallbackToOriginal(reason)
    }

    /**
     * Generous ceiling, not a performance target: Transformer runs many times faster than realtime,
     * so even the 50 MB inbound cap finishes well inside this. It exists so an encoder wedged by an
     * OEM bug degrades to "saved the whole audio" instead of a Save button that never returns.
     */
    private const val EXPORT_TIMEOUT_MS = 60_000L
    private const val TIMEOUT_REASON = "timeout"
}

/**
 * The trimmer's transient output files. A cut lives in `cacheDir/trims/` (OS-evictable, never
 * external) until the save pipeline copies it into `Music/`, then it is dead weight — the same
 * lifecycle and FileProvider handoff the recorder uses (ADR 0019), so the cut clears the inbound
 * validator in `AddButtonFeature` with no fork of the persistence path.
 */
internal object TrimTempFiles {
    private const val DIR = "trims"
    private const val PREFIX = "trim-"

    /** AAC in MP4 — the one container Transformer's default muxer writes (ADR 0028 D1). */
    private const val EXTENSION = ".m4a"
    private val authority = BuildConfig.APPLICATION_ID + ".fileprovider"

    fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /**
     * A fresh output file, after purging previous cuts. Purging here (rather than on screen entry)
     * keeps at most one abandoned cut on disk: re-dragging the handles and saving again replaces it.
     */
    fun newTempFile(context: Context): File {
        val dir = dir(context)
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "$PREFIX${System.currentTimeMillis()}$EXTENSION")
    }

    fun contentUriFor(
        context: Context,
        file: File,
    ): Uri = FileProvider.getUriForFile(context, authority, file)
}
