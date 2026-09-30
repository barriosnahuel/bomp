/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.rememberReduceMotionEnabled
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

// Below this content height the flexing demo (min DEMO_MIN_HEIGHT) plus the headline no longer fit, so
// the body switches to the scrollable column.
private val GUIDE_MIN_FLEX_HEIGHT = 420.dp

// Below this window height the action bar scrolls with the lesson instead of staying pinned.
private val GUIDE_MIN_PINNED_BAR_HEIGHT = 360.dp

/**
 * Focused single-step guide reached from the Hub's "bring in an audio you already have" row. Reuses the
 * onboarding IMPORT step's content ([ONBOARDING_IMPORT_STEP]) and shared chrome ([DemoStage],
 * [OnboardingStepHeadline], [OnboardingPrimaryCta]) so the lesson stays in sync with the full tour —
 * but without the tour's navigation machinery (no progress dots, no story tap-halves, no step funnel
 * analytics). The CTA is terminal ("Got it") rather than the tour's "Go on".
 *
 * The bottom bar pins the CTA plus a secondary "look on your phone" action ([onBrowseFiles]) that opens
 * the system file browser, with its reach spelled out (downloads, music, recordings, Drive). The bar is
 * the Scaffold's `bottomBar`, so the snackbar host sits above it and never covers either action — except
 * in a very short window, where the bar scrolls inline with the lesson instead.
 *
 * Stateless: [onClose] is owned by the host (`LandingScreen`), which also emits the BRING_GUIDE
 * screen_view and owns the picker. While [showEmptyResultNotice] is true the guide shows the
 * "wasn't it there?" snackbar — no action, since the user is already on the screen that explains the
 * share path — and calls [onEmptyResultNoticeShown] once it leaves the screen. Back closes the guide,
 * returning the user where they were.
 */
@Composable
internal fun BringFromAppsGuide(
    onClose: () -> Unit,
    onBrowseFiles: () -> Unit,
    showEmptyResultNotice: Boolean = false,
    onEmptyResultNoticeShown: () -> Unit = {},
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val step = ONBOARDING_IMPORT_STEP
    val snackbarHostState = remember { SnackbarHostState() }
    val emptyResultMessage = stringResource(R.string.app_import_empty_message)
    val currentOnNoticeShown by rememberUpdatedState(onEmptyResultNoticeShown)

    // Keyed on the flag, not on a one-shot event: a recreate while the snackbar is up restarts this
    // effect with the flag still set (the host saves it), so the notice comes back instead of vanishing.
    LaunchedEffect(showEmptyResultNotice) {
        if (showEmptyResultNotice) {
            snackbarHostState.showSnackbar(message = emptyResultMessage, duration = SnackbarDuration.Long)
            currentOnNoticeShown()
        }
    }

    // A window too short to spare the bar's height (split-screen, large fonts in landscape) stops pinning
    // it: the actions scroll with the lesson instead of the bar eating the whole window.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pinBar = maxHeight >= GUIDE_MIN_PINNED_BAR_HEIGHT
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (pinBar) GuideActionBar(onClose = onClose, onBrowseFiles = onBrowseFiles, padForNavigationBar = true)
            },
        ) { innerPadding ->
            // Same cramped-window handling as OnboardingTour, plus a height floor: the pinned action bar takes
            // height the tour doesn't, so a short portrait window (small phone, large fonts) scrolls too instead
            // of squeezing the headline out. Otherwise portrait lets the demo flex via weight.
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val cramped = !pinBar || maxWidth > maxHeight || maxHeight < GUIDE_MIN_FLEX_HEIGHT
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (cramped) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight()),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (cramped) Modifier else Modifier.weight(1f))
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
                    }
                    if (!pinBar) GuideActionBar(onClose = onClose, onBrowseFiles = onBrowseFiles, padForNavigationBar = false)
                }
            }
        }
    }
}

/**
 * The guide's action bar: the terminal CTA, then a divider and the Text-tier (ADR 0010) file-browser
 * action. A `surface` container + top divider frames it as a zone of action over the content, as in
 * `VaultUnlockCta`. Pinned as the Scaffold `bottomBar` it gets no insets, so [padForNavigationBar] makes
 * it pad for the navigation bar itself; scrolled inline, the content padding already covers it.
 */
@Composable
private fun GuideActionBar(
    onClose: () -> Unit,
    onBrowseFiles: () -> Unit,
    padForNavigationBar: Boolean,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().then(if (padForNavigationBar) Modifier.navigationBarsPadding() else Modifier)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.XL, vertical = Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                OnboardingPrimaryCta(
                    text = stringResource(R.string.app_hub_bring_guide_cta),
                    showTrailingArrow = false,
                    onClick = onClose,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onBrowseFiles) {
                        Icon(
                            painter = painterResource(R.drawable.app_ic_folder),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.SM))
                        Column {
                            Text(text = stringResource(R.string.app_hub_files_cta))
                            Text(
                                text = stringResource(R.string.app_hub_files_scope),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
