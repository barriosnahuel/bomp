/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Canonical Firebase event-param **keys**. Mirrors [AnalyticsUserProperty]: a single source of
 * truth so every [AnalyticsEvent.params] call-site references one constant instead of retyping the
 * key. A wrong key is invisible at runtime (Firebase silently records the typo'd param), so
 * centralizing the call-sites is the only guard against drift between events.
 *
 * Production code references these constants; **tests intentionally hardcode the wire literal**
 * (`params["scope"]`, not `params[AnalyticsParam.SCOPE]`). The literal in the test is the lock on
 * the dashboard/BigQuery contract: changing a value here (`"scope"` → `"scopee"`) must fail a test,
 * which it can't if the test also reads the constant.
 */
object AnalyticsParam {
    const val SCOPE = "scope"
    const val SOURCE = "source"
    const val AUDIOS = "audios"
    const val MATCHES = "matches"
    const val GRANTED = "granted"
    const val ASSIGNED = "assigned"
    const val PINNED = "pinned"
    const val SURFACE = "surface"
    const val QUERY_LENGTH = "query_length"
    const val FIELD = "field"
    const val NAME_LENGTH = "name_length"
    const val NAME_WORD_COUNT = "name_word_count"
    const val NAME_HIT_LIMIT = "name_hit_limit"
    const val NAME_CHANGED = "name_changed"
    const val CURRENT_SOUNDS = "current_sounds"
    const val REASON = "reason"
}

/**
 * Canonical values for the `scope` param — public (My Bomps) vs private (Vault). Used by every
 * collection event so a typo can't split the dashboard into "private" / "prviate".
 */
object AnalyticsScope {
    const val PUBLIC = "public"
    const val PRIVATE = "private"

    /** Maps a collection's `isPublic` flag to its canonical scope value (My Bomps vs Vault). */
    fun of(isPublic: Boolean): String = if (isPublic) PUBLIC else PRIVATE
}

/**
 * Canonical values for the `source` param — the surface that triggered a `collection_create` or a
 * `vault_unlock`. Shared object because the two events overlap (add_bomp / assign_sheet / manage)
 * and product reads them as one "which entry point drives this" funnel.
 */
object AnalyticsSource {
    const val ADD_BOMP = "add_bomp"
    const val ASSIGN_SHEET = "assign_sheet"
    const val VAULT_TAB = "vault_tab"
    const val VAULT_FAB = "vault_fab"
    const val VAULT_FILTER = "vault_filter"
    const val MANAGE = "manage"
    const val MY_SOUNDS_FILTER = "my_sounds_filter"
    const val SEARCH = "search"
}
