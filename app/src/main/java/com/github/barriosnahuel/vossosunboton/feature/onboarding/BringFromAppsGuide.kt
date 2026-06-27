/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.rememberReduceMotionEnabled
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

private val STAGE_RADIUS = 24.dp
private val CTA_HEIGHT = 56.dp

/**
 * Focused single-step guide reached from the Hub's "bring audios from other apps" row. Reuses the
 * onboarding IMPORT step's content (title/body) and its live share-in demo ([OnboardingImportDemo]) so
 * the lesson stays in sync with the full tour — but without the tour's navigation machinery (no progress
 * dots, no story tap-halves, no step funnel analytics). The CTA is terminal ("Got it") rather than the
 * tour's "Go on", since there is nothing after it.
 *
 * Stateless: [onClose] is owned by the host (`LandingScreen`), which also emits the ONBOARDING
 * screen_view and clears its visibility flag. Back closes the guide, returning the user where they were.
 */
@Composable
internal fun BringFromAppsGuide(onClose: () -> Unit) {
    val reduceMotion = rememberReduceMotionEnabled()
    BackHandler { onClose() }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { innerPadding ->
        // The terminal CTA stays pinned at the bottom (always reachable); the demo + copy scroll above it
        // so they survive short windows, large fonts and split-screen. Mirrors the tour's portrait layout.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.XL, vertical = Spacing.XL),
        ) {
            // The demo takes the flexible top (shrinks / scrolls within on short windows) so the lesson
            // copy + terminal CTA below stay visible without a page scroll, mirroring the tour's stage.
            // Decorative animation: one merged description for TalkBack (the IMPORT step's demo copy).
            val demoDescription = stringResource(R.string.app_onboarding_step1_demo_description)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(STAGE_RADIUS))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(STAGE_RADIUS))
                        .padding(Spacing.LG)
                        .clearAndSetSemantics {
                            contentDescription = demoDescription
                        },
            ) {
                OnboardingImportDemo(reduceMotion = reduceMotion)
            }
            Spacer(Modifier.height(Spacing.LG))
            Text(
                text = stringResource(R.string.app_onboarding_step1_eyebrow).uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.SM))
            Text(
                text = stringResource(R.string.app_onboarding_step1_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.MD))
            Text(
                text = stringResource(R.string.app_onboarding_step1_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.XL))
            // Filled-primary tier (ADR 0010): the single, terminal action of the screen. Tall acid pill.
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(CTA_HEIGHT),
                shape = RoundedCornerShape(percent = 50),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.app_hub_bring_guide_cta),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
