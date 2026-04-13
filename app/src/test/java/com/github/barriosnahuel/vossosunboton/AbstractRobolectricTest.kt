package com.github.barriosnahuel.vossosunboton

import android.os.Build
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suppress detekt suggestion because annotations RunWith and Config are required to all tests.
 */
@Suppress("UnnecessaryAbstractClass")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.S_V2], application = TestApplication::class)
internal abstract class AbstractRobolectricTest
