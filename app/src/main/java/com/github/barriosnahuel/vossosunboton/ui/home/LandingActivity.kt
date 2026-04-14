package com.github.barriosnahuel.vossosunboton.ui.home

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

class LandingActivity : ComponentActivity() {
    private val viewModel: SoundsViewModel by viewModels()

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
    }
}
