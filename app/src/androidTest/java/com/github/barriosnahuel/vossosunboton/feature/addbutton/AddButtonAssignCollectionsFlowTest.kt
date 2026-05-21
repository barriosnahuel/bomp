/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNode
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the "Assign to collections" section of [AddButtonActivity].
 *
 * Round-1 introduced the section; round-2 fixed the "+ New collection" chip wiring so that on
 * `AddButtonActivity` (a separate `ViewModelStoreOwner`) it opens `InlineCollectionCreateSheet`
 * — the one that lives in the same activity scope, NOT `CollectionSheetHost` (which lives in
 * `LandingActivity`). The auto-tag-on-create signal flows through `onCreated(Collection)`,
 * which the section composable wires to the parent's selection state.
 *
 * The tests below validate:
 * - The section + its sub-headers render in Create mode whenever the incoming URI passes
 *   `AddButtonFeature.validateContentUri`.
 * - A seeded public collection produces a chip the user can toggle on.
 * - Tapping the "+ Nueva" chip opens the inline create sheet (NOT the host sheet), and saving a
 *   new name closes the sheet and surfaces the new collection chip selected — that selected
 *   state is the auto-tag signal.
 *
 * Failure shape on regression: the section title fails to render → either `AddButtonScreen`
 * dropped the section, or the strings module override silently broke `app_addbutton_collections_section_title`.
 * The toggle test fails → the wiring between `AssignToCollectionsSection.onPublicSelectionChange`
 * and the parent state holder lost a callback (round-2 had this exact regression).
 */
@RunWith(AndroidJUnit4::class)
internal class AddButtonAssignCollectionsFlowTest : AbstractUiTest() {
    @Test
    fun assignToCollectionsSectionRendersInCreateMode() {
        TestData.seedPublicCollection(context, name = "Familia")

        ActivityScenario.launch<AddButtonActivity>(launchIntent(uri = TestData.seedPreviewAudio(context))).use {
            composeRule.awaitNodeWithText(sectionTitle()).assertIsDisplayed()
            composeRule.onNodeWithText(publicSubsectionLabel()).assertIsDisplayed()
            composeRule.onNodeWithText("Familia").assertIsDisplayed()
            composeRule.onNodeWithText(newCollectionChip()).assertIsDisplayed()
        }
    }

    @Test
    fun tappingNewCollectionChipOpensTheInlineCreateSheet() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(uri = TestData.seedPreviewAudio(context))).use {
            composeRule.awaitNodeWithText(newCollectionChip()).performClick()
            // The inline sheet shares its title resource with the host sheet — that's intentional
            // (same copy, two hosts) and the right thing to assert.
            composeRule.awaitNodeWithText(sheetTitleNewPublic()).assertIsDisplayed()
            composeRule.onNodeWithText(nameLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun creatingACollectionFromTheInlineSheetSurfacesItAsASelectedChip() {
        ActivityScenario.launch<AddButtonActivity>(launchIntent(uri = TestData.seedPreviewAudio(context))).use {
            composeRule.awaitNodeWithText(newCollectionChip()).performClick()
            composeRule.awaitNodeWithText(sheetTitleNewPublic()).assertIsDisplayed()

            // Two text-input nodes coexist while the sheet is open: the Bomp's name field and the
            // sheet's name field. Disambiguate by the sheet field's label ("Name"). The Bomp field's
            // label is the much longer "Choose a funny name for your new Bomp", which contains the
            // substring "name" lowercased but never "Name" capitalized — so a case-sensitive
            // substring on the resource label uniquely targets the sheet field.
            composeRule
                .awaitNode(
                    hasSetTextAction().and(hasText(nameLabel(), substring = true, ignoreCase = false)),
                ).performTextInput("Recetas")
            // "Save" exists twice while the sheet is open (AddButton's primary save + sheet's
            // confirm). Disambiguate by requiring the Save node be a sibling of the sheet title —
            // they live as direct children of the sheet's Column.
            composeRule
                .awaitNode(
                    hasText(saveLabel()).and(hasAnySibling(hasText(sheetTitleNewPublic()))),
                ).performClick()

            // Sheet dismissed + new chip visible in the public subsection.
            composeRule.awaitNodeWithText("Recetas").assertIsDisplayed()
        }
    }

    private fun sectionTitle() = context.getString(R.string.app_addbutton_collections_section_title)

    private fun publicSubsectionLabel() = context.getString(R.string.app_addbutton_collections_public_label)

    private fun newCollectionChip() = context.getString(R.string.app_addbutton_collections_new_chip)

    private fun sheetTitleNewPublic() = context.getString(R.string.app_collection_sheet_title_new_public)

    private fun nameLabel() = context.getString(R.string.app_collection_sheet_name_label)

    private fun saveLabel() = context.getString(R.string.app_collection_sheet_save)

    private fun launchIntent(uri: Uri?): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
}
