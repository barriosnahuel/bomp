/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Regression net for the analytics catalogue. Fails when:
 * - A new [AnalyticsEvent] subclass is added without a regression test in [EVENTS_WITH_REGRESSION_TEST].
 * - A new [CanonicalScreenName] entry is added without a regression test in [SCREEN_VIEWS_WITH_REGRESSION_TEST].
 *
 * Update the canonical lists below whenever you add an event or a screen name. The matching call-site test lives in
 * the module that owns the call-site (see plan 04 §6 Paso 13).
 */
internal class AnalyticsCoverageMatrixTest {
    @Test
    fun `every AnalyticsEvent subclass has a regression test`() {
        val declared =
            AnalyticsEvent::class
                .sealedSubclasses
                .mapNotNull { it.simpleName }
                .toSet()
        val missing = declared - EVENTS_WITH_REGRESSION_TEST
        assertWithMessage(MISSING_EVENT_HINT).that(missing).isEmpty()

        val obsolete = EVENTS_WITH_REGRESSION_TEST - declared
        assertWithMessage(OBSOLETE_EVENT_HINT).that(obsolete).isEmpty()
    }

    @Test
    fun `every canonical screen name has a regression test`() {
        val declared = CanonicalScreenName.ALL.toSet()
        val missing = declared - SCREEN_VIEWS_WITH_REGRESSION_TEST
        assertWithMessage(MISSING_SCREEN_HINT).that(missing).isEmpty()

        val obsolete = SCREEN_VIEWS_WITH_REGRESSION_TEST - declared
        assertWithMessage(OBSOLETE_SCREEN_HINT).that(obsolete).isEmpty()
    }

    @Test
    fun `every canonical user property has a regression test`() {
        val declared = AnalyticsUserProperty.ALL.toSet()
        val missing = declared - USER_PROPERTIES_WITH_REGRESSION_TEST
        assertWithMessage(MISSING_USER_PROPERTY_HINT).that(missing).isEmpty()

        val obsolete = USER_PROPERTIES_WITH_REGRESSION_TEST - declared
        assertWithMessage(OBSOLETE_USER_PROPERTY_HINT).that(obsolete).isEmpty()
    }

    companion object {
        private val EVENTS_WITH_REGRESSION_TEST: Set<String> =
            setOf(
                "SoundAdd",
                "ListenSessionStart",
                "ListenSessionEnd",
                "ListenBackgrounded",
                "ListenTransport",
                "SoundAddAbandonedAfterError",
                "SoundEdit",
                "SoundTrim",
                "SoundDelete",
                "SoundDeleteUndone",
                "SoundPlay",
                "PinToggle",
                "VisibilityToggle",
                "SearchZeroResults",
                "Share",
                "AboutCreditsOpen",
                "AboutLicenseOpen",
                "AboutSourceOpen",
                "AboutPrivacyPolicyOpen",
                "AboutDataSafetyOpen",
                "AboutTermsOfServiceOpen",
                "AboutBrandingAudioPlay",
                "AboutGratitudeCafecitoOpen",
                "AboutGratitudeKofiOpen",
                "MilestoneAudios",
                "WelcomeStickerShown",
                "WelcomeStickerPlay",
                "WelcomeStickerCompleted",
                "WelcomeStickerDismissed",
                "DuplicateNameHintShown",
                "DuplicateNameHintPlay",
                "CollectionCreate",
                "CollectionDelete",
                "CollectionRename",
                "CollectionFilterApply",
                "CollectionView",
                "CollectionAudioToggle",
                "VaultUnlock",
                "VaultUnprotectedWarningShown",
                "VaultSearchUnlockCtaShown",
                "OnboardingOpened",
                "OnboardingStepViewed",
                "OnboardingCompleted",
                "OnboardingDismissed",
                "ImportHubOpened",
                "ImportHubImportSelected",
                "ImportHubRecordSelected",
                "ImportHubBringSelected",
                "RecordingCompleted",
                "RecordPermissionResult",
                "RecordingDraftBannerShown",
                "RecordingDraftResumed",
                "RecordingDraftDiscarded",
                "LegacySoundsRecovered",
            )

        private val SCREEN_VIEWS_WITH_REGRESSION_TEST: Set<String> =
            setOf(
                CanonicalScreenName.MY_SOUNDS,
                CanonicalScreenName.EXPLORE_SOUNDS,
                CanonicalScreenName.ABOUT,
                CanonicalScreenName.SEARCH_SOUND,
                CanonicalScreenName.ADD_SOUND,
                CanonicalScreenName.EDIT_SOUND,
                CanonicalScreenName.VAULT,
                CanonicalScreenName.VAULT_LISTEN,
                CanonicalScreenName.VAULT_UNLOCK,
                CanonicalScreenName.COLLECTION_CREATE,
                CanonicalScreenName.MANAGE_COLLECTIONS,
                CanonicalScreenName.ONBOARDING,
                CanonicalScreenName.BRING_GUIDE,
                CanonicalScreenName.RECORD_SOUND,
            )

        private val USER_PROPERTIES_WITH_REGRESSION_TEST: Set<String> =
            setOf(
                AnalyticsUserProperty.CURRENT_SOUNDS,
                AnalyticsUserProperty.CURRENT_PINNED,
                AnalyticsUserProperty.CURRENT_COLLECTIONS_PUBLIC,
                AnalyticsUserProperty.CURRENT_COLLECTIONS_PRIVATE,
                AnalyticsUserProperty.CURRENT_AUDIOS_IN_COLLECTIONS,
                AnalyticsUserProperty.CURRENT_PUBLIC_DEFAULT,
                AnalyticsUserProperty.CURRENT_PUBLIC_CUSTOM,
                AnalyticsUserProperty.CURRENT_VAULT_DEFAULT,
                AnalyticsUserProperty.CURRENT_VAULT_CUSTOM,
                AnalyticsUserProperty.LIFETIME_SHARES,
                AnalyticsUserProperty.LIFETIME_PLAYS,
                AnalyticsUserProperty.LIFETIME_COLLECTION_CREATES,
                AnalyticsUserProperty.LIFETIME_COLLECTION_DELETES,
                AnalyticsUserProperty.LIFETIME_COLLECTION_RENAMES,
                AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS,
                AnalyticsUserProperty.LIFETIME_VAULT_UNLOCKS,
            )

        private const val MISSING_EVENT_HINT =
            "AnalyticsEvent subclasses without a regression test. Add a call-site test under the matching module" +
                " (see plan 04 §6 Paso 13) and add the simple name to EVENTS_WITH_REGRESSION_TEST."
        private const val OBSOLETE_EVENT_HINT =
            "EVENTS_WITH_REGRESSION_TEST mentions classes that no longer exist. Remove the stale entries."
        private const val MISSING_SCREEN_HINT =
            "CanonicalScreenName entries without a regression test. Cover the screen_view in the call-site test" +
                " for its hosting screen and add the literal to SCREEN_VIEWS_WITH_REGRESSION_TEST."
        private const val OBSOLETE_SCREEN_HINT =
            "SCREEN_VIEWS_WITH_REGRESSION_TEST mentions screens that no longer exist. Remove the stale entries."
        private const val MISSING_USER_PROPERTY_HINT =
            "AnalyticsUserProperty entries without a regression test. Cover the user property in the matching" +
                " call-site test (`fake.userProperties[name]`) and add the constant to" +
                " USER_PROPERTIES_WITH_REGRESSION_TEST."
        private const val OBSOLETE_USER_PROPERTY_HINT =
            "USER_PROPERTIES_WITH_REGRESSION_TEST mentions user properties that no longer exist. Remove the stale" +
                " entries."
    }
}
