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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsNavMethod
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsOnboardingStep
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.ui.rememberReduceMotionEnabled
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

internal const val ONBOARDING_STEP_COUNT = 3

// Test tags for the invisible "stories" tap zones (test-only metadata; not announced by TalkBack).
internal const val ONBOARDING_TAP_PREV = "onboarding_tap_prev"
internal const val ONBOARDING_TAP_NEXT = "onboarding_tap_next"

private val STAGE_RADIUS = 24.dp
private val CTA_HEIGHT = 56.dp
private val DOT_HEIGHT = 7.dp
private val DOT_ACTIVE_WIDTH = 22.dp

// Min height the demo keeps when the layout switches to its scrollable (landscape) variant.
private val DEMO_MIN_HEIGHT = 200.dp

private data class OnboardingStepContent(
    // Stable concept slug for analytics — survives a future reorder of the display positions, so the
    // funnel keys on "what the step teaches", not its (mutable) 1/2/3 index. NOT user-facing.
    val key: String,
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
            key = AnalyticsOnboardingStep.IMPORT,
            number = "01",
            eyebrow = R.string.app_onboarding_step1_eyebrow,
            title = R.string.app_onboarding_step1_title,
            body = R.string.app_onboarding_step1_body,
            cta = R.string.app_onboarding_step1_cta,
            demoDescription = R.string.app_onboarding_step1_demo_description,
        ),
        OnboardingStepContent(
            key = AnalyticsOnboardingStep.ORGANIZE,
            number = "02",
            eyebrow = R.string.app_onboarding_step2_eyebrow,
            title = R.string.app_onboarding_step2_title,
            body = R.string.app_onboarding_step2_body,
            cta = R.string.app_onboarding_step2_cta,
            demoDescription = R.string.app_onboarding_step2_demo_description,
        ),
        OnboardingStepContent(
            key = AnalyticsOnboardingStep.BOMPEAR,
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
    val context = LocalContext.current
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }

    val stepCount = ONBOARDING_STEPS.size

    // The method that triggered the upcoming step change, read by the step-viewed effect below.
    // Defaults to "open" so the first step view (on first composition) is attributed to the open.
    var pendingMethod by remember { mutableStateOf(AnalyticsNavMethod.OPEN) }
    // Emit step_viewed only when the step actually changes — never on a bare recompose or an Activity
    // recreate. `lastLoggedStep` is rememberSaveable so after a rotation/restore it already equals the
    // restored step and the effect skips, avoiding a phantom step_viewed mis-attributed to "open".
    var lastLoggedStep by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(safeStep) {
        if (safeStep != lastLoggedStep) {
            tracker.log(
                AnalyticsEvent.OnboardingStepViewed(
                    step = safeStep + 1,
                    stepKey = content.key,
                    stepCount = stepCount,
                    method = pendingMethod,
                ),
            )
            lastLoggedStep = safeStep
        }
    }

    // One-shot latch: a rapid double-tap (or button + tap) on the last step must not log two
    // Completed / two Dismissed. A fresh tour instance per open resets it naturally.
    var terminated by remember { mutableStateOf(false) }

    // Navigation helpers: each records the method (tap / button / back) so the funnel can compare
    // the "stories" gesture vs the explicit controls. Finishing logs Completed; dismissing logs
    // Dismissed with the step the user left from. The host owns the actual step state.
    fun advance(method: String) {
        pendingMethod = method
        onAdvance()
    }

    fun back(method: String) {
        pendingMethod = method
        onStepBack()
    }

    fun finish(method: String) {
        if (terminated) return
        terminated = true
        tracker.log(AnalyticsEvent.OnboardingCompleted(stepKey = content.key, stepCount = stepCount, method = method))
        onFinish()
    }

    fun dismiss(method: String) {
        if (terminated) return
        terminated = true
        tracker.log(
            AnalyticsEvent.OnboardingDismissed(
                step = safeStep + 1,
                stepKey = content.key,
                stepCount = stepCount,
                method = method,
            ),
        )
        onSkip()
    }

    // Back steps the tour backwards; from the first step it dismisses (returns where the user was).
    BackHandler { if (safeStep > 0) back(AnalyticsNavMethod.BACK) else dismiss(AnalyticsNavMethod.BACK) }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { innerPadding ->
        // Portrait has room to pin the CTA (the demo takes the flexible middle via weight). When the
        // window is short — landscape, split-screen, large fonts — switch to a scrollable column so the
        // copy + CTA stay reachable, matching the rest of the app (AboutScreen, the empty state). A
        // proper landscape / large-screen redesign is a separate epic (backlog future-06).
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Landscape (and other wide-but-short windows) can't fit the pinned-CTA layout, so it
            // becomes scrollable. Portrait keeps the demo's weight + pinned CTA. Orientation, not a
            // height threshold, so test viewports (portrait) keep the pinned path deterministically.
            val cramped = maxWidth > maxHeight
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (cramped) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight())
                        .padding(horizontal = Spacing.XL),
            ) {
                // Progress centered at the top — the "stories" convention (Instagram / WhatsApp): the
                // top-left is mentally reserved for back, so a left-aligned indicator there reads as a
                // control. Skip stays top-right, overlaid on the same row.
                Box(
                    modifier = Modifier.fillMaxWidth().height(Spacing.XXL + Spacing.XL),
                    contentAlignment = Alignment.Center,
                ) {
                    OnboardingProgress(step = safeStep)
                    TextButton(
                        onClick = { dismiss(AnalyticsNavMethod.BUTTON) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Text(stringResource(R.string.app_onboarding_skip))
                    }
                }

                // The demo stage takes the flexible middle (shrinks on short screens / large fonts) so the
                // copy + acid CTA below stay pinned and reachable without a page scroll. Two invisible
                // "stories" tap halves sit over it: tap the left side → previous step, right side → next
                // (or finish off the last step). They're inside the 24dp content padding, so their outer
                // edge clears the system back-gesture zone; taps (not swipes) never trigger system back.
                Box(
                    modifier =
                        if (cramped) {
                            Modifier.fillMaxWidth().heightIn(min = DEMO_MIN_HEIGHT)
                        } else {
                            Modifier.weight(1f).fillMaxWidth()
                        },
                ) {
                    DemoStage(
                        step = safeStep,
                        reduceMotion = reduceMotion,
                        demoDescription = stringResource(content.demoDescription),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(ONBOARDING_TAP_PREV)
                                    .semantics(mergeDescendants = true) {}
                                    .pointerInput(safeStep) {
                                        detectTapGestures { if (safeStep > 0) back(AnalyticsNavMethod.TAP) }
                                    },
                        )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(ONBOARDING_TAP_NEXT)
                                    .semantics(mergeDescendants = true) {}
                                    .pointerInput(safeStep, isLast) {
                                        detectTapGestures {
                                            if (isLast) finish(AnalyticsNavMethod.TAP) else advance(AnalyticsNavMethod.TAP)
                                        }
                                    },
                        )
                    }
                }

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
                    onClick = { if (isLast) finish(AnalyticsNavMethod.BUTTON) else advance(AnalyticsNavMethod.BUTTON) },
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
