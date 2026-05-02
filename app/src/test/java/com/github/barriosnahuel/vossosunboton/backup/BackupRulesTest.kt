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
     * Sticker Cero invariant: the welcome-sticker prefs file MUST NOT be referenced by any
     * `<include>` rule. Backup rules use `<include>`-only semantics — only listed paths are
     * backed up — so a restored device gets a fresh welcome on relaunch (matches the spec
     * intent: a new device is the right "Hi, I'm Nahu" moment again).
     */
    @Test
    fun `welcome-sticker prefs are not referenced by any backup include rule`() {
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

        assertThat(allIncludes.none { (_, path) -> path.contains("welcome-sticker") }).isTrue()
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
