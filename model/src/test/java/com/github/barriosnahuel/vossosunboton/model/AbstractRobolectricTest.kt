/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model

import android.os.Build
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric base for unit tests in the `:model` module.
 *
 * Mirrors `app`'s `AbstractRobolectricTest` (same single-SDK default) but without the
 * custom `TestApplication` — model code only needs a plain `Context`, so we
 * let Robolectric supply its default `Application`. Tests in `:app` keep their
 * own base because they exercise UI/Compose flows that benefit from Timber
 * being planted in `TestApplication.onCreate`.
 */
@Suppress("UnnecessaryAbstractClass")
@RunWith(RobolectricTestRunner::class)
@Config(
    // Single-SDK default (ADR 0013) — see app's AbstractRobolectricTest. A multi-SDK matrix
    // belongs only on a test guarding a real SDK_INT branch, marked `// sdk-boundary:`.
    sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM],
)
internal abstract class AbstractRobolectricTest
