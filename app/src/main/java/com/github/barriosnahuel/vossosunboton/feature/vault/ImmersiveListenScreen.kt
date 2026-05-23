/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.home.formatFullDate
import com.github.barriosnahuel.vossosunboton.ui.predictiveBackTransition
import com.github.barriosnahuel.vossosunboton.ui.theme.ImmersiveListenTheme
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Listen mode — the full-screen, listen-only player for a single Vault audio. Reached by tapping
 * play on any audio in the Vault tab (`CollectionPlaybackUI.IMMERSIVE`); the Vault list itself is
 * unchanged (same `SoundsList` cards as My Sounds).
 *
 * Layout: the three facts a Vault audio carries — collection, date, name — are grouped in the top
 * half; the audio's real waveform sits in the bottom half; the transport (back-to-start + play)
 * pins to the very bottom. Colors stay on the always-dark [ImmersiveListenTheme] (status-bar icon
 * legibility + design intent). The Claude Design mock additionally shows a polaroid, a caption and
 * a subtitle; the Vault stores none of those, so they are omitted.
 */
@Composable
internal fun ImmersiveListenHost(
    viewModel: SoundsViewModel,
    soundId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    LaunchedEffect(Unit) { tracker.logScreen(CanonicalScreenName.VAULT_LISTEN) }

    val library by viewModel.library.collectAsStateWithLifecycle()
    val playingSound by viewModel.playingSound.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val pausedProgress by viewModel.pausedProgress.collectAsStateWithLifecycle()
    val durations by viewModel.soundDurations.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val collectionsByAudio by viewModel.audioCollectionsIndex.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeVaultFilter.collectAsStateWithLifecycle()
    val systemLabel = stringResource(R.string.app_vault_baul_name)

    val sound = library.firstOrNull { it.id == soundId }

    // The audio left the library (deleted from another surface while listen mode was open) — close.
    LaunchedEffect(library, soundId) {
        if (library.isNotEmpty() && library.none { it.id == soundId }) onBack()
    }

    if (sound == null) {
        // Library not hydrated yet (cold start / process recreate). Hold an opaque backdrop that
        // still honours back instead of flashing the Vault list behind it.
        ImmersiveBackdrop(modifier = Modifier.predictiveBackTransition(onBack = onBack)) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                ImmersiveTopBar(onBack = onBack)
            }
        }
        return
    }

    // Real amplitude envelope — instant if already decoded this session, otherwise decoded off the
    // main thread (the screen shows a neutral placeholder until it arrives). Kept in-memory only;
    // see the PR discussion on why persisting this derived data is not worth it yet.
    var peaks by remember(sound.id) { mutableStateOf(WaveformExtractor.cached(sound.id)) }
    LaunchedEffect(sound.id) {
        if (peaks == null) peaks = WaveformExtractor.extract(context, sound, WAVEFORM_BARS)
    }

    val isThisPlaying = playingSound?.id == soundId
    val paused = pausedProgress[soundId]
    val durationMs =
        if (isThisPlaying) playbackProgress?.durationMs else paused?.durationMs ?: durations[soundId]
    val positionMs = if (isThisPlaying) playbackProgress?.positionMs ?: 0 else paused?.positionMs ?: 0
    val fraction =
        (if (isThisPlaying) playbackProgress?.fraction ?: 0f else paused?.fraction ?: 0f).coerceIn(0f, 1f)

    ImmersiveListenScreen(
        title = sound.name,
        collectionLabel = resolveListenCollectionLabel(soundId, collections, collectionsByAudio, activeFilter, systemLabel),
        dateLabel = sound.dateAdded?.let { formatFullDate(it) },
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isThisPlaying,
        progressFraction = fraction,
        peaks = peaks,
        onPlayPause = { viewModel.playOrStop(sound.copy(isPlaying = isThisPlaying)) },
        onRestart = { viewModel.seekTo(0, soundId) },
        onSeek = { f -> durationMs?.let { d -> viewModel.seekTo((f * d).toInt(), soundId) } },
        onBack = onBack,
    )
}

/**
 * Resolves the private-collection label for the header. Prefers the active Vault filter chip when
 * the audio belongs to it; otherwise the first private collection the audio is in (an audio can be
 * tagged to several). System "Baúl" resolves to its locale-aware label.
 */
internal fun resolveListenCollectionLabel(
    soundId: String,
    collections: List<Collection>,
    collectionsByAudio: Map<String, List<String>>,
    activeFilter: String?,
    systemLabel: String,
): String {
    val memberIds = collectionsByAudio[soundId].orEmpty().toSet()
    val privateMemberships = collections.filter { it.isPrivate && it.id in memberIds }
    val chosen =
        activeFilter?.let { filter -> privateMemberships.firstOrNull { it.id == filter } }
            ?: privateMemberships.firstOrNull()
    return chosen?.let { if (it.isSystem) systemLabel else it.name }.orEmpty()
}

@Composable
@Suppress("LongParameterList")
internal fun ImmersiveListenScreen(
    title: String,
    collectionLabel: String,
    dateLabel: String?,
    positionMs: Int,
    durationMs: Int?,
    isPlaying: Boolean,
    progressFraction: Float,
    peaks: FloatArray?,
    onPlayPause: () -> Unit,
    onRestart: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ImmersiveBackdrop(modifier = modifier.predictiveBackTransition(onBack = onBack)) {
        // safeDrawingPadding keeps the content clear of the status/navigation bars while the
        // backdrop gradient still bleeds full-screen behind them (edge-to-edge).
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ImmersiveTopBar(onBack = onBack)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top half: collection · date · name, grouped close together.
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ImmersiveFields(collectionLabel = collectionLabel, dateLabel = dateLabel, title = title)
                }
                // Bottom half: the real waveform (scrubbable) + the position / duration readout.
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ImmersiveWaveform(
                            progressFraction = progressFraction,
                            peaks = peaks,
                            onSeek = onSeek,
                            modifier = Modifier.fillMaxWidth().height(WAVEFORM_HEIGHT),
                        )
                        Spacer(modifier = Modifier.height(Spacing.SM))
                        TimeReadout(positionMs = positionMs, durationMs = durationMs)
                    }
                }
                ImmersiveControls(isPlaying = isPlaying, onRestart = onRestart, onPlayPause = onPlayPause)
                Spacer(modifier = Modifier.height(Spacing.LG))
            }
        }
    }
}

@Composable
private fun ImmersiveBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Always-dark "lights-down" surface (see ImmersiveListenTheme): legible status-bar icons in both
    // modes + matches the design. The acid wash at low alpha gives an emotional glow without a
    // one-off color token (same approach as VaultEmptyState).
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
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .background(wash),
        ) {
            content()
        }
    }
}

@Composable
private fun ImmersiveTopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TOP_BAR_HEIGHT)
                .padding(horizontal = Spacing.SM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.app_ic_arrow_back),
                contentDescription = stringResource(R.string.app_vault_immersive_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.app_vault_immersive_mode_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = EYEBROW_LETTER_SPACING,
            )
        }
        // Balances the back button so the title stays centered. No overflow menu: listen mode is
        // listen-only and exposes no per-audio actions (share is forbidden in the Vault).
        Spacer(modifier = Modifier.width(TOP_BAR_ICON_SLOT))
    }
}

@Composable
private fun ImmersiveFields(
    collectionLabel: String,
    dateLabel: String?,
    title: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        if (collectionLabel.isNotEmpty()) {
            Text(
                text = collectionLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = EYEBROW_LETTER_SPACING,
            )
        }
        if (dateLabel != null) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = EYEBROW_LETTER_SPACING,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimeReadout(
    positionMs: Int,
    durationMs: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDuration(positionMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(durationMs ?: 0),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImmersiveControls(
    isPlaying: Boolean,
    onRestart: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.XL, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Skip-to-start on the LEFT — the universal transport convention (music/podcast players),
        // not a square "stop" on the right. Secondary action: plain icon button, play stays the
        // prominent filled control.
        IconButton(onClick = onRestart, modifier = Modifier.size(RESTART_BUTTON_SIZE)) {
            Icon(
                painter = painterResource(R.drawable.app_ic_skip_previous),
                contentDescription = stringResource(R.string.app_vault_immersive_restart),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(RESTART_ICON_SIZE),
            )
        }
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(TRANSPORT_BUTTON_SIZE),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                painter =
                    if (isPlaying) {
                        rememberVectorPainter(AppIcons.Pause)
                    } else {
                        painterResource(R.drawable.app_ic_play_arrow)
                    },
                contentDescription = stringResource(if (isPlaying) R.string.app_pause else R.string.app_play),
                modifier = Modifier.size(TRANSPORT_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun ImmersiveWaveform(
    progressFraction: Float,
    peaks: FloatArray?,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Real amplitude envelope from [WaveformExtractor]. Until it arrives (or if decoding failed)
    // `peaks` is null and we draw a neutral flat baseline — never a fake-looking wave. Dragging
    // horizontally scrubs: the fill follows the finger and `onSeek` fires the fraction on release.
    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = WAVEFORM_UNPLAYED_ALPHA)
    val barCount = peaks?.size ?: WAVEFORM_BARS
    val scrubberLabel = stringResource(R.string.app_vault_immersive_waveform)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val displayFraction = (scrub ?: progressFraction).coerceIn(0f, 1f)
    Canvas(
        modifier =
            modifier
                .semantics {
                    contentDescription = scrubberLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrub = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            scrub?.let(onSeek)
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                    )
                },
    ) {
        val slot = size.width / barCount
        val barWidth = slot * WAVEFORM_BAR_FILL
        val centerY = size.height / 2f
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        for (i in 0 until barCount) {
            val amplitude = peaks?.getOrNull(i) ?: WAVEFORM_PLACEHOLDER_FRACTION
            val barHeight = amplitude.coerceIn(WAVEFORM_MIN_FRACTION, 1f) * size.height
            val played = i.toFloat() / barCount < displayFraction
            val x = i * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (played) playedColor else unplayedColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

// Bar count used for the placeholder + as the extractor's target resolution.
private const val WAVEFORM_BARS = 56
private const val WAVEFORM_BAR_FILL = 0.45f
private const val WAVEFORM_MIN_FRACTION = 0.06f
private const val WAVEFORM_PLACEHOLDER_FRACTION = 0.12f
private const val WAVEFORM_UNPLAYED_ALPHA = 0.30f
private val WAVEFORM_HEIGHT = 120.dp
private val TOP_BAR_HEIGHT = 64.dp
private val TOP_BAR_ICON_SLOT = 48.dp
private val TRANSPORT_BUTTON_SIZE = 80.dp
private val TRANSPORT_ICON_SIZE = 36.dp
private val RESTART_BUTTON_SIZE = 56.dp
private val RESTART_ICON_SIZE = 30.dp
private val EYEBROW_LETTER_SPACING = 2.sp

// Radial-glow stops + alphas — mirror VaultEmptyState's emotional wash (low on purpose).
private const val GLOW_CENTER_ALPHA = 0.16f
private const val GLOW_MID_ALPHA = 0.04f
private const val GLOW_STOP_CENTER = 0.0f
private const val GLOW_STOP_MID = 0.55f
private const val GLOW_STOP_EDGE = 1.0f
