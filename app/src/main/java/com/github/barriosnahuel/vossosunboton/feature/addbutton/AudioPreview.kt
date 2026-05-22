/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.home.formatRelativeDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stops in-flight URI-bound preview playback. Used by `AudioPreview`'s `DisposableEffect.onDispose`
 * and by `AddButtonScreen.save()` when transitioning to the success morph — without the latter the
 * only stop is the disposal path, which fires when the Activity tears down ~600 ms later, leaving
 * audio bleeding through the confirmation overlay. Guarded on a non-null URI to leave Sound-bound
 * playback owned by other surfaces (Home/Explore — reported via the listener, not [playbackState];
 * see ADR 0007) untouched.
 */
internal fun stopActivePreviewPlayback() {
    val controller = PlayerControllerFactory.instance
    if (controller.playbackState.value?.uri != null) {
        controller.stopPlayingSound()
    }
}

@Composable
internal fun AudioPreview(
    context: Context,
    source: Uri,
    soundName: String,
    dateAdded: Long?,
) {
    val controller = remember { PlayerControllerFactory.instance }
    var durationMs by remember(source) { mutableStateOf(0) }

    LaunchedEffect(source) {
        // MediaMetadataRetriever runs off-thread to avoid blocking the main looper. Failure leaves
        // durationMs at 0 which keeps the Card hidden — same UX as a player that can't prepare.
        // Mirrors AddButtonFeature's canonical pattern (same wrapper message so both call-sites
        // group under one Crashlytics issue; the breadcrumb disambiguates the path).
        durationMs =
            withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, source)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
                    } finally {
                        retriever.release()
                    }
                }.onFailure {
                    Tracker.log("addbutton.preview.uri=$source")
                    Tracker.track(RuntimeException("Failed to extract duration metadata", it))
                }.getOrDefault(0)
            }
    }

    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val isOurPreview = playbackState?.uri == source
    val isPlaying = isOurPreview && playbackState?.isPlaying == true
    val sliderPosition =
        if (isOurPreview && durationMs > 0) {
            playbackState!!.positionMs.toFloat() / durationMs
        } else {
            0f
        }

    DisposableEffect(source) {
        onDispose {
            // Stop preview playback when the user backs out of AddButton. Guard against pre-empting
            // an unrelated playback (e.g., user backed out and Home is now the active player).
            if (controller.playbackState.value?.uri == source) {
                controller.stopPlayingSound()
            }
        }
    }

    if (durationMs > 0) {
        Card(
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    text = soundName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    val playContainerColor = MaterialTheme.colorScheme.primaryContainer
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .background(
                                    color = if (isPlaying) playContainerColor.copy(alpha = 0.18f) else Color.Transparent,
                                    shape = CircleShape,
                                ).padding(6.dp),
                    ) {
                        FilledIconButton(
                            onClick = {
                                when {
                                    isPlaying -> controller.pause()
                                    isOurPreview -> controller.resume()
                                    else -> controller.startPlayingUri(context, source)
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = playContainerColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        ) {
                            Icon(
                                painter =
                                    if (isPlaying) {
                                        rememberVectorPainter(
                                            AppIcons.Pause,
                                        )
                                    } else {
                                        painterResource(R.drawable.app_ic_play_arrow)
                                    },
                                contentDescription = stringResource(R.string.app_addbutton_preview_audio),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { value ->
                                if (durationMs > 0) controller.seekTo((value * durationMs).toInt())
                            },
                            enabled = isPlaying,
                            colors =
                                SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                    disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                    disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatDuration(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (dateAdded != null) {
                                Text(
                                    text = formatRelativeDate(dateAdded),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
