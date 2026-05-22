/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlin.math.sin

/**
 * Listen mode — the full-screen, listen-only player for a single Vault audio. Reached by tapping
 * play on any audio in the Vault tab (`CollectionPlaybackUI.IMMERSIVE`); the Vault list itself is
 * unchanged (same `SoundsList` cards as My Sounds).
 *
 * Renders only the four facts a Vault audio actually carries — name, date, duration and the private
 * collection it belongs to. The Claude Design mock additionally shows a hand-written caption, an
 * event eyebrow and a free-text subtitle; the Vault stores none of those, so they are deliberately
 * omitted (mirroring `VaultEmptyState`, which dropped the polaroid for the same reason). The hero
 * photo is replaced by a decorative gradient tile, and the waveform is synthetic (the player exposes
 * position/duration, not amplitude samples) — both follow the design's own synthetic approach.
 *
 * Colors stay on the ink × acid `AppTheme` roles (no warm literals, no `isSystemInDarkTheme`),
 * matching the Vault-list decision to render against the real theme: dark mode reads like the mock,
 * light mode follows the theme. Always-dark, if wanted, is a follow-up theming choice.
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
            Column { ImmersiveTopBar(collectionLabel = "", onBack = onBack) }
        }
        return
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
        onPlayPause = { viewModel.playOrStop(sound.copy(isPlaying = isThisPlaying)) },
        onBack = onBack,
    )
}

/**
 * Resolves the private-collection label for the top bar. Prefers the active Vault filter chip when
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
internal fun ImmersiveListenScreen(
    title: String,
    collectionLabel: String,
    dateLabel: String?,
    positionMs: Int,
    durationMs: Int?,
    isPlaying: Boolean,
    progressFraction: Float,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ImmersiveBackdrop(modifier = modifier.predictiveBackTransition(onBack = onBack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ImmersiveTopBar(collectionLabel = collectionLabel, onBack = onBack)
            // `heightIn(min = maxHeight)` + `verticalScroll` keeps the spread layout (hero up top,
            // transport pinned low) when the content fits, but lets it scroll instead of clipping on
            // short screens or at large accessibility font scales.
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = maxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.XL, vertical = Spacing.LG),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    MemoryTile()
                    ImmersiveTitleBlock(title = title, dateLabel = dateLabel)
                    ImmersiveTransport(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        progressFraction = progressFraction,
                        onPlayPause = onPlayPause,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Warm-mood wash adapted to the palette: the accent role at low alpha gives an emotional glow
    // without a one-off color token. Same approach as VaultEmptyState; theme-aware in both modes.
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

@Composable
private fun ImmersiveTopBar(
    collectionLabel: String,
    onBack: () -> Unit,
) {
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
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_vault_immersive_mode_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = EYEBROW_LETTER_SPACING,
            )
            if (collectionLabel.isNotEmpty()) {
                Text(
                    text = collectionLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = EYEBROW_LETTER_SPACING,
                )
            }
        }
        // Balances the back button so the title column stays centered. No overflow menu: listen
        // mode is listen-only and exposes no per-audio actions (share is forbidden in the Vault).
        Spacer(modifier = Modifier.width(TOP_BAR_ICON_SLOT))
    }
}

@Composable
private fun MemoryTile() {
    // Decorative hero standing in for the mock's polaroid. The Vault stores no images, so this is a
    // palette gradient, not a (fake) photo — and carries no caption. Purely decorative => no
    // contentDescription.
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val tileBrush =
        remember(accent, surface) {
            Brush.linearGradient(listOf(accent.copy(alpha = TILE_ACCENT_ALPHA), surface))
        }
    Box(
        modifier =
            Modifier
                .rotate(MEMORY_TILE_TILT)
                .size(MEMORY_TILE_SIZE)
                .clip(RoundedCornerShape(Spacing.MD))
                .background(tileBrush),
    )
}

@Composable
private fun ImmersiveTitleBlock(
    title: String,
    dateLabel: String?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (dateLabel != null) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = EYEBROW_LETTER_SPACING,
            )
            Spacer(modifier = Modifier.height(Spacing.SM))
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
private fun ImmersiveTransport(
    positionMs: Int,
    durationMs: Int?,
    isPlaying: Boolean,
    progressFraction: Float,
    onPlayPause: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.LG),
    ) {
        ImmersiveWaveform(
            progressFraction = progressFraction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(WAVEFORM_HEIGHT),
        )
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
        Text(
            text = stringResource(R.string.app_vault_immersive_listen_only_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = EYEBROW_LETTER_SPACING,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Suppress("MagicNumber")
private fun ImmersiveWaveform(
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    // Synthetic, deterministic bars: the player exposes position/duration, not amplitude samples,
    // so (like the mock) heights come from a fixed sin seed. No animation — keeps the screen idle
    // for `waitForIdle()`-based tests. Played portion uses the accent role; the rest is muted.
    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
    Canvas(modifier = modifier) {
        val slot = size.width / WAVEFORM_BARS
        val barWidth = slot * WAVEFORM_BAR_FILL
        val centerY = size.height / 2f
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        for (i in 0 until WAVEFORM_BARS) {
            val seed = sin(i * 0.7) * 0.4 + sin(i * 0.23) * 0.35 + 0.5
            val barHeight = seed.coerceIn(WAVEFORM_MIN_FRACTION.toDouble(), 1.0).toFloat() * size.height
            val played = i.toFloat() / WAVEFORM_BARS < progressFraction
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

private const val WAVEFORM_BARS = 56
private const val WAVEFORM_BAR_FILL = 0.45f
private const val WAVEFORM_MIN_FRACTION = 0.10f
private val WAVEFORM_HEIGHT = 96.dp
private val MEMORY_TILE_SIZE = 188.dp
private const val MEMORY_TILE_TILT = -3f
private const val TILE_ACCENT_ALPHA = 0.55f
private val TOP_BAR_HEIGHT = 64.dp
private val TOP_BAR_ICON_SLOT = 48.dp
private val TRANSPORT_BUTTON_SIZE = 80.dp
private val TRANSPORT_ICON_SIZE = 36.dp
private val EYEBROW_LETTER_SPACING = 2.sp

// Radial-glow stops + alphas — mirror VaultEmptyState's emotional wash (low on purpose).
private const val GLOW_CENTER_ALPHA = 0.16f
private const val GLOW_MID_ALPHA = 0.04f
private const val GLOW_STOP_CENTER = 0.0f
private const val GLOW_STOP_MID = 0.55f
private const val GLOW_STOP_EDGE = 1.0f
