/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.rememberReduceMotionEnabled
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Focused single-step guide reached from the Hub's "bring audios from other apps" row. Reuses the
 * onboarding IMPORT step's content ([ONBOARDING_IMPORT_STEP]) and shared chrome ([DemoStage],
 * [OnboardingStepHeadline], [OnboardingPrimaryCta]) so the lesson stays in sync with the full tour —
 * but without the tour's navigation machinery (no progress dots, no story tap-halves, no step funnel
 * analytics). The CTA is terminal ("Got it") rather than the tour's "Go on".
 *
 * Stateless: [onClose] is owned by the host (`LandingScreen`), which also emits the BRING_GUIDE
 * screen_view and clears its visibility flag. Back closes the guide, returning the user where they were.
 */
@Composable
internal fun BringFromAppsGuide(onClose: () -> Unit) {
    val reduceMotion = rememberReduceMotionEnabled()
    val step = ONBOARDING_IMPORT_STEP
    BackHandler { onClose() }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { innerPadding ->
        // Same cramped-window handling as OnboardingTour: portrait pins the CTA (the demo flexes via
        // weight); landscape / split-screen / large fonts switch to a fully scrollable column so the
        // copy + terminal CTA stay reachable instead of overflowing off-screen.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val cramped = maxWidth > maxHeight
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (cramped) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight())
                        .padding(horizontal = Spacing.XL, vertical = Spacing.XL),
            ) {
                DemoStage(
                    step = 0,
                    reduceMotion = reduceMotion,
                    demoDescription = stringResource(step.demoDescription),
                    modifier =
                        if (cramped) {
                            Modifier.fillMaxWidth().heightIn(min = DEMO_MIN_HEIGHT)
                        } else {
                            Modifier.weight(1f).fillMaxWidth()
                        },
                )
                Spacer(Modifier.height(Spacing.LG))
                OnboardingStepHeadline(content = step, leadingNumber = null)
                Spacer(Modifier.height(Spacing.XL))
                OnboardingPrimaryCta(
                    text = stringResource(R.string.app_hub_bring_guide_cta),
                    showTrailingArrow = false,
                    onClick = onClose,
                )
            }
        }
    }
}
