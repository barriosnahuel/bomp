/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Verifies every critical color pair in the Neo-Club palette meets WCAG 2.2 AA contrast ratios.
 *
 * Thresholds:
 *   ≥ 4.5:1 — normal text (body, labels, icons conveying meaning)
 *   ≥ 3.0:1 — large text and non-text UI components (borders, focus indicators)
 *
 * If a test fails here, the AppTheme.kt change that caused it broke an accessibility contract.
 * Fix the palette mapping; do not lower the threshold.
 */
internal class AppThemeContrastTest {
    // --- Light mode ---

    @Test
    fun `light onSurface on surface body text passes AA`() {
        assertTextAA(LightColors.onSurface, LightColors.surface, "light: onSurface / surface")
    }

    @Test
    fun `light primary on surface active labels pass AA`() {
        assertTextAA(LightColors.primary, LightColors.surface, "light: primary / surface")
    }

    @Test
    fun `light onPrimaryContainer on primaryContainer button text passes AA`() {
        assertTextAA(LightColors.onPrimaryContainer, LightColors.primaryContainer, "light: onPrimaryContainer / primaryContainer")
    }

    @Test
    fun `light onSecondary on secondary top-bar title passes AA`() {
        assertTextAA(LightColors.onSecondary, LightColors.secondary, "light: onSecondary / secondary")
    }

    @Test
    fun `light onSurfaceVariant on surfaceVariant card muted text passes AA`() {
        assertTextAA(LightColors.onSurfaceVariant, LightColors.surfaceVariant, "light: onSurfaceVariant / surfaceVariant")
    }

    @Test
    fun `light onError on error error text passes AA`() {
        assertTextAA(LightColors.onError, LightColors.error, "light: onError / error")
    }

    @Test
    fun `light onErrorContainer on errorContainer swipe-delete passes AA`() {
        assertTextAA(LightColors.onErrorContainer, LightColors.errorContainer, "light: onErrorContainer / errorContainer")
    }

    @Test
    fun `light inverseOnSurface on inverseSurface snackbar body passes AA`() {
        assertTextAA(LightColors.inverseOnSurface, LightColors.inverseSurface, "light: inverseOnSurface / inverseSurface")
    }

    @Test
    fun `light inversePrimary on inverseSurface snackbar action passes AA`() {
        assertTextAA(LightColors.inversePrimary, LightColors.inverseSurface, "light: inversePrimary / inverseSurface")
    }

    @Test
    fun `light primaryContainer on secondary search accent on dark bar passes non-text AA`() {
        assertNonTextAA(LightColors.primaryContainer, LightColors.secondary, "light: primaryContainer / secondary")
    }

    @Test
    fun `light outline on surface input border passes non-text AA`() {
        assertNonTextAA(LightColors.outline, LightColors.surface, "light: outline / surface")
    }

    @Test
    fun `light primary on surfaceVariant pin icon on card passes non-text AA`() {
        assertNonTextAA(LightColors.primary, LightColors.surfaceVariant, "light: primary / surfaceVariant")
    }

    // --- Dark mode ---

    @Test
    fun `dark onSurface on surface body text passes AA`() {
        assertTextAA(DarkColors.onSurface, DarkColors.surface, "dark: onSurface / surface")
    }

    @Test
    fun `dark primary on surface active labels pass AA`() {
        assertTextAA(DarkColors.primary, DarkColors.surface, "dark: primary / surface")
    }

    @Test
    fun `dark onPrimaryContainer on primaryContainer button text passes AA`() {
        assertTextAA(DarkColors.onPrimaryContainer, DarkColors.primaryContainer, "dark: onPrimaryContainer / primaryContainer")
    }

    @Test
    fun `dark onSecondary on secondary top-bar title passes AA`() {
        assertTextAA(DarkColors.onSecondary, DarkColors.secondary, "dark: onSecondary / secondary")
    }

    @Test
    fun `dark onSurfaceVariant on surfaceVariant card muted text passes AA`() {
        assertTextAA(DarkColors.onSurfaceVariant, DarkColors.surfaceVariant, "dark: onSurfaceVariant / surfaceVariant")
    }

    @Test
    fun `dark onError on error error text passes AA`() {
        assertTextAA(DarkColors.onError, DarkColors.error, "dark: onError / error")
    }

    @Test
    fun `dark onErrorContainer on errorContainer swipe-delete passes AA`() {
        assertTextAA(DarkColors.onErrorContainer, DarkColors.errorContainer, "dark: onErrorContainer / errorContainer")
    }

    @Test
    fun `dark inverseOnSurface on inverseSurface snackbar body passes AA`() {
        assertTextAA(DarkColors.inverseOnSurface, DarkColors.inverseSurface, "dark: inverseOnSurface / inverseSurface")
    }

    @Test
    fun `dark inversePrimary on inverseSurface snackbar action passes AA`() {
        assertTextAA(DarkColors.inversePrimary, DarkColors.inverseSurface, "dark: inversePrimary / inverseSurface")
    }

    @Test
    fun `dark primaryContainer on secondary search accent on dark bar passes non-text AA`() {
        assertNonTextAA(DarkColors.primaryContainer, DarkColors.secondary, "dark: primaryContainer / secondary")
    }

    @Test
    fun `dark outline on surface input border passes non-text AA`() {
        assertNonTextAA(DarkColors.outline, DarkColors.surface, "dark: outline / surface")
    }

    @Test
    fun `dark primary on surfaceVariant pin icon on card passes non-text AA`() {
        assertNonTextAA(DarkColors.primary, DarkColors.surfaceVariant, "dark: primary / surfaceVariant")
    }

    // --- About screen pairs ---

    @Test
    fun `light onSurface on surfaceVariant About card name passes AA`() {
        assertTextAA(LightColors.onSurface, LightColors.surfaceVariant, "light: onSurface / surfaceVariant")
    }

    @Test
    fun `dark onSurface on surfaceVariant About card name passes AA`() {
        assertTextAA(DarkColors.onSurface, DarkColors.surfaceVariant, "dark: onSurface / surfaceVariant")
    }

    @Test
    fun `light onSurfaceVariant on background About muted text passes AA`() {
        assertTextAA(LightColors.onSurfaceVariant, LightColors.background, "light: onSurfaceVariant / background")
    }

    @Test
    fun `dark onSurfaceVariant on background About muted text passes AA`() {
        assertTextAA(DarkColors.onSurfaceVariant, DarkColors.background, "dark: onSurfaceVariant / background")
    }

    // --- Onboarding tour muted text (eyebrow + body on the Scaffold `surface`) ---

    @Test
    fun `light onSurfaceVariant on surface onboarding text passes AA`() {
        assertTextAA(LightColors.onSurfaceVariant, LightColors.surface, "light: onSurfaceVariant / surface")
    }

    @Test
    fun `dark onSurfaceVariant on surface onboarding text passes AA`() {
        assertTextAA(DarkColors.onSurfaceVariant, DarkColors.surface, "dark: onSurfaceVariant / surface")
    }

    // --- Onboarding step 01/02 illustrations — zone accents over their own tint, on the stage ---
    // The translucent zone tints composite over the DemoStage's `surfaceVariant` background (not bare
    // surface), so the assertions use surfaceVariant as the base — the real render stack. The
    // foreign-app (WhatsApp) accent reads as text (label) + a 2dp border/wave and must meet ≥4.5:1:
    // bright green clears it on the near-black dark stage but fails on the light one, where it darkens
    // to WhatsApp's brand teal — both proven so rememberOnboardingIllustrationColors can't regress.

    @Test
    fun `dark foreign-app accent on its green zone passes AA`() {
        val zone = WhatsAppGreenBright.copy(alpha = FOREIGN_ZONE_ALPHA).compositeOver(DarkColors.surfaceVariant)
        assertTextAA(WhatsAppGreenBright, zone, "dark: WhatsApp accent / green zone")
    }

    @Test
    fun `light foreign-app accent on its green zone passes AA`() {
        val zone = WhatsAppGreenBright.copy(alpha = FOREIGN_ZONE_ALPHA).compositeOver(LightColors.surfaceVariant)
        assertTextAA(WhatsAppGreenDeep, zone, "light: WhatsApp accent / green zone")
    }

    @Test
    fun `dark Bomp accent on its acid zone passes AA`() {
        val zone = DarkColors.primaryContainer.copy(alpha = BOMP_ZONE_ALPHA).compositeOver(DarkColors.surfaceVariant)
        assertTextAA(DarkColors.primary, zone, "dark: Bomp accent / acid zone")
    }

    @Test
    fun `light Bomp accent on its acid zone passes AA`() {
        val zone = LightColors.primaryContainer.copy(alpha = BOMP_ZONE_ALPHA).compositeOver(LightColors.surfaceVariant)
        assertTextAA(LightColors.primary, zone, "light: Bomp accent / acid zone")
    }

    // The step-02 neutral collection folder's 2dp `outline` border separates a `surface` card from the
    // `surfaceVariant` stage — guard the thinner of the two adjacencies (outline / surfaceVariant).

    @Test
    fun `light neutral folder border on stage passes non-text AA`() {
        assertNonTextAA(LightColors.outline, LightColors.surfaceVariant, "light: outline / surfaceVariant (folder border)")
    }

    @Test
    fun `dark neutral folder border on stage passes non-text AA`() {
        assertNonTextAA(DarkColors.outline, DarkColors.surfaceVariant, "dark: outline / surfaceVariant (folder border)")
    }

    // --- MySoundsEmptyState ripple pairs ---
    // Lock the inner-ring composite (primary @ INNER_RIPPLE_ALPHA over surface) to ≥3:1 non-text
    // in both modes. Guards against future alpha drift in MySoundsEmptyState.kt.

    @Test
    fun `light primary at inner-ripple alpha on surface passes non-text AA`() {
        assertNonTextAA(
            LightColors.primary.copy(alpha = INNER_RIPPLE_ALPHA_FOR_TEST).compositeOver(LightColors.surface),
            LightColors.surface,
            "light: primary @α=$INNER_RIPPLE_ALPHA_FOR_TEST / surface (MySoundsEmptyState inner ring)",
        )
    }

    @Test
    fun `dark primary at inner-ripple alpha on surface passes non-text AA`() {
        assertNonTextAA(
            DarkColors.primary.copy(alpha = INNER_RIPPLE_ALPHA_FOR_TEST).compositeOver(DarkColors.surface),
            DarkColors.surface,
            "dark: primary @α=$INNER_RIPPLE_ALPHA_FOR_TEST / surface (MySoundsEmptyState inner ring)",
        )
    }
}

private const val INNER_RIPPLE_ALPHA_FOR_TEST = 0.85f

private fun Color.compositeOver(bg: Color): Color =
    Color(
        red = red * alpha + bg.red * (1f - alpha),
        green = green * alpha + bg.green * (1f - alpha),
        blue = blue * alpha + bg.blue * (1f - alpha),
        alpha = 1f,
    )

private fun assertTextAA(
    fg: Color,
    bg: Color,
    label: String,
) {
    assertContrast(fg = fg, bg = bg, minRatio = 4.5f, label = label)
}

private fun assertNonTextAA(
    fg: Color,
    bg: Color,
    label: String,
) {
    assertContrast(fg = fg, bg = bg, minRatio = 3.0f, label = label)
}

private fun assertContrast(
    fg: Color,
    bg: Color,
    minRatio: Float,
    label: String,
) {
    val ratio = contrastRatio(fg, bg)
    val msg = "WCAG contrast [$label]: got ${"%.2f".format(ratio)}:1, need ≥ ${"%.1f".format(minRatio)}:1"
    assertWithMessage(msg).that(ratio).isAtLeast(minRatio)
}

private fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val l1 = maxOf(a.luminance(), b.luminance())
    val l2 = minOf(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}
