/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:Suppress("TooManyFunctions")

package com.github.barriosnahuel.vossosunboton.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

// Live-gesture demos for the onboarding tour. Each step paints a scaled-down slice of the product's
// own UI with a gesture indicator on top — the anti-carousel stance from the design (teach the real,
// non-obvious gesture, not a marketing slide). Decorative to the screen reader: the hosting step
// stage carries one contentDescription and clears these descendants from the semantics tree, so every
// glyph here is intentionally null-described.
private val MINI_CARD_RADIUS = 14.dp
private val TILE = 44.dp
private val TILE_RADIUS = 12.dp
private val HAIRLINE = 1.dp
private val WAVE = listOf(4, 8, 13, 7, 11, 15, 9, 5, 12, 8, 14, 6, 10, 5)

// Touch-dot pulse — one expanding-ring cycle. Held static (mid-pulse) under reduce-motion.
private const val RING_PULSE_MS = 1600
private const val RING_SCALE_FROM = 0.5f
private const val RING_SCALE_TO = 1.9f
private const val RING_ALPHA_FROM = 0.9f
private const val RING_ALPHA_TO = 0f
private const val RING_STATIC_SCALE = 1.4f
private const val RING_STATIC_ALPHA = 0.5f

// The second, non-pinned card in the organize demo sits behind the gesture, dimmed.
private const val DIMMED_CARD_ALPHA = 0.5f

/** Mono metadata is rendered ALL CAPS per the design's casing rule; locale-uppercased from a resource. */
@Composable
private fun stringResourceUpper(
    @StringRes res: Int,
): String = stringResource(res).uppercase()

/** A finger-press indicator: a filled acid dot with an expanding acid ring (static under reduce-motion). */
@Composable
private fun TouchDot(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.primaryContainer
    val ringScale: Float
    val ringAlpha: Float
    if (reduceMotion) {
        ringScale = RING_STATIC_SCALE
        ringAlpha = RING_STATIC_ALPHA
    } else {
        val transition = rememberInfiniteTransition(label = "ob-touch")
        val scale by transition.animateFloat(
            initialValue = RING_SCALE_FROM,
            targetValue = RING_SCALE_TO,
            animationSpec = infiniteRepeatable(tween(RING_PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "ob-ring-scale",
        )
        val alpha by transition.animateFloat(
            initialValue = RING_ALPHA_FROM,
            targetValue = RING_ALPHA_TO,
            animationSpec = infiniteRepeatable(tween(RING_PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "ob-ring-alpha",
        )
        ringScale = scale
        ringAlpha = alpha
    }
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        this.alpha = ringAlpha
                    }.border(2.dp, dotColor, RoundedCornerShape(percent = 50)),
        )
        Box(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(percent = 50)).background(dotColor))
    }
}

/** A short mono caption (e.g. "0:08", "SHARE WITH") in the acid-on-ink editorial voice. */
@Composable
private fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Flow indicator — a chevron tinted acid, rotated to point along the gesture/audio path. */
@Composable
private fun FlowChevron(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(R.drawable.app_ic_keyboard_arrow_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.rotate(rotationDegrees),
    )
}

/** A destination/source tile in a share sheet. [highlight] = the Bomp tile (acid), the rest neutral. */
@Composable
private fun ShareTile(
    label: String,
    highlight: Boolean,
    glyph: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(TILE)
                    .clip(RoundedCornerShape(TILE_RADIUS))
                    .background(
                        if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ).border(
                        HAIRLINE,
                        if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(TILE_RADIUS),
                    ),
            contentAlignment = Alignment.Center,
        ) { glyph() }
        Spacer(Modifier.height(Spacing.XS))
        MonoLabel(label)
    }
}

/** A scaled-down My Bomps card. [highlightShare] wraps the share glyph in an acid pill (step 3). */
@Composable
private fun MiniCard(
    name: String,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    showShare: Boolean = false,
    highlightShare: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MINI_CARD_RADIUS))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    HAIRLINE,
                    if (highlightShare) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(MINI_CARD_RADIUS),
                ).padding(horizontal = Spacing.MD, vertical = Spacing.MD),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pinned) {
                    Icon(
                        imageVector = AppIcons.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (showShare) {
                    Box(
                        modifier =
                            Modifier
                                .padding(start = Spacing.SM)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (highlightShare) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ).padding(Spacing.XS),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.app_ic_share),
                            contentDescription = null,
                            tint =
                                if (highlightShare) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.SM))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_play_arrow),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.SM))
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/** A small waveform strip — the editorial stand-in for a received voice note. */
@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WAVE.forEach { h ->
            Box(
                modifier =
                    Modifier
                        .padding(end = 2.dp)
                        .width(2.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

// ── Step 1 — SUMAR: a voice note arrives from another app; Bomp is the highlighted destination, the
// audio travels INTO it. Mirror of step 3 (where Bomp is the source). ───────────────────────────────
@Composable
internal fun OnboardingImportDemo(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Received, long-pressed (selected) voice note — acid hairline = "selected".
        Box(contentAlignment = Alignment.CenterEnd) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                        .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_play_arrow),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.SM))
                Waveform()
                Spacer(Modifier.width(Spacing.SM))
                MonoLabel("0:08")
            }
            TouchDot(reduceMotion = reduceMotion, modifier = Modifier.offset(x = (-12).dp))
        }
        FlowChevron(rotationDegrees = 90f, modifier = Modifier.padding(vertical = Spacing.XS).size(26.dp))
        // System share sheet — Bomp is the highlighted destination the audio lands in.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(HAIRLINE, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(Spacing.MD),
        ) {
            MonoLabel(stringResourceUpper(R.string.app_onboarding_share_with))
            Spacer(Modifier.height(Spacing.SM))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ShareTile(label = stringResourceUpper(R.string.app_onboarding_dest_bomp), highlight = true) {
                    Box(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                }
                ShareTile(label = stringResourceUpper(R.string.app_onboarding_dest_chat), highlight = false) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_share),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                ShareTile(label = stringResourceUpper(R.string.app_onboarding_dest_more), highlight = false) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_more_vert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Step 2 — GUARDAR: swipe a card to pin it; collections + the Baúl below. ──────────────────────────
@Composable
internal fun OnboardingOrganizeDemo(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
            DemoChip(label = stringResource(R.string.app_onboarding_chip_all), active = true)
            DemoChip(label = stringResource(R.string.app_onboarding_chip_collection), active = false)
            DemoChip(label = stringResource(R.string.app_vault_baul_name), active = false, locked = true)
        }
        Spacer(Modifier.height(Spacing.MD))
        // Swipe-to-pin: the acid pin reveal peeks from the left as the card slides right.
        Box {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(MINI_CARD_RADIUS))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = AppIcons.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = Spacing.LG).size(20.dp),
                )
            }
            MiniCard(name = stringResource(R.string.app_onboarding_demo_card_pinned), modifier = Modifier.offset(x = 56.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TouchDot(reduceMotion = reduceMotion)
            FlowChevron(rotationDegrees = 0f, modifier = Modifier.size(22.dp))
        }
        MiniCard(
            name = stringResource(R.string.app_onboarding_demo_card_other),
            modifier = Modifier.graphicsLayer { alpha = DIMMED_CARD_ALPHA },
        )
    }
}

// ── Step 3 — BOMPEAR: tap share, the audio flies OUT to a chat. Bomp is the source here. ─────────────
@Composable
internal fun OnboardingBompearDemo(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.CenterEnd) {
            MiniCard(name = stringResource(R.string.app_onboarding_demo_card_send), showShare = true, highlightShare = true)
            TouchDot(reduceMotion = reduceMotion, modifier = Modifier.offset(x = (-6).dp))
        }
        FlowChevron(rotationDegrees = 90f, modifier = Modifier.padding(vertical = Spacing.SM).size(26.dp))
        val destinations =
            listOf(R.string.app_onboarding_dest_chat, R.string.app_onboarding_dest_group, R.string.app_onboarding_dest_more)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            destinations.forEach { res ->
                ShareTile(label = stringResourceUpper(res), highlight = false) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_share),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoChip(
    label: String,
    active: Boolean,
    locked: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .border(
                    HAIRLINE,
                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(percent = 50),
                ).padding(horizontal = Spacing.MD, vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (locked) {
            Icon(
                painter = painterResource(R.drawable.app_ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.XS).size(11.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}
