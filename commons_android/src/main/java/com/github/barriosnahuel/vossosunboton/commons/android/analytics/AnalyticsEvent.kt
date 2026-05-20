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

    /**
     * Save flow ended in error and the user left without retrying. Emitted on explicit Dismiss of the error snackbar
     * or on `ON_STOP` while the error is still pending. Best-effort: process death silently drops the signal.
     */
    data class SoundAddAbandonedAfterError(
        val reason: String,
    ) : AnalyticsEvent(name = "sound_add_abandoned_after_error", hasFirstVariant = false) {
        override fun params(): Bundle = Bundle().apply { putString("reason", reason) }
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

    /** External Terms of Service link followed from About. The user leaves the app. */
    object AboutTermsOfServiceOpen : AnalyticsEvent(name = "about_terms_of_service_open", hasFirstVariant = true)

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

    /**
     * Welcome sticker manually dismissed — swipe-left or long-press → Delete. Distinct from
     * [WelcomeStickerCompleted] (audio reached natural end) so dashboards can compare engagement
     * (listened all the way) vs impatience (dismissed early).
     */
    object WelcomeStickerDismissed : AnalyticsEvent(name = "welcome_sticker_dismissed", hasFirstVariant = true)

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

    /**
     * Non-blocking duplicate-name hint appeared in the New Bomp screen because the typed name
     * matches an existing Bomp in the user's library. The call-site gates emission per distinct
     * match (keyed by the matched sound's id) to avoid keystroke noise.
     */
    object DuplicateNameHintShown : AnalyticsEvent(name = "duplicate_name_hint_shown", hasFirstVariant = true)

    /**
     * User tapped the inline play button on the duplicate-name hint to listen to the existing
     * Bomp before deciding whether to save the duplicate.
     */
    object DuplicateNameHintPlay : AnalyticsEvent(name = "duplicate_name_hint_play", hasFirstVariant = true)

    /**
     * A new collection was created. [scope] is `"public"` or `"private"`; [audios] is the audio
     * count at creation (always 0 today but kept for future "create from selection" flows).
     */
    data class CollectionCreate(
        val scope: String,
        val audios: Int,
    ) : AnalyticsEvent(name = "collection_create", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putString("scope", scope)
                putInt("audios", audios)
            }
    }

    /**
     * A collection was deleted. [scope] mirrors [CollectionCreate]; [audios] is the count of
     * audios that lost their tag (audio files themselves are not removed from disk).
     * `hasFirstVariant = true` so dashboards can isolate the first time a user prunes their
     * organization, distinct from routine cleanup.
     */
    data class CollectionDelete(
        val scope: String,
        val audios: Int,
    ) : AnalyticsEvent(name = "collection_delete", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putString("scope", scope)
                putInt("audios", audios)
            }
    }

    /**
     * A collection was renamed. `hasFirstVariant = true` so the first rename — a signal that the
     * initial naming did not satisfy — is separable from later touch-ups.
     */
    data class CollectionRename(
        val scope: String,
    ) : AnalyticsEvent(name = "collection_rename", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString("scope", scope) }
    }

    /**
     * A collection filter chip was activated. [matches] is the number of audios that survive the
     * filter (spots actively-managed vs. empty/abandoned collections). [scope] is `"public"` (the
     * My Sounds filter row) or `"private"` (the Vault filter row) — lets product compare which
     * surface's filtering users actually lean on.
     */
    data class CollectionFilterApply(
        val matches: Int,
        val scope: String,
    ) : AnalyticsEvent(name = "collection_filter_apply", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putInt("matches", matches)
                putString("scope", scope)
            }
    }

    /**
     * A collection was opened for viewing from the Manage Collections overflow ("View collection").
     * [scope] mirrors [CollectionCreate]. Separates Manage-as-navigation from Manage-as-housekeeping
     * — do users reach their audios *through* Manage, or only edit metadata there?
     */
    data class CollectionView(
        val scope: String,
    ) : AnalyticsEvent(name = "collection_view", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString("scope", scope) }
    }

    /**
     * Audio was added to or removed from a collection via the assign-to-collection sheet or the
     * Add/Edit Bomp chip group. [assigned] = true when the audio just joined, false when it left.
     * [scope] mirrors [CollectionCreate].
     */
    data class CollectionAudioToggle(
        val assigned: Boolean,
        val scope: String,
    ) : AnalyticsEvent(name = "collection_audio_toggle", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putBoolean("assigned", assigned)
                putString("scope", scope)
            }
    }

    /**
     * Biometric prompt resolved for a Vault collection. [granted] reflects whether the user
     * authenticated successfully (true) or cancelled / failed (false). [source] is the entry point
     * that triggered the prompt — one of `"vault_tab"`, `"search"` (the "Search your Vault too"
     * CTA), `"add_bomp"` (New/Edit Bomp assign section), `"assign_sheet"` (long-press → assign), or
     * `"manage"` (Manage Collections' locked-Vault card). No PII — collection id is out of scope;
     * only the cumulative grant/cancel rate and which surface drives unlocks matter.
     */
    data class VaultUnlock(
        val granted: Boolean,
        val source: String,
    ) : AnalyticsEvent(name = "vault_unlock", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putBoolean("granted", granted)
                putString("source", source)
            }
    }

    /**
     * Device has no biometric or device-credential lock configured but a private collection
     * exists — the warning chip on the card is showing. Emitted at most once per process via
     * `markFiredOnce("vault_unprotected_warning")` so we don't flood dashboards on scroll.
     */
    object VaultUnprotectedWarningShown :
        AnalyticsEvent(name = "vault_unprotected_warning_shown", hasFirstVariant = false)

    /**
     * The "Search your Vault too" CTA became visible at the foot of the search overlay (the user
     * has a non-empty private collection AND the Vault is locked this session). Impression signal
     * for the new search entry point. Emitted at most once per process via
     * `markFiredOnce("vault_search_cta_shown")` so re-opening search / recomposition doesn't flood.
     */
    object VaultSearchUnlockCtaShown :
        AnalyticsEvent(name = "vault_search_unlock_cta_shown", hasFirstVariant = false)
}
