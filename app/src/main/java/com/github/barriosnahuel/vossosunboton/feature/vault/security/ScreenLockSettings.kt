/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault.security

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker

/**
 * One-tap shortcut to the system "set up a screen lock" flow, surfaced only when the Vault is
 * unprotected because the device has neither a biometric nor a device credential enrolled (spec § 6
 * edge case "dispositivo sin PIN"). The Vault itself never blocks in that state — this only offers a
 * resilient path to harden it.
 *
 * Uses [DevicePolicyManager.ACTION_SET_NEW_PASSWORD], the OEM-portable intent that prompts the user
 * to set a device security challenge (PIN / pattern / password). Available since API 8, needs no
 * permission and no device-policy controller. On API 30+ the implicit-intent target is hidden by
 * package-visibility filtering, so the manifest declares a matching `<queries>` entry — without it
 * [isAvailable] would always return `false` on modern devices and the affordance would never appear.
 * We never assume it resolves: [isAvailable] gates the affordance so it never renders a dead button,
 * and [open] still guards the launch the way `AboutScreen.openUrl` guards its `ACTION_VIEW`.
 */
object ScreenLockSettings {
    /** The implicit intent that opens the system set-screen-lock screen. */
    fun intent(): Intent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)

    /** True when some activity can handle [intent] — the affordance's visibility gate. */
    fun isAvailable(context: Context): Boolean = intent().resolveActivity(context.packageManager) != null

    /**
     * Launches the set-screen-lock screen. Returns `false` (and tracks a non-fatal) if no activity
     * handles it despite [isAvailable] returning `true` earlier — the same defensive pattern as the
     * About screen's external-link launch.
     */
    fun open(context: Context): Boolean =
        try {
            context.startActivity(intent())
            true
        } catch (e: ActivityNotFoundException) {
            Tracker.log("vault.screenlock_setup_unavailable")
            Tracker.track(RuntimeException("Could not launch ACTION_SET_NEW_PASSWORD", e))
            false
        }
}
