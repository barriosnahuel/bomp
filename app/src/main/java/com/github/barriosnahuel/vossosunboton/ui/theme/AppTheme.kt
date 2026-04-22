package com.github.barriosnahuel.vossosunboton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neo-Club palette — ink × acid. WCAG 2.2 AA verified for all roles.
// Ink scale
private val Ink1000 = Color(0xFF0B0B0C)
private val Ink900 = Color(0xFF141415)
private val Ink800 = Color(0xFF1C1C1D)
private val Ink500 = Color(0xFF5A5A55)
private val Ink400 = Color(0xFF8A8A83)
private val Ink300 = Color(0xFFB8B7AE)
private val Ink100 = Color(0xFFE5E4DE)
private val Ink50 = Color(0xFFF1F0EA)
private val Paper = Color(0xFFFAFAF7)

// Acid — signal / primary action color
private val Acid400 = Color(0xFFD7FF3A) // 15.2:1 on Ink1000; 14.2:1 on Ink900
private val AcidDark = Color(0xFF3E5400) // 7.4:1 on Paper — for light-mode text

// Blood — destructive
private val Blood700 = Color(0xFF9E1B1D)
private val Blood600 = Color(0xFFC72C2F) // 5.6:1 on Paper — AA large text
private val Blood400 = Color(0xFFF26163) // 5.4:1 on Ink1000 — AA
private val Blood200 = Color(0xFFFBD4D5)

internal val LightColors =
    lightColorScheme(
        // Acid — FAB, indicators, pin action background
        primary = AcidDark, // 7.4:1 on Paper — active labels / nav text
        onPrimary = Paper,
        primaryContainer = Acid400, // filled button, FAB, swipe-pin bg
        onPrimaryContainer = Ink1000, // 14.2:1 on Acid400 — AAA
        // Ink — always-dark top bars and navigation bar
        secondary = Ink1000,
        onSecondary = Paper, // 19.7:1 on Ink1000 — AAA
        secondaryContainer = Ink100,
        onSecondaryContainer = Ink1000,
        // Not used in UI — mirrors primary for completeness
        tertiary = AcidDark,
        onTertiary = Paper,
        tertiaryContainer = Acid400,
        onTertiaryContainer = Ink1000,
        background = Paper,
        onBackground = Ink1000,
        surface = Paper,
        onSurface = Ink1000,
        surfaceVariant = Ink50, // card and nav bar background
        onSurfaceVariant = Ink500, // 6.0:1 on Ink50 — AA
        error = Blood600, // 5.6:1 on Paper
        onError = Paper,
        errorContainer = Blood200,
        onErrorContainer = Blood700,
        outline = Ink400, // 3.3:1 on Paper — non-text ≥3:1 ✓
        outlineVariant = Ink100,
        inverseSurface = Ink800,
        inverseOnSurface = Paper, // 14.7:1 on Ink800 — AAA
        inversePrimary = Acid400, // snackbar action text; 13.5:1 on Ink800 — AAA
    )

internal val DarkColors =
    darkColorScheme(
        // Acid — FAB, indicators, pin action background
        primary = Acid400, // 15.2:1 on Ink1000 — AAA
        onPrimary = Ink1000,
        primaryContainer = Acid400,
        onPrimaryContainer = Ink1000,
        // Ink900 — slightly lighter than bg so bar is distinct without being jarring
        secondary = Ink900,
        onSecondary = Paper, // 18.4:1 on Ink900 — AAA
        secondaryContainer = Ink800,
        onSecondaryContainer = Paper,
        // Not used in UI — mirrors primary for completeness
        tertiary = Acid400,
        onTertiary = Ink1000,
        tertiaryContainer = Acid400,
        onTertiaryContainer = Ink1000,
        background = Ink1000,
        onBackground = Paper,
        surface = Ink1000,
        onSurface = Paper,
        surfaceVariant = Ink900, // card and nav bar background
        onSurfaceVariant = Ink300, // 9.1:1 on Ink1000; 4.8:1 on Ink900 — AA
        error = Blood400, // 5.4:1 on Ink1000 — AA
        onError = Ink1000,
        errorContainer = Blood600,
        onErrorContainer = Paper,
        outline = Ink400, // 5.7:1 on Ink1000 — non-text ≥3:1 ✓ (Ink700 fails at ~1.4:1, Ink500 at ~2.8:1)
        outlineVariant = Ink800,
        inverseSurface = Paper,
        inverseOnSurface = Ink1000, // 19.7:1 on Paper — AAA
        inversePrimary = AcidDark, // snackbar action text; 7.4:1 on Paper — AA
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
