/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.welcome.isWelcomeStickerName
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.about.AboutScreen
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(viewModel: SoundsViewModel) {
    val sounds by viewModel.sounds.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val hasBundledSounds by viewModel.hasBundledSounds.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val soundDurations by viewModel.soundDurations.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchPending by viewModel.isSearchPending.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val tabBackStack = remember { mutableStateListOf<AppTab>() }
    var isAboutVisible by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab, isAboutVisible, isSearchVisible) {
        val name =
            when {
                isSearchVisible -> CanonicalScreenName.SEARCH_SOUND
                isAboutVisible -> CanonicalScreenName.ABOUT
                selectedTab == AppTab.MY_SOUNDS -> CanonicalScreenName.MY_SOUNDS
                selectedTab == AppTab.EXPLORE_SOUNDS -> CanonicalScreenName.EXPLORE_SOUNDS
                else -> null
            }
        name?.let {
            AnalyticsTrackerProvider.get(context.applicationContext).logScreen(it)
        }
    }

    if (isAboutVisible) {
        AboutScreen(onBack = { isAboutVisible = false })
        return
    }

    BackHandler(enabled = tabBackStack.isNotEmpty()) {
        viewModel.selectTab(tabBackStack.removeAt(tabBackStack.lastIndex))
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToTopEvent.collect {
            listState.animateScrollToItem(0)
        }
    }

    SnackbarEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        topBar = { AppTopBar(onAboutClick = { isAboutVisible = true }) },
        bottomBar = {
            if (hasBundledSounds) {
                AppBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (tab == selectedTab) {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        } else {
                            tabBackStack.add(selectedTab)
                            viewModel.selectTab(tab)
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showSearch() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.app_search),
                )
            }
        },
    ) { innerPadding ->
        if (sounds.isEmpty() && selectedTab == AppTab.MY_SOUNDS) {
            MySoundsEmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            SoundsList(
                sounds = sounds,
                playbackProgress = playbackProgress,
                soundDurations = soundDurations,
                listState = listState,
                modifier = Modifier.padding(innerPadding),
                onPlayClick = { sound -> viewModel.playOrStop(sound) },
                onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                onShareClick = { sound ->
                    val surface =
                        if (selectedTab == AppTab.MY_SOUNDS) {
                            CanonicalScreenName.MY_SOUNDS
                        } else {
                            CanonicalScreenName.EXPLORE_SOUNDS
                        }
                    coroutineScope.launch { ShareFeature.instance.share(context, sound, surface) }
                },
                onDelete = { sound -> viewModel.deleteSound(sound) },
                onPinClick = { sound -> viewModel.togglePin(sound) },
                onEdit = { sound ->
                    context.startActivity(LandingActivity.editIntent(context, sound))
                },
            )
        }
    }

    if (isSearchVisible) {
        SearchOverlay(
            query = searchQuery,
            results = searchResults,
            isSearchPending = isSearchPending,
            playbackProgress = playbackProgress,
            soundDurations = soundDurations,
            onQueryChange = viewModel::onSearchQueryChange,
            onClose = viewModel::hideSearch,
            onPlayClick = viewModel::playOrStop,
            onSeek = viewModel::seekTo,
            onShareClick = { sound ->
                coroutineScope.launch {
                    ShareFeature.instance.share(context, sound, CanonicalScreenName.SEARCH_SOUND)
                }
            },
            onPinClick = viewModel::togglePin,
            onDelete = { sound ->
                viewModel.hideSearch()
                viewModel.deleteSound(sound)
            },
        )
    }
}

@Composable
private fun SnackbarEffects(
    viewModel: SoundsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val deletedEvent by viewModel.deletedSoundEvent.collectAsState()
    val context = LocalContext.current
    val audioDeletedMessage = stringResource(R.string.app_feedback_audio_deleted)
    val welcomeDismissedMessage = stringResource(R.string.app_welcome_sticker_feedback_dismissed)
    val undoLabel = stringResource(R.string.app_undo)
    val playbackErrorMessage = stringResource(R.string.app_error_playback_failed)
    val buttonSavedTemplate = stringResource(R.string.app_feedback_button_saved)
    val buttonRenamedTemplate = stringResource(R.string.app_feedback_button_renamed)

    LaunchedEffect(Unit) {
        viewModel.playbackErrorEvent.collect {
            snackbarHostState.showSnackbar(
                message = playbackErrorMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.buttonSavedEvent.collect { name ->
            snackbarHostState.showSnackbar(
                message = String.format(buttonSavedTemplate, name),
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.buttonRenamedEvent.collect { name ->
            snackbarHostState.showSnackbar(
                message = String.format(buttonRenamedTemplate, name),
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(deletedEvent) {
        val event = deletedEvent ?: return@LaunchedEffect
        val message =
            if (isWelcomeStickerName(event.sound.name, context)) {
                welcomeDismissedMessage
            } else {
                audioDeletedMessage
            }
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.restoreSound()
            SnackbarResult.Dismissed -> viewModel.confirmDelete()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(onAboutClick: () -> Unit) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.app_overflow_menu),
                )
            }
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_about)) },
                    onClick = {
                        isMenuExpanded = false
                        onAboutClick()
                    },
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                titleContentColor = MaterialTheme.colorScheme.onSecondary,
                actionIconContentColor = MaterialTheme.colorScheme.onSecondary,
            ),
    )
}

@Composable
private fun AppBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    val itemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        NavigationBarItem(
            colors = itemColors,
            selected = selectedTab == AppTab.MY_SOUNDS,
            onClick = { onTabSelected(AppTab.MY_SOUNDS) },
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.app_navigation_menu_item_my_sounds)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_my_sounds)) },
        )
        NavigationBarItem(
            colors = itemColors,
            selected = selectedTab == AppTab.EXPLORE_SOUNDS,
            onClick = { onTabSelected(AppTab.EXPLORE_SOUNDS) },
            icon = { Icon(AppIcons.ViewComfyAlt, contentDescription = stringResource(R.string.app_navigation_menu_item_explore_sounds)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_explore_sounds)) },
        )
    }
}

private const val DELETE_ANIMATION_DURATION_MS = 300
private val WELCOME_BORDER_WIDTH = 1.5.dp

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    playbackProgress: PlaybackProgress?,
    soundDurations: Map<String, Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
    onPinClick: (Sound) -> Unit,
    onEdit: (Sound) -> Unit,
) {
    val dismissingItems = remember { mutableStateSetOf<String>() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val remainingFormat = stringResource(R.string.app_welcome_sticker_remaining_format)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = Spacing.MD, bottom = Spacing.SM),
        ) {
            itemsIndexed(sounds, key = { _, sound -> sound.name }) { _, sound ->
                val isDeleting = sound.name in dismissingItems
                val isWelcome = isWelcomeStickerName(sound.name, context)
                AnimatedVisibility(
                    visible = !isDeleting,
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = spring(),
                        ),
                    enter = EnterTransition.None,
                    exit =
                        scaleOut(
                            targetScale = 0f,
                            animationSpec = tween(durationMillis = DELETE_ANIMATION_DURATION_MS),
                        ) + fadeOut(animationSpec = tween(durationMillis = DELETE_ANIMATION_DURATION_MS)),
                ) {
                    val resolvedDurationMs = soundDurations[sound.name]
                    val welcomeBorder =
                        if (isWelcome) {
                            BorderStroke(WELCOME_BORDER_WIDTH, MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            null
                        }
                    val welcomeTrailingLabel =
                        if (isWelcome) {
                            val remainingMs =
                                when {
                                    sound.isPlaying && playbackProgress != null ->
                                        (playbackProgress.durationMs - playbackProgress.positionMs).coerceAtLeast(0)
                                    resolvedDurationMs != null -> resolvedDurationMs
                                    else -> null
                                }
                            remainingMs?.let { String.format(remainingFormat, formatDuration(it)) }
                        } else {
                            null
                        }
                    SoundItem(
                        sound = sound,
                        playbackProgress = if (sound.isPlaying) playbackProgress else null,
                        durationMs = resolvedDurationMs,
                        onPlayClick = { onPlayClick(sound) },
                        onSeek = onSeek,
                        onShareClick = { onShareClick(sound) },
                        onDelete = {
                            dismissingItems.add(sound.name)
                            coroutineScope.launch {
                                delay(DELETE_ANIMATION_DURATION_MS.toLong())
                                onDelete(sound)
                                dismissingItems.remove(sound.name)
                            }
                        },
                        onPinClick = { onPinClick(sound) },
                        onEditClick =
                            if (!sound.isBundled()) {
                                { onEdit(sound) }
                            } else {
                                null
                            },
                        originLabel = if (isWelcome) stringResource(R.string.app_welcome_sticker_origin) else null,
                        isWelcomeVariant = isWelcome,
                        borderOverride = welcomeBorder,
                        trailingLabel = welcomeTrailingLabel,
                    )
                }
            }
        }
    }
}
