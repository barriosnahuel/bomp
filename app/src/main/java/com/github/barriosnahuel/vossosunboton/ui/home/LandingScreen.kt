/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:Suppress("TooManyFunctions")

package com.github.barriosnahuel.vossosunboton.ui.home
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsSource
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity
import com.github.barriosnahuel.vossosunboton.feature.addbutton.findFragmentActivity
import com.github.barriosnahuel.vossosunboton.feature.share.ShareAppIntent
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.vault.requestUnlock
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.feature.welcome.isWelcomeSticker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.playstore.PlayStoreReferrer
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
    val pausedProgress by viewModel.pausedProgress.collectAsState()
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
    // Saveable so a rotation while About / Manage are open does not silently bounce the user back
    // to the sound list — those screens swap out the Scaffold body via a `when` below, and losing
    // the flag = losing the screen.
    var isAboutVisible by rememberSaveable { mutableStateOf(false) }
    var manageRequest by rememberSaveable(stateSaver = ManageRequest.Saver) {
        mutableStateOf<ManageRequest?>(null)
    }
    // Id of the Vault audio whose immersive listen-mode player is open, or null. Saveable so the
    // overlay survives an Activity recreate (rotation, theme, system kill); the host re-resolves
    // the Sound from `library` on the way back.
    var immersiveListenSoundId by rememberSaveable { mutableStateOf<String?>(null) }

    // Import Hub open state. Saveable so an Activity recreate (rotation, theme, system kill) while
    // the sheet is open does not silently rewind the user back to the list (§ Stateful Composables).
    var isHubVisible by rememberSaveable { mutableStateOf(false) }
    // Onboarding tour: null = closed, 0..2 = open at that step. One nullable Int captures both
    // "is it open" and the step, so an Activity recreate mid-tour cannot rewind the user to step 0
    // (§ Stateful Composables). On-demand only — no first-run auto-open, no "seen" flag.
    var onboardingStep by rememberSaveable { mutableStateOf<Int?>(null) }
    // System file picker for the Hub's "import audio" path. OpenDocument(arrayOf("audio/*")) opens the
    // full SAF browser filtered to audio (non-audio files are not selectable) — better at surfacing
    // on-device audio across OEMs than GetContent's "Recent" view. We copy the audio at save time, so we
    // never take persistable permission; cancel returns null → no-op, no message. The picked content URI
    // reaches AddButtonActivity through the same inbound validator as the share sheet — createIntent
    // forwards the read grant to that Activity. (App-private media like WhatsApp voice notes is not
    // browsable by any SAF picker on Android 11+; that content arrives via the share sheet instead.)
    val importPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { context.startActivity(AddButtonActivity.createIntent(context, it)) }
        }

    // Key on the open/closed boolean, not the raw step: advancing or going back within the tour must
    // not re-emit screen_view=onboarding (one open = one screen view, like the other overlays).
    LaunchedEffect(selectedTab, isAboutVisible, manageRequest, isSearchVisible, onboardingStep != null) {
        val name =
            when {
                onboardingStep != null -> CanonicalScreenName.ONBOARDING
                isSearchVisible -> CanonicalScreenName.SEARCH_SOUND
                isAboutVisible -> CanonicalScreenName.ABOUT
                manageRequest != null -> CanonicalScreenName.MANAGE_COLLECTIONS
                selectedTab == AppTab.MY_SOUNDS -> CanonicalScreenName.MY_SOUNDS
                selectedTab == AppTab.VAULT -> CanonicalScreenName.VAULT
                selectedTab == AppTab.EXPLORE_SOUNDS -> CanonicalScreenName.EXPLORE_SOUNDS
                else -> null
            }
        name?.let {
            AnalyticsTrackerProvider.get(context.applicationContext).logScreen(it)
        }
    }

    BackHandler(
        enabled =
            !isAboutVisible &&
                manageRequest == null &&
                immersiveListenSoundId == null &&
                onboardingStep == null &&
                tabBackStack.isNotEmpty(),
    ) {
        viewModel.selectTab(tabBackStack.removeAt(tabBackStack.lastIndex))
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToTopEvent.collect {
            listState.animateScrollToItem(0)
        }
    }

    SnackbarEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    val collections by viewModel.collections.collectAsState()
    val activeFilter by viewModel.activeMySoundsFilter.collectAsState()
    val publicCollections = remember(collections) { collections.filter { it.isPublic } }
    val collectionsByAudio by viewModel.audioCollectionsIndex.collectAsState()
    val privateCollections = remember(collections) { collections.filter { it.isPrivate } }

    // My Sounds is the always-composed base layer; About / Manage Collections render as opaque
    // full-screen overlays stacked on top of it (the same layering SearchOverlay uses below).
    // Keeping the list composed behind is what lets predictiveBackTransition reveal the live
    // My Sounds as the user swipes those screens away — an exclusive `when` (the prior shape)
    // exposed the bare window background instead. The overlays' Scaffold Surface is opaque: it
    // blocks touch propagation and covers the list at rest, so there is no visual or input change
    // until a back gesture is in progress. When the nav3 migration (backlog 08) lands, About/Manage
    // become real destinations and this manual layering is removed.
    //
    // While a sub-screen is open the occluded list is cleared from the semantics tree, so TalkBack
    // and UI tests neither reach nor double-match nodes hidden behind the opaque overlay (e.g. a
    // collection name shown both in the chip row and in the Manage list). Drawing is untouched, so
    // the gesture still reveals the live list visually.
    val subScreenOpen = isAboutVisible || manageRequest != null || immersiveListenSoundId != null || onboardingStep != null
    ScaffoldedLanding(
        modifier = if (subScreenOpen) Modifier.clearAndSetSemantics {} else Modifier,
        viewModel = viewModel,
        sounds = sounds,
        selectedTab = selectedTab,
        hasBundledSounds = hasBundledSounds,
        playbackProgress = playbackProgress,
        pausedProgress = pausedProgress,
        soundDurations = soundDurations,
        snackbarHostState = snackbarHostState,
        listState = listState,
        coroutineScope = coroutineScope,
        context = context,
        tabBackStack = tabBackStack,
        collections = collections,
        activeFilter = activeFilter,
        publicCollections = publicCollections,
        collectionsByAudio = collectionsByAudio,
        privateCollections = privateCollections,
        onAboutClick = { isAboutVisible = true },
        onManageCollectionsClick = { manageRequest = ManageRequest.Generic },
        onActiveFilterEditClick = { collectionId ->
            manageRequest = ManageRequest.Focused(collectionId)
        },
        onImmersivePlay = { sound ->
            // Vault play opens immersive listen mode and starts playback (unless already playing).
            immersiveListenSoundId = sound.id
            if (!sound.isPlaying) viewModel.playOrStop(sound)
        },
        onCreateClick = { isHubVisible = true },
        onShowOnboarding = { onboardingStep = 0 },
    )

    // Exactly one sub-screen overlays the list at a time (About wins over Manage, preserving the
    // prior priority); neither branch removes the base layer.
    when {
        isAboutVisible -> AboutScreen(onBack = { isAboutVisible = false })
        manageRequest != null ->
            com.github.barriosnahuel.vossosunboton.feature.collections.ManageCollectionsScreen(
                viewModel = viewModel,
                focusedCollectionId = (manageRequest as? ManageRequest.Focused)?.collectionId,
                onBack = { manageRequest = null },
            )
    }

    // Immersive listen mode for a Vault audio — layers above everything (it covers the top bar, so
    // About/Manage are unreachable while it is open). Back pauses playback (position retained) so no
    // audio keeps playing headless behind the Vault list, then closes the overlay.
    immersiveListenSoundId?.let { listeningId ->
        com.github.barriosnahuel.vossosunboton.feature.vault.ImmersiveListenHost(
            viewModel = viewModel,
            soundId = listeningId,
            onBack = {
                viewModel.playingSound.value
                    ?.takeIf { it.id == listeningId }
                    ?.let { viewModel.playOrStop(it) }
                immersiveListenSoundId = null
            },
        )
    }

    com.github.barriosnahuel.vossosunboton.feature.collections
        .CollectionSheetHost(viewModel = viewModel)

    com.github.barriosnahuel.vossosunboton.feature.collections
        .AssignCollectionSheet(viewModel = viewModel)

    com.github.barriosnahuel.vossosunboton.feature.collections
        .CollectionDeleteDialog(viewModel = viewModel)

    if (isHubVisible) {
        ImportHubSheet(
            // Guard on isHubVisible so a double-tap on the import row (both taps land before the
            // sheet's hide animation recomposes it away) launches the picker once, not twice.
            onImport = {
                if (isHubVisible) {
                    isHubVisible = false
                    importPicker.launch(arrayOf("audio/*"))
                }
            },
            onHowItWorks = {
                isHubVisible = false
                onboardingStep = 0
            },
            onDismiss = { isHubVisible = false },
        )
    }

    // On-demand onboarding tour overlaying everything (opened from the Hub or the empty state). The
    // final "start" CTA lands on the Hub so the user can import right away ("Empezá cae al Hub").
    onboardingStep?.let { current ->
        com.github.barriosnahuel.vossosunboton.feature.onboarding.OnboardingTour(
            step = current,
            onAdvance = {
                onboardingStep =
                    ((onboardingStep ?: 0) + 1)
                        .coerceAtMost(com.github.barriosnahuel.vossosunboton.feature.onboarding.ONBOARDING_STEP_COUNT - 1)
            },
            onStepBack = { onboardingStep = ((onboardingStep ?: 0) - 1).coerceAtLeast(0) },
            onSkip = { onboardingStep = null },
            onFinish = {
                onboardingStep = null
                isHubVisible = true
            },
        )
    }

    if (isSearchVisible) {
        SearchOverlayHost(
            viewModel = viewModel,
            query = searchQuery,
            results = searchResults,
            isSearchPending = isSearchPending,
            playbackProgress = playbackProgress,
            pausedProgress = pausedProgress,
            soundDurations = soundDurations,
        )
    }
}

/**
 * Owns the SearchOverlay + the biometric plumbing for the "Search your Vault too" CTA. Lives in
 * `LandingScreen.kt` (next to the overlay's only host) so the biometric gate plumbing — which only
 * exists in `LandingActivity` (and `AddButtonActivity`) thanks to FragmentActivity migration — has
 * a single home.
 */
@Composable
private fun SearchOverlayHost(
    viewModel: SoundsViewModel,
    query: String,
    results: List<com.github.barriosnahuel.vossosunboton.model.Sound>,
    isSearchPending: Boolean,
    playbackProgress: PlaybackProgress?,
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val gate = remember(activity) { activity?.let { BiometricGate(it) } }
    val status = remember(gate) { gate?.status() ?: BiometricGateStatus.UNAVAILABLE }
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    val vaultOpen by VaultSessionState.flow.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val collectionsByAudio by viewModel.audioCollectionsIndex.collectAsState()
    val hasSearchableVaultContent =
        remember(collections) {
            collections.any { it.isPrivate && it.audioIds.isNotEmpty() }
        }
    val showVaultUnlockCta = hasSearchableVaultContent && !vaultOpen
    // Impression: fire once per process the first time the CTA is actually shown. markFiredOnce
    // gates it so re-opening search or recomposition doesn't re-emit. Keyed on the boolean so it
    // re-checks when the CTA flips visible.
    LaunchedEffect(showVaultUnlockCta) {
        if (showVaultUnlockCta && tracker.markFiredOnce("vault_search_cta_shown")) {
            tracker.log(AnalyticsEvent.VaultSearchUnlockCtaShown)
        }
    }
    SearchOverlay(
        query = query,
        results = results,
        isSearchPending = isSearchPending,
        playbackProgress = playbackProgress,
        pausedProgress = pausedProgress,
        soundDurations = soundDurations,
        onQueryChange = viewModel::onSearchQueryChange,
        onClose = viewModel::hideSearch,
        onPlayClick = viewModel::playOrStop,
        onSeek = viewModel::seekTo,
        onShareClick = { sound -> viewModel.share(sound) },
        onPinClick = viewModel::togglePin,
        onDelete = { sound ->
            viewModel.hideSearch()
            viewModel.deleteSound(sound)
        },
        showVaultUnlockCta = showVaultUnlockCta,
        onUnlockVault = {
            requestUnlock(
                context = context,
                gate = gate,
                status = status,
                tracker = tracker,
                source = AnalyticsSource.SEARCH,
            )
        },
        collectionsByAudio = collectionsByAudio,
        allCollections = collections,
        vaultOpen = vaultOpen,
    )
}

/**
 * Extracted Scaffold body to keep [LandingScreen] readable now that the top-level swap between
 * Landing / About / Manage Collections lives in a `when`. All the previous Scaffold logic moves
 * here unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldedLanding(
    modifier: Modifier = Modifier,
    viewModel: SoundsViewModel,
    sounds: List<Sound>,
    selectedTab: AppTab,
    hasBundledSounds: Boolean,
    playbackProgress: PlaybackProgress?,
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    tabBackStack: androidx.compose.runtime.snapshots.SnapshotStateList<AppTab>,
    collections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    activeFilter: String?,
    publicCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    collectionsByAudio: Map<String, List<String>>,
    privateCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    onAboutClick: () -> Unit,
    onManageCollectionsClick: () -> Unit,
    onActiveFilterEditClick: (String) -> Unit,
    onImmersivePlay: (Sound) -> Unit,
    onCreateClick: () -> Unit,
    onShowOnboarding: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                onSearchClick = { viewModel.showSearch() },
                onAboutClick = onAboutClick,
                onManageCollectionsClick = onManageCollectionsClick,
            )
        },
        // FAB only on My Bomps (design "regla por tab"): Explore is a consumption surface and Vault's
        // job is to listen — both stay FAB-less. The single + opens the import Hub. Suppressed in the
        // welcome-empty state, where MySoundsEmptyState carries the one "Import" CTA instead — a single
        // imperative, not two routes to the same Hub.
        floatingActionButton = {
            // Mirror MySoundsBody's `showWelcomeEmptyState` so the FAB and the inline CTA are never
            // both shown: the list is welcome-empty when there are no audios and no *resolvable*
            // active filter (an unresolved/stale filter id collapses to the welcome-empty state, not
            // the filter one). The MY_SOUNDS guard below scopes it to that tab.
            val showsWelcomeEmpty =
                sounds.isEmpty() &&
                    !(activeFilter != null && publicCollections.any { it.id == activeFilter })
            if (selectedTab == AppTab.MY_SOUNDS && !showsWelcomeEmpty) {
                FloatingActionButton(
                    onClick = onCreateClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_add),
                        contentDescription = stringResource(R.string.app_hub_fab_description),
                    )
                }
            }
        },
        bottomBar = {
            // Vault is always visible (spec § 2.1) — no `hasBundledSounds` gating. The Explore
            // tab keeps its conditional behaviour through `AppBottomBar.hasExplore`.
            AppBottomBar(
                selectedTab = selectedTab,
                hasExplore = hasBundledSounds,
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
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.VAULT ->
                com.github.barriosnahuel.vossosunboton.feature.vault.VaultScreen(
                    privateCollections = privateCollections,
                    viewModel = viewModel,
                    listState = listState,
                    onActiveFilterEditClick = onActiveFilterEditClick,
                    onImmersivePlay = onImmersivePlay,
                    modifier = Modifier.padding(innerPadding),
                )
            else -> {
                MySoundsBody(
                    selectedTab = selectedTab,
                    sounds = sounds,
                    playbackProgress = playbackProgress,
                    pausedProgress = pausedProgress,
                    soundDurations = soundDurations,
                    listState = listState,
                    publicCollections = publicCollections,
                    activeFilterId = activeFilter,
                    innerPadding = innerPadding,
                    collectionsByAudio = collectionsByAudio,
                    allCollections = collections,
                    onActiveFilterEditClick = onActiveFilterEditClick,
                    onImportClick = onCreateClick,
                    onShowOnboarding = onShowOnboarding,
                    viewModel = viewModel,
                    context = context,
                )
            }
        }
    }
}

// Indefinite + dismiss action so the snackbar stays until the user dismisses it. WCAG 2.2 AA
// ground: TalkBack reads ~10–12 chars/sec so es-AR copy of 100+ chars (e.g. copy_failed) would
// not finish reading before SnackbarDuration.Long (10 s) auto-dismissed. Letting the user
// close it on their own pace is the only way to guarantee they receive the full message.
private suspend fun showShareErrorSnackbar(
    snackbarHostState: SnackbarHostState,
    context: Context,
    errorRes: Int,
    actionLabel: String,
) {
    snackbarHostState.showSnackbar(
        message = context.getString(errorRes),
        actionLabel = actionLabel,
        duration = SnackbarDuration.Indefinite,
    )
}

@Composable
private fun SnackbarEffects(
    viewModel: SoundsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val deletedEvent by viewModel.deletedSoundEvent.collectAsState()
    val audioDeletedMessage = stringResource(R.string.app_feedback_audio_deleted)
    val welcomeDismissedMessage = stringResource(R.string.app_welcome_sticker_feedback_dismissed)
    val welcomeInfoMessage = stringResource(R.string.app_welcome_sticker_info)
    val undoLabel = stringResource(R.string.app_undo)
    val playbackErrorMessage = stringResource(R.string.app_error_playback_failed)
    val shareDismissLabel = stringResource(R.string.app_snackbar_action_dismiss)
    // ADR 0012 — dual-home coach (title + reassurance on one snackbar) and the "moved to Vault" undo.
    val dualHomeCoachMessage =
        stringResource(R.string.app_assign_dual_home_coach_title) +
            " " + stringResource(R.string.app_assign_dual_home_coach_sub)
    val dualHomeCoachAction = stringResource(R.string.app_assign_dual_home_coach_action)
    val movedToVaultMessage = stringResource(R.string.app_assign_moved_to_vault_message)
    val activityContext = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.playbackErrorEvent.collect {
            snackbarHostState.showSnackbar(
                message = playbackErrorMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        // Activity-side half of the share split: the VM produced an Intent on IO; here we wrap it in a
        // chooser and call startActivity from the UI thread. If startActivity throws, launchChooser
        // returns the same @StringRes contract used by shareErrorEvent — surface it the same way.
        viewModel.shareIntentEvent.collect { event ->
            val errorRes = ShareFeature.instance.launchChooser(activityContext, event.intent, event.surface)
            if (errorRes != null) {
                showShareErrorSnackbar(snackbarHostState, activityContext, errorRes, shareDismissLabel)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareErrorEvent.collect { errorRes ->
            showShareErrorSnackbar(snackbarHostState, activityContext, errorRes, shareDismissLabel)
        }
    }

    // Informative, one-shot snackbar shown the first time the welcome audio finishes playing — warm
    // "it's yours, delete it whenever" message. No action: the swipe-hint nudge teaches deletion;
    // this just reassures. Short (Material's duration for actionless info) — it's brief, non-critical
    // reassurance (the card stays whether read or not), so Long would linger.
    LaunchedEffect(Unit) {
        viewModel.welcomeInfoEvent.collect {
            snackbarHostState.showSnackbar(
                message = welcomeInfoMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // ADR 0012 — one-time coach: teaches that a Bomp can live in My Sounds AND the Vault. Action
    // just dismisses (no VM call); the VM already marked it seen before emitting.
    LaunchedEffect(Unit) {
        viewModel.dualHomeCoachEvent.collect {
            snackbarHostState.showSnackbar(
                message = dualHomeCoachMessage,
                actionLabel = dualHomeCoachAction,
                duration = SnackbarDuration.Long,
            )
        }
    }

    // ADR 0012 — "moved to Vault" feedback: the user turned visibility OFF while it stays in a
    // private collection. Undo re-enables visibility (the toggle already persisted false).
    LaunchedEffect(Unit) {
        viewModel.movedToVaultEvent.collect { event ->
            val result =
                snackbarHostState.showSnackbar(
                    message = movedToVaultMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.setAudioVisibleInMySounds(event.audioId, event.name, visible = true)
            }
        }
    }

    LaunchedEffect(deletedEvent) {
        val event = deletedEvent ?: return@LaunchedEffect
        val message =
            if (isWelcomeSticker(event.sound)) {
                welcomeDismissedMessage
            } else {
                audioDeletedMessage
            }
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = deletionSnackbarDuration(event.sound),
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.restoreSound()
            SnackbarResult.Dismissed -> viewModel.confirmDelete()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    onSearchClick: () -> Unit,
    onAboutClick: () -> Unit,
    onManageCollectionsClick: () -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    painter = painterResource(R.drawable.app_ic_search),
                    contentDescription = stringResource(R.string.app_search),
                )
            }
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.app_ic_more_vert),
                    contentDescription = stringResource(R.string.app_overflow_menu),
                )
            }
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_manage_collections_menu_item)) },
                    onClick = {
                        isMenuExpanded = false
                        onManageCollectionsClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_about)) },
                    onClick = {
                        isMenuExpanded = false
                        onAboutClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_share_app_menu_item)) },
                    onClick = {
                        isMenuExpanded = false
                        ShareAppIntent.launch(context, PlayStoreReferrer.CONTENT_MENU)
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
    hasExplore: Boolean,
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
            icon = {
                Icon(
                    painterResource(R.drawable.app_ic_home),
                    contentDescription = stringResource(R.string.app_navigation_menu_item_my_sounds),
                )
            },
            label = { Text(stringResource(R.string.app_navigation_menu_item_my_sounds)) },
        )
        NavigationBarItem(
            colors = itemColors,
            selected = selectedTab == AppTab.VAULT,
            onClick = { onTabSelected(AppTab.VAULT) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.app_ic_lock),
                    contentDescription = stringResource(R.string.app_navigation_menu_item_vault),
                )
            },
            label = { Text(stringResource(R.string.app_navigation_menu_item_vault)) },
        )
        // Explore stays conditional: with no bundled audios there is nothing to render and the
        // tab would land on a blank page. Spec § 2.1 keeps the existing rule for Explore.
        if (hasExplore) {
            NavigationBarItem(
                colors = itemColors,
                selected = selectedTab == AppTab.EXPLORE_SOUNDS,
                onClick = { onTabSelected(AppTab.EXPLORE_SOUNDS) },
                icon = {
                    Icon(
                        AppIcons.ViewComfyAlt,
                        contentDescription = stringResource(R.string.app_navigation_menu_item_explore_sounds),
                    )
                },
                label = { Text(stringResource(R.string.app_navigation_menu_item_explore_sounds)) },
            )
        }
    }
}

private const val DELETE_ANIMATION_DURATION_MS = 300
private val WELCOME_BORDER_WIDTH = 1.5.dp

// Reserve room below the last card on My Bomps so it scrolls clear of the + FAB (56dp FAB + margin).
// Other surfaces keep the default Spacing.SM.
private val FAB_LIST_BOTTOM_PADDING = 88.dp

@Composable
internal fun SoundsList(
    sounds: List<Sound>,
    playbackProgress: PlaybackProgress?,
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onPlayClick: (Sound) -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: (Sound) -> Unit,
    onDelete: (Sound) -> Unit,
    onPinClick: (Sound) -> Unit,
    onEdit: (Sound) -> Unit,
    onAddToCollection: ((Sound) -> Unit)? = null,
    // Per-audio reverse index of *collection IDs* (not names) this audio belongs to. The render
    // site resolves each id against [allCollections] so system collections substitute the locale-
    // aware string resource for their stored literal name.
    collectionsByAudio: Map<String, List<String>> = emptyMap(),
    allCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection> = emptyList(),
    // When true, a filter chip is currently narrowing the list — the inline collection labels
    // inside each card become redundant context noise (the user already sees the filter pill
    // and the ActiveFilterHeader). Hide them so the meta row reads "0:14   today" instead of
    // "0:14   Familia · today". On unfiltered lists ("All"), labels keep helping users see
    // taxonomy at a glance.
    filterIsActive: Boolean = false,
    // When false, the per-card share icon is hidden. Driven from the rendering context: the
    // Vault tab passes false because every audio it shows belongs to a `LISTEN_ONLY` profile.
    shareEnabled: Boolean = true,
    // Bottom padding for the underlying LazyColumn. Surfaces that overlay an FAB or persistent
    // CTA on top of the list (e.g. the Vault tab's ExtendedFloatingActionButton) bump this so the
    // last item can scroll above the overlay; everything else stays at the default Spacing.SM.
    bottomContentPadding: androidx.compose.ui.unit.Dp = Spacing.SM,
    // When true, the welcome card runs its one-time swipe-hint nudge (peek the delete background and
    // settle back) to teach the swipe gesture; [onWelcomeHintShown] fires once it has run. Only the
    // My Sounds list passes these — the Vault never renders the welcome.
    welcomeHintPending: Boolean = false,
    onWelcomeHintShown: () -> Unit = {},
) {
    val dismissingItems = remember { mutableStateSetOf<String>() }
    val coroutineScope = rememberCoroutineScope()
    // Locale-aware fallback for system collections (the seeded "Baúl") — resolved once at the
    // top of the composable since `stringResource` cannot be invoked from a non-Composable
    // `mapNotNull` lambda.
    val systemCollectionLabel = stringResource(R.string.app_vault_baul_name)
    val collectionsById = remember(allCollections) { allCollections.associateBy { it.id } }
    // Live Vault lock state, read directly here the same way SearchOverlayHost / VaultScreen do —
    // private-collection tags are suppressed while locked so a cross-tagged audio shown on My Sounds
    // never leaks a private collection's name. On the Vault tab this is always open (the tab only
    // renders unlocked), so private tags show there as expected.
    val vaultOpen by VaultSessionState.flow.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = Spacing.MD, bottom = bottomContentPadding),
        ) {
            itemsIndexed(sounds, key = { _, sound -> sound.id }) { _, sound ->
                val isDeleting = sound.id in dismissingItems
                val isWelcome = isWelcomeSticker(sound)
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
                    val resolvedDurationMs = soundDurations[sound.id]
                    // A sound shows progress when it is playing (live `playbackProgress`) OR when it
                    // is paused (`pausedProgress[id]`, retained so the bar doesn't snap to zero).
                    val effectiveProgress =
                        if (sound.isPlaying) playbackProgress else pausedProgress[sound.id]
                    val welcomeBorder =
                        if (isWelcome) {
                            BorderStroke(WELCOME_BORDER_WIDTH, MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            null
                        }
                    val labels =
                        if (isWelcome) {
                            emptyList()
                        } else {
                            collectionOriginLabels(sound.id, collectionsByAudio, collectionsById, systemCollectionLabel, vaultOpen)
                        }
                    SoundItem(
                        sound = sound,
                        playbackProgress = effectiveProgress,
                        durationMs = resolvedDurationMs,
                        onPlayClick = { onPlayClick(sound) },
                        onSeek = onSeek,
                        onShareClick = { onShareClick(sound) },
                        onDelete = {
                            dismissingItems.add(sound.id)
                            coroutineScope.launch {
                                delay(DELETE_ANIMATION_DURATION_MS.toLong())
                                onDelete(sound)
                                dismissingItems.remove(sound.id)
                            }
                        },
                        onPinClick = { onPinClick(sound) },
                        onEditClick =
                            if (!sound.isBundled()) {
                                { onEdit(sound) }
                            } else {
                                null
                            },
                        onAddToCollection =
                            if (!sound.isBundled() && !isWelcome && onAddToCollection != null) {
                                { onAddToCollection(sound) }
                            } else {
                                null
                            },
                        originLabel = if (isWelcome) stringResource(R.string.app_welcome_sticker_origin) else null,
                        isWelcomeVariant = isWelcome,
                        borderOverride = welcomeBorder,
                        showSwipeHint = isWelcome && welcomeHintPending,
                        onSwipeHintShown = onWelcomeHintShown,
                        collectionLabels = labels,
                        showCollectionLabels = !filterIsActive,
                        shareEnabled = shareEnabled,
                    )
                }
            }
        }
    }
}

// Deleting the welcome is now a real, manual, destructive action (it no longer self-destructs), so
// it needs Long like any user-deleted sound to keep Undo reachable.
internal fun deletionSnackbarDuration(
    @Suppress("UNUSED_PARAMETER") sound: Sound,
): SnackbarDuration = SnackbarDuration.Long

@Composable
private fun MySoundsBody(
    selectedTab: AppTab,
    sounds: List<Sound>,
    playbackProgress: PlaybackProgress?,
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
    listState: LazyListState,
    publicCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    activeFilterId: String?,
    innerPadding: PaddingValues,
    collectionsByAudio: Map<String, List<String>>,
    allCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    onActiveFilterEditClick: (String) -> Unit,
    onImportClick: () -> Unit,
    onShowOnboarding: () -> Unit,
    viewModel: SoundsViewModel,
    context: Context,
) {
    val activeCollection = publicCollections.firstOrNull { it.id == activeFilterId }
    val welcomeHintPending by viewModel.welcomeHintPending.collectAsStateWithLifecycle()
    val isOnMySounds = selectedTab == AppTab.MY_SOUNDS
    val showFilterEmptyState = isOnMySounds && activeFilterId != null && sounds.isEmpty() && activeCollection != null
    val showWelcomeEmptyState = sounds.isEmpty() && isOnMySounds && !showFilterEmptyState
    // The ActiveFilterHeader is suppressed alongside the chip row whenever the body collapses to a
    // ZRP — same reasoning as the Vault locked state: a header above a "nothing here yet" body
    // adds noise instead of context.
    val showHeader = isOnMySounds && !showWelcomeEmptyState && !showFilterEmptyState

    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(innerPadding)) {
        if (isOnMySounds) {
            // Always rendered on My Sounds — even with no public collections the user still needs
            // the "+ Nueva" chip to bootstrap one. Hiding the whole row left the create affordance
            // unreachable, see v2.4.0 launch regression.
            com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterChipsRow(
                publicCollections = publicCollections,
                activeFilterId = activeFilterId,
                onFilterSelected = { id -> viewModel.selectMySoundsFilter(id) },
                onCreateRequested = {
                    viewModel.requestCreateCollection(
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = AnalyticsSource.MY_SOUNDS_FILTER,
                    )
                },
            )
        }
        if (showHeader) {
            com.github.barriosnahuel.vossosunboton.feature.collections.ActiveFilterHeader(
                activeCollection = activeCollection,
                audioCount = sounds.size,
                isVaultContext = false,
                onEditClick = { activeCollection?.id?.let(onActiveFilterEditClick) },
            )
        }
        when {
            showFilterEmptyState && activeCollection != null ->
                MySoundsFilterEmptyState(collectionName = activeCollection.name)
            showWelcomeEmptyState ->
                // weight(1f) so the centered group fills the space *below* the chips row, not the
                // full viewport — without it the empty state's fillMaxSize requests the whole height
                // (Column gives non-weighted children the full max), pushing the CTA below the fold.
                MySoundsEmptyState(
                    onImportClick = onImportClick,
                    onShowOnboarding = onShowOnboarding,
                    modifier = Modifier.weight(1f),
                )
            else ->
                SoundsList(
                    sounds = sounds,
                    playbackProgress = playbackProgress,
                    pausedProgress = pausedProgress,
                    soundDurations = soundDurations,
                    listState = listState,
                    collectionsByAudio = collectionsByAudio,
                    allCollections = allCollections,
                    bottomContentPadding = if (isOnMySounds) FAB_LIST_BOTTOM_PADDING else Spacing.SM,
                    filterIsActive = selectedTab == AppTab.MY_SOUNDS && activeFilterId != null,
                    onPlayClick = { sound -> viewModel.playOrStop(sound) },
                    onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                    onShareClick = { sound -> viewModel.share(sound) },
                    onDelete = { sound -> viewModel.deleteSound(sound) },
                    onPinClick = { sound -> viewModel.togglePin(sound) },
                    onEdit = { sound ->
                        context.startActivity(LandingActivity.editIntent(context, sound))
                    },
                    onAddToCollection = { sound -> viewModel.requestAssignCollections(sound.id) },
                    welcomeHintPending = welcomeHintPending,
                    onWelcomeHintShown = { viewModel.markWelcomeHintShown() },
                )
        }
    }
}

@Composable
private fun MySoundsFilterEmptyState(collectionName: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XL),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_my_sounds_filter_empty_collection_headline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.foundation.layout
            .Spacer(modifier = Modifier.padding(top = Spacing.SM))
        Text(
            text = stringResource(R.string.app_my_sounds_filter_empty_collection_body, collectionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Top-level navigation request to open [com.github.barriosnahuel.vossosunboton.feature.collections.ManageCollectionsScreen].
 * `Generic` opens it at the top with no row highlight; `Focused` deep-links to a specific
 * collection (scroll + transient tint). Persisted across rotation via [Saver].
 */
internal sealed class ManageRequest {
    object Generic : ManageRequest()

    data class Focused(
        val collectionId: String,
    ) : ManageRequest()

    companion object {
        val Saver: androidx.compose.runtime.saveable.Saver<ManageRequest?, Any> =
            androidx.compose.runtime.saveable.Saver(
                save = { value ->
                    when (value) {
                        null -> ""
                        Generic -> "generic"
                        is Focused -> "focused:${value.collectionId}"
                    }
                },
                restore = { raw ->
                    val s = raw as? String ?: return@Saver null
                    when {
                        s.isEmpty() -> null
                        s == "generic" -> Generic
                        s.startsWith("focused:") -> Focused(s.removePrefix("focused:"))
                        else -> null
                    }
                },
            )
    }
}
