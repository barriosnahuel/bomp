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
 * Handles navigation events by updating [LandingNavigationState], keeping `SoundsViewModel`'s
 * `selectedTab` in sync through [onTabChange] — the ViewModel still projects the sounds list off
 * the selected tab, so every tab change (taps, back pops, deep links) must reach it.
 */
internal class LandingNavigator(
    val state: LandingNavigationState,
    private val onTabChange: (AppTab) -> Unit,
) {
    fun navigate(route: NavKey) {
        val tab = route.toTabOrNull()
        if (tab != null) {
            state.topLevelRoute = route
            onTabChange(tab)
        } else {
            state.activeStack.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.activeStack
        if (currentStack.lastOrNull() == state.topLevelRoute) {
            // At the base of the current tab: exit through home.
            state.topLevelRoute = state.startRoute
            onTabChange(AppTab.MY_SOUNDS)
        } else {
            currentStack.removeLastOrNull()
        }
    }

    /**
     * Removes [route] from whichever stack holds it — not necessarily the active one. Needed by
     * destinations whose actions switch tabs before closing themselves (e.g. Manage Collections'
     * "view collection"): a plain [goBack] would pop the *new* tab's stack and leave the closed
     * screen resurrectable on the old one.
     */
    fun close(route: NavKey) {
        // Exactly one instance is removed, preferring the active stack: routes are value-equal
        // (@Serializable objects/data classes), and a route legitimately duplicated as another
        // tab's history must survive closing the visible one.
        val stacks =
            buildList {
                add(state.activeStack)
                state.backStacks.values.forEach { if (it !== state.activeStack) add(it) }
            }
        for (stack in stacks) {
            val index = stack.lastIndexOf(route)
            if (index >= 0) {
                stack.removeAt(index)
                return
            }
        }
    }

    /** True when [route] is the visible destination — the idempotence guard for double-tap opens. */
    fun isVisible(route: NavKey): Boolean = state.visibleRoute == route
}
