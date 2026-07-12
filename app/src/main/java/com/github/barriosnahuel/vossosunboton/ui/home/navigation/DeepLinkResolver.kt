/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home.navigation

import com.github.barriosnahuel.vossosunboton.ui.home.AppTab

/**
 * Resolves a `push-me://open<path>` deep link to the tab it may open.
 *
 * The closed allowlist and its **unknown-path → My Sounds** fallback are the security boundary
 * (CLAUDE.md § *Deep link path allowlist*): an inbound Intent is untrusted input, and no path may
 * route anywhere the allowlist does not name. Navigation 3 has no declarative deep-link API — the
 * matcher lives here, on the Activity side, per the official recipe (ADR 0024 § *Deep links*).
 *
 * Pure so the boundary is testable without an Activity, and without a checkout that happens to ship
 * bundled audios: [hasBundledAudios] is passed in rather than read from the packaged catalogue.
 */
internal fun resolveDeepLinkTab(
    path: String?,
    hasBundledAudios: Boolean,
): AppTab {
    val requested =
        when (path) {
            "/home" -> AppTab.MY_SOUNDS
            "/explore" -> AppTab.EXPLORE_SOUNDS
            else -> AppTab.MY_SOUNDS
        }
    // Explore is empty in release builds (no bundled audios) and in any debug build that hasn't
    // populated model/src/debug/res/raw/. Routing there would land on a blank tab.
    return if (requested == AppTab.EXPLORE_SOUNDS && !hasBundledAudios) AppTab.MY_SOUNDS else requested
}
