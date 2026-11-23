/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Filter decisions of [shouldReportToCrashlytics] against synthetic violation stacks. The JankStats cases pin the
 * matcher to the single static-init frame that reflects a hidden platform field, so violations raised by our own
 * frame listeners (invoked from JankStats callbacks) keep crashing the debug build.
 */
class StrictModeConfiguratorTest {
    private fun violation(vararg frames: Pair<String, String>): Throwable =
        RuntimeException("synthetic violation").apply {
            stackTrace = frames.map { (cls, method) -> StackTraceElement(cls, method, "Synthetic.kt", 1) }.toTypedArray()
        }

    @Test
    fun `JankStats reading the hidden Choreographer frame-time field during static init is not reported`() {
        val jankStatsHiddenFieldRead =
            violation(
                "android.os.StrictMode" to "lambda\$static\$1",
                "java.lang.Class" to "getDeclaredField",
                "androidx.metrics.performance.DelegatingOnPreDrawListener" to "<clinit>",
                "androidx.metrics.performance.JankStatsApi16Impl" to "getExpectedFrameDuration",
                "androidx.metrics.performance.JankStatsApi24Impl" to "getExpectedFrameDuration",
            )

        assertThat(shouldReportToCrashlytics(jankStatsHiddenFieldRead)).isFalse()
    }

    @Test
    fun `a violation raised by our listener inside a JankStats frame callback is still reported`() {
        val ownCodeInsideJankStatsCallback =
            violation(
                "android.os.StrictMode\$AndroidBlockGuardPolicy" to "onReadFromDisk",
                "com.github.barriosnahuel.vossosunboton.JankStatsLogger" to "onFrame",
                "androidx.metrics.performance.JankStatsBaseImpl" to "logFrameData",
                "androidx.metrics.performance.JankStatsApi24Impl" to "frameMetricsAvailableListenerDelegate\$lambda\$0",
                "androidx.metrics.performance.DelegatingFrameMetricsListener" to "onFrameMetricsAvailable",
            )

        assertThat(shouldReportToCrashlytics(ownCodeInsideJankStatsCallback)).isTrue()
    }

    @Test
    fun `a violation raised from the JankStats pre-draw listener outside its static init is still reported`() {
        val preDrawCallback =
            violation(
                "android.os.StrictMode\$AndroidBlockGuardPolicy" to "onReadFromDisk",
                "androidx.metrics.performance.DelegatingOnPreDrawListener" to "onPreDraw",
            )

        assertThat(shouldReportToCrashlytics(preDrawCallback)).isTrue()
    }
}
