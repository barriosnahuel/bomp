/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.PlaybackProgress
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Immersive listen view for a single private [Collection]. Spec § 3.5:
 *
 * - Dark scrim background (drawn via `Ink1000` so it stays the same in light and dark themes —
 *   the goal is "step out of the bright app into a quiet room").
 * - No share button anywhere in the view (preset `listen-only`).
 * - Delete lives behind the overflow to reduce accidents in emotional moments.
 * - Title is the collection name. Audio rows show name + Play/Pause + progress bar. The waveform
 *   + Palette API treatment from the spec is deferred (handoff) but the dark immersive surface
 *   keeps the same intent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImmersiveListenScreen(
    collection: Collection,
    viewModel: SoundsViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // Source from `library` (full catalog) instead of `sounds` (tab-filtered). When the Vault tab
    // is selected, `sounds` collapses to emptyList() so the LazyColumn would render zero rows
    // even though the collection holds audios. `library` carries the whole user catalog so
    // resolving `collection.audioIds → Sound` works regardless of selected tab.
    val library by viewModel.library.collectAsState()
    val collectionsState by viewModel.collections.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val pausedProgress by viewModel.pausedProgress.collectAsState()
    val durations by viewModel.soundDurations.collectAsState()

    // The collection's audioIds are looked up against the latest snapshot so a tag-changed
    // upstream (in another tab) reflects immediately. Sounds not present in `library` (e.g. file
    // deleted) are filtered out so the row count matches what the user can actually play.
    val freshCollection =
        remember(collection.id, collectionsState) {
            collectionsState.firstOrNull { it.id == collection.id } ?: collection
        }
    val audioIds = remember(freshCollection) { freshCollection.audioIds.toSet() }
    val visibleSounds =
        remember(audioIds, library) {
            library.filter { it.id in audioIds }
        }

    LaunchedEffect(collection.id) {
        AnalyticsTrackerProvider
            .get(context.applicationContext)
            .logScreen(CanonicalScreenName.VAULT_COLLECTION_LISTEN)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0C)),
        containerColor = Color(0xFF0B0B0C),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_vault_listen_title_format, freshCollection.name),
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.app_vault_listen_back),
                            tint = Color.White,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0B0B0C),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (visibleSounds.isEmpty()) {
                EmptyImmersiveBody(collectionName = freshCollection.name)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD),
                ) {
                    items(visibleSounds, key = { it.id }) { sound ->
                        ImmersiveAudioRow(
                            sound = sound,
                            collection = freshCollection,
                            playbackProgress =
                                if (sound.isPlaying) playbackProgress else pausedProgress[sound.id],
                            durationMs = durations[sound.id],
                            onPlayClick = { viewModel.playOrStop(sound) },
                            onRemove = {
                                viewModel.removeAudioFromCollection(
                                    collectionId = collection.id,
                                    audioId = sound.id,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveAudioRow(
    sound: Sound,
    collection: Collection,
    playbackProgress: PlaybackProgress?,
    durationMs: Int?,
    onPlayClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1D))
                .padding(Spacing.LG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = if (sound.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription =
                        if (sound.isPlaying) stringResource(R.string.app_pause) else stringResource(R.string.app_play),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.size(Spacing.SM))
            Text(
                text = sound.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { overflowOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.app_vault_listen_overflow),
                    tint = Color.White,
                )
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text =
                                stringResource(R.string.app_vault_listen_delete_audio, collection.name),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        overflowOpen = false
                        onRemove()
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.XS))
        val fraction =
            playbackProgress?.fraction
                ?: (if ((durationMs ?: 0) > 0) 0f else 0f)
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color(0xFFD7FF3A),
            trackColor = Color(0x33FFFFFF),
        )
    }
}

@Composable
private fun EmptyImmersiveBody(collectionName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_vault_listen_empty_headline),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.SM))
        Text(
            text = stringResource(R.string.app_vault_listen_empty_body, collectionName),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB8B7AE),
            textAlign = TextAlign.Center,
        )
    }
}
