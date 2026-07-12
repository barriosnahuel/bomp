/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.github.barriosnahuel.vossosunboton.ui.home.AppTab
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Back-stack semantics of programmatic tab switches (deep links, "view collection"). The graph owns
 * navigation: switching to a tab must land on that tab's root, not on whatever history was left
 * behind — see docs/adr/0024-jetpack-navigation-3.md.
 */
class LandingNavigatorTest {
    private var stoppedPlayback = 0

    private fun navigator(
        homeStack: List<NavKey> = listOf(HomeRoute),
        vaultStack: List<NavKey> = listOf(VaultRoute),
        exploreStack: List<NavKey> = listOf(ExploreRoute),
        startOn: NavKey = HomeRoute,
    ): LandingNavigator {
        val state =
            LandingNavigationState(
                topLevelRoute = mutableStateOf(startOn),
                backStacks =
                    mapOf(
                        HomeRoute to NavBackStack(*homeStack.toTypedArray()),
                        VaultRoute to NavBackStack(*vaultStack.toTypedArray()),
                        ExploreRoute to NavBackStack(*exploreStack.toTypedArray()),
                    ),
            )
        return LandingNavigator(state = state, onStopPlayback = { stoppedPlayback++ })
    }

    @Test
    fun `switching tabs drops destinations left open on the tab being left`() {
        val navigator = navigator(homeStack = listOf(HomeRoute, AboutRoute))

        navigator.switchToTabRoot(AppTab.EXPLORE_SOUNDS)

        assertThat(navigator.state.visibleRoute).isEqualTo(ExploreRoute)
        // Back from Explore's root exits through home — which must be the sounds list, not the
        // About screen the user left behind before the deep link arrived.
        navigator.goBack()
        assertThat(navigator.state.visibleRoute).isEqualTo(HomeRoute)
    }

    @Test
    fun `switching to the tab already showing an overlay returns to the tab root`() {
        val navigator = navigator(homeStack = listOf(HomeRoute, AboutRoute))

        navigator.switchToTabRoot(AppTab.MY_SOUNDS)

        assertThat(navigator.state.visibleRoute).isEqualTo(HomeRoute)
    }

    @Test
    fun `switching to a tab that has its own history lands on the tab root`() {
        val navigator =
            navigator(
                exploreStack = listOf(ExploreRoute, AboutRoute),
                startOn = HomeRoute,
            )

        navigator.switchToTabRoot(AppTab.EXPLORE_SOUNDS)

        assertThat(navigator.state.visibleRoute).isEqualTo(ExploreRoute)
    }

    @Test
    fun `switching tabs stops an immersive listen playing on the tab being left`() {
        val navigator = navigator(homeStack = listOf(HomeRoute, ImmersiveListenRoute(soundId = "s1")))

        navigator.switchToTabRoot(AppTab.VAULT)

        assertThat(stoppedPlayback).isEqualTo(1)
        assertThat(navigator.state.backStacks.getValue(HomeRoute)).containsExactly(HomeRoute)
    }

    @Test
    fun `switching tabs leaves no import hub sheet to resurrect on the next visit`() {
        val navigator = navigator(homeStack = listOf(HomeRoute, ImportHubRoute))

        navigator.switchToTabRoot(AppTab.VAULT)
        navigator.switchToTabRoot(AppTab.MY_SOUNDS)

        assertThat(navigator.state.visibleRoute).isEqualTo(HomeRoute)
    }

    @Test
    fun `an unsaved recording is not silently kept alive under a tab switch`() {
        val navigator = navigator(homeStack = listOf(HomeRoute, RecorderRoute(resumeDraft = false)))

        navigator.switchToTabRoot(AppTab.EXPLORE_SOUNDS)
        navigator.goBack()

        assertThat(navigator.state.visibleRoute).isEqualTo(HomeRoute)
    }
}
