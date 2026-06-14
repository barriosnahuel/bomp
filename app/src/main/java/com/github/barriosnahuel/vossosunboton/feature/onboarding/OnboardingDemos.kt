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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.BOMP_ZONE_ALPHA
import com.github.barriosnahuel.vossosunboton.ui.theme.OnboardingIllustrationColors
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import com.github.barriosnahuel.vossosunboton.ui.theme.rememberOnboardingIllustrationColors

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

// Collection folders in the step-02 "drag to a collection" illustration.
private const val FOLDER_DOT_COUNT = 3
private val FOLDER_HEIGHT = 76.dp
private val FOLDER_DOT = 8.dp

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

/** A flat waveform strip — explicitly NOT a control (no play circle), tinted to its zone's accent. */
@Composable
private fun Waveform(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WAVE.forEach { h ->
            Box(
                modifier =
                    Modifier
                        .padding(end = 2.dp)
                        .width(2.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(color),
            )
        }
    }
}

private val ZONE_RADIUS = 18.dp
private val APP_TILE = 30.dp

// Zone widths — the origin slightly wider than the destination, offset to read as "from here to there".
private const val FOREIGN_ZONE_WIDTH = 0.86f
private const val BOMP_ZONE_WIDTH = 0.82f

// ── Step 1 — SUMAR · "Ilustración de marca" (post user-test). Two flat color ZONES — a WhatsApp-green
// origin and a Bomp-acid destination — with the audio travelling between them. No play button, no
// realistic share sheet: the test showed a faithful UI got tapped and hid that the audio lived in
// another app. The chromatic split (foreign green → Bomp acid) IS the lesson. Decorative: the stage
// clears these from the semantics tree. Design: 2c · Opción A.
@Composable
internal fun OnboardingImportDemo(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val illustration = rememberOnboardingIllustrationColors()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        ForeignAppZone(colors = illustration, reduceMotion = reduceMotion, modifier = Modifier.align(Alignment.Start))
        ShareCue()
        BompZone(modifier = Modifier.align(Alignment.End))
    }
}

/** The origin zone: a green-tinted card bordered in the foreign-app accent, holding a voice note. */
@Composable
private fun ForeignAppZone(
    colors: OnboardingIllustrationColors,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(FOREIGN_ZONE_WIDTH), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ZONE_RADIUS))
                    .background(colors.foreignZone)
                    .border(2.dp, colors.foreignAccent, RoundedCornerShape(ZONE_RADIUS))
                    .padding(Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                // The foreign app's icon tile + name — a green app badge, the way it reads in a chat header.
                Box(modifier = Modifier.size(APP_TILE).clip(RoundedCornerShape(percent = 30)).background(colors.foreignAccent))
                Text(
                    text = stringResourceUpper(R.string.app_onboarding_demo_foreign_app),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.foreignAccent,
                )
                Text(
                    text = stringResource(R.string.app_onboarding_demo_foreign_sender),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            // Received voice note — flat, no play button: an accent-outlined dot + flat wave + duration.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .border(2.dp, colors.foreignAccent, RoundedCornerShape(percent = 50)),
                )
                Waveform(color = colors.foreignAccent)
                MonoLabel(stringResource(R.string.app_onboarding_demo_voice_duration))
            }
        }
        TouchDot(reduceMotion = reduceMotion, modifier = Modifier.offset(x = Spacing.MD))
    }
}

/** The travel cue between the zones: a down chevron + a soft "share" word in the acid voice. */
@Composable
private fun ShareCue() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FlowChevron(rotationDegrees = 90f, modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(R.string.app_onboarding_demo_share_cue),
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The destination zone: an acid-tinted card with the Bomp mark and "it stays here". */
@Composable
private fun BompZone(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth(BOMP_ZONE_WIDTH)
                .clip(RoundedCornerShape(ZONE_RADIUS))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = BOMP_ZONE_ALPHA))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(ZONE_RADIUS))
                .padding(Spacing.MD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        // The Bomp logo: acid tile + play triangle — the only "play" mark here, and it's a brand mark.
        Box(
            modifier =
                Modifier
                    .size(
                        APP_TILE + Spacing.SM,
                    ).clip(RoundedCornerShape(percent = 28))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.app_ic_play_arrow),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(
                text = stringResource(R.string.app_onboarding_demo_destination),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResourceUpper(R.string.app_onboarding_demo_destination_sub),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Step 2 — GUARDAR · "Colecciones (arrastrar)": drag an audio sticker into a collection. The
// sticker is flat (no play button — anti-affordance, same stance as step 01); an acid drag path
// leads to the highlighted folder, the drop target. Teaches "ordenala a tu manera". Design: 2c ·
// Paso 02 · Toma 3. ──────────────────────────────────────────────────────────────────────────────
@Composable
internal fun OnboardingOrganizeDemo(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        // The dragged audio, with the finger on it.
        Box(contentAlignment = Alignment.CenterEnd) {
            AudioSticker(accent = MaterialTheme.colorScheme.primary)
            TouchDot(reduceMotion = reduceMotion, modifier = Modifier.offset(x = (-6).dp))
        }
        // Drag path + the "your way" cue.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            FlowChevron(rotationDegrees = 90f, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(R.string.app_onboarding_demo_organize_cue),
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Three collections — the highlighted "Familia" is where the audio lands.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
            CollectionFolder(
                label = stringResource(R.string.app_onboarding_demo_collection_family),
                highlight = true,
                modifier = Modifier.weight(1f),
            )
            CollectionFolder(
                label = stringResource(R.string.app_onboarding_chip_collection),
                highlight = false,
                modifier = Modifier.weight(1f),
            )
            CollectionFolder(
                label = stringResource(R.string.app_vault_baul_name),
                highlight = false,
                locked = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A flat audio "sticker": an accent-outlined play glyph (not a filled, tappable button) + flat wave. */
@Composable
private fun AudioSticker(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(MINI_CARD_RADIUS))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, accent, RoundedCornerShape(MINI_CARD_RADIUS))
                .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(percent = 50)).border(2.dp, accent, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.app_ic_play_arrow),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(11.dp),
            )
        }
        Waveform(color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A collection folder card: a dot cluster + label. [highlight] = the acid drop target. */
@Composable
private fun CollectionFolder(
    label: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    val accent = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier =
            modifier
                .height(FOLDER_HEIGHT)
                .clip(RoundedCornerShape(MINI_CARD_RADIUS))
                .background(
                    if (highlight) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = BOMP_ZONE_ALPHA)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).border(2.dp, accent, RoundedCornerShape(MINI_CARD_RADIUS))
                .padding(Spacing.SM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.SM, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            repeat(FOLDER_DOT_COUNT) {
                Box(Modifier.size(FOLDER_DOT).clip(RoundedCornerShape(percent = 50)).background(accent))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            if (locked) {
                Icon(
                    painter = painterResource(R.drawable.app_ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
