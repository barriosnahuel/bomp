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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundItem(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onDelete: () -> Unit,
    onPinClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    originLabel: String? = null,
) {
    if (sound.isBundled()) {
        SoundCard(
            sound = sound,
            playbackProgress = playbackProgress,
            onPlayClick = onPlayClick,
            onSeek = onSeek,
            onShareClick = onShareClick,
            onPinClick = null,
            onEditClick = null,
            onDelete = null,
            originLabel = originLabel,
        )
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
                        performPinHaptic(view)
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        currentOnPinClick()
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        performDeleteHaptic(view)
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

private fun performPinHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

private fun performDeleteHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionBackground(dismissState: SwipeToDismissBoxState) {
    if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) return
    val isPinAction = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
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
                .padding(horizontal = 16.dp),
        contentAlignment = alignment,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
    }
}

@Composable
private fun SoundCard(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onPinClick: (() -> Unit)?,
    onEditClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    originLabel: String? = null,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(playbackProgress) {
        if (!isDragging) {
            sliderPosition = playbackProgress?.fraction ?: 0f
        }
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick =
                        if (onEditClick != null) {
                            { showMenu = true }
                        } else {
                            null
                        },
                ),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = formatDuration(playbackProgress?.positionMs ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.weight(1f))
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
        if (sound.isPinned) {
            Icon(
                imageVector = AppIcons.PushPin,
                contentDescription = stringResource(R.string.app_pinned),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onShareClick) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.app_share_chooser_title),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (onEditClick != null) {
            Box {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                ) {
                    if (onPinClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (sound.isPinned) R.string.app_unpin else R.string.app_pin)) },
                            leadingIcon = { Icon(AppIcons.PushPin, contentDescription = null) },
                            onClick = {
                                onMenuDismiss()
                                onPinClick()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.app_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            onMenuDismiss()
                            onEditClick()
                        },
                    )
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
