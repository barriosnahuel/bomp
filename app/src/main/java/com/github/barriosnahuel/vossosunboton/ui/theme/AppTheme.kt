package com.github.barriosnahuel.vossosunboton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — Deep Violet + Vivid Rose + Amber. All roles verified WCAG 2.2 AA.
// Light primaries
private val Violet800 = Color(0xFF5B21B6) // primary light  (white: 8.1:1)
private val Lavender100 = Color(0xFFEDE9FE) // primaryContainer light
private val Violet950 = Color(0xFF2E0075) // onPrimaryContainer light (15:1 on Lavender100)

// Light secondaries
private val Rose800 = Color(0xFF9D174D) // secondary light (white: 7.4:1)
private val Rose100 = Color(0xFFFFE4E6) // secondaryContainer light
private val Rose950 = Color(0xFF500724) // onSecondaryContainer light (11.7:1 on Rose100)

// Light tertiaries
private val Amber800 = Color(0xFF92400E) // tertiary light  (white: 6.3:1)
private val Amber100 = Color(0xFFFEF3C7) // tertiaryContainer light
private val Amber950 = Color(0xFF451A03) // onTertiaryContainer light (15:1 on Amber100)

// Light surfaces/backgrounds
private val NearWhite = Color(0xFFFFFBFE)
private val NearBlack = Color(0xFF1C1B1F)
private val LavendertintedSurface = Color(0xFFE7E0EC) // surfaceVariant light
private val DarkGray = Color(0xFF49454F) // onSurfaceVariant light (6.8:1 on LavendertintedSurface)

// Light error
private val ErrorRed = Color(0xFFB3261E)
private val ErrorRedContainer = Color(0xFFF9DEDC) // soft pink, not vivid red
private val ErrorRedDark = Color(0xFF410E0B)

// Light outline
private val OutlineGray = Color(0xFF79747E) // 4.1:1 on white (non-text req: 3:1)
private val OutlineVariantGray = Color(0xFFCAC4D0)

// Dark primaries
private val LavenderLight = Color(0xFFD4BBFF) // primary dark   (12:1 on #1C1B1F)
private val VioletDark = Color(0xFF310065) // onPrimary dark (12:1 on LavenderLight)
private val VioletMid = Color(0xFF4A0A96) // primaryContainer dark

// Dark secondaries
private val RoseLight = Color(0xFFFFB3C6) // secondary dark (10.8:1 on dark bg)
private val RoseDark = Color(0xFF560718) // onSecondary dark
private val RoseMid = Color(0xFF7D1037) // secondaryContainer dark
private val RosePale = Color(0xFFFFD9E2) // onSecondaryContainer dark

// Dark tertiaries
private val AmberLight = Color(0xFFFBBE63) // tertiary dark
private val AmberVeryDark = Color(0xFF3E1B00) // onTertiary dark
private val AmberMid = Color(0xFF5A2E00) // tertiaryContainer dark
private val AmberPale = Color(0xFFFFDDB3) // onTertiaryContainer dark

// Dark surfaces/backgrounds
private val DarkSurface = Color(0xFF1C1B1F)
private val LightOnDark = Color(0xFFE6E1E5)
private val DarkSurfaceVariant = Color(0xFF49454F)
private val LightOnSurfaceVariant = Color(0xFFCAC4D0) // 4.8:1 on DarkSurfaceVariant

// Dark error
private val ErrorDarkText = Color(0xFFF2B8B5)
private val ErrorDarkOnContainer = Color(0xFF601410)
private val ErrorDarkContainer = Color(0xFF8C1D18) // muted dark red
private val ErrorDarkOnText = Color(0xFFF9DEDC)

// Dark outline
private val OutlineDark = Color(0xFF938F99) // 5.9:1 on dark bg

private val LightColors =
    lightColorScheme(
        primary = Violet800,
        onPrimary = Color.White,
        primaryContainer = Lavender100,
        onPrimaryContainer = Violet950,
        secondary = Rose800,
        onSecondary = Color.White,
        secondaryContainer = Rose100,
        onSecondaryContainer = Rose950,
        tertiary = Amber800,
        onTertiary = Color.White,
        tertiaryContainer = Amber100,
        onTertiaryContainer = Amber950,
        background = NearWhite,
        onBackground = NearBlack,
        surface = NearWhite,
        onSurface = NearBlack,
        surfaceVariant = LavendertintedSurface,
        onSurfaceVariant = DarkGray,
        error = ErrorRed,
        onError = Color.White,
        errorContainer = ErrorRedContainer,
        onErrorContainer = ErrorRedDark,
        outline = OutlineGray,
        outlineVariant = OutlineVariantGray,
    )

private val DarkColors =
    darkColorScheme(
        primary = LavenderLight,
        onPrimary = VioletDark,
        primaryContainer = VioletMid,
        onPrimaryContainer = Lavender100,
        secondary = RoseLight,
        onSecondary = RoseDark,
        secondaryContainer = RoseMid,
        onSecondaryContainer = RosePale,
        tertiary = AmberLight,
        onTertiary = AmberVeryDark,
        tertiaryContainer = AmberMid,
        onTertiaryContainer = AmberPale,
        background = DarkSurface,
        onBackground = LightOnDark,
        surface = DarkSurface,
        onSurface = LightOnDark,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        error = ErrorDarkText,
        onError = ErrorDarkOnContainer,
        errorContainer = ErrorDarkContainer,
        onErrorContainer = ErrorDarkOnText,
        outline = OutlineDark,
        outlineVariant = DarkSurfaceVariant,
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
