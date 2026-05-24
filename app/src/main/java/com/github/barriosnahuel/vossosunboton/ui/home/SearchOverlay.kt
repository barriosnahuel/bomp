/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.predictiveBackTransition
import com.github.barriosnahuel.vossosunboton.ui.theme.MUTED_TEXT_ALPHA
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
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onPinClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
    showVaultUnlockCta: Boolean = false,
    onUnlockVault: () -> Unit = {},
    // Per-audio reverse index of collection IDs, resolved against [allCollections] so each result
    // shows where it lives (collection name, the Vault's localized name, or "Explore" for bundled
    // audios). Default empty keeps the overlay renderable from previews/tests that don't wire it.
    collectionsByAudio: Map<String, List<String>> = emptyMap(),
    allCollections: List<Collection> = emptyList(),
    // While the Vault session is locked, private-collection tags are suppressed so a cross-tagged
    // result never leaks a private collection's name without authentication. Default false = safe
    // (hide private) for callers that don't wire it.
    vaultOpen: Boolean = false,
) {
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
                .predictiveBackTransition(onBack = onClose)
                .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
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
                                painter = painterResource(R.drawable.app_ic_arrow_back),
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = HINT_TEXT_ALPHA),
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
                                    pausedProgress = pausedProgress,
                                    soundDurations = soundDurations,
                                    collectionsByAudio = collectionsByAudio,
                                    allCollections = allCollections,
                                    vaultOpen = vaultOpen,
                                    onPlayClick = onPlayClick,
                                    onSeek = onSeek,
                                    onShareClick = onShareClick,
                                    onPinClick = onPinClick,
                                    onDelete = onDelete,
                                )
                        }
                    }
                    if (showVaultUnlockCta) {
                        VaultUnlockCta(
                            onUnlock = onUnlockVault,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Footer CTA shown at the bottom of the SearchOverlay when:
 *  - the user has at least one private collection (so the offer is relevant), AND
 *  - the Vault is locked in this process session.
 *
 * Tapping it asks for biometric/credential confirmation; on success, [VaultSessionState] flips and
 * the parent ViewModel re-emits `searchResults` with the previously-hidden private matches merged
 * in (no separate gated section — Opción D). Visibility is independent of the current query so it
 * never acts as an oracle for "is there a match in the Vault for X?".
 */
@Composable
private fun VaultUnlockCta(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bottom action bar: a `surface`-colored Surface + top divider frames the area as a distinct
    // "zone of action" over the (possibly scrolling) results, so the affordance comes from the
    // container, not the button. The button itself is the same TextButton-with-icon typology as
    // ManageCollectionsScreen's "+ New collection" (color `primary` = AcidDark/Acid400, AA in both
    // modes). Avoids opening an "outlined" or "tonal" button tier — see ADR 0010.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.LG, vertical = Spacing.SM),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = onUnlock) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.XS))
                    Text(stringResource(R.string.app_search_vault_cta))
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED_TEXT_ALPHA),
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
                        painter = painterResource(R.drawable.app_ic_close),
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
                focusedPlaceholderColor = contentColor.copy(alpha = MUTED_TEXT_ALPHA),
                unfocusedPlaceholderColor = contentColor.copy(alpha = MUTED_TEXT_ALPHA),
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
                Tracker.log("search.field=query")
                Tracker.track(RuntimeException("Search field focus request failed", it))
            }
    }
}

@Composable
private fun SearchResultsList(
    results: List<Sound>,
    playbackProgress: PlaybackProgress?,
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
    collectionsByAudio: Map<String, List<String>>,
    allCollections: List<Collection>,
    vaultOpen: Boolean,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onPinClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
) {
    val collectionsById = remember(allCollections) { allCollections.associateBy { it.id } }
    val systemCollectionLabel = stringResource(R.string.app_vault_baul_name)
    val exploreLabel = stringResource(R.string.app_search_origin_explore)
    LazyColumn(contentPadding = PaddingValues(vertical = Spacing.SM)) {
        items(results, key = { it.id }) { sound ->
            SoundItem(
                sound = sound,
                playbackProgress = if (sound.isPlaying) playbackProgress else pausedProgress[sound.id],
                durationMs = soundDurations[sound.id],
                onPlayClick = { onPlayClick(sound) },
                onSeek = onSeek,
                onShareClick = { onShareClick(sound) },
                onDelete = { onDelete(sound) },
                onPinClick = { onPinClick(sound) },
                collectionLabels =
                    searchResultCollectionLabels(
                        sound = sound,
                        collectionsByAudio = collectionsByAudio,
                        collectionsById = collectionsById,
                        systemCollectionLabel = systemCollectionLabel,
                        exploreLabel = exploreLabel,
                        vaultOpen = vaultOpen,
                    ),
            )
        }
    }
}

/**
 * Origin tags for a search result. A bundled audio belongs to the read-only Explore catalog, so it
 * carries the single [exploreLabel]; any other audio defers to the shared [collectionOriginLabels]
 * — same lock-aware rule used by My Sounds and the Vault list, so a private collection's name never
 * leaks on a locked search surface.
 */
internal fun searchResultCollectionLabels(
    sound: Sound,
    collectionsByAudio: Map<String, List<String>>,
    collectionsById: Map<String, Collection>,
    systemCollectionLabel: String,
    exploreLabel: String,
    vaultOpen: Boolean,
): List<String> =
    if (sound.isBundled()) {
        listOf(exploreLabel)
    } else {
        collectionOriginLabels(sound.id, collectionsByAudio, collectionsById, systemCollectionLabel, vaultOpen)
    }

// Dim level of the full-screen scrim behind the search surface (over Color.Black). A named alpha,
// not a magic literal — see ui/theme/Alpha.kt for the shared ones and the rationale.
private const val SCRIM_ALPHA = 0.6f

// Faintest instructional / empty-state hint text on the search surface.
private const val HINT_TEXT_ALPHA = 0.4f
