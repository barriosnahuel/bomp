package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
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
    fun `LandingActivity with EXTRA_BUTTON_SAVED in onCreate navigates to HOME tab`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), LandingActivity::class.java).apply {
                putExtra(LandingActivity.EXTRA_BUTTON_SAVED, true)
            }
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.HOME)
            }
        }
    }

    @Test
    fun `LandingActivity with EXTRA_BUTTON_SAVED in onNewIntent navigates to HOME tab`() {
        val controller =
            Robolectric
                .buildActivity(LandingActivity::class.java)
                .create()
                .start()
                .resume()
        val intent = Intent().apply { putExtra(LandingActivity.EXTRA_BUTTON_SAVED, true) }

        controller.newIntent(intent)

        val viewModel = ViewModelProvider(controller.get(), SoundsViewModel.Factory)[SoundsViewModel::class.java]
        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.HOME)
    }
}
