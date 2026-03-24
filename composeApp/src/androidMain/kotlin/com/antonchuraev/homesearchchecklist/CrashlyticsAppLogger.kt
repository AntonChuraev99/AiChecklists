package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Decorator that adds Firebase Crashlytics breadcrumbs and non-fatal recording
 * on top of any [AppLogger] implementation.
 *
 * - warning/error messages → Crashlytics breadcrumb log
 * - error with throwable → Crashlytics non-fatal exception
 */
class CrashlyticsAppLogger(
    private val delegate: AppLogger
) : AppLogger by delegate {

    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun warning(tag: String, message: String) {
        delegate.warning(tag, message)
        crashlytics.log("W/$tag: $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        delegate.error(tag, message, throwable)
        crashlytics.log("E/$tag: $message")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        }
    }
}
