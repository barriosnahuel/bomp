/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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

internal const val ONBOARDING_STEP_COUNT = 3

private val STAGE_RADIUS = 24.dp
private val CTA_HEIGHT = 56.dp
private val DOT_HEIGHT = 7.dp
private val DOT_ACTIVE_WIDTH = 22.dp

private data class OnboardingStepContent(
    val number: String,
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val cta: Int,
    @StringRes val demoDescription: Int,
)

private val ONBOARDING_STEPS =
    listOf(
        OnboardingStepContent(
            number = "01",
            eyebrow = R.string.app_onboarding_step1_eyebrow,
            title = R.string.app_onboarding_step1_title,
            body = R.string.app_onboarding_step1_body,
            cta = R.string.app_onboarding_step1_cta,
            demoDescription = R.string.app_onboarding_step1_demo_description,
        ),
        OnboardingStepContent(
            number = "02",
            eyebrow = R.string.app_onboarding_step2_eyebrow,
            title = R.string.app_onboarding_step2_title,
            body = R.string.app_onboarding_step2_body,
            cta = R.string.app_onboarding_step2_cta,
            demoDescription = R.string.app_onboarding_step2_demo_description,
        ),
        OnboardingStepContent(
            number = "03",
            eyebrow = R.string.app_onboarding_step3_eyebrow,
            title = R.string.app_onboarding_step3_title,
            body = R.string.app_onboarding_step3_body,
            cta = R.string.app_onboarding_step3_cta,
            demoDescription = R.string.app_onboarding_step3_demo_description,
        ),
    )

/**
 * On-demand onboarding tour — 3 skippable, re-openable steps, each a live demo of a real product
 * gesture (import in, swipe-to-pin, bomp out). Stateless: [step] and the navigation callbacks are
 * owned by the host (`LandingScreen`) so the step index survives an Activity recreate via the host's
 * `rememberSaveable`. There is no first-run auto-trigger and no "seen" persistence — the tour is
 * reachable any time from the Hub and the empty state. The final "start" CTA lands on the Hub.
 */
@Composable
internal fun OnboardingTour(
    step: Int,
    onAdvance: () -> Unit,
    onStepBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val safeStep = step.coerceIn(0, ONBOARDING_STEPS.lastIndex)
    val content = ONBOARDING_STEPS[safeStep]
    val isLast = safeStep == ONBOARDING_STEPS.lastIndex

    // Back steps the tour backwards; from the first step it dismisses (returns where the user was).
    BackHandler { if (safeStep > 0) onStepBack() else onSkip() }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.XL),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(Spacing.XXL + Spacing.XL),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OnboardingProgress(step = safeStep)
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.app_onboarding_skip))
                }
            }

            // The demo stage takes the flexible middle (shrinks on short screens / large fonts) so the
            // copy + acid CTA below stay pinned and reachable without a page scroll.
            DemoStage(
                step = safeStep,
                reduceMotion = reduceMotion,
                demoDescription = stringResource(content.demoDescription),
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(Spacing.LG))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = content.number,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.SM))
                Text(
                    text = stringResource(content.eyebrow).uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.SM))
            Text(
                text = stringResource(content.title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.MD))
            Text(
                text = stringResource(content.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.XL))
            // Filled-primary tier (ADR 0010): the single forward action of the screen. Tall acid pill.
            Button(
                onClick = { if (isLast) onFinish() else onAdvance() },
                modifier = Modifier.fillMaxWidth().height(CTA_HEIGHT),
                shape = RoundedCornerShape(percent = 50),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(
                    text = stringResource(content.cta),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isLast) {
                    Spacer(Modifier.width(Spacing.SM))
                    Icon(
                        painter = painterResource(R.drawable.app_ic_keyboard_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.height(Spacing.XL),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.XL))
        }
    }
}

/** The demo illustration framed in a hairline-bordered tonal card. Decorative: one merged description. */
@Composable
private fun DemoStage(
    step: Int,
    reduceMotion: Boolean,
    demoDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().heightIn(min = Spacing.XXL), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(STAGE_RADIUS))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(STAGE_RADIUS))
                    .padding(Spacing.LG)
                    .clearAndSetSemantics { contentDescription = demoDescription },
        ) {
            when (step) {
                0 -> OnboardingImportDemo(reduceMotion = reduceMotion)
                1 -> OnboardingOrganizeDemo(reduceMotion = reduceMotion)
                else -> OnboardingBompearDemo(reduceMotion = reduceMotion)
            }
        }
    }
}

@Composable
private fun OnboardingProgress(step: Int) {
    val description = stringResource(R.string.app_onboarding_progress_description, step + 1, ONBOARDING_STEP_COUNT)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(ONBOARDING_STEP_COUNT) { index ->
            val active = index == step
            Box(
                modifier =
                    Modifier
                        .height(DOT_HEIGHT)
                        .width(if (active) DOT_ACTIVE_WIDTH else DOT_HEIGHT)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
                        ),
            )
        }
    }
}
