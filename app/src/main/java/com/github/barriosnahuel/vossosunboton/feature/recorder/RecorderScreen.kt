/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:Suppress("TooManyFunctions") // One cohesive immersive screen + its permission/discard chrome.

package com.github.barriosnahuel.vossosunboton.feature.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.waveform.EnvelopeWaveform
import com.github.barriosnahuel.vossosunboton.feature.waveform.WAVEFORM_MIN_BAR
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.theme.ImmersiveListenTheme
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * The immersive recorder (ADR 0019) — the listen screen "in reverse". Stateless: driven by
 * [RecorderState] and callbacks; the host ([RecorderHost]) owns the VM, permission, and discard
 * dialog. Always-dark via [ImmersiveListenTheme]; colours come from M3 roles only (acid record button,
 * `error` REC dot) — no hardcoded literals (CLAUDE.md § Design system).
 */
@Composable
internal fun RecorderScreen(
    state: RecorderState,
    isPreviewPlaying: Boolean,
    previewPositionMs: Long,
    peaks: FloatArray?,
    onRecordTap: () -> Unit,
    onStopTap: () -> Unit,
    onPreviewToggle: () -> Unit,
    onUseClip: () -> Unit,
    onReRecord: () -> Unit,
    onClose: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    RecorderBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) {
            RecorderTopBar(onClose = onClose)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                RecorderHeadline(state, isPreviewPlaying, previewPositionMs)
                RecorderVisual(state, previewPositionMs, peaks, onSeek)
                RecorderTransport(
                    state = state,
                    isPreviewPlaying = isPreviewPlaying,
                    onRecordTap = onRecordTap,
                    onStopTap = onStopTap,
                    onPreviewToggle = onPreviewToggle,
                    onUseClip = onUseClip,
                    onReRecord = onReRecord,
                )
            }
        }
    }
}

@Composable
private fun RecorderTopBar(onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.app_ic_close),
                contentDescription = stringResource(R.string.app_recorder_cd_close),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun RecorderHeadline(
    state: RecorderState,
    isPreviewPlaying: Boolean,
    previewPositionMs: Long,
) {
    val timerDescription = stringResource(R.string.app_recorder_cd_timer)
    Box(modifier = Modifier.height(HEADLINE_HEIGHT), contentAlignment = Alignment.Center) {
        when (state) {
            is RecorderState.Recording ->
                // Timer with a live REC dot (error red).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    Box(
                        modifier =
                            Modifier
                                .size(REC_DOT_SIZE)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                    )
                    HeadlineTimer(state.elapsedMs, timerDescription)
                }
            is RecorderState.Review ->
                // While previewing, follow the playback position; otherwise rest at the clip length.
                HeadlineTimer(
                    millis = if (isPreviewPlaying) previewPositionMs else state.durationMs,
                    description = timerDescription,
                )
            RecorderState.Ready -> Unit
        }
    }
}

@Composable
private fun HeadlineTimer(
    millis: Long,
    description: String,
) {
    Text(
        text = formatDuration(millis.toInt()),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun RecorderVisual(
    state: RecorderState,
    previewPositionMs: Long,
    peaks: FloatArray?,
    onSeek: (Float) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(WAVEFORM_HEIGHT), contentAlignment = Alignment.Center) {
        when (state) {
            is RecorderState.Recording -> LiveWaveform(amplitude = state.amplitude, tick = state.elapsedMs)
            is RecorderState.Review ->
                EnvelopeWaveform(
                    progress = if (state.durationMs > 0L) previewPositionMs.toFloat() / state.durationMs else 0f,
                    peaks = peaks,
                    contentDescription = stringResource(R.string.app_recorder_cd_waveform),
                    barCount = RECORDER_WAVEFORM_BARS,
                    barFill = WAVEFORM_BAR_FILL,
                    modifier = Modifier.fillMaxWidth().height(WAVEFORM_HEIGHT),
                    onSeek = onSeek,
                )
            RecorderState.Ready -> LiveWaveform(amplitude = 0f, tick = 0L)
        }
    }
}

/**
 * A live input meter: a rolling buffer of recent peak amplitudes, newest on the right. Acid bars
 * (`primary`) while sound comes in; a flat baseline when idle. Distinct from the listen screen's
 * scrubbable envelope — this reads as "recording now".
 */
@Composable
private fun LiveWaveform(
    amplitude: Float,
    tick: Long,
    modifier: Modifier = Modifier,
) {
    val bars: SnapshotStateList<Float> = remember { mutableStateListOf<Float>().apply { repeat(RECORDER_WAVEFORM_BARS) { add(0f) } } }
    LaunchedEffect(tick) {
        bars.removeAt(0)
        bars.add(amplitude.coerceIn(0f, 1f))
    }
    val activeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(WAVEFORM_HEIGHT)) {
        val slot = size.width / RECORDER_WAVEFORM_BARS
        val barWidth = slot * WAVEFORM_BAR_FILL
        val centerY = size.height / 2f
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        bars.forEachIndexed { i, raw ->
            val barHeight = raw.coerceIn(WAVEFORM_MIN_BAR, 1f) * size.height
            val x = i * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun RecorderTransport(
    state: RecorderState,
    isPreviewPlaying: Boolean,
    onRecordTap: () -> Unit,
    onStopTap: () -> Unit,
    onPreviewToggle: () -> Unit,
    onUseClip: () -> Unit,
    onReRecord: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        when (state) {
            RecorderState.Ready -> {
                HeroButton(
                    iconPainter = painterResource(R.drawable.app_ic_mic),
                    contentDescription = stringResource(R.string.app_recorder_cd_record),
                    onClick = onRecordTap,
                )
                Text(
                    text = stringResource(R.string.app_recorder_ready_hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            is RecorderState.Recording -> {
                HeroButton(
                    iconPainter = rememberVectorPainter(AppIcons.Stop),
                    contentDescription = stringResource(R.string.app_recorder_cd_stop),
                    onClick = onStopTap,
                )
                Text(
                    text = stringResource(R.string.app_recorder_recording_hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            is RecorderState.Review -> {
                HeroButton(
                    iconPainter =
                        if (isPreviewPlaying) {
                            rememberVectorPainter(AppIcons.Pause)
                        } else {
                            painterResource(R.drawable.app_ic_play_arrow)
                        },
                    contentDescription =
                        stringResource(
                            if (isPreviewPlaying) R.string.app_recorder_cd_pause else R.string.app_recorder_cd_play,
                        ),
                    onClick = onPreviewToggle,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.SM),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.MD, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onReRecord) {
                        Text(text = stringResource(R.string.app_recorder_rerecord))
                    }
                    Button(
                        onClick = onUseClip,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Text(text = stringResource(R.string.app_recorder_use))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(
    iconPainter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(HERO_BUTTON_SIZE),
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = contentDescription,
            modifier = Modifier.size(HERO_ICON_SIZE),
        )
    }
}

/** On-brand priming shown before the system permission dialog. */
@Composable
internal fun MicPermissionPriming(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
) {
    RecorderBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.app_ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(PRIMING_ICON_SIZE),
            )
            Spacer(Modifier.height(Spacing.LG))
            Text(
                text = stringResource(R.string.app_recorder_permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.SM))
            Text(
                text = stringResource(R.string.app_recorder_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.XL))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(text = stringResource(R.string.app_recorder_permission_allow))
            }
            TextButton(onClick = onNotNow) {
                Text(text = stringResource(R.string.app_recorder_permission_not_now))
            }
        }
    }
}

/** Shown after a denial — routes to settings or an import escape so the user is never stuck. */
@Composable
internal fun MicPermissionDenied(
    onOpenSettings: () -> Unit,
    onImportInstead: () -> Unit,
    onClose: () -> Unit,
) {
    RecorderBackdrop {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            RecorderTopBar(onClose = onClose)
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_recorder_denied_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Spacing.XL))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Text(text = stringResource(R.string.app_recorder_open_settings))
                }
                TextButton(onClick = onImportInstead) {
                    Text(text = stringResource(R.string.app_recorder_import_instead))
                }
            }
        }
    }
}

/** Confirms discarding captured audio on back — losing a memory shouldn't be one accidental tap. */
@Composable
internal fun RecorderDiscardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_recorder_discard_title)) },
        text = { Text(stringResource(R.string.app_recorder_discard_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.app_recorder_discard_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_recorder_discard_keep)) }
        },
    )
}

@Composable
private fun RecorderBackdrop(content: @Composable () -> Unit) {
    // Clone of the listen screen's always-dark wash (its ImmersiveBackdrop is private): legible status
    // bars in both modes + the same warm glow, so creating a memory and re-living one share the light.
    ImmersiveListenTheme {
        val accent = MaterialTheme.colorScheme.primary
        val backgroundColor = MaterialTheme.colorScheme.background
        val wash =
            remember(accent, backgroundColor) {
                Brush.radialGradient(
                    colorStops =
                        arrayOf(
                            GLOW_STOP_CENTER to accent.copy(alpha = GLOW_CENTER_ALPHA),
                            GLOW_STOP_MID to accent.copy(alpha = GLOW_MID_ALPHA),
                            GLOW_STOP_EDGE to backgroundColor,
                        ),
                )
            }
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor).background(wash)) {
            content()
        }
    }
}

private val TOP_BAR_HEIGHT = 64.dp
private val HEADLINE_HEIGHT = 96.dp
private val REC_DOT_SIZE = 10.dp
private val HERO_BUTTON_SIZE = 104.dp
private val HERO_ICON_SIZE = 44.dp
private val PRIMING_ICON_SIZE = 64.dp
private val WAVEFORM_HEIGHT = 120.dp
internal const val RECORDER_WAVEFORM_BARS = 48
private const val WAVEFORM_BAR_FILL = 0.5f
private const val GLOW_CENTER_ALPHA = 0.16f
private const val GLOW_MID_ALPHA = 0.04f
private const val GLOW_STOP_CENTER = 0.0f
private const val GLOW_STOP_MID = 0.55f
private const val GLOW_STOP_EDGE = 1.0f
