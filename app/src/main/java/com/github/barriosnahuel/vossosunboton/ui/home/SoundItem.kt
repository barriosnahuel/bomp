/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

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
    originLabel: String? = null,
    isWelcomeVariant: Boolean = false,
    borderOverride: BorderStroke? = null,
    trailingLabel: String? = null,
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
                onDelete = onDelete,
                originLabel = originLabel,
                borderOverride = borderOverride,
                trailingLabel = trailingLabel,
            )
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
                    onDelete = null,
                    onLongClick = { performRejectHaptic(view) },
                    originLabel = originLabel,
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
            onDelete = onDelete,
            originLabel = originLabel,
        )
    }
}

private fun performConfirmHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

private fun performRejectHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
    val icon = if (isPinAction) AppIcons.PushPin else Icons.Default.Delete
    val alignment = if (isPinAction) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = Spacing.LG),
        contentAlignment = alignment,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
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
    onDelete: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    originLabel: String? = null,
    borderOverride: BorderStroke? = null,
    trailingLabel: String? = null,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(playbackProgress) {
        if (!isDragging) {
            sliderPosition = playbackProgress?.fraction ?: 0f
        }
    }

    val hasMenuItems = onEditClick != null || onDelete != null
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
                onDeleteClick = onDelete,
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
                                color = if (sound.isPlaying) playContainerColor.copy(alpha = 0.18f) else Color.Transparent,
                                shape = CircleShape,
                            ).padding(6.dp),
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
                            imageVector = if (sound.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
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
                                onSeek((sliderPosition * playbackProgress.durationMs).toInt())
                            }
                        },
                        enabled = sound.isPlaying,
                        colors =
                            SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SoundCardMetaRow(
                        sound = sound,
                        playbackProgress = playbackProgress,
                        durationMs = durationMs,
                        trailingLabel = trailingLabel,
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
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val displayMs =
            when {
                sound.isPlaying && playbackProgress != null -> playbackProgress.positionMs
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
            val dateAdded = sound.dateAdded
            if (dateAdded != null) {
                Text(
                    text = formatRelativeDate(dateAdded),
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
    onDeleteClick: (() -> Unit)?,
) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        if (onPinClick != null) {
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (sound.isPinned) AppIcons.PushPin else AppIcons.PushPinOutlined,
                    contentDescription = stringResource(if (sound.isPinned) R.string.app_unpin else R.string.app_pin),
                    tint = if (sound.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onShareClick) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.app_share_chooser_title),
            )
        }
        if (onEditClick != null || onDeleteClick != null) {
            Box {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                ) {
                    if (onEditClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                onMenuDismiss()
                                onEditClick()
                            },
                        )
                    }
                    if (onDeleteClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
