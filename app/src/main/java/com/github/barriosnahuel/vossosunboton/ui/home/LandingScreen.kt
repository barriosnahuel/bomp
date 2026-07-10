/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:Suppress("TooManyFunctions")

package com.github.barriosnahuel.vossosunboton.ui.home
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsSource
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity
import com.github.barriosnahuel.vossosunboton.feature.addbutton.findFragmentActivity
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecordingActivity
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
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.AboutRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.BottomSheetSceneStrategy
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.BringFromAppsRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.ExploreRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.HomeRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.ImmersiveListenRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.ImportHubRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.LandingNavigator
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.ManageCollectionsRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.OnboardingRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.VaultRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.instantTabTransitions
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.rememberLandingNavigationState
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.toRoute
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.toTabOrNull
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
    val context = LocalContext.current
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }

    // Navigation 3 backbone (ADR 0024): one saveable back stack per tab + typed child destinations.
    // The Navigator keeps SoundsViewModel.selectedTab in sync — the ViewModel still projects the
    // sounds list off the selected tab.
    val navState = rememberLandingNavigationState()
    val navigator = remember(navState) { LandingNavigator(navState) { tab -> viewModel.selectTab(tab) } }

    // ViewModel → graph sync: deep links (LandingActivity.handleDeeplink → selectTab) and any other
    // programmatic tab change reach the graph through here. Navigator-side changes call selectTab
    // themselves and StateFlow de-dups equal values, so this cannot loop. selectedTab survives
    // process death (SavedStateHandle) so this never fights the restored back stack.
    LaunchedEffect(selectedTab) {
        if (navState.topLevelRoute.toTabOrNull() != selectedTab) {
            // Leaving the current stack programmatically: an open immersive listen must not keep
            // playing headless behind another tab, and the modal Hub sheet must not resurrect on a
            // later visit — close both. Full-screen destinations stay: they are tab history (D2).
            when (val visible = navState.visibleRoute) {
                is ImmersiveListenRoute -> {
                    viewModel.stopPlayback()
                    navigator.close(visible)
                }
                ImportHubRoute -> navigator.close(visible)
                else -> Unit
            }
            navigator.navigate(selectedTab.toRoute())
        }
    }

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

    // Single import-Hub entry point: logs the funnel's ENTRY with the [source] surface, then opens.
    // The isVisible guard makes the open idempotent so a rapid double-tap on a trigger can't
    // double-count `import_hub_opened` (or push the sheet twice).
    val openHub = { source: String ->
        if (!navigator.isVisible(ImportHubRoute)) {
            tracker.log(AnalyticsEvent.ImportHubOpened(source = source))
            navigator.navigate(ImportHubRoute)
        }
    }

    // Single onboarding-tour entry point: logs the ENTRY with the [source] surface (empty state, the
    // welcome footer, or the overflow menu), then opens the tour at step 0. The guard mirrors
    // openHub's: idempotent open so a rapid double-tap can't double-count `onboarding_opened`.
    val openOnboarding = { source: String ->
        if (!navigator.isVisible(OnboardingRoute)) {
            tracker.log(AnalyticsEvent.OnboardingOpened(source = source))
            navigator.navigate(OnboardingRoute)
        }
    }

    // screen_view derives from the visible destination, with the search overlay winning while open.
    // Routes with no canonical name (Hub sheet, immersive listen) map to null = "keep the previous
    // screen", and distinctUntilChanged keeps open/close round-trips over them from re-emitting —
    // the same semantics the pre-Nav3 boolean model had.
    LaunchedEffect(Unit) {
        snapshotFlow { screenNameFor(navState, isSearchVisible) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { AnalyticsTrackerProvider.get(context.applicationContext).logScreen(it) }
    }

    SnackbarEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    val collections by viewModel.collections.collectAsState()
    val activeFilter by viewModel.activeMySoundsFilter.collectAsState()
    val publicCollections = remember(collections) { collections.filter { it.isPublic } }
    val collectionsByAudio by viewModel.audioCollectionsIndex.collectAsState()
    val privateCollections = remember(collections) { collections.filter { it.isPrivate } }

    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }

    // The three tab entries share this content; each binds its own constant tab.
    val tabContent: @Composable (AppTab) -> Unit = { tab ->
        TabContent(
            tab = tab,
            viewModel = viewModel,
            sounds = sounds,
            hasBundledSounds = hasBundledSounds,
            playbackProgress = playbackProgress,
            pausedProgress = pausedProgress,
            soundDurations = soundDurations,
            snackbarHostState = snackbarHostState,
            context = context,
            collections = collections,
            activeFilter = activeFilter,
            publicCollections = publicCollections,
            collectionsByAudio = collectionsByAudio,
            privateCollections = privateCollections,
            onTabSelected = { target -> navigator.navigate(target.toRoute()) },
            // isVisible-style guards mirror openHub's: a rapid double-tap must not push twice.
            onAboutClick = {
                if (!navigator.isVisible(AboutRoute)) navigator.navigate(AboutRoute)
            },
            onManageCollectionsClick = {
                if (navState.visibleRoute !is ManageCollectionsRoute) {
                    navigator.navigate(ManageCollectionsRoute())
                }
            },
            onActiveFilterEditClick = { collectionId ->
                if (navState.visibleRoute !is ManageCollectionsRoute) {
                    navigator.navigate(ManageCollectionsRoute(collectionId))
                }
            },
            onImmersivePlay = { sound ->
                // Vault play opens immersive listen mode and starts a listen session — always from 0 on
                // the long-form engine (ADR 0022), unless the audio is already mid-session. Guarded so a
                // double-tap can't push the destination twice.
                if (navState.visibleRoute !is ImmersiveListenRoute) {
                    navigator.navigate(ImmersiveListenRoute(sound.id))
                    if (!sound.isPlaying) viewModel.startListenSession(sound)
                }
            },
            onCreateClick = openHub,
            // One source-parameterized entry point (mirrors onCreateClick): each surface binds its own
            // AnalyticsSource at the leaf call site below, so no per-surface callback has to be threaded.
            onShowOnboarding = openOnboarding,
        )
    }

    NavDisplay(
        entries =
            navState.toEntries(
                entryProvider {
                    entry<HomeRoute>(metadata = instantTabTransitions()) { tabContent(AppTab.MY_SOUNDS) }
                    entry<VaultRoute>(metadata = instantTabTransitions()) { tabContent(AppTab.VAULT) }
                    entry<ExploreRoute>(metadata = instantTabTransitions()) { tabContent(AppTab.EXPLORE_SOUNDS) }
                    entry<AboutRoute> {
                        AboutScreen(onBack = { navigator.close(AboutRoute) })
                    }
                    entry<ManageCollectionsRoute> { key ->
                        // close(key), not goBack(): Manage's "view collection" action switches tab before
                        // closing, and a plain pop would hit the new tab's stack (see LandingNavigator.close).
                        com.github.barriosnahuel.vossosunboton.feature.collections.ManageCollectionsScreen(
                            viewModel = viewModel,
                            focusedCollectionId = key.focusedCollectionId,
                            onBack = { navigator.close(key) },
                        )
                    }
                    entry<ImmersiveListenRoute> { key ->
                        // Back STOPS playback definitively (sessions have no cross-open retention —
                        // ADR 0022) so no audio keeps playing headless behind the Vault list.
                        com.github.barriosnahuel.vossosunboton.feature.vault.ImmersiveListenHost(
                            viewModel = viewModel,
                            soundId = key.soundId,
                            onBack = {
                                viewModel.stopPlayback()
                                navigator.close(key)
                            },
                        )
                    }
                    entry<OnboardingRoute> {
                        OnboardingHost(
                            onClose = { navigator.close(OnboardingRoute) },
                            onFinished = {
                                navigator.close(OnboardingRoute)
                                // The tour teaches My Bomps and lands on the Hub to add a first Bomp. When
                                // opened from the overflow on Vault/Explore, return to My Bomps first so the
                                // saved Bomp lands on the tab the user is looking at.
                                if (selectedTab != AppTab.MY_SOUNDS) {
                                    navigator.navigate(HomeRoute)
                                }
                                openHub(AnalyticsSource.ONBOARDING_FINISH)
                            },
                        )
                    }
                    entry<BringFromAppsRoute> {
                        com.github.barriosnahuel.vossosunboton.feature.onboarding.BringFromAppsGuide(
                            onClose = { navigator.close(BringFromAppsRoute) },
                        )
                    }
                    entry<ImportHubRoute>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                        ImportHubSheet(
                            // Guard on visibility so a double-tap on a row (both taps land before the pop
                            // recomposes the sheet away) fires its action once, not twice.
                            onImport = {
                                if (navigator.isVisible(ImportHubRoute)) {
                                    tracker.log(AnalyticsEvent.ImportHubImportSelected)
                                    navigator.close(ImportHubRoute)
                                    importPicker.launch(arrayOf("audio/*"))
                                }
                            },
                            onRecord = {
                                if (navigator.isVisible(ImportHubRoute)) {
                                    tracker.log(AnalyticsEvent.ImportHubRecordSelected)
                                    navigator.close(ImportHubRoute)
                                    context.startActivity(RecordingActivity.createIntent(context))
                                }
                            },
                            onBringFromApps = {
                                if (navigator.isVisible(ImportHubRoute)) {
                                    tracker.log(AnalyticsEvent.ImportHubBringSelected)
                                    navigator.close(ImportHubRoute)
                                    navigator.navigate(BringFromAppsRoute)
                                }
                            },
                        )
                    }
                },
            ),
        onBack = {
            // Route-aware side effect: popping the immersive listen (gesture or button) must stop
            // the session, mirroring its explicit onBack above.
            if (navState.visibleRoute is ImmersiveListenRoute) viewModel.stopPlayback()
            navigator.goBack()
        },
        sceneStrategies = listOf(bottomSheetStrategy),
    )

    com.github.barriosnahuel.vossosunboton.feature.collections
        .CollectionSheetHost(viewModel = viewModel)

    com.github.barriosnahuel.vossosunboton.feature.collections
        .AssignCollectionSheet(viewModel = viewModel)

    com.github.barriosnahuel.vossosunboton.feature.collections
        .CollectionDeleteDialog(viewModel = viewModel)

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
 * Maps the visible destination (+ the search overlay) to its canonical `screen_view` literal.
 * Routes with no screen of their own (Hub sheet, immersive listen) are transparent: the name
 * resolves to the entry beneath them in the stack, so opening/closing them never re-emits and a
 * tab change underneath them still surfaces — the pre-Nav3 semantics.
 */
private fun screenNameFor(
    navState: com.github.barriosnahuel.vossosunboton.ui.home.navigation.LandingNavigationState,
    isSearchVisible: Boolean,
): String? =
    when {
        isSearchVisible -> CanonicalScreenName.SEARCH_SOUND
        else -> navState.activeStack.lastOrNull { canonicalNameFor(it) != null }?.let { canonicalNameFor(it) }
    }

private fun canonicalNameFor(route: NavKey): String? =
    when (route) {
        HomeRoute -> CanonicalScreenName.MY_SOUNDS
        VaultRoute -> CanonicalScreenName.VAULT
        ExploreRoute -> CanonicalScreenName.EXPLORE_SOUNDS
        AboutRoute -> CanonicalScreenName.ABOUT
        is ManageCollectionsRoute -> CanonicalScreenName.MANAGE_COLLECTIONS
        OnboardingRoute -> CanonicalScreenName.ONBOARDING
        BringFromAppsRoute -> CanonicalScreenName.BRING_GUIDE
        else -> null
    }

/**
 * Hosts the onboarding tour as a destination. The step lives here as `rememberSaveable` (not a
 * route argument) so advancing steps never mutates the back stack and an Activity recreate
 * mid-tour cannot rewind the user to step 0 (§ Stateful Composables); the tour's internal
 * `BackHandler` keeps owning step-back vs dismiss.
 */
@Composable
private fun OnboardingHost(
    onClose: () -> Unit,
    onFinished: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    com.github.barriosnahuel.vossosunboton.feature.onboarding.OnboardingTour(
        step = step,
        onAdvance = {
            step = (step + 1).coerceAtMost(com.github.barriosnahuel.vossosunboton.feature.onboarding.ONBOARDING_STEP_COUNT - 1)
        },
        onStepBack = { step = (step - 1).coerceAtLeast(0) },
        onSkip = onClose,
        onFinish = onFinished,
    )
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
 * One tab's full screen: the shared chrome (top bar, FAB, bottom bar, snackbar host) plus the
 * tab's body. Each tab route renders its own instance; hoisted state (list scroll, snackbars)
 * lives in [LandingScreen] so it behaves exactly as the single pre-Nav3 Scaffold did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabContent(
    tab: AppTab,
    viewModel: SoundsViewModel,
    sounds: List<Sound>,
    hasBundledSounds: Boolean,
    playbackProgress: PlaybackProgress?,
    pausedProgress: Map<String, PlaybackProgress>,
    soundDurations: Map<String, Int>,
    snackbarHostState: SnackbarHostState,
    context: Context,
    collections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    activeFilter: String?,
    publicCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    collectionsByAudio: Map<String, List<String>>,
    privateCollections: List<com.github.barriosnahuel.vossosunboton.model.Collection>,
    onTabSelected: (AppTab) -> Unit,
    onAboutClick: () -> Unit,
    onManageCollectionsClick: () -> Unit,
    onActiveFilterEditClick: (String) -> Unit,
    onImmersivePlay: (Sound) -> Unit,
    // Opens the import Hub, carrying the funnel `source` of the surface that triggered it (the FAB
    // vs the empty-state CTA), so `import_hub_opened` is attributed honestly.
    onCreateClick: (String) -> Unit,
    // Opens the onboarding tour, carrying the funnel `source` of the surface that triggered it (empty
    // state, welcome footer, or overflow). Each leaf surface binds its own constant.
    onShowOnboarding: (String) -> Unit,
) {
    // Per-tab list state: each tab keeps its own scroll position (multi-back-stack state retention,
    // ADR 0024), and a predictive-back preview composing two tabs at once never shares one
    // LazyListState between two live lists. The scroll-to-top event only reaches the visible tab —
    // the others are not composed.
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Only the ACTIVE tab collects the one-shot scroll event: during a tab transition (or a
    // predictive-back preview) two TabContent instances are composed at once, and the Channel's
    // single delivery (ADR 0003) must not be consumed by the outgoing tab's collector.
    val selectedTabNow by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isActiveTab = selectedTabNow == tab
    LaunchedEffect(isActiveTab) {
        if (isActiveTab) {
            viewModel.scrollToTopEvent.collect {
                listState.animateScrollToItem(0)
            }
        }
    }
    Scaffold(
        topBar = {
            AppTopBar(
                onSearchClick = { viewModel.showSearch() },
                onSeeHowItWorks = { onShowOnboarding(AnalyticsSource.OVERFLOW_MENU) },
                onManageCollectionsClick = onManageCollectionsClick,
                onAboutClick = onAboutClick,
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
            if (tab == AppTab.MY_SOUNDS && !showsWelcomeEmpty) {
                FloatingActionButton(
                    onClick = { onCreateClick(AnalyticsSource.FAB) },
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
                selectedTab = tab,
                hasExplore = hasBundledSounds,
                onTabSelected = { target ->
                    if (target == tab) {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    } else {
                        onTabSelected(target)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (tab) {
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
                    selectedTab = tab,
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
                    onImportClick = { onCreateClick(AnalyticsSource.EMPTY_STATE) },
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
        val message = deletionSnackbarMessage(activityContext, event.sound)
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

// Test tag for the overflow "See how it works" item, which shares its label with the welcome footer.
internal const val OVERFLOW_SEE_HOW_IT_WORKS_TAG = "overflow_see_how_it_works"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    onSearchClick: () -> Unit,
    onSeeHowItWorks: () -> Unit,
    onManageCollectionsClick: () -> Unit,
    onAboutClick: () -> Unit,
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
            // Ordered by intent for a confused newcomer (help first), then organize; a divider splits
            // "use the app" from "about the app" (share + about last, the conventional tail). Leading
            // icons are decorative — the item text is the accessible name (emojis read poorly in TalkBack).
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    // Tagged so tests can address this item without its label ("See how it works"),
                    // which it intentionally shares with the welcome footer. Same label → same action,
                    // so the duplication is fine for users; the tag only disambiguates for tests.
                    modifier = Modifier.testTag(OVERFLOW_SEE_HOW_IT_WORKS_TAG),
                    text = { Text(stringResource(R.string.app_my_sounds_empty_secondary)) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.app_ic_help), contentDescription = null)
                    },
                    onClick = {
                        isMenuExpanded = false
                        onSeeHowItWorks()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_manage_collections_menu_item)) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.app_ic_library_music), contentDescription = null)
                    },
                    onClick = {
                        isMenuExpanded = false
                        onManageCollectionsClick()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_share_app_menu_item)) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.app_ic_share), contentDescription = null)
                    },
                    onClick = {
                        isMenuExpanded = false
                        ShareAppIntent.launch(context, PlayStoreReferrer.CONTENT_MENU)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_about)) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.app_ic_info), contentDescription = null)
                    },
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
    // When true (My Sounds, list is only the welcome), a "see how it works" footer trails the list so a
    // fresh-install user can reach the onboarding tour. Off everywhere else (Vault/Explore/Search).
    showWelcomeFooter: Boolean = false,
    onShowOnboardingFromWelcome: () -> Unit = {},
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
            if (showWelcomeFooter) {
                item(key = "welcome_onboarding_footer") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.SM),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Shared with the empty-state secondary (MySoundsEmptyState) so the two never
                        // drift in copy or button typology.
                        SeeHowItWorksButton(onClick = onShowOnboardingFromWelcome)
                    }
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

// Two intentional voices sharing one brand register: the welcome leans on Undo with a warm
// author line (never a goodbye, which a reversible action contradicts); a user audio names what
// was deleted. Blank name (unreachable via creation, which blocks blank) falls back to the
// nameless voice so the placeholder never renders empty.
internal fun deletionSnackbarMessage(
    context: Context,
    sound: Sound,
): String =
    when {
        isWelcomeSticker(sound) -> context.getString(R.string.app_welcome_sticker_feedback_dismissed)
        sound.name.isBlank() -> context.getString(R.string.app_feedback_audio_deleted_unnamed)
        else -> context.getString(R.string.app_feedback_audio_deleted, sound.name)
    }

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
    // Source-parameterized tour opener; this body binds EMPTY_STATE for the empty-state secondary and
    // WELCOME_FOOTER for the welcome footer at their respective leaf call sites.
    onShowOnboarding: (String) -> Unit,
    viewModel: SoundsViewModel,
    context: Context,
) {
    val activeCollection = publicCollections.firstOrNull { it.id == activeFilterId }
    val welcomeHintPending by viewModel.welcomeHintPending.collectAsStateWithLifecycle()
    // Re-collected on each resume so a draft created/cleared while the user was in the recorder (or
    // dropped by a process death) re-evaluates the banner without an explicit onResume hook (ADR 0019).
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle(initialValue = null)
    val draftTracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    // Impression — once per distinct draft (keyed on its file), so returning to Landing with the same
    // draft doesn't inflate the denominator of the resume rate.
    LaunchedEffect(pendingDraft?.file?.name) {
        if (pendingDraft != null) draftTracker.log(AnalyticsEvent.RecordingDraftBannerShown)
    }
    val isOnMySounds = selectedTab == AppTab.MY_SOUNDS
    val showFilterEmptyState = isOnMySounds && activeFilterId != null && sounds.isEmpty() && activeCollection != null
    val showWelcomeEmptyState = sounds.isEmpty() && isOnMySounds && !showFilterEmptyState
    // Fresh install lands here, not on the ZRP: the welcome audio keeps the list non-empty. Surface a
    // "see how it works" footer under the lone welcome so a new user can reach the tour without first
    // deleting the welcome. Drops away the moment any real Bomp exists.
    val showWelcomeFooter = isOnMySounds && sounds.isNotEmpty() && sounds.all { isWelcomeSticker(it) }
    // The ActiveFilterHeader is suppressed alongside the chip row whenever the body collapses to a
    // ZRP — same reasoning as the Vault locked state: a header above a "nothing here yet" body
    // adds noise instead of context.
    val showHeader = isOnMySounds && !showWelcomeEmptyState && !showFilterEmptyState

    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(innerPadding)) {
        pendingDraft?.let {
            RecorderDraftBanner(
                onContinue = {
                    draftTracker.log(AnalyticsEvent.RecordingDraftResumed)
                    context.startActivity(RecordingActivity.createIntent(context, resumeDraft = true))
                },
                onDiscard = {
                    draftTracker.log(AnalyticsEvent.RecordingDraftDiscarded)
                    viewModel.discardDraft()
                },
            )
        }
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
            showFilterEmptyState ->
                MySoundsFilterEmptyState(collectionName = activeCollection.name)
            showWelcomeEmptyState ->
                // weight(1f) so the centered group fills the space *below* the chips row, not the
                // full viewport — without it the empty state's fillMaxSize requests the whole height
                // (Column gives non-weighted children the full max), pushing the CTA below the fold.
                MySoundsEmptyState(
                    onImportClick = onImportClick,
                    onShowOnboarding = { onShowOnboarding(AnalyticsSource.EMPTY_STATE) },
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
                    showWelcomeFooter = showWelcomeFooter,
                    onShowOnboardingFromWelcome = { onShowOnboarding(AnalyticsSource.WELCOME_FOOTER) },
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
