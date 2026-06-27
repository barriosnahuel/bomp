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
    const val VISIBLE = "visible"
    const val SURFACE = "surface"
    const val QUERY_LENGTH = "query_length"
    const val FIELD = "field"
    const val NAME_LENGTH = "name_length"
    const val NAME_WORD_COUNT = "name_word_count"
    const val NAME_HIT_LIMIT = "name_hit_limit"
    const val NAME_CHANGED = "name_changed"
    const val CURRENT_SOUNDS = "current_sounds"
    const val REASON = "reason"
    const val STEP = "step"
    const val STEP_KEY = "step_key"
    const val STEP_COUNT = "step_count"
    const val METHOD = "method"
}

/**
 * Canonical, stable `step_key` values for the onboarding funnel — the CONCEPT each step teaches,
 * independent of its display position. Keying queries on these (not the 1/2/3 index) keeps the funnel
 * meaningful if the steps are ever reordered. Referenced by `ONBOARDING_STEPS` so a rename can't
 * silently drift the emitter away from what queries filter on.
 */
object AnalyticsOnboardingStep {
    const val IMPORT = "import"
    const val ORGANIZE = "organize"
    const val BOMPEAR = "bompear"
}

/**
 * Canonical values for the onboarding `method` param — HOW the user moved through / out of the tour:
 * the "stories" edge-tap, the explicit button (CTA / Skip), the device back, or the initial open.
 * Lets product compare gesture adoption vs button use from day one.
 */
object AnalyticsNavMethod {
    const val OPEN = "open"
    const val TAP = "tap"
    const val BUTTON = "button"
    const val BACK = "back"
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
    const val VAULT_FILTER = "vault_filter"
    const val MANAGE = "manage"
    const val MY_SOUNDS_FILTER = "my_sounds_filter"
    const val SEARCH = "search"

    /** Onboarding tour entry points. */
    const val EMPTY_STATE = "my_sounds_empty_state"

    /** "See how it works" footer under the lone welcome audio on a fresh install. */
    const val WELCOME_FOOTER = "welcome_footer"

    /** "See how it works" item in the top bar overflow menu (reopenable any time). */
    const val OVERFLOW_MENU = "overflow_menu"

    /**
     * Import-Hub open entry points — the `+` FAB on My Bomps vs the onboarding tour's closing
     * "Start" drop. The empty My Bomps "Import" CTA reuses [EMPTY_STATE] (same physical surface).
     */
    const val FAB = "fab"
    const val ONBOARDING_FINISH = "onboarding_finish"
}
