/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.NameSoundRoute

/**
 * Graph destination for naming + saving an audio — the shared tail of every internal creation/edit flow
 * (ADR 0024 D4). Resolves the route's value-type arguments into an [AddButtonMode] and hands them to the
 * same [AddButtonScreen] the share-sheet trampoline hosts.
 *
 * Edit carries an id, not a `Sound`, so the audio is looked up in the live library. Until the first load
 * lands the lookup legitimately misses — rendering "not found" then would tear the screen down on every
 * cold start, so nothing is drawn until [SoundsViewModel.isInitialLoadComplete]. A miss *after* the load
 * means the audio is genuinely gone (deleted from another surface, or a stale restored back stack), and
 * the destination pops rather than showing an editor bound to nothing.
 */
@Composable
internal fun NameSoundDestination(
    key: NameSoundRoute,
    viewModel: SoundsViewModel,
    context: Context,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val initialLoadComplete by viewModel.isInitialLoadComplete.collectAsStateWithLifecycle()

    // Resolved on the first library snapshot that can satisfy the route, then pinned. Re-deriving on
    // every emission would hand AddButtonScreen a *different* Edit mode the moment the rename it just
    // saved lands in the library — `Sound` compares by all fields (ADR 0008) — re-keying that screen's
    // `screen_view` effect and double-counting `edit_sound`. Pinning in a plain holder (not snapshot
    // state) keeps this a memo: resolution stays synchronous with composition, so the not-found branch
    // below can't fire on a first frame that simply hasn't resolved yet.
    val pinned = remember(key) { ResolvedMode() }
    val resolvedMode =
        pinned.value ?: when {
            key.editSoundId != null -> library.firstOrNull { it.id == key.editSoundId }?.let(AddButtonMode::Edit)
            key.uri != null -> AddButtonMode.Create(Uri.parse(key.uri))
            else -> null
        }?.also { pinned.value = it }

    if (resolvedMode == null) {
        // An unresolvable route is a bug (a malformed key) or a vanished audio; either way there is
        // nothing to name. Wait out the initial load first — see the KDoc.
        val settled = key.editSoundId == null || initialLoadComplete
        LaunchedEffect(settled) {
            if (settled) {
                Tracker.log("addbutton.route=${if (key.editSoundId != null) "edit" else "create"}")
                Tracker.track(RuntimeException("Naming destination opened with no resolvable audio"))
                onBack()
            }
        }
        return
    }

    AddButtonScreen(
        context = context,
        mode = resolvedMode,
        source = key.source,
        onSaved = { onDone() },
        onNavigateUp = onBack,
    )
}

/** Plain (non-snapshot) holder: pinning the resolved mode is a memo, not observable state. */
private class ResolvedMode {
    var value: AddButtonMode? = null
}
