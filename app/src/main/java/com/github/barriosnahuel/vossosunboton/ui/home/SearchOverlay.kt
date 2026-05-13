/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

private sealed class SearchDisplayState {
    data object Initial : SearchDisplayState()

    data class Results(
        val sounds: List<Sound>,
    ) : SearchDisplayState()

    data object ZeroResults : SearchDisplayState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    query: String,
    results: List<Sound>,
    isSearchPending: Boolean,
    playbackProgress: PlaybackProgress?,
    soundDurations: Map<String, Int>,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onPinClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
) {
    BackHandler { onClose() }

    val displayState =
        when {
            query.isBlank() -> SearchDisplayState.Initial
            isSearchPending -> SearchDisplayState.Initial
            results.isEmpty() -> SearchDisplayState.ZeroResults
            else -> SearchDisplayState.Results(results)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { SearchField(query = query, onQueryChange = onQueryChange) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.app_search_close),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.ime),
                ) {
                    AnimatedContent(
                        targetState = displayState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "search_state",
                        modifier = Modifier.fillMaxSize(),
                    ) { state ->
                        when (state) {
                            is SearchDisplayState.Initial ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_search_initial_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = Spacing.XXL),
                                    )
                                }

                            is SearchDisplayState.ZeroResults ->
                                SearchZeroResultsContent(
                                    headline = stringResource(R.string.app_search_empty_headline),
                                    subtext = stringResource(R.string.app_search_empty_subtext),
                                )

                            is SearchDisplayState.Results ->
                                SearchResultsList(
                                    results = state.sounds,
                                    playbackProgress = playbackProgress,
                                    soundDurations = soundDurations,
                                    onPlayClick = onPlayClick,
                                    onSeek = onSeek,
                                    onShareClick = onShareClick,
                                    onPinClick = onPinClick,
                                    onDelete = onDelete,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchZeroResultsContent(
    headline: String,
    subtext: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.XXL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.SM))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val contentColor = MaterialTheme.colorScheme.onSecondary

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.app_search_hint)) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.app_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = contentColor,
                unfocusedTextColor = contentColor,
                cursorColor = MaterialTheme.colorScheme.primaryContainer,
                focusedPlaceholderColor = contentColor.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = contentColor.copy(alpha = 0.6f),
                focusedTrailingIconColor = MaterialTheme.colorScheme.primaryContainer,
                unfocusedTrailingIconColor = contentColor,
            ),
    )

    LaunchedEffect(Unit) {
        // Mirrors AddButtonScreen's auto-focus contract: search opens, the IME comes up, the user
        // types — so requestFocus is the path that makes the overlay usable. `withFrameNanos`
        // waits for the first layout pass so the FocusRequester has a node attached. A failure
        // here means the user has to tap the field manually, which is silent at runtime — surface
        // it as a non-fatal so a spike points at a real composition-lifecycle bug.
        withFrameNanos { /* wait for first frame so the node is attached */ }
        runCatching { focusRequester.requestFocus() }
            .onFailure {
                Tracker.log("home.field=search")
                Tracker.track(RuntimeException("Search field focus request failed", it))
            }
    }
}

@Composable
private fun SearchResultsList(
    results: List<Sound>,
    playbackProgress: PlaybackProgress?,
    soundDurations: Map<String, Int>,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onPinClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = Spacing.SM)) {
        items(results, key = { it.name }) { sound ->
            SoundItem(
                sound = sound,
                playbackProgress = if (sound.isPlaying) playbackProgress else null,
                durationMs = soundDurations[sound.name],
                onPlayClick = { onPlayClick(sound) },
                onSeek = onSeek,
                onShareClick = { onShareClick(sound) },
                onDelete = { onDelete(sound) },
                onPinClick = { onPinClick(sound) },
            )
        }
    }
}
