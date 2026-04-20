package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundItem(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteClick: () -> Unit,
    originLabel: String? = null,
) {
    if (sound.isBundled()) {
        SoundCard(
            sound = sound,
            playbackProgress = playbackProgress,
            onPlayClick = onPlayClick,
            onSeek = onSeek,
            onShareClick = onShareClick,
            onFavoriteClick = onFavoriteClick,
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
                positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            )
        }
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground(dismissState) },
    ) {
        SoundCard(
            sound = sound,
            playbackProgress = playbackProgress,
            onPlayClick = onPlayClick,
            onSeek = onSeek,
            onShareClick = onShareClick,
            onFavoriteClick = onFavoriteClick,
            originLabel = originLabel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteBackground(dismissState: SwipeToDismissBoxState) {
    // Only render when an active swipe is in progress to avoid the background
    // bleeding through the card's horizontal padding when at rest.
    if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) return
    val alignment =
        when (dismissState.dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            SwipeToDismissBoxValue.Settled -> Alignment.Center
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SoundCard(
    sound: Sound,
    playbackProgress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    originLabel: String? = null,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(playbackProgress) {
        if (!isDragging) {
            sliderPosition = playbackProgress?.fraction ?: 0f
        }
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
                if (!sound.isBundled()) {
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (sound.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription =
                                stringResource(
                                    if (sound.isFavorite) R.string.app_remove_from_favorites else R.string.app_add_to_favorites,
                                ),
                            tint =
                                if (sound.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                        )
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.app_share_chooser_title),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onPlayClick) {
                    Icon(
                        imageVector = if (sound.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (sound.isPlaying) R.string.app_pause else R.string.app_play),
                    )
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
                                inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.24f),
                                disabledInactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.24f),
                                disabledThumbColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.24f),
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
private fun formatRelativeDate(epochMs: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val dateDay = startOfDay(epochMs)
    val diffDays = TimeUnit.MILLISECONDS.toDays(today - dateDay)
    val resources = LocalResources.current
    return when {
        diffDays == 0L -> stringResource(R.string.app_date_today)
        diffDays == 1L -> stringResource(R.string.app_date_yesterday)
        diffDays < RELATIVE_DATE_MAX_DAYS -> resources.getQuantityString(R.plurals.app_date_days_ago, diffDays.toInt(), diffDays.toInt())
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
    }
}
