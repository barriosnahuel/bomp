package com.github.barriosnahuel.vossosunboton.commons.android.error

import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Simple interface to hide the implementation details of our error tracking tool.
 */
interface Trackable {
    /**
     * Report the given `throwable` to our tracking tool.
     */
    fun track(throwable: Throwable)

    /**
     * Log the given `message` to our error tracking platform. Each log must be useful to give us more context when troubleshooting.
     * @param message the message to upload.
     */
    fun log(message: String)
}

/**
 * Current implementation of the [Trackable] interface.
 * This tracker should be used along the entire project whenever you want to track some error or log something to the error platform.
 */
object Tracker : Trackable {
    override fun track(throwable: Throwable) {
        Timber.e("Tracking error to Firebase Crashlytics: %s", throwable.message)
        throwable.printStackTrace()

        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    override fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }
}

/**
 * Tree to plant on Timber to be able to log messages to the error tracking platform.
 */
class ErrorTrackerTree : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        Tracker.log(message)
    }
}
