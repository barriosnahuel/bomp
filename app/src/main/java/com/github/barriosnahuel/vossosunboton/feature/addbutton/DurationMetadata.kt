/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import java.util.concurrent.TimeUnit

/**
 * Duration of [uri]'s audio via Media3's MetadataRetriever — the ADR 0022 replacement for the
 * AEP-prohibited [android.media.MediaMetadataRetriever]. Blocking, bounded by
 * [METADATA_TIMEOUT_SECONDS]: call on an IO dispatcher. Throws on unreadable or duration-less
 * sources — callers keep their per-site `runCatching` + Tracker shells so Crashlytics grouping
 * (one shared wrapper title) and per-path breadcrumbs stay exactly as they are.
 */
@OptIn(UnstableApi::class) // media3-inspector's MetadataRetriever is the documented replacement; API surface still @UnstableApi.
internal fun readDurationMsBlocking(
    context: Context,
    uri: Uri,
): Int =
    MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).build().use { retriever ->
        val durationUs = retriever.retrieveDurationUs().get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(durationUs != C.TIME_UNSET) { "Source reports no duration" }
        (durationUs / MICROS_PER_MS).toInt()
    }

private const val METADATA_TIMEOUT_SECONDS = 10L
private const val MICROS_PER_MS = 1_000L
