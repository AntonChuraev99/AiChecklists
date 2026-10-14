package com.antonchuraev.homesearchchecklist.core.common.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The ONE place that persists notification-permission ask state on Android (per install), shared by
 * feature:home's NotificationPermissionController and feature:onboarding's requester — both depend
 * on core:common:api, so neither duplicates the storage contract.
 *
 * Stores a single fact: the last system-dialog answer was provably permanent (see
 * [classifyNotificationDialogResult]). The CTA then opens app settings instead of a dialog the OS
 * would no longer show.
 */
object NotificationPermissionLedger {
    private const val PREFS = "notification_permission"
    private const val KEY_PERMANENTLY_DENIED = "permanently_denied"

    fun isPermanentlyDenied(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERMANENTLY_DENIED, false)

    fun setPermanentlyDenied(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERMANENTLY_DENIED, value).apply()
    }

    /** Persists what a dialog answer proved: permanent → set, anything else → clear. */
    fun record(context: Context, outcome: NotificationDialogOutcome) {
        setPermanentlyDenied(context, outcome.isPermanent)
    }

    /**
     * Opens this app's notification settings (app-details page as fallback for OEMs without it).
     * Returns the failure, if both launches failed, so the caller can log it with its own tag.
     */
    fun openAppNotificationSettings(context: Context): Throwable? {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            details
        }
        return runCatching { context.startActivity(primary) }
            .recoverCatching { context.startActivity(details) }
            .exceptionOrNull()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
