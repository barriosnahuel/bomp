/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault.security

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Unit coverage for [ScreenLockSettings] — the resilient deep link the Vault offers when the device
 * has no screen lock configured (spec § 6 "dispositivo sin PIN"). Pins the wire action string and
 * the resolvability gate so a refactor can't silently swap the intent or let the affordance render
 * as a dead button on a device that can't handle it.
 */
internal class ScreenLockSettingsTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `intent targets the system set-new-password action`() {
        assertThat(ScreenLockSettings.intent().action).isEqualTo("android.app.action.SET_NEW_PASSWORD")
    }

    @Test
    fun `isAvailable is true when an activity resolves the intent`() {
        context.packageManager.registerSettingsResolver()

        assertThat(ScreenLockSettings.isAvailable(context)).isTrue()
    }

    @Test
    fun `isAvailable is false when nothing resolves the intent`() {
        assertThat(ScreenLockSettings.isAvailable(context)).isFalse()
    }

    @Test
    fun `open launches the set-new-password screen`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        activity.packageManager.registerSettingsResolver()

        val launched = ScreenLockSettings.open(activity)

        assertThat(launched).isTrue()
        assertThat(shadowOf(activity).nextStartedActivity.action).isEqualTo("android.app.action.SET_NEW_PASSWORD")
    }

    @Test
    fun `open returns false and tracks a non-fatal when no activity handles the launch`() {
        // The resilience contract: even if isAvailable raced ahead of an uninstall, open must not
        // crash. Force the ActivityNotFoundException branch with a context whose startActivity throws.
        val context = mockk<Context>()
        every { context.startActivity(any()) } throws ActivityNotFoundException("no handler")

        val launched = ScreenLockSettings.open(context)

        assertThat(launched).isFalse()
        verify(exactly = 1) { Tracker.track(any()) }
    }

    // Robolectric 4.16.1 deprecated every ShadowPackageManager resolve-info setter with no lightweight
    // replacement; the installPackage-based alternative would rewrite this boundary test's setup.
    @Suppress("DEPRECATION")
    private fun PackageManager.registerSettingsResolver() =
        shadowOf(this).addResolveInfoForIntent(ScreenLockSettings.intent(), settingsResolveInfo())

    private fun settingsResolveInfo() =
        ResolveInfo().apply {
            activityInfo =
                ActivityInfo().apply {
                    packageName = "com.android.settings"
                    name = "SetNewPasswordActivity"
                }
        }
}
