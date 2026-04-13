package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

class LandingActivity : ComponentActivity() {
    private val viewModel: SoundsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                LandingScreen(viewModel = viewModel)
            }
        }
    }
}
