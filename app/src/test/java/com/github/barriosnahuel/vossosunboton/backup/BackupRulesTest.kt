package com.github.barriosnahuel.vossosunboton.backup

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

internal class BackupRulesTest : AbstractRobolectricTest() {
    @Test
    fun `app_backup_rules includes both audio files and metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val includes = parseIncludes(context.resources.getXml(R.xml.app_backup_rules))

        assertThat(includes).containsAtLeast("external" to "Music", "sharedpref" to "my-prefs")
    }

    @Test
    fun `app_data_extraction_rules cloud-backup includes both audio files and metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val includes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "cloud-backup",
            )

        assertThat(includes).containsAtLeast("external" to "Music", "sharedpref" to "my-prefs")
    }

    @Test
    fun `app_data_extraction_rules device-transfer includes both audio files and metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val includes =
            parseIncludes(
                context.resources.getXml(R.xml.app_data_extraction_rules),
                parentTag = "device-transfer",
            )

        assertThat(includes).containsAtLeast("external" to "Music", "sharedpref" to "my-prefs")
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
