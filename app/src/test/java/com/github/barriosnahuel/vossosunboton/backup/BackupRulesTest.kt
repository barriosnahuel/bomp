/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.backup

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

internal class BackupRulesTest : AbstractRobolectricTest() {
    /**
     * Anti-drift assertion: the backup rules MUST point at exactly the file the DataStore
     * writes. Without this check, renaming the DataStore name in `SoundsRepository` would
     * silently break Auto Backup (no test would fail, no error would be logged).
     */
    private val expectedDatastoreInclude = "file" to SoundsRepository.BACKUP_FILE_PATH

    @Test
    fun `app_backup_rules includes both audio files and the bomps datastore file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val includes = parseIncludes(context.resources.getXml(R.xml.app_backup_rules))

        assertThat(includes).containsAtLeast("external" to "Music", expectedDatastoreInclude)
    }

    @Test
    fun `app_data_extraction_rules cloud-backup includes both audio files and the bomps datastore file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val includes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "cloud-backup",
            )

        assertThat(includes).containsAtLeast("external" to "Music", expectedDatastoreInclude)
    }

    @Test
    fun `app_data_extraction_rules device-transfer includes both audio files and the bomps datastore file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val includes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "device-transfer",
            )

        assertThat(includes).containsAtLeast("external" to "Music", expectedDatastoreInclude)
    }

    /**
     * Sticker Cero invariant: the welcome-sticker DataStore file IS backed up. The user's "I
     * dismissed this" gesture is meaningful state that should survive an Auto Backup or
     * device-transfer restore — forcing them through the welcome card on every restored device
     * is poor UX.
     */
    @Test
    fun `welcome-sticker prefs are referenced by every backup include rule`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedInclude = "file" to "datastore/welcome-sticker.preferences_pb"

        assertIncludedEverywhere(context, expectedInclude)
    }

    /**
     * Lifetime user-property counters (`lifetime_plays`, `lifetime_shares`) MUST be backed up.
     * Without backup, a restored device starts at 0 and the next `setUserProperty` overwrites
     * the prior cumulative value in the Firebase user-property dashboard — corruption.
     */
    @Test
    fun `analytics counters prefs are referenced by every backup include rule`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedInclude = "file" to "datastore/analytics-counters.preferences_pb"

        assertIncludedEverywhere(context, expectedInclude)
    }

    /**
     * `first_*` event flags MUST NOT be backed up. Firebase's user-identity model is per-install
     * (`first_open` is emitted per-install, not per-user). Backing up the flags would suppress
     * the `first_*` series on the restored device — desyncs from Firebase's lifecycle. Each
     * install should emit its own first-events series.
     */
    @Test
    fun `analytics flags prefs are NOT referenced by any backup include rule`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupIncludes = parseIncludes(context.resources.getXml(R.xml.app_backup_rules))
        val cloudIncludes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "cloud-backup",
            )
        val transferIncludes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "device-transfer",
            )
        val allIncludes = backupIncludes + cloudIncludes + transferIncludes

        assertThat(allIncludes.none { (_, path) -> path.contains("analytics-flags") }).isTrue()
    }

    private fun assertIncludedEverywhere(
        context: Context,
        include: Pair<String, String>,
    ) {
        val backupIncludes = parseIncludes(context.resources.getXml(R.xml.app_backup_rules))
        val cloudIncludes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "cloud-backup",
            )
        val transferIncludes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "device-transfer",
            )

        assertThat(backupIncludes).contains(include)
        assertThat(cloudIncludes).contains(include)
        assertThat(transferIncludes).contains(include)
    }

    private fun parseIncludes(
        parser: XmlResourceParser,
        parentTag: String? = null,
    ): List<Pair<String, String>> {
        val includes = mutableListOf<Pair<String, String>>()
        var insideParent = parentTag == null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            insideParent = updateParentScope(parser, parentTag, insideParent)
            if (insideParent) collectInclude(parser, includes)
        }

        return includes
    }

    private fun updateParentScope(
        parser: XmlResourceParser,
        parentTag: String?,
        current: Boolean,
    ): Boolean {
        if (parentTag == null) return true
        return when {
            parser.eventType == XmlPullParser.START_TAG && parser.name == parentTag -> true
            parser.eventType == XmlPullParser.END_TAG && parser.name == parentTag -> false
            else -> current
        }
    }

    private fun collectInclude(
        parser: XmlResourceParser,
        into: MutableList<Pair<String, String>>,
    ) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "include") {
            val domain = parser.getAttributeValue(null, "domain")
            val path = parser.getAttributeValue(null, "path")
            if (domain != null && path != null) into += domain to path
        }
    }
}
