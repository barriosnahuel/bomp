package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(viewModel: SoundsViewModel) {
    val sounds by viewModel.sounds.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchPending by viewModel.isSearchPending.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val tabBackStack = remember { mutableStateListOf<AppTab>() }

    BackHandler(enabled = tabBackStack.isNotEmpty()) {
        viewModel.selectTab(tabBackStack.removeAt(tabBackStack.lastIndex))
    }

    SnackbarEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        topBar = { AppTopBar() },
        bottomBar = {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showSearch() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.app_search),
                )
            }
        },
    ) { innerPadding ->
        SoundsList(
            sounds = sounds,
            playbackProgress = playbackProgress,
            listState = listState,
            modifier = Modifier.padding(innerPadding),
            onPlayClick = { sound -> viewModel.playOrStop(sound) },
            onSeek = { positionMs -> viewModel.seekTo(positionMs) },
            onShareClick = { sound -> ShareFeature.instance.share(context, sound) },
            onDelete = { sound -> viewModel.deleteSound(sound) },
            onPinClick = { sound -> viewModel.togglePin(sound) },
            onEdit = { sound ->
                context.startActivity(LandingActivity.editIntent(context, sound))
            },
        )
    }

    if (isSearchVisible) {
        SearchOverlay(
            query = searchQuery,
            results = searchResults,
            isSearchPending = isSearchPending,
            playbackProgress = playbackProgress,
            onQueryChange = viewModel::onSearchQueryChange,
            onClose = viewModel::hideSearch,
            onPlayClick = viewModel::playOrStop,
            onSeek = viewModel::seekTo,
            onShareClick = { sound -> ShareFeature.instance.share(context, sound) },
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
    val buttonDeletedMessage = stringResource(R.string.app_feedback_button_deleted)
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
        if (deletedEvent == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = buttonDeletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.restoreSound()
            SnackbarResult.Dismissed -> viewModel.confirmDelete(context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar() {
    // Light mode: primary (#5B21B6 deep violet) — dark bar, white title.
    // Dark mode:  primaryContainer (#4A0A96 deep violet) — dark bar, lavender title.
    // Both are dark bars, so the status bar keeps light (white) icons in both modes.
    val isDark = isSystemInDarkTheme()
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                titleContentColor = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
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
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    NavigationBar(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        NavigationBarItem(
            colors = itemColors,
            selected = selectedTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.app_navigation_menu_item_home)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_home)) },
        )
        NavigationBarItem(
            colors = itemColors,
            selected = selectedTab == AppTab.EXPLORE,
            onClick = { onTabSelected(AppTab.EXPLORE) },
            icon = { Icon(AppIcons.ViewComfyAlt, contentDescription = stringResource(R.string.app_navigation_menu_item_explore)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_explore)) },
        )
    }
}

private const val DELETE_ANIMATION_DURATION_MS = 300

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    playbackProgress: PlaybackProgress?,
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
        ) {
            itemsIndexed(sounds, key = { _, sound -> sound.name }) { _, sound ->
                val isDeleting = sound.name in dismissingItems
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
                    SoundItem(
                        sound = sound,
                        playbackProgress = if (sound.isPlaying) playbackProgress else null,
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
                    )
                }
            }
        }
    }
}
