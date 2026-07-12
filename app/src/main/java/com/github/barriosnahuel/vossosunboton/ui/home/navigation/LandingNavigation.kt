/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import com.github.barriosnahuel.vossosunboton.feature.addbutton.AddSoundSource
import com.github.barriosnahuel.vossosunboton.ui.home.AppTab
import kotlinx.serialization.Serializable

// Navigation 3 backbone for Landing: typed routes, one back stack per tab, and the Navigator that
// mutates them. Architecture decision + trade-offs: docs/adr/0024-jetpack-navigation-3.md.

@Serializable
data object HomeRoute : NavKey

@Serializable
data object VaultRoute : NavKey

@Serializable
data object ExploreRoute : NavKey

@Serializable
data object AboutRoute : NavKey

@Serializable
data class ManageCollectionsRoute(
    val focusedCollectionId: String? = null,
) : NavKey

@Serializable
data class ImmersiveListenRoute(
    val soundId: String,
) : NavKey

/** Tour step progression stays inside the destination (`rememberSaveable`), matching today's semantics. */
@Serializable
data object OnboardingRoute : NavKey

@Serializable
data object BringFromAppsRoute : NavKey

/** Rendered as a modal bottom sheet via [BottomSheetSceneStrategy] metadata. */
@Serializable
data object ImportHubRoute : NavKey

/**
 * In-app recorder (ADR 0019) as a destination (ADR 0024 D4). [resumeDraft] true — the Landing draft
 * banner — restores the pending clip into Review, matching the Activity's old `EXTRA_RESUME_DRAFT`.
 * Record and Review are states of `RecorderViewModel`, not separate routes: the capture screen morphs
 * in place, so a "preview" route would be a destination the user can never navigate back to.
 */
@Serializable
data class RecorderRoute(
    val resumeDraft: Boolean = false,
) : NavKey

/**
 * Naming + save, the shared tail of every creation/edit flow (ADR 0024 D4). Exactly one of
 * [uri]/[editSoundId] is set: [uri] (a `content`/`file` URI, string-encoded because `Uri` is not
 * `@Serializable`) drives Create; [editSoundId] drives Edit, resolved against the live library so the
 * route stays a value type — the graph never carries a `Sound` snapshot that could go stale.
 * [source] tags the `sound_add` funnel and the persisted provenance ([AddSoundSource]); Edit ignores it,
 * which is why it defaults rather than being spelled at the edit call sites.
 *
 * The URI still flows through `AddButtonFeature`'s inbound validator at save time, exactly as it did
 * from the Activity — see CLAUDE.md § *Security boundaries*.
 */
@Serializable
data class NameSoundRoute(
    val source: String = AddSoundSource.IMPORT,
    val uri: String? = null,
    val editSoundId: String? = null,
) : NavKey

/**
 * Tab entries switch instantly (no cross-scaffold animation), matching the pre-Nav3 body swap —
 * the top/bottom bars must read as static chrome while tabs change. Child destinations keep the
 * NavDisplay defaults, which is where the automatic predictive back pays off (ADR 0024).
 *
 * The outgoing tab must stay composed beneath until the incoming one has drawn
 * (`KeepUntilTransitionsFinished`, the official recipe's pattern): `ExitTransition.None` drops it
 * immediately, exposing the bare window background for however many frames the new tab's first
 * composition takes — on device that reads as an empty-state flash when entering the Vault.
 */
internal fun instantTabTransitions() =
    metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished }
        put(NavDisplay.PredictivePopTransitionKey) {
            EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
        }
    }

internal fun AppTab.toRoute(): NavKey =
    when (this) {
        AppTab.MY_SOUNDS -> HomeRoute
        AppTab.VAULT -> VaultRoute
        AppTab.EXPLORE_SOUNDS -> ExploreRoute
    }

internal fun NavKey.toTabOrNull(): AppTab? =
    when (this) {
        HomeRoute -> AppTab.MY_SOUNDS
        VaultRoute -> AppTab.VAULT
        ExploreRoute -> AppTab.EXPLORE_SOUNDS
        else -> null
    }

/**
 * Creates the Landing navigation state: the current top-level route plus one saveable back stack
 * per tab. Both survive configuration changes and process death (`rememberSerializable` +
 * `rememberNavBackStack`), which is what lets an open overlay survive rotation without the
 * hand-written `Saver`s the boolean model needed.
 */
@Composable
internal fun rememberLandingNavigationState(): LandingNavigationState {
    val topLevelRoute =
        rememberSerializable(
            serializer = MutableStateSerializer(NavKeySerializer()),
        ) {
            mutableStateOf<NavKey>(HomeRoute)
        }
    val backStacks: Map<NavKey, NavBackStack<NavKey>> =
        mapOf(
            HomeRoute to rememberNavBackStack(HomeRoute),
            VaultRoute to rememberNavBackStack(VaultRoute),
            ExploreRoute to rememberNavBackStack(ExploreRoute),
        )
    return remember { LandingNavigationState(topLevelRoute = topLevelRoute, backStacks = backStacks) }
}

/**
 * State holder for Landing navigation. Mutated only through [LandingNavigator] (UDF). Follows the
 * official multiple-back-stacks recipe, including its "exit through home" pattern: the Home stack
 * is always at the bottom, so the user always leaves the app from My Sounds.
 */
internal class LandingNavigationState(
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: NavKey by topLevelRoute

    val startRoute: NavKey = HomeRoute

    /** The route whose entry is currently on top (the visible destination). */
    val visibleRoute: NavKey
        get() = activeStack.lastOrNull() ?: topLevelRoute

    val activeStack: NavBackStack<NavKey>
        get() = backStacks.getValue(topLevelRoute)

    private val stacksInUse: List<NavKey>
        get() =
            if (topLevelRoute == startRoute) {
                listOf(startRoute)
            } else {
                listOf(startRoute, topLevelRoute)
            }

    /** Flattens the in-use stacks into decorated entries for `NavDisplay`. */
    @Composable
    fun toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decoratedEntries =
            backStacks.mapValues { (_, stack) ->
                val decorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                        // Screen-lifetime ViewModels (today the recorder) scope to their NavEntry, not to
                        // the Activity (ADR 0024 D3): popping the destination clears the ViewModel, so a
                        // second visit starts fresh instead of resurrecting the previous capture's state.
                        // SoundsViewModel is unaffected — it is Activity-scoped and passed in by hand.
                        rememberViewModelStoreNavEntryDecorator<NavKey>(),
                    )
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = decorators,
                    entryProvider = entryProvider,
                )
            }
        return stacksInUse.flatMap { decoratedEntries[it] ?: emptyList() }
    }
}

/**
 * Handles navigation events by updating [LandingNavigationState]. The graph is the single source of
 * truth for where the user is: `SoundsViewModel` mirrors the active tab (it projects the sounds list
 * off it) by observing [LandingNavigationState.topLevelRoute], never by driving it.
 *
 * [onStopPlayback] is the one effect the navigator cannot own: leaving a tab with an immersive
 * listen open must silence it, and playback lives in the ViewModel.
 */
internal class LandingNavigator(
    val state: LandingNavigationState,
    private val onStopPlayback: () -> Unit,
) {
    /**
     * Tab taps and destination pushes. A tab tap *keeps* that tab's history (D2 multiple back
     * stacks): the user who left About open on Home expects it there when they come back.
     */
    fun navigate(route: NavKey) {
        val tab = route.toTabOrNull()
        if (tab != null) {
            state.topLevelRoute = route
        } else {
            state.activeStack.add(route)
        }
    }

    /**
     * Programmatic navigation to a tab — a deep link, or Manage Collections' "view collection".
     * Unlike a tab tap it lands on the tab's *root*: the caller named a destination, so leaving the
     * user under whatever screen happened to be open (or under another tab's leftover history, which
     * "exit through home" would surface later) would ignore what was asked for.
     *
     * Every stack is reset, not just the destination's: the leftovers are only reachable *because*
     * of history the user didn't choose to keep, and Home's stack is always below the active tab.
     */
    fun switchToTabRoot(tab: AppTab) {
        // An immersive listen being left behind must not keep playing headless under the new tab.
        if (state.visibleRoute is ImmersiveListenRoute) onStopPlayback()
        state.backStacks.values.forEach { stack ->
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        state.topLevelRoute = tab.toRoute()
    }

    fun goBack() {
        val currentStack = state.activeStack
        if (currentStack.lastOrNull() == state.topLevelRoute) {
            // At the base of the current tab: exit through home.
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }

    /** Removes [route] from the active stack — the only stack a visible destination can live on. */
    fun close(route: NavKey) {
        val stack = state.activeStack
        val index = stack.lastIndexOf(route)
        if (index >= 0) stack.removeAt(index)
    }

    /**
     * Pops the whole creation flow, landing back on the tab the user started from.
     *
     * A save reached from the recorder leaves `[tab, RecorderRoute, NameSoundRoute]` on the stack, and
     * popping one entry would drop the user back into the recorder — on a clip they just saved. Popping
     * only the contiguous creation routes on top (rather than truncating to the tab) keeps any other
     * history below intact.
     */
    fun closeCreationFlow() {
        val stack = state.activeStack
        while (stack.lastOrNull().let { it is NameSoundRoute || it is RecorderRoute }) {
            stack.removeLastOrNull()
        }
    }

    /** True when [route] is the visible destination — the idempotence guard for double-tap opens. */
    fun isVisible(route: NavKey): Boolean = state.visibleRoute == route
}
