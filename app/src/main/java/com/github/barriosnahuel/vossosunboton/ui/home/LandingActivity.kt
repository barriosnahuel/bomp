package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

class LandingActivity : ComponentActivity() {
    private val viewModel: SoundsViewModel by viewModels { SoundsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The TopAppBar is always a dark violet in both light and dark modes, so status
        // bar icons must always be light (white). SystemBarStyle.dark forces this.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                LandingScreen(viewModel = viewModel)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        handleDeeplink(intent)
        if (intent.getBooleanExtra(EXTRA_BUTTON_SAVED, false)) {
            viewModel.onButtonSaved()
        }
    }

    private fun handleDeeplink(intent: Intent) {
        val uri = intent.data ?: return
        val tab =
            when (uri.path) {
                "/home" -> AppTab.HOME
                "/favorites" -> AppTab.FAVORITES
                else -> AppTab.EXPLORE
            }
        viewModel.selectTab(tab)
    }

    companion object {
        const val EXTRA_BUTTON_SAVED = "extra_button_saved"
    }
}
