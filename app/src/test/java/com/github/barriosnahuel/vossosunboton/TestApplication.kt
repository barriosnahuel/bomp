package com.github.barriosnahuel.vossosunboton

import android.app.Application
import org.robolectric.shadows.ShadowLog
import timber.log.Timber

internal class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ShadowLog.stream = System.out

        Timber.plant(Timber.DebugTree())
        Timber.d("Creating TEST (%s) application...", BuildConfig.BUILD_TYPE)
    }
}
