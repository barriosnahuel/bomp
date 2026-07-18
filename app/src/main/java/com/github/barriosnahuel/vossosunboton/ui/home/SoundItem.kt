/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.feature.playback.seekTargetMs
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.haptics.performConfirmHaptic
import com.github.barriosnahuel.vossosunboton.ui.haptics.performRejectHaptic
import com.github.barriosnahuel.vossosunboton.ui.rememberReduceMotionEnabled
import com.github.barriosnahuel.vossosunboton.ui.theme.DISABLED_TRACK_ALPHA
import com.github.barriosnahuel.vossosunboton.ui.theme.MUTED_TEXT_ALPHA
import com.github.barriosnahuel.vossosunboton.ui.theme.PLAYING_TINT_ALPHA
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import com.github.barriosnahuel.vossosunboton.ui.theme.bompEffectsSpec
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

// One-time swipe-hint nudge (welcome card): how far it peeks and the in/out durations.
private val WELCOME_HINT_PEEK = 88.dp
private const val WELCOME_HINT_OUT_MS = 220
private const val WELCOME_HINT_BACK_MS = 280

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundItem(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    durationMs: Int? = null,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onDelete: () -> Unit,
    onPinClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onAddToCollection: (() -> Unit)? = null,
    originLabel: String? = null,
    isWelcomeVariant: Boolean = false,
    borderOverride: BorderStroke? = null,
    trailingLabel: String? = null,
    // When true (welcome variant only), the card runs a one-time swipe-hint nudge: it peeks the
    // delete background and settles back, teaching that the card is swipe-to-delete. [onSwipeHintShown]
    // fires once it has run (or was skipped for reduced-motion) so it never repeats.
    showSwipeHint: Boolean = false,
    onSwipeHintShown: () -> Unit = {},
    collectionLabels: List<String> = emptyList(),
    showCollectionLabels: Boolean = true,
    shareEnabled: Boolean = true,
) {
    if (isWelcomeVariant) {
        // System anchor: pin and edit are not available, but Delete IS — exposed via swipe-left
        // and via the long-press dropdown so the Bomper is not forced to listen end-to-end. Share
        // stays visible too (sharing the welcome message is positive word-of-mouth). Background
        // reuses `surfaceVariant` so the AA contrast checks for the regular card apply; the tonal
        // differentiator is the thin Acid hairline border passed via [borderOverride].
        val view = LocalView.current
        val dismissState =
            remember {
                SwipeToDismissBoxState(
                    initialValue = SwipeToDismissBoxValue.Settled,
                    positionalThreshold = { totalDistance -> totalDistance * 0.35f },
                )
            }
        val currentOnDelete by rememberUpdatedState(onDelete)
        LaunchedEffect(Unit) {
            snapshotFlow { dismissState.settledValue }
                .collect { settled ->
                    when (settled) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            // Pin attempt — not applicable to a system anchor. Reject haptic
                            // mirrors the bundled-sound delete-attempt treatment.
                            performRejectHaptic(view)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            performConfirmHaptic(view)
                            currentOnDelete()
                        }
                        SwipeToDismissBoxValue.Settled -> Unit
                    }
                }
        }

        // One-time swipe-hint nudge: the card peeks the delete background and settles back, teaching
        // the swipe-to-delete gesture. Driven by a self-contained Animatable
        // offset rather than the SwipeToDismissBox anchors, which have no public partial-peek API.
        val hintOffsetX = remember { Animatable(0f) }
        val density = LocalDensity.current
        val peekPx = remember(density) { with(density) { WELCOME_HINT_PEEK.toPx() } }
        val reduceMotion = rememberReduceMotionEnabled()
        val currentOnHintShown by rememberUpdatedState(onSwipeHintShown)
        LaunchedEffect(showSwipeHint) {
            if (!showSwipeHint) return@LaunchedEffect
            // Wait for the first frame so the node is laid out before animating, mirroring the
            // FocusRequester incantation documented in CLAUDE.md.
            withFrameNanos { }
            try {
                if (!reduceMotion) {
                    hintOffsetX.animateTo(-peekPx, tween(WELCOME_HINT_OUT_MS))
                    hintOffsetX.animateTo(0f, tween(WELCOME_HINT_BACK_MS))
                }
                currentOnHintShown()
            } catch (e: CancellationException) {
                // Left composition mid-peek — keep the hint pending so it runs again next time.
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("Welcome swipe hint nudge failed", e))
                currentOnHintShown()
            }
        }

        Box {
            // Delete-side background revealed under the card during the peek — mirrors the delete
            // half of [SwipeActionBackground] (which renders nothing while the state is Settled).
            if (hintOffsetX.value < 0f) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = Spacing.LG),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Box(modifier = Modifier.offset { IntOffset(hintOffsetX.value.roundToInt(), 0) }) {
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = { SwipeActionBackground(dismissState, canPin = false, canDelete = true) },
                ) {
                    SoundCard(
                        sound = sound,
                        playbackProgress = playbackProgress,
                        durationMs = durationMs,
                        onPlayClick = onPlayClick,
                        onSeek = onSeek,
                        onShareClick = onShareClick,
                        onPinClick = null,
                        onEditClick = null,
                        onAddToCollection = null,
                        onDelete = onDelete,
                        originLabel = originLabel,
                        borderOverride = borderOverride,
                        trailingLabel = trailingLabel,
                        collectionLabels = collectionLabels,
                        showCollectionLabels = showCollectionLabels,
                        shareEnabled = shareEnabled,
                    )
                }
            }
        }
        return
    }
    if (sound.isBundled()) {
        val view = LocalView.current
        val dismissState =
            remember {
                SwipeToDismissBoxState(
                    initialValue = SwipeToDismissBoxValue.Settled,
                    positionalThreshold = { totalDistance -> totalDistance * 0.35f },
                )
            }
        val currentOnPinClick by rememberUpdatedState(onPinClick)
        LaunchedEffect(Unit) {
            snapshotFlow { dismissState.settledValue }
                .collect { settled ->
                    when (settled) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            performConfirmHaptic(view)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            currentOnPinClick()
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            performRejectHaptic(view)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                        SwipeToDismissBoxValue.Settled -> Unit
                    }
                }
        }
        val noOpHaptic =
            remember {
                object : HapticFeedback {
                    @Suppress("EmptyFunctionBlock")
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
                }
            }
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = { SwipeActionBackground(dismissState, canDelete = false) },
        ) {
            CompositionLocalProvider(LocalHapticFeedback provides noOpHaptic) {
                SoundCard(
                    sound = sound,
                    playbackProgress = playbackProgress,
                    durationMs = durationMs,
                    onPlayClick = onPlayClick,
                    onSeek = onSeek,
                    onShareClick = onShareClick,
                    onPinClick = onPinClick,
                    onEditClick = null,
                    onAddToCollection = null,
                    onDelete = null,
                    onLongClick = { performRejectHaptic(view) },
                    originLabel = originLabel,
                    collectionLabels = collectionLabels,
                    showCollectionLabels = showCollectionLabels,
                    shareEnabled = shareEnabled,
                )
            }
        }
        return
    }
    // rememberSaveable (used by rememberSwipeToDismissBoxState) restores the dismissed state
    // when the item re-enters the composition after undo, immediately re-triggering onDelete.
    // remember (no persistence) ensures the state always starts at Settled on re-entry.
    val dismissState =
        remember {
            SwipeToDismissBoxState(
                initialValue = SwipeToDismissBoxValue.Settled,
                positionalThreshold = { totalDistance -> totalDistance * 0.35f },
            )
        }
    val view = LocalView.current
    // rememberUpdatedState ensures the LaunchedEffect(Unit) always calls the CURRENT lambda,
    // not the one captured at first composition. Without this, onPinClick closes over the
    // initial Sound (isPinned=false), so a second swipe re-pins instead of unpinning.
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentOnDelete by rememberUpdatedState(onDelete)
    // settledValue only changes when an animation COMPLETES (unlike currentValue, which changes
    // at animation start). Watching it ensures we act only after the card is fully swiped.
    LaunchedEffect(Unit) {
        snapshotFlow { dismissState.settledValue }
            .collect { settled ->
                when (settled) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        performConfirmHaptic(view)
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        currentOnPinClick()
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        performConfirmHaptic(view)
                        currentOnDelete()
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeActionBackground(dismissState) },
    ) {
        SoundCard(
            sound = sound,
            playbackProgress = playbackProgress,
            durationMs = durationMs,
            onPlayClick = onPlayClick,
            onSeek = onSeek,
            onShareClick = onShareClick,
            onPinClick = onPinClick,
            onEditClick = onEditClick,
            onAddToCollection = onAddToCollection,
            onDelete = onDelete,
            originLabel = originLabel,
            collectionLabels = collectionLabels,
            showCollectionLabels = showCollectionLabels,
            shareEnabled = shareEnabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionBackground(
    dismissState: SwipeToDismissBoxState,
    canPin: Boolean = true,
    canDelete: Boolean = true,
) {
    if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) return
    val isPinAction = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    val actionAvailable = if (isPinAction) canPin else canDelete
    if (!actionAvailable) return
    val backgroundColor =
        if (isPinAction) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    val iconTint =
        if (isPinAction) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
    val icon = if (isPinAction) rememberVectorPainter(AppIcons.PushPin) else painterResource(R.drawable.app_ic_delete)
    val alignment = if (isPinAction) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = Spacing.LG),
        contentAlignment = alignment,
    ) {
        Icon(painter = icon, contentDescription = null, tint = iconTint)
    }
}

@Composable
private fun SoundCard(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    durationMs: Int?,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onPinClick: (() -> Unit)?,
    onEditClick: (() -> Unit)?,
    onAddToCollection: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    originLabel: String? = null,
    borderOverride: BorderStroke? = null,
    trailingLabel: String? = null,
    collectionLabels: List<String> = emptyList(),
    showCollectionLabels: Boolean = true,
    shareEnabled: Boolean = true,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(playbackProgress) {
        if (!isDragging) {
            sliderPosition = playbackProgress?.fraction ?: 0f
        }
    }

    val hasMenuItems = onEditClick != null || onDelete != null || onAddToCollection != null
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.SM, vertical = Spacing.XS)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick =
                        if (hasMenuItems) {
                            { showMenu = true }
                        } else {
                            onLongClick
                        },
                ),
        border = borderOverride ?: BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.SM, vertical = Spacing.SM),
        ) {
            SoundCardHeader(
                sound = sound,
                originLabel = originLabel,
                showMenu = showMenu,
                onMenuDismiss = { showMenu = false },
                onPinClick = onPinClick,
                onShareClick = onShareClick,
                onEditClick = onEditClick,
                onAddToCollection = onAddToCollection,
                onDeleteClick = onDelete,
                shareEnabled = shareEnabled,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                val reduceMotion = rememberReduceMotionEnabled()
                val playContainerColor = MaterialTheme.colorScheme.primaryContainer
                // Tint fades with an effects spring (no bounce on colour); reduce-motion snaps it.
                val playTint by animateColorAsState(
                    // Fade to the same hue at alpha 0 (not Color.Transparent): interpolating toward
                    // transparent black would dip the tint dark in sRGB while the alpha drops.
                    targetValue =
                        if (sound.isPlaying) {
                            playContainerColor.copy(alpha = PLAYING_TINT_ALPHA)
                        } else {
                            playContainerColor.copy(alpha = 0f) // alpha-ok
                        },
                    animationSpec = if (reduceMotion) snap() else bompEffectsSpec(),
                    label = "play_tint",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .background(color = playTint, shape = CircleShape)
                            .padding(6.dp),
                ) {
                    FilledIconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(44.dp),
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = playContainerColor,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Icon(
                            painter =
                                if (sound.isPlaying) {
                                    rememberVectorPainter(
                                        AppIcons.Pause,
                                    )
                                } else {
                                    painterResource(R.drawable.app_ic_play_arrow)
                                },
                            contentDescription = stringResource(if (sound.isPlaying) R.string.app_pause else R.string.app_play),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { value ->
                            isDragging = true
                            sliderPosition = value
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            if (playbackProgress != null) {
                                // reserveEndMargin = false: a full-right drag may intend to reach
                                // the end and complete, unlike the waveform scrub (see seekTargetMs).
                                seekTargetMs(playbackProgress.durationMs.toLong(), sliderPosition, reserveEndMargin = false)
                                    ?.let(onSeek)
                            }
                        },
                        enabled = sound.isPlaying,
                        colors =
                            SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_TRACK_ALPHA),
                                disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_TRACK_ALPHA),
                                disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_TRACK_ALPHA),
                                disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SoundCardMetaRow(
                        sound = sound,
                        playbackProgress = playbackProgress,
                        durationMs = durationMs,
                        trailingLabel = trailingLabel,
                        collectionLabels = if (showCollectionLabels) collectionLabels else emptyList(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundCardMetaRow(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    durationMs: Int?,
    trailingLabel: String?,
    collectionLabels: List<String> = emptyList(),
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // `playbackProgress` is non-null while playing AND while paused (the caller passes the
        // retained paused position), so a paused sound shows where it is, not its full duration.
        val displayMs =
            when {
                playbackProgress != null -> playbackProgress.positionMs
                durationMs != null -> durationMs
                else -> null
            }
        if (displayMs != null) {
            Text(
                text = formatDuration(displayMs),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (trailingLabel != null) {
            Text(text = trailingLabel, style = MaterialTheme.typography.labelSmall)
        } else {
            // Right-aligned compact line: "[collection, collection · today]". Collections render
            // first so the eye sees taxonomy before recency; "·" separator follows the existing
            // typographic convention (see `originLabel` divider above the title row).
            val dateText = sound.dateAdded?.let { formatRelativeDate(it) }
            val parts =
                buildList {
                    if (collectionLabels.isNotEmpty()) add(collectionLabels.joinToString(", "))
                    if (dateText != null) add(dateText)
                }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SoundCardHeader(
    sound: Sound,
    originLabel: String?,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onPinClick: (() -> Unit)?,
    onShareClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    onAddToCollection: (() -> Unit)?,
    onDeleteClick: (() -> Unit)?,
    shareEnabled: Boolean = true,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    // Pin/unpin tint crossfades with an effects spring (no bounce on colour); reduce-motion snaps it.
    val pinTint by animateColorAsState(
        targetValue =
            if (sound.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else bompEffectsSpec(),
        label = "pin_tint",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sound.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            if (originLabel != null) {
                Text(
                    text = originLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_TEXT_ALPHA),
                )
            }
        }
        if (onPinClick != null) {
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (sound.isPinned) AppIcons.PushPin else AppIcons.PushPinOutlined,
                    contentDescription = stringResource(if (sound.isPinned) R.string.app_unpin else R.string.app_pin),
                    tint = pinTint,
                )
            }
        }
        if (shareEnabled) {
            IconButton(onClick = onShareClick) {
                Icon(
                    painter = painterResource(R.drawable.app_ic_share),
                    contentDescription = stringResource(R.string.app_share_chooser_title),
                )
            }
        }
        if (onEditClick != null || onDeleteClick != null || onAddToCollection != null) {
            Box {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                ) {
                    if (onAddToCollection != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_audio_menu_add_to_collection)) },
                            leadingIcon = { Icon(AppIcons.Add, contentDescription = null) },
                            onClick = {
                                onMenuDismiss()
                                onAddToCollection()
                            },
                        )
                    }
                    if (onEditClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_edit)) },
                            leadingIcon = { Icon(painterResource(R.drawable.app_ic_edit), contentDescription = null) },
                            onClick = {
                                onMenuDismiss()
                                onEditClick()
                            },
                        )
                    }
                    if (onDeleteClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_delete)) },
                            leadingIcon = { Icon(painterResource(R.drawable.app_ic_delete), contentDescription = null) },
                            onClick = {
                                onMenuDismiss()
                                onDeleteClick()
                            },
                        )
                    }
                }
            }
        }
    }
}
