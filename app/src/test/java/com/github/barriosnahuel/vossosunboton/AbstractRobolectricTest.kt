/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Suppress detekt suggestion because annotations RunWith and Config are required to all tests.
 */
@Suppress("UnnecessaryAbstractClass")
@RunWith(RobolectricTestRunner::class)
@Config(
    // Single-SDK default (ADR 0013): SDK-independent tests run once at VANILLA_ICE_CREAM (35) —
    // recent, close to targetSdk, already proven in the prior matrix. A multi-SDK matrix belongs
    // only on a test guarding a real Build.VERSION.SDK_INT branch, marked `// sdk-boundary:`
    // (see HapticsTest and AddButtonActivitySdkBoundaryTest). Enforced by check-adr-invariants.sh.
    sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM],
    application = TestApplication::class,
)
internal abstract class AbstractRobolectricTest {
    /**
     * Globally neutralise [Tracker] so production code paths that fire `Tracker.track(...)` from a
     * coroutine launched on a `TestDispatcher` do not crash with
     * `IllegalStateException("Default FirebaseApp is not initialized")` — `TestApplication` does
     * not initialise Firebase. The escaping exception otherwise lands in
     * `kotlinx.coroutines.test.internal.ExceptionCollector` (a JVM-global singleton) and surfaces
     * later as `UncaughtExceptionsBeforeTest` on whichever Compose UI test happens to drain the
     * buffer first under CI's class-ordering. Subclasses that need to assert specific track
     * invocations still get them via MockK's `verify { ... }` — the stub only neutralises the body.
     */
    @Before
    fun neutraliseTrackerInTests() {
        mockkObject(Tracker)
        every { Tracker.track(any()) } answers { nothing }
        every { Tracker.log(any()) } answers { nothing }
    }

    /**
     * Robolectric reports every system feature as absent unless declared, which would make each test
     * run as a mic-less device (Hub skipped, recorder gated off). Declare the microphone like any phone
     * has one; a test of the mic-less path flips it back with `setSystemFeature(FEATURE_MICROPHONE, false)`.
     * Runs as a `@Before`, so a test that launches its Activity from a Rule composes before it and sees no mic.
     */
    @Before
    fun declareMicrophonePresent() {
        Shadows
            .shadowOf(ApplicationProvider.getApplicationContext<Context>().packageManager)
            .setSystemFeature(PackageManager.FEATURE_MICROPHONE, true)
    }

    @After
    fun restoreTrackerAfterTest() {
        unmockkObject(Tracker)
    }
}
