/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.playback.ListenSessionMeter
import com.github.barriosnahuel.vossosunboton.feature.playback.seekTargetMs
import com.github.barriosnahuel.vossosunboton.feature.waveform.EnvelopeWaveform
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.home.formatFullDate
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
        ImmersiveBackdrop(modifier = Modifier) {
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

    TrackListenSession(
        tracker = tracker,
        soundId = soundId,
        isPlaying = isThisPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    ImmersiveListenScreen(
        title = sound.name,
        collectionLabel = resolveListenCollectionLabel(soundId, collections, collectionsByAudio, activeFilter, systemLabel),
        dateLabel = sound.dateAdded?.let { formatFullDate(it) },
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isThisPlaying,
        progressFraction = fraction,
        peaks = peaks,
        onPlayPause = {
            // Pause keeps the shared toggle; play goes to the session API so a replay after natural
            // completion stays on the long-form engine and starts from 0 (ADR 0022) — routing it
            // through playOrStop would fall back to MediaPlayer once the session target cleared.
            if (isThisPlaying) {
                viewModel.playOrStop(sound.copy(isPlaying = true))
            } else {
                viewModel.startListenSession(sound)
            }
        },
        onRestart = { viewModel.seekTo(0, soundId) },
        onSeek = { f -> durationMs?.let { d -> seekTargetMs(d.toLong(), f)?.let { viewModel.seekTo(it, soundId) } } },
        onBack = onBack,
    )
}

/**
 * Business tracking for a listen session: that it happened, how much of the audio actually got
 * heard, and whether it kept playing once the app left the foreground (the feature's promise).
 *
 * The session is the SCREEN, not the play tap — a resume after a pause re-enters
 * `startListenSession`, so counting starts there would report one session per tap and deflate every
 * per-session ratio. Both ends of the pair are skipped on a configuration change: a rotation tears
 * the composition down and rebuilds it, and counting that would inflate starts against ends. The
 * meter and the "already reported" flag survive that rebuild via `rememberSaveable`, so a rotation
 * mid-listen neither restarts the tally nor re-opens the session.
 */
@Composable
internal fun TrackListenSession(
    tracker: AnalyticsTracker,
    soundId: String,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int?,
    isChangingConfigurations: () -> Boolean = rememberConfigurationChangeProbe(),
) {
    val meter = rememberSaveable(soundId, saver = ListenSessionMeterSaver) { ListenSessionMeter() }
    var sessionReported by rememberSaveable(soundId) { mutableStateOf(false) }

    // Read as parameters, not lambdas: the meter only accrues if this composable is subscribed to
    // the position state, and a lambda read inside SideEffect subscribes to nothing.
    SideEffect {
        meter.onPosition(positionMs)
        meter.onDuration(durationMs)
    }

    LaunchedEffect(soundId) {
        if (!sessionReported) {
            sessionReported = true
            tracker.log(AnalyticsEvent.ListenSessionStart(surface = CanonicalScreenName.VAULT_LISTEN))
        }
    }

    // The observer and the teardown outlive the recomposition that created them, so they read the
    // latest value through this handle instead of capturing the one from first composition.
    val playingNow by rememberUpdatedState(isPlaying)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, soundId) {
        var backgroundReported = false
        val observer =
            LifecycleEventObserver { _, event ->
                val leftForeground = event == Lifecycle.Event.ON_STOP && !isChangingConfigurations()
                if (leftForeground && playingNow && !backgroundReported) {
                    backgroundReported = true
                    tracker.log(AnalyticsEvent.ListenBackgrounded(surface = CanonicalScreenName.VAULT_LISTEN))
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isChangingConfigurations()) {
                tracker.log(
                    AnalyticsEvent.ListenSessionEnd(
                        surface = CanonicalScreenName.VAULT_LISTEN,
                        listenedMs = meter.listenedMs,
                        durationMs = meter.durationMs,
                    ),
                )
            }
        }
    }
}

/** Reads "is this teardown a rotation?" off the host Activity; injectable so tests can drive it. */
@Composable
private fun rememberConfigurationChangeProbe(): () -> Boolean {
    val activity = LocalActivity.current
    return remember(activity) { { activity?.isChangingConfigurations == true } }
}

/** The tally is durable progress, so it survives an Activity recreate (CLAUDE.md § Stateful Composables). */
private val ListenSessionMeterSaver =
    listSaver<ListenSessionMeter, Int>(
        save = { listOf(it.listenedMs, it.durationMs) },
        restore = { saved -> ListenSessionMeter(initialListenedMs = saved[0]).apply { onDuration(saved[1]) } },
    )

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
    ImmersiveBackdrop(modifier = modifier) {
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
                        EnvelopeWaveform(
                            progress = progressFraction,
                            peaks = peaks,
                            contentDescription = stringResource(R.string.app_vault_immersive_waveform),
                            barCount = WAVEFORM_BARS,
                            barFill = WAVEFORM_BAR_FILL,
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

// Bar count used for the placeholder + as the extractor's target resolution; bar width as a
// fraction of each slot. The waveform itself is the shared [EnvelopeWaveform].
private const val WAVEFORM_BARS = 56
private const val WAVEFORM_BAR_FILL = 0.45f
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
