/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Catalogue guard for [AnalyticsUserProperty]. Firebase Analytics silently drops a user property whose name breaks
 * its constraints (`E/FA Name is too long…`), so an over-length name never reaches the dashboard or BigQuery and the
 * failure is invisible until someone reads logcat. These tests fail at build time instead.
 *
 * Limits per https://firebase.google.com/docs/analytics/user-properties (Android): name ≤ 24 chars, starts with a
 * letter, alphanumeric + underscore only, no reserved `firebase_` / `google_` / `ga_` prefix.
 */
internal class AnalyticsUserPropertyNameTest {
    @Test
    fun `every user property name fits the Firebase 24-character limit`() {
        val tooLong = AnalyticsUserProperty.ALL.filter { it.length > MAX_NAME_LENGTH }
        assertWithMessage(
            "User property names exceeding Firebase's $MAX_NAME_LENGTH-char limit are dropped silently. Shorten them" +
                " in AnalyticsUserProperty.",
        ).that(tooLong).isEmpty()
    }

    @Test
    fun `every user property name uses Firebase's allowed character set`() {
        val malformed = AnalyticsUserProperty.ALL.filterNot { NAME_PATTERN.matches(it) }
        assertWithMessage(
            "User property names must start with a letter and contain only letters, digits and underscores.",
        ).that(malformed).isEmpty()
    }

    @Test
    fun `user property names are unique`() {
        val duplicates =
            AnalyticsUserProperty.ALL
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        assertWithMessage(
            "Two constants resolve to the same user property name; Firebase would silently merge their values.",
        ).that(duplicates).isEmpty()
    }

    @Test
    fun `no user property name uses a reserved Firebase prefix`() {
        val reserved = AnalyticsUserProperty.ALL.filter { name -> RESERVED_PREFIXES.any { name.startsWith(it) } }
        assertWithMessage(
            "User property names cannot start with a reserved prefix ($RESERVED_PREFIXES); Firebase rejects them.",
        ).that(reserved).isEmpty()
    }

    private companion object {
        private const val MAX_NAME_LENGTH = 24
        private val NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*$")
        private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    }
}
