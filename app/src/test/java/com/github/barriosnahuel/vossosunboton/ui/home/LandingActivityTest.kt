/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric

internal class LandingActivityTest : AbstractRobolectricTest() {
    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `LandingActivity reaches RESUMED without crashing`() {
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun `LandingActivity with EXTRA_BUTTON_SAVED in onCreate navigates to MY_SOUNDS tab`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), LandingActivity::class.java).apply {
                putExtra(LandingActivity.EXTRA_BUTTON_SAVED, true)
            }
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }

    @Test
    fun `LandingActivity with EXTRA_BUTTON_SAVED in onNewIntent navigates to MY_SOUNDS tab`() {
        val controller =
            Robolectric
                .buildActivity(LandingActivity::class.java)
                .create()
                .start()
                .resume()
        val intent = Intent().apply { putExtra(LandingActivity.EXTRA_BUTTON_SAVED, true) }

        controller.newIntent(intent)

        val viewModel = ViewModelProvider(controller.get(), SoundsViewModel.Factory)[SoundsViewModel::class.java]
        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
    }

    @Test
    fun `deep link with home path opens MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/home"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }

    @Test
    fun `deep link with explore path opens EXPLORE_SOUNDS tab when bundled audios exist`() {
        // Skip when this checkout has no bundled audios — the empty-Explore UX fallback would
        // mask the allowlist behaviour we want to assert. Run only on debug builds with raw/.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        if (PackagedAudios.get(ctx).isEmpty()) {
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/explore"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.EXPLORE_SOUNDS)
            }
        }
    }

    @Test
    fun `deep link with unknown path falls back to MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/unknown"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }

    @Test
    fun `deep link with no path falls back to MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }
}
