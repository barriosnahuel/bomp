/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

/**
 * Marks an instrumented class as a capture/utility driver, not a behaviour test. The default suite
 * excludes it via the `notAnnotation` runner argument in `app/build.gradle`, so
 * `connectedDebugAndroidTest` / `run-instrumented-tests.sh` skip it. The store-screenshot driver
 * (`capture-store-screenshots.sh`) still runs it, because it targets the class directly with
 * `am instrument -e class …`, which does not pass Gradle's instrumentation runner arguments.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
internal annotation class ScreenshotCapture
