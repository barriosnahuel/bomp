package com.github.barriosnahuel.vossosunboton

import android.app.Application
import com.github.barriosnahuel.vossosunboton.commons.android.error.ErrorTrackerTree
import timber.log.Timber

internal abstract class CustomBuildTypeApplication : Application() {
    override fun onCreate() {
        Timber.plant(Timber.DebugTree())
        Timber.plant(ErrorTrackerTree())

        super.onCreate()

        Timber.d("Creating DEBUG application...")

        DebugTools.configure(this)
    }
}
