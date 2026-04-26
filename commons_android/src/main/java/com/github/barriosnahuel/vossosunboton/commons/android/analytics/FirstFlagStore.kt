/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Persistent flags marking events as "fired at least once" on the current install. Used by the wrapper to gate
 * `first_*` variants and by call-sites to gate one-shot milestones.
 */
interface FirstFlagStore {
    /** `true` when [event] has never been marked fired on this install. */
    fun isFirstTime(event: String): Boolean

    /** Mark [event] as fired so subsequent [isFirstTime] calls return `false`. */
    fun markFired(event: String)
}
