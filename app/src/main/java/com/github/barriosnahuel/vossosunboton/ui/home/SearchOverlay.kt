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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound

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
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onFavoriteClick: (Sound) -> Unit,
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
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                SearchEmptyStateContent(
                                    icon = Icons.Default.Search,
                                    headline = stringResource(R.string.app_search_initial_headline),
                                    subtext = stringResource(R.string.app_search_initial_subtext),
                                )

                            is SearchDisplayState.ZeroResults ->
                                SearchEmptyStateContent(
                                    icon = Icons.Default.MusicNote,
                                    headline = stringResource(R.string.app_search_empty_headline),
                                    subtext = stringResource(R.string.app_search_empty_subtext),
                                )

                            is SearchDisplayState.Results ->
                                SearchResultsList(
                                    results = state.sounds,
                                    playbackProgress = playbackProgress,
                                    onPlayClick = onPlayClick,
                                    onSeek = onSeek,
                                    onShareClick = onShareClick,
                                    onFavoriteClick = onFavoriteClick,
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
private fun SearchEmptyStateContent(
    icon: ImageVector,
    headline: String,
    subtext: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
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

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.app_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.app_search_close),
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
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SearchResultsList(
    results: List<Sound>,
    playbackProgress: PlaybackProgress?,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onFavoriteClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
) {
    val homeLabel = stringResource(R.string.app_search_origin_home)
    val exploreLabel = stringResource(R.string.app_search_origin_explore)
    val homeAndFavoritesLabel = stringResource(R.string.app_search_origin_home_and_favorites)

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(results, key = { it.name }) { sound ->
            val originLabel =
                when {
                    sound.isBundled() -> exploreLabel
                    sound.isFavorite -> homeAndFavoritesLabel
                    else -> homeLabel
                }
            SoundItem(
                sound = sound,
                playbackProgress = if (sound.isPlaying) playbackProgress else null,
                onPlayClick = { onPlayClick(sound) },
                onSeek = onSeek,
                onShareClick = { onShareClick(sound) },
                onDelete = { onDelete(sound) },
                onFavoriteClick = { onFavoriteClick(sound) },
                originLabel = originLabel,
            )
        }
    }
}
