/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// Onboarding step 01 ("Ilustración de marca") needs one color that is NOT a Push Me semantic role:
// the WhatsApp green that signals "this audio lives in another app" (the real chromatic cue that
// makes the two-zones illustration read). It lives here, in the theme package, for two reasons:
//  1. It is a brand/illustration color, so the color-literal guard (which exempts ui/theme/) lets it
//     exist only here — components must receive it, never inline a hex.
//  2. The bright WhatsApp green passes WCAG AA on the near-black dark surface but FAILS on the light
//     paper surface, so the accent must change per mode. Keeping `isSystemInDarkTheme()` here (not in
//     the component) honors § Design system "never add isDark in component files".
//
// Both accents are AA-verified against their own (zone-tinted) background by OnboardingContrastTest.
internal val WhatsAppGreenBright = Color(0xFF25D366) // 8.8:1 on Ink1000 zone — dark-mode accent + zone tint
internal val WhatsAppGreenDeep = Color(0xFF075E54) // 6.6:1 on Paper zone — WhatsApp's brand teal, light-mode accent

// Subtle so the zone reads as a tint, never a filled control. Bright green in both modes (decorative,
// keeps the "WhatsApp" association); the AA-required accent (border/label/wave) is what adapts.
internal const val FOREIGN_ZONE_ALPHA = 0.12f

/** Per-mode colors for the foreign-app (WhatsApp) zone of onboarding step 01. */
internal data class OnboardingIllustrationColors(
    val foreignAccent: Color,
    val foreignZone: Color,
)

@Composable
internal fun rememberOnboardingIllustrationColors(): OnboardingIllustrationColors {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        OnboardingIllustrationColors(
            foreignAccent = if (dark) WhatsAppGreenBright else WhatsAppGreenDeep,
            foreignZone = WhatsAppGreenBright.copy(alpha = FOREIGN_ZONE_ALPHA),
        )
    }
}
