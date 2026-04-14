package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.model.Sound
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(viewModel: SoundsViewModel) {
    val sounds by viewModel.sounds.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val deletedEvent by viewModel.deletedSoundEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(enabled = selectedTab != AppTab.EXPLORE) {
        viewModel.selectTab(AppTab.EXPLORE)
    }

    LaunchedEffect(deletedEvent) {
        if (deletedEvent == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.app_feedback_button_deleted),
                actionLabel = context.getString(R.string.app_undo),
                duration = SnackbarDuration.Long,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.restoreSound()
            SnackbarResult.Dismissed -> viewModel.confirmDelete(context)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, colors = TopAppBarDefaults.topAppBarColors()) },
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == selectedTab) {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    } else {
                        viewModel.selectTab(tab)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        SoundsList(
            sounds = sounds,
            listState = listState,
            modifier = Modifier.padding(innerPadding),
            onPlayClick = { sound -> viewModel.playOrStop(sound) },
            onShareClick = { sound -> ShareFeature.instance.share(context, sound) },
            onDelete = { sound -> viewModel.deleteSound(sound) },
        )
    }
}

@Composable
private fun AppBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.app_navigation_menu_item_home)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_home)) },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.FAVORITES,
            onClick = { onTabSelected(AppTab.FAVORITES) },
            icon = { Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.app_navigation_menu_item_favourites)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_favourites)) },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.EXPLORE,
            onClick = { onTabSelected(AppTab.EXPLORE) },
            icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.app_navigation_menu_item_search)) },
            label = { Text(stringResource(R.string.app_navigation_menu_item_search)) },
        )
    }
}

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onPlayClick: (Sound) -> Unit,
    onShareClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState) {
            itemsIndexed(sounds, key = { _, sound -> sound.name }) { _, sound ->
                SoundItem(
                    sound = sound,
                    onPlayClick = { onPlayClick(sound) },
                    onShareClick = { onShareClick(sound) },
                    onDelete = { onDelete(sound) },
                )
            }
        }
    }
}
