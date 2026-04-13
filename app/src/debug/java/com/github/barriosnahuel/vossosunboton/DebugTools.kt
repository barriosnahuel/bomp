package com.github.barriosnahuel.vossosunboton

import android.app.Application
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker

internal object DebugTools {
    fun configure(application: Application) {
        StrictModeConfigurator.initializeWithDefaults(Tracker)
    }
}
