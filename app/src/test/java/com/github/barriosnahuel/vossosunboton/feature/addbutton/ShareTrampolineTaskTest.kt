/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The share-sheet contract, asserted where it actually lives: the manifest. Sharing an audio into Bomp
 * must not drag the user into Bomp — saving (or backing out) returns them to the app they shared from,
 * with whatever they had open in Bomp left untouched.
 *
 * That is a task-level guarantee, so it is spelled in task-level attributes and can only regress there;
 * no UI test can see it, because the failure needs a *second* task (Bomp already in Recents) to exist.
 */
internal class ShareTrampolineTaskTest : AbstractRobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val trampoline: ActivityInfo
        get() =
            context.packageManager.getActivityInfo(
                ComponentName(context, AddButtonActivity::class.java),
                0,
            )

    @Test
    fun `the share trampoline is launched into the task of the app that shared the audio`() {
        // singleTask would instead "locate the activity on an existing task with the same affinity"
        // (developer.android.com/guide/components/activities/tasks-and-back-stack): with Bomp already in
        // Recents, the share lands inside Bomp's own task and pulls the whole thing to the foreground —
        // so finishing drops the user into Bomp's history rather than back into WhatsApp.
        assertThat(trampoline.launchMode).isEqualTo(ActivityInfo.LAUNCH_SINGLE_TOP)
    }

    @Test
    fun `the share trampoline has no affinity with Bomp's own task`() {
        // A caller that shares with FLAG_ACTIVITY_NEW_TASK would otherwise route the trampoline into
        // Bomp's task on affinity alone, re-creating the same bug from the other direction.
        assertThat(trampoline.taskAffinity).isNotEqualTo(context.applicationInfo.taskAffinity)
    }

    @Test
    fun `the share trampoline never leaves a ghost entry in Recents`() {
        // Only meaningful while the trampoline is the root of its own task, which is the fallback path
        // when the sharing app hands it a task of its own.
        assertThat(trampoline.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS).isNotEqualTo(0)
    }
}
