package com.github.barriosnahuel.vossosunboton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF6200EE)
private val PurpleDark = Color(0xFF3D00E0)
private val Accent = Color(0xFF021AEE)

private val LightColors =
    lightColorScheme(
        primary = Purple,
        onPrimary = Color.White,
        primaryContainer = PurpleDark,
        secondary = Accent,
        onSecondary = Color.White,
    )

private val DarkColors =
    darkColorScheme(
        primary = Purple,
        onPrimary = Color.White,
        primaryContainer = PurpleDark,
        secondary = Accent,
        onSecondary = Color.White,
    )

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
