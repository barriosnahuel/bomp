/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Bundle

/**
 * Single source of truth for every custom Analytics event the app emits.
 *
 * Each subclass binds an event name to its params. The wrapper handles the [hasFirstVariant] toggle, so call-sites do
 * not duplicate the first-time logic. Naming and conventions live in `plans/04-firebase-analytics-core-funnel.md` §4
 * and the project's CLAUDE.md "Analytics events" section.
 */
sealed class AnalyticsEvent(
    val name: String,
    val hasFirstVariant: Boolean,
) {
    /**
     * Optional event params. Default is no params.
     */
    open fun params(): Bundle? = null

    /** New audio created. Pairs with `screen_view {add_sound}`. */
    data class SoundAdd(
        val source: String,
        val nameLength: Int,
        val nameWordCount: Int,
        val nameHitLimit: Boolean,
        val currentSounds: Int,
    ) : AnalyticsEvent(name = "sound_add", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putString("source", source)
                putInt("name_length", nameLength)
                putInt("name_word_count", nameWordCount)
                putBoolean("name_hit_limit", nameHitLimit)
                putInt("current_sounds", currentSounds)
            }
    }

    /** Audio renamed. Pairs with `screen_view {edit_sound}`. */
    data class SoundEdit(
        val nameLength: Int,
        val nameWordCount: Int,
        val nameHitLimit: Boolean,
        val nameChanged: Boolean,
    ) : AnalyticsEvent(name = "sound_edit", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putString("field", "name")
                putInt("name_length", nameLength)
                putInt("name_word_count", nameWordCount)
                putBoolean("name_hit_limit", nameHitLimit)
                putBoolean("name_changed", nameChanged)
            }
    }

    /** Audio deleted (post-snackbar commit). NOT emitted when the user taps "Undo" before the timeout. */
    object SoundDelete : AnalyticsEvent(name = "sound_delete", hasFirstVariant = true)

    /** User tapped "Undo" on the delete snackbar before the timeout. No companion `sound_delete` is emitted. */
    object SoundDeleteUndone : AnalyticsEvent(name = "sound_delete_undone", hasFirstVariant = false)

    /** Audio played. [surface] must match a canonical `screen_name` from the catalog. */
    data class SoundPlay(
        val surface: String,
    ) : AnalyticsEvent(name = "sound_play", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString("surface", surface) }
    }

    /** Audio pinned/unpinned via swipe. */
    data class PinToggle(
        val pinned: Boolean,
    ) : AnalyticsEvent(name = "pin_toggle", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putBoolean("pinned", pinned) }
    }

    /** Search returned zero results for a non-blank query. Debounced upstream to avoid keystroke noise. */
    data class SearchZeroResults(
        val queryLength: Int,
    ) : AnalyticsEvent(name = "search_zero_results", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putInt("query_length", queryLength) }
    }

    /**
     * Audio shared to an external app via the chooser. Uses Firebase's recommended `share` event for native reports;
     * the `first_share` variant is custom (Firebase does not auto-emit it).
     */
    data class Share(
        val surface: String,
    ) : AnalyticsEvent(name = "share", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString("surface", surface) }
    }

    /** Credits section expanded inside About. In-screen reveal, not a destination. */
    object AboutCreditsOpen : AnalyticsEvent(name = "about_credits_open", hasFirstVariant = true)

    /** License modal sheet opened from About. */
    object AboutLicenseOpen : AnalyticsEvent(name = "about_license_open", hasFirstVariant = true)

    /** External "Source code" link followed from About. The user leaves the app. */
    object AboutSourceOpen : AnalyticsEvent(name = "about_source_open", hasFirstVariant = true)

    /** External Privacy Policy link followed from About. The user leaves the app. */
    object AboutPrivacyPolicyOpen : AnalyticsEvent(name = "about_privacy_policy_open", hasFirstVariant = true)

    /** External Data Safety link followed from About. The user leaves the app. */
    object AboutDataSafetyOpen : AnalyticsEvent(name = "about_data_safety_open", hasFirstVariant = true)

    /**
     * Brand audio played from the About hero section. NOTE: separate surface from the home-grid
     * Sticker Cero introduced in v2.0.0 — that one emits its own [WelcomeStickerPlay] event.
     */
    object AboutBrandingAudioPlay : AnalyticsEvent(name = "about_branding_audio_play", hasFirstVariant = true)

    /**
     * Welcome sticker (Sticker Cero, fresh-install variant) became visible at the top of MY_SOUNDS.
     * Gated call-site-side via `tracker.markFiredOnce("welcome_sticker_shown")` so it fires at most
     * once per install. `hasFirstVariant = false` because the marker is already one-shot.
     */
    object WelcomeStickerShown : AnalyticsEvent(name = "welcome_sticker_shown", hasFirstVariant = false)

    /**
     * User tapped play on the welcome sticker. `hasFirstVariant = true` so the dashboard can tell
     * a first listen apart from replays (only path to a replay is the Undo flow on the snackbar) —
     * a useful signal when deciding whether to ship the deferred UPDATE flow.
     */
    object WelcomeStickerPlay : AnalyticsEvent(name = "welcome_sticker_play", hasFirstVariant = true)

    /**
     * Welcome audio reached natural end-of-stream. The auto-destruct + Undo snackbar follows.
     * `hasFirstVariant = true` so a second completion (after Undo + replay) is visible.
     */
    object WelcomeStickerCompleted : AnalyticsEvent(name = "welcome_sticker_completed", hasFirstVariant = true)

    /**
     * User tapped Undo on the welcome auto-destruct snackbar. Suppresses the regular
     * [SoundDeleteUndone] for this branch to avoid double-counting in dashboards.
     */
    object WelcomeStickerUndone : AnalyticsEvent(name = "welcome_sticker_undone", hasFirstVariant = true)

    /** External Cafecito link followed from the About gratitude frame. Emitted only after the intent dispatch succeeds. */
    object AboutGratitudeCafecitoOpen : AnalyticsEvent(name = "about_gratitude_cafecito_open", hasFirstVariant = true)

    /** External Ko-fi link followed from the About gratitude frame. Emitted only after the intent dispatch succeeds. */
    object AboutGratitudeKofiOpen : AnalyticsEvent(name = "about_gratitude_kofi_open", hasFirstVariant = true)

    /**
     * Audio-count milestone crossed for the first time on this install. The call-site gates emission via
     * [FirstFlagStore]; no additional `first_*` variant is emitted (the milestone is intrinsically one-shot).
     */
    data class MilestoneAudios(
        val threshold: Int,
    ) : AnalyticsEvent(name = "milestone_sounds_$threshold", hasFirstVariant = false)
}
