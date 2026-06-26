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
                putString(AnalyticsParam.SOURCE, source)
                putInt(AnalyticsParam.NAME_LENGTH, nameLength)
                putInt(AnalyticsParam.NAME_WORD_COUNT, nameWordCount)
                putBoolean(AnalyticsParam.NAME_HIT_LIMIT, nameHitLimit)
                putInt(AnalyticsParam.CURRENT_SOUNDS, currentSounds)
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
                putString(AnalyticsParam.FIELD, "name")
                putInt(AnalyticsParam.NAME_LENGTH, nameLength)
                putInt(AnalyticsParam.NAME_WORD_COUNT, nameWordCount)
                putBoolean(AnalyticsParam.NAME_HIT_LIMIT, nameHitLimit)
                putBoolean(AnalyticsParam.NAME_CHANGED, nameChanged)
            }
    }

    /**
     * Save flow ended in error and the user left without retrying. Emitted on explicit Dismiss of the error snackbar
     * or on `ON_STOP` while the error is still pending. Best-effort: process death silently drops the signal.
     */
    data class SoundAddAbandonedAfterError(
        val reason: String,
    ) : AnalyticsEvent(name = "sound_add_abandoned_after_error", hasFirstVariant = false) {
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.REASON, reason) }
    }

    /** Audio deleted (post-snackbar commit). NOT emitted when the user taps "Undo" before the timeout. */
    object SoundDelete : AnalyticsEvent(name = "sound_delete", hasFirstVariant = true)

    /** User tapped "Undo" on the delete snackbar before the timeout. No companion `sound_delete` is emitted. */
    object SoundDeleteUndone : AnalyticsEvent(name = "sound_delete_undone", hasFirstVariant = false)

    /** Audio played. [surface] must match a canonical `screen_name` from the catalog. */
    data class SoundPlay(
        val surface: String,
    ) : AnalyticsEvent(name = "sound_play", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.SURFACE, surface) }
    }

    /** Audio pinned/unpinned via swipe. */
    data class PinToggle(
        val pinned: Boolean,
    ) : AnalyticsEvent(name = "pin_toggle", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putBoolean(AnalyticsParam.PINNED, pinned) }
    }

    /** "Visible en Mis Sonidos" toggled from the assign sheet (ADR 0012). */
    data class VisibilityToggle(
        val visible: Boolean,
    ) : AnalyticsEvent(name = "visibility_toggle", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putBoolean(AnalyticsParam.VISIBLE, visible) }
    }

    /** Search returned zero results for a non-blank query. Debounced upstream to avoid keystroke noise. */
    data class SearchZeroResults(
        val queryLength: Int,
    ) : AnalyticsEvent(name = "search_zero_results", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putInt(AnalyticsParam.QUERY_LENGTH, queryLength) }
    }

    /**
     * Audio shared to an external app via the chooser. Uses Firebase's recommended `share` event for native reports;
     * the `first_share` variant is custom (Firebase does not auto-emit it).
     */
    data class Share(
        val surface: String,
    ) : AnalyticsEvent(name = "share", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.SURFACE, surface) }
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
     * Welcome sticker (Sticker Cero, fresh-install variant) became visible in MY_SOUNDS. It is now a
     * persistent, date-sorted audio, not force-pinned to row 0. Gated call-site
     * via `tracker.markFiredOnce("welcome_sticker_shown")` so it fires at most once per install.
     * `hasFirstVariant = false` because the marker is already one-shot.
     */
    object WelcomeStickerShown : AnalyticsEvent(name = "welcome_sticker_shown", hasFirstVariant = false)

    /**
     * User tapped play on the welcome sticker. `hasFirstVariant = true` so the dashboard can tell a
     * first listen apart from replays — useful to gauge affection (do they come back to it) and as
     * the engagement denominator for the deferred UPDATE flow.
     */
    object WelcomeStickerPlay : AnalyticsEvent(name = "welcome_sticker_play", hasFirstVariant = true)

    /**
     * Welcome audio reached its natural end for the FIRST time — the "they heard it through" onboarding
     * milestone. Gated by the `acknowledged` flag in `SoundsViewModel.onPlayerStop`
     * so unlimited replays of the now-persistent welcome don't inflate it; replays are measured by
     * [WelcomeStickerPlay]. `hasFirstVariant = false` because it is already one-shot.
     */
    object WelcomeStickerCompleted : AnalyticsEvent(name = "welcome_sticker_completed", hasFirstVariant = false)

    /**
     * Welcome sticker manually dismissed — swipe-left or long-press → Delete. Distinct from
     * [WelcomeStickerCompleted] (audio reached natural end) so dashboards can compare engagement
     * (listened all the way) vs impatience (dismissed early). Its Undo logs the generic
     * [SoundDeleteUndone] (welcome is just-another-audio now).
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
     * [source] is the surface that opened the create flow — `"add_bomp"` (New/Edit Bomp assign),
     * `"assign_sheet"` (long-press → assign), `"vault_fab"` (Vault tab FAB), `"manage"` (Manage
     * Collections), or `"my_sounds_filter"` (the My Sounds filter chip row). Tells product which
     * entry point drives organization.
     */
    data class CollectionCreate(
        val scope: String,
        val audios: Int,
        val source: String,
    ) : AnalyticsEvent(name = "collection_create", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putString(AnalyticsParam.SCOPE, scope)
                putInt(AnalyticsParam.AUDIOS, audios)
                putString(AnalyticsParam.SOURCE, source)
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
                putString(AnalyticsParam.SCOPE, scope)
                putInt(AnalyticsParam.AUDIOS, audios)
            }
    }

    /**
     * A collection was renamed. `hasFirstVariant = true` so the first rename — a signal that the
     * initial naming did not satisfy — is separable from later touch-ups.
     */
    data class CollectionRename(
        val scope: String,
    ) : AnalyticsEvent(name = "collection_rename", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.SCOPE, scope) }
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
                putInt(AnalyticsParam.MATCHES, matches)
                putString(AnalyticsParam.SCOPE, scope)
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
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.SCOPE, scope) }
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
                putBoolean(AnalyticsParam.ASSIGNED, assigned)
                putString(AnalyticsParam.SCOPE, scope)
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
                putBoolean(AnalyticsParam.GRANTED, granted)
                putString(AnalyticsParam.SOURCE, source)
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

    /**
     * Onboarding funnel · ENTRY. [source] = which surface opened it (IMPORT_HUB vs EMPTY_STATE).
     * `hasFirstVariant = true` so first-ever opens are isolable.
     *
     * Funnel query guide (whole funnel; each sibling event adds its own notes):
     *  - started vs finished = count(onboarding_opened) vs count(onboarding_completed) — aggregate only;
     *    there is no per-open id by design, so a single open can't be joined to its own end.
     *  - abandonment = opened - completed - dismissed (dismissed = explicit exits only; backgrounding
     *    the app fires nothing); the abandon step = that open's last onboarding_step_viewed.step_key.
     *  - GA4: register source/step_key/method (custom dimensions) + step/step_count (custom metrics)
     *    before they appear in Explorations — NOT retroactive. The BigQuery export has every param raw.
     */
    data class OnboardingOpened(
        val source: String,
    ) : AnalyticsEvent(name = "onboarding_opened", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.SOURCE, source) }
    }

    /**
     * Onboarding funnel · STEP REACH (one per step entry). [step] = 1-indexed position; [stepKey] =
     * stable concept ([AnalyticsOnboardingStep]); [stepCount] = total steps (the denominator, survives
     * a future step-count change); [method] = how they arrived ([AnalyticsNavMethod]). No first variant.
     * QUERY: fires on back / re-entry too, so raw COUNT overcounts — use COUNT(DISTINCT user) or
     * first-touch per user, or filter by [method]. method=open marks the open's first view (a recreate
     * does NOT re-emit). Build the funnel on step_key, not step (positions can be reordered).
     */
    data class OnboardingStepViewed(
        val step: Int,
        val stepKey: String,
        val stepCount: Int,
        val method: String,
    ) : AnalyticsEvent(name = "onboarding_step_viewed", hasFirstVariant = false) {
        override fun params(): Bundle =
            Bundle().apply {
                putInt(AnalyticsParam.STEP, step)
                putString(AnalyticsParam.STEP_KEY, stepKey)
                putInt(AnalyticsParam.STEP_COUNT, stepCount)
                putString(AnalyticsParam.METHOD, method)
            }
    }

    /**
     * Onboarding funnel · SUCCESS (reached + finished the last step). completion rate =
     * count(completed) / count(opened). [stepKey] / [stepCount] = the last step's concept + total (so
     * it's queryable standalone and stays correct under a reorder); [method] = button (CTA) vs tap
     * (stories). `hasFirstVariant = true`.
     */
    data class OnboardingCompleted(
        val stepKey: String,
        val stepCount: Int,
        val method: String,
    ) : AnalyticsEvent(name = "onboarding_completed", hasFirstVariant = true) {
        override fun params(): Bundle =
            Bundle().apply {
                putString(AnalyticsParam.STEP_KEY, stepKey)
                putInt(AnalyticsParam.STEP_COUNT, stepCount)
                putString(AnalyticsParam.METHOD, method)
            }
    }

    /**
     * Onboarding funnel · EXPLICIT EXIT (Skip, or device-back from step 1) — NOT "all non-completers"
     * (backgrounding fires nothing; see the abandonment recipe on [OnboardingOpened]). [step] /
     * [stepKey] / [stepCount] mark where they left; [method] is how. No first variant.
     */
    data class OnboardingDismissed(
        val step: Int,
        val stepKey: String,
        val stepCount: Int,
        val method: String,
    ) : AnalyticsEvent(name = "onboarding_dismissed", hasFirstVariant = false) {
        override fun params(): Bundle =
            Bundle().apply {
                putInt(AnalyticsParam.STEP, step)
                putString(AnalyticsParam.STEP_KEY, stepKey)
                putInt(AnalyticsParam.STEP_COUNT, stepCount)
                putString(AnalyticsParam.METHOD, method)
            }
    }

    /**
     * Import-Hub funnel · ENTRY. The add-a-Bomp Hub bottom sheet opened. [source] = which surface
     * opened it: `"fab"` (the `+` on My Bomps), `"my_sounds_empty_state"` (the empty-state Import
     * CTA), or `"onboarding_finish"` (the tour's closing "Start" drops the user here).
     * `hasFirstVariant = true` so first-ever opens are isolable.
     *
     * Funnel: import_hub_opened → import_hub_import_selected → `sound_add {source=import}`.
     * Hub abandonment = opened − import_selected; picker/naming drop-off = import_selected −
     * sound_add(source=import). No dedicated cancel event by design — it is derivable by subtraction.
     *
     * For an *import-intent* funnel, scope the denominator to proactive opens
     * (`source IN ("fab", "my_sounds_empty_state")`) and treat `"onboarding_finish"` as its own
     * cohort: the tour drops the user on the Hub, so folding it into the total inflates `opened`
     * vs `import_selected`. Its own conversion (does landing them on the Hub post-tour convert?)
     * is a separate, deliberate question.
     */
    data class ImportHubOpened(
        val source: String,
    ) : AnalyticsEvent(name = "import_hub_opened", hasFirstVariant = true) {
        override fun params(): Bundle = Bundle().apply { putString(AnalyticsParam.SOURCE, source) }
    }

    /**
     * Import-Hub funnel · INTENT. The user tapped the live "import audio from your device" row,
     * committing to pick a file (the system picker launches next). The genuine middle funnel step:
     * separates "opened the Hub but never engaged its CTA" from "engaged but bailed in the picker /
     * naming screen". `hasFirstVariant = true`.
     */
    object ImportHubImportSelected : AnalyticsEvent(name = "import_hub_import_selected", hasFirstVariant = true)

    /**
     * Import-Hub funnel · INTENT (record). The user tapped the live "record" row, committing to the
     * in-app recorder (ADR 0019). Sibling of [ImportHubImportSelected]; together they split Hub intent
     * between the two creation channels. `hasFirstVariant = true`.
     */
    object ImportHubRecordSelected : AnalyticsEvent(name = "import_hub_record_selected", hasFirstVariant = true)

    /**
     * In-app recorder funnel · COMPLETION (ADR 0019). A capture reached the review state — via
     * tap-to-stop, the 60 s auto-stop, or an interruption-preserve. The missing middle between the
     * INTENT ([ImportHubRecordSelected]) and the conversion (`sound_add {source=record}`): lets us see
     * where users drop (granted the mic but never recorded? recorded but never saved?). Not emitted on
     * a draft *restore* — that is a recovered prior completion, not a new one.
     */
    object RecordingCompleted : AnalyticsEvent(name = "recording_completed", hasFirstVariant = true)

    /**
     * Outcome of the app's first runtime permission, `RECORD_AUDIO` (ADR 0019). [granted] is true on
     * allow, false on deny — the grant rate of the recorder's gate. No first-variant: every result
     * counts, not just the first request.
     */
    data class RecordPermissionResult(
        val granted: Boolean,
    ) : AnalyticsEvent(name = "record_permission_result", hasFirstVariant = false) {
        override fun params(): Bundle =
            Bundle().apply {
                putBoolean(AnalyticsParam.GRANTED, granted)
            }
    }

    /**
     * Draft recovery (ADR 0019 § Draft recovery). The resume banner was offered on My Bomps for an
     * unsaved recording. Denominator for the resume rate ([RecordingDraftResumed] / this).
     */
    object RecordingDraftBannerShown : AnalyticsEvent(name = "recording_draft_banner_shown", hasFirstVariant = true)

    /** Draft recovery. The user resumed an unsaved recording from the banner ("Continue"). */
    object RecordingDraftResumed : AnalyticsEvent(name = "recording_draft_resumed", hasFirstVariant = true)

    /** Draft recovery. The user discarded an unsaved recording from the banner ("Discard"). */
    object RecordingDraftDiscarded : AnalyticsEvent(name = "recording_draft_discarded", hasFirstVariant = true)

    /**
     * Pre-stable-id `sounds_json` data was found on disk and recovered on read — the install carried
     * audio saved before sound records had a stable id (ADR 0008 → 0018), which now heals on read
     * instead of wiping the list. Gated one-shot via `tracker.markFiredOnce("legacy_sounds_recovered")`
     * so the per-read recovery doesn't flood dashboards; `hasFirstVariant = false` because the marker
     * is already one-shot. Makes the legacy population countable, so ADR 0018's retirement criterion
     * ("retire the migration once the population reaches zero") is actually verifiable.
     */
    object LegacySoundsRecovered : AnalyticsEvent(name = "legacy_sounds_recovered", hasFirstVariant = false)
}
