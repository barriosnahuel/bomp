/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home.navigation

import com.github.barriosnahuel.vossosunboton.ui.home.AppTab
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The deep-link allowlist is a security boundary: an inbound Intent is untrusted input
 * (CLAUDE.md § *Deep link path allowlist*). Asserted here rather than through the Activity because
 * every Activity-level assertion lands on My Sounds — which is also the default state, so those
 * tests cannot distinguish "the allowlist rejected the path" from "nothing happened at all".
 */
class DeepLinkResolverTest {
    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist — only named paths route). */
    @Test
    fun `the home path opens My Sounds`() {
        assertThat(resolveDeepLinkTab("/home", hasBundledAudios = true)).isEqualTo(AppTab.MY_SOUNDS)
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist — only named paths route). */
    @Test
    fun `the explore path opens Explore when there are bundled audios`() {
        assertThat(resolveDeepLinkTab("/explore", hasBundledAudios = true)).isEqualTo(AppTab.EXPLORE_SOUNDS)
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist — unknown paths never route off Home). */
    @Test
    fun `an unknown path falls back to My Sounds`() {
        assertThat(resolveDeepLinkTab("/unknown", hasBundledAudios = true)).isEqualTo(AppTab.MY_SOUNDS)
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist — no path is not a wildcard). */
    @Test
    fun `a link with no path falls back to My Sounds`() {
        assertThat(resolveDeepLinkTab(null, hasBundledAudios = true)).isEqualTo(AppTab.MY_SOUNDS)
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist — exact match only, no traversal or casing tricks). */
    @Test
    fun `paths that merely resemble an allowed one do not route to it`() {
        // Exact-match `when`, so none of these reach Explore: the allowlist is not a prefix, a
        // suffix, or a case-insensitive match, and a traversal segment buys nothing.
        val lookalikes = listOf("/explore/", "/EXPLORE", "/explore/../explore", "//explore", "/explore?x=1", "explore")
        lookalikes.forEach { path ->
            assertThat(resolveDeepLinkTab(path, hasBundledAudios = true)).isEqualTo(AppTab.MY_SOUNDS)
        }
    }

    @Test
    fun `the explore path falls back to My Sounds when there is nothing bundled to explore`() {
        // Release builds ship no bundled audios — routing there would land the user on a blank tab.
        assertThat(resolveDeepLinkTab("/explore", hasBundledAudios = false)).isEqualTo(AppTab.MY_SOUNDS)
    }
}
