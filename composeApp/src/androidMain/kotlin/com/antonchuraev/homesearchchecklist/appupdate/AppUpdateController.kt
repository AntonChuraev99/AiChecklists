package com.antonchuraev.homesearchchecklist.appupdate

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.days

/**
 * Drives the Google Play in-app update flow (Android-only).
 *
 * Ported from the swapfaceandroid reference implementation. On a cold start the app
 * calls [checkAndStartUpdate]; if Play allows an IMMEDIATE update it starts the blocking
 * full-screen flow, otherwise it falls back to FLEXIBLE (background download → restart
 * snackbar). [checkAndResumeUpdate] is called on every onResume to re-surface a downloaded
 * flexible update or to resume an interrupted immediate one.
 *
 * Registered as a Koin singleton in `PlatformModule.android.kt`; the [AppUpdateLauncher]
 * composable observes [updateState] and forwards activity results via [handleUpdateResult].
 */
class AppUpdateController(
    private val context: Context,
    private val logger: AppLogger,
    private val analytics: AnalyticsTracker,
) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    // Pending-state of an IMMEDIATE update is persisted here so the new process started after
    // the restart can compare the target versionCode against the actual one and log
    // app_update_completed. Play Core's IMMEDIATE flow kills the old process BEFORE returning
    // RESULT_OK — without this bridge there is nothing to fire the completion event from.
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    // Type of the currently active update flow — needed to branch in handleUpdateResult.
    // For flexible, RESULT_OK = user accepted (download starts in background), NOT completion.
    // Read/written from both the result callback (UI thread) and the install listener
    // (Play Core thread), so it must be @Volatile for cross-thread visibility.
    @Volatile
    private var currentUpdateType: Int = AppUpdateType.IMMEDIATE

    // Version is read once in the constructor — BuildConfig is not available in this KMP
    // library module, so PackageManager is the portable source.
    private val versionCode: Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }
    }.getOrDefault(0)

    private val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    init {
        detectCompletedImmediateUpdate()
    }

    // Listener is registered before startUpdateFlowForResult and removed only after a terminal
    // InstallStatus (INSTALLED/FAILED/CANCELED). Intermediate statuses (PENDING/DOWNLOADING/
    // INSTALLING) are not reported to analytics — only DOWNLOADED → state + event;
    // terminal → event + unregister.
    private var installStateListener: InstallStateUpdatedListener? = null

    // ------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------

    /**
     * Checks for an available update and starts the best allowed flow (IMMEDIATE preferred,
     * FLEXIBLE fallback). Call once on cold start.
     */
    suspend fun checkAndStartUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        _updateState.value = AppUpdateState.Checking
        logger.debug(TAG, "Checking for app updates...")
        analytics.event(EVENT_CHECK_STARTED, versionParams())

        runCatching {
            val appUpdateInfo = getAppUpdateInfo()

            when {
                appUpdateInfo.isImmediateUpdateAvailable() -> {
                    logger.debug(TAG, "Immediate update available, starting update flow")
                    logUpdateAvailable("immediate", appUpdateInfo)
                    _updateState.value = AppUpdateState.UpdateAvailable
                    startImmediateUpdate(appUpdateInfo, launcher)
                }

                appUpdateInfo.isFlexibleUpdateAvailable() -> {
                    logger.debug(TAG, "Flexible update available (immediate not allowed), starting flexible flow")
                    logUpdateAvailable("flexible", appUpdateInfo)
                    _updateState.value = AppUpdateState.UpdateAvailable
                    startFlexibleUpdate(appUpdateInfo, launcher)
                }

                else -> {
                    logger.debug(TAG, "No update available or no update type allowed")
                    analytics.event(
                        EVENT_CHECK_NO_UPDATE,
                        versionParams() + mapOf(
                            "update_availability" to appUpdateInfo.updateAvailability().toString(),
                        ),
                    )
                    _updateState.value = AppUpdateState.UpdateNotAvailable
                }
            }
        }.onFailure { e ->
            logFailure(e, "Failed to check for updates", source = "check")
            _updateState.value = AppUpdateState.Error(e)
        }
    }

    /**
     * Checks whether an update was interrupted (call in onResume). Resumes an in-progress
     * immediate flow, or surfaces an already-downloaded flexible update.
     */
    suspend fun checkAndResumeUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        runCatching {
            val appUpdateInfo = getAppUpdateInfo()

            when {
                // Immediate update interrupted — restart it.
                appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    logger.debug(TAG, "Immediate update in progress, resuming")
                    _updateState.value = AppUpdateState.UpdateInProgress
                    startImmediateUpdate(appUpdateInfo, launcher)
                }

                // Flexible already downloaded (user closed the app before restarting).
                appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED -> {
                    logger.debug(TAG, "Flexible update already downloaded, showing restart prompt")
                    _updateState.value = AppUpdateState.UpdateDownloaded
                }

                else -> {
                    logger.debug(
                        TAG,
                        "checkAndResumeUpdate: nothing to resume " +
                            "(availability=${appUpdateInfo.updateAvailability()}, " +
                            "installStatus=${appUpdateInfo.installStatus()})",
                    )
                }
            }
        }.onFailure { e ->
            logFailure(e, "Failed to resume update", source = "resume")
        }
    }

    /** Handles the activity result from the update flow. */
    fun handleUpdateResult(resultCode: Int) {
        val type = currentUpdateType
        when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                if (type == AppUpdateType.FLEXIBLE) {
                    // For flexible, RESULT_OK = user agreed to start the download, not completion;
                    // completion is caught by InstallStateUpdatedListener.
                    logger.debug(TAG, "Flexible update flow accepted by user, download in progress")
                    _updateState.value = AppUpdateState.UpdateInProgress
                } else {
                    logger.debug(TAG, "Immediate update flow completed successfully")
                    analytics.event(
                        EVENT_COMPLETED,
                        versionParams() + mapOf("update_type" to "immediate"),
                    )
                }
            }

            android.app.Activity.RESULT_CANCELED -> {
                val updateType = if (type == AppUpdateType.FLEXIBLE) "flexible" else "immediate"
                logger.debug(TAG, "$updateType update flow canceled by user")
                analytics.event(
                    EVENT_CANCELED,
                    versionParams() + mapOf("update_type" to updateType),
                )
                if (type == AppUpdateType.FLEXIBLE) {
                    unregisterInstallStateListener()
                }
                // For immediate — the flow restarts on the next onResume.
            }

            else -> {
                val updateType = if (type == AppUpdateType.FLEXIBLE) "flexible" else "immediate"
                logger.error(TAG, "Update flow ($updateType) failed with result code: $resultCode")
                analytics.event(
                    EVENT_FAILED,
                    versionParams() + mapOf(
                        "update_type" to updateType,
                        "result_code" to resultCode.toString(),
                    ),
                )
                if (type == AppUpdateType.FLEXIBLE) {
                    unregisterInstallStateListener()
                }
                _updateState.value = AppUpdateState.Error(
                    IllegalStateException("Update ($updateType) failed with result code: $resultCode"),
                )
            }
        }
    }

    /**
     * Completes a flexible update after the user tapped "Restart". Only valid when
     * [updateState] is [AppUpdateState.UpdateDownloaded].
     */
    fun completeFlexibleUpdate() {
        logger.debug(TAG, "User confirmed restart — completing flexible update")
        analytics.event(
            EVENT_COMPLETED,
            versionParams() + mapOf("update_type" to "flexible"),
        )
        appUpdateManager.completeUpdate()
    }

    // ------------------------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------------------------

    private fun startImmediateUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        currentUpdateType = AppUpdateType.IMMEDIATE
        _updateState.value = AppUpdateState.UpdateInProgress
        analytics.event(
            EVENT_STARTED,
            versionParams() + mapOf("update_type" to "immediate"),
        )
        // Persist the "pending immediate update" — survives a process kill by Play Store.
        // detectCompletedImmediateUpdate() in the next process compares target vs actual.
        savePendingImmediateUpdate(targetVersionCode = appUpdateInfo.availableVersionCode())
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
        )
    }

    private fun startFlexibleUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        currentUpdateType = AppUpdateType.FLEXIBLE

        // Register the listener BEFORE starting the flow (Play Core requirement).
        // Remove it after a terminal state to avoid a leak.
        val listener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    logger.debug(TAG, "Flexible update downloaded — ready to install")
                    analytics.event(
                        EVENT_DOWNLOADED,
                        versionParams() + mapOf("update_type" to "flexible"),
                    )
                    _updateState.value = AppUpdateState.UpdateDownloaded
                }

                InstallStatus.INSTALLED -> {
                    logger.debug(TAG, "Flexible update installed")
                    analytics.event(
                        EVENT_COMPLETED,
                        versionParams() + mapOf("update_type" to "flexible"),
                    )
                    unregisterInstallStateListener()
                    _updateState.value = AppUpdateState.Idle
                }

                InstallStatus.FAILED -> {
                    logger.error(TAG, "Flexible update installation failed (installStatus=FAILED)")
                    analytics.event(
                        EVENT_FAILED,
                        versionParams() + mapOf(
                            "update_type" to "flexible",
                            "result_code" to "install_status_failed",
                        ),
                    )
                    unregisterInstallStateListener()
                    _updateState.value = AppUpdateState.Error(
                        IllegalStateException("Flexible update installation failed"),
                    )
                }

                InstallStatus.CANCELED -> {
                    logger.debug(TAG, "Flexible update canceled (installStatus=CANCELED)")
                    analytics.event(
                        EVENT_CANCELED,
                        versionParams() + mapOf("update_type" to "flexible"),
                    )
                    unregisterInstallStateListener()
                }

                else -> {
                    // PENDING / DOWNLOADING / INSTALLING — intermediate, not reported to analytics.
                    logger.debug(TAG, "Flexible update install status: ${state.installStatus()}")
                }
            }
        }

        installStateListener = listener
        appUpdateManager.registerListener(listener)

        _updateState.value = AppUpdateState.UpdateInProgress
        analytics.event(
            EVENT_STARTED,
            versionParams() + mapOf("update_type" to "flexible"),
        )

        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
        )
    }

    private fun unregisterInstallStateListener() {
        installStateListener?.let { listener ->
            runCatching { appUpdateManager.unregisterListener(listener) }
                .onFailure { logger.debug(TAG, "unregisterListener failed (already unregistered?): ${it.message}") }
            installStateListener = null
        }
    }

    private suspend fun getAppUpdateInfo(): AppUpdateInfo = suspendCancellableCoroutine { cont ->
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun logUpdateAvailable(updateType: String, appUpdateInfo: AppUpdateInfo) {
        analytics.event(
            EVENT_AVAILABLE,
            versionParams() + mapOf(
                "update_type" to updateType,
                "available_version_code" to appUpdateInfo.availableVersionCode().toString(),
            ),
        )
    }

    // ERROR_APP_NOT_OWNED (-10) — APK not from Play Store (sideload, alt-store, Internal Testing,
    // multi-user devices where the app was installed by a different Google account).
    // ERROR_PLAY_STORE_NOT_FOUND (-9) — device without Play Store.
    // ERROR_API_NOT_AVAILABLE (-3) — device does not support the API.
    // These are expected states, not Crashlytics-worthy bugs — log to logcat and send analytics
    // (app_update_unavailable) to see how many users have no in-app update.
    private fun logFailure(error: Throwable, message: String, source: String) {
        val installEx = error as? InstallException
        val errorCode = installEx?.errorCode
        val expected = errorCode in EXPECTED_INSTALL_ERROR_CODES

        if (expected) {
            logger.debug(TAG, "$message (expected: $errorCode)")
            analytics.event(
                EVENT_UNAVAILABLE,
                versionParams() + mapOf(
                    "source" to source,
                    "error_code" to errorCode.toString(),
                    "reason" to reasonForCode(errorCode),
                ),
            )
        } else {
            logger.error(TAG, message, error)
            analytics.event(
                EVENT_UNEXPECTED_ERROR,
                versionParams() + mapOf(
                    "source" to source,
                    "error_class" to (error::class.simpleName ?: "Unknown"),
                    "error_code" to (errorCode?.toString() ?: "n/a"),
                ),
            )
        }
    }

    private fun reasonForCode(code: Int?): String = when (code) {
        InstallErrorCode.ERROR_APP_NOT_OWNED -> "app_not_owned"
        InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND -> "no_play_store"
        InstallErrorCode.ERROR_API_NOT_AVAILABLE -> "api_not_available"
        else -> "other"
    }

    /** Version params added to every app_update_* event. */
    private fun versionParams(): Map<String, Any> = mapOf(
        "app_version_code" to versionCode,
        "app_version_name" to versionName,
    )

    private fun savePendingImmediateUpdate(targetVersionCode: Int) {
        runCatching {
            prefs.edit()
                .putBoolean(KEY_PENDING_IMMEDIATE, true)
                .putInt(KEY_PENDING_TARGET_CODE, targetVersionCode)
                .putInt(KEY_PENDING_FROM_CODE, versionCode)
                .putString(KEY_PENDING_FROM_NAME, versionName)
                .putLong(KEY_PENDING_STARTED_AT, System.currentTimeMillis())
                .apply()
        }.onFailure { logger.debug(TAG, "Failed to persist pending immediate update: ${it.message}") }
    }

    /**
     * On process start, check whether a pending immediate update is "hanging":
     *  - currentVersion >= target → completion success, send app_update_completed.
     *  - currentVersion == from (no shift) and > 7d elapsed → timeout, clear silently.
     *  - currentVersion > from but < target → intermediate install, still counts as completion.
     *  - flag absent → do nothing.
     */
    private fun detectCompletedImmediateUpdate() {
        runCatching {
            if (!prefs.getBoolean(KEY_PENDING_IMMEDIATE, false)) return@runCatching

            val targetCode = prefs.getInt(KEY_PENDING_TARGET_CODE, 0)
            val fromCode = prefs.getInt(KEY_PENDING_FROM_CODE, 0)
            val fromName = prefs.getString(KEY_PENDING_FROM_NAME, "unknown") ?: "unknown"
            val startedAt = prefs.getLong(KEY_PENDING_STARTED_AT, 0L)
            val ageMs = System.currentTimeMillis() - startedAt

            when {
                versionCode >= targetCode && targetCode > 0 -> {
                    val outcome = if (versionCode == targetCode) "exact" else "intermediate"
                    logger.debug(
                        TAG,
                        "Detected completed IMMEDIATE update $fromCode → $versionCode (target=$targetCode, $outcome)",
                    )
                    analytics.event(
                        EVENT_COMPLETED,
                        versionParams() + mapOf(
                            "update_type" to "immediate",
                            "from_version_code" to fromCode.toString(),
                            "from_version_name" to fromName,
                            "target_version_code" to targetCode.toString(),
                            "outcome" to outcome,
                            "duration_ms" to ageMs.toString(),
                        ),
                    )
                    clearPendingImmediateUpdate()
                }

                ageMs > PENDING_TIMEOUT.inWholeMilliseconds -> {
                    logger.debug(TAG, "Pending immediate update timeout (age=${ageMs}ms), clearing without event")
                    clearPendingImmediateUpdate()
                }

                else -> {
                    logger.debug(
                        TAG,
                        "Pending immediate update still in flight (current=$versionCode, target=$targetCode, age=${ageMs}ms)",
                    )
                }
            }
        }.onFailure { logger.debug(TAG, "detectCompletedImmediateUpdate failed: ${it.message}") }
    }

    private fun clearPendingImmediateUpdate() {
        runCatching {
            prefs.edit()
                .remove(KEY_PENDING_IMMEDIATE)
                .remove(KEY_PENDING_TARGET_CODE)
                .remove(KEY_PENDING_FROM_CODE)
                .remove(KEY_PENDING_FROM_NAME)
                .remove(KEY_PENDING_STARTED_AT)
                .apply()
        }
    }

    companion object {
        private const val TAG = "AppUpdateController"
        private const val PREFS_NAME = "app_update_state"
        private const val KEY_PENDING_IMMEDIATE = "pending_immediate"
        private const val KEY_PENDING_TARGET_CODE = "pending_target_code"
        private const val KEY_PENDING_FROM_CODE = "pending_from_code"
        private const val KEY_PENDING_FROM_NAME = "pending_from_name"
        private const val KEY_PENDING_STARTED_AT = "pending_started_at"
        private val PENDING_TIMEOUT = 7.days

        // Analytics event keys (not user-facing — allowed as literals per project rules).
        private const val EVENT_CHECK_STARTED = "app_update_check_started"
        private const val EVENT_CHECK_NO_UPDATE = "app_update_check_no_update"
        private const val EVENT_AVAILABLE = "app_update_available"
        private const val EVENT_STARTED = "app_update_started"
        private const val EVENT_DOWNLOADED = "app_update_downloaded"
        private const val EVENT_COMPLETED = "app_update_completed"
        private const val EVENT_CANCELED = "app_update_canceled"
        private const val EVENT_FAILED = "app_update_failed"
        private const val EVENT_UNAVAILABLE = "app_update_unavailable"
        private const val EVENT_UNEXPECTED_ERROR = "app_update_unexpected_error"

        private val EXPECTED_INSTALL_ERROR_CODES = setOf(
            InstallErrorCode.ERROR_APP_NOT_OWNED,
            InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
            InstallErrorCode.ERROR_API_NOT_AVAILABLE,
        )
    }
}

private fun AppUpdateInfo.isImmediateUpdateAvailable(): Boolean {
    return updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
        isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
}

private fun AppUpdateInfo.isFlexibleUpdateAvailable(): Boolean {
    return updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
        isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
}
