package com.antonchuraev.homesearchchecklist.attribution

import android.content.Context
import android.content.SharedPreferences
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import java.net.URLDecoder

/**
 * Captures the Google Play Install Referrer (utm_source / utm_medium / utm_campaign / utm_content /
 * utm_term + gclid) ONCE per install and forwards it to Amplitude/Firebase as user-properties via
 * [AnalyticsTracker.setUserProperties].
 *
 * Why this exists: the Amplitude Android/Kotlin SDK has NO built-in install-referrer / UTM
 * autocapture (that is a Browser-SDK-only feature), so ad-install cohorts were previously
 * indistinguishable from organic in Amplitude. The Play Install Referrer Library is the only
 * supported source of the encoded `referrer` string Play attaches to Google Ads / store-link
 * installs.
 *
 * One-shot: the encoded referrer is a per-INSTALL value, so we read it once, persist
 * `install_attribution/referrer_captured = true`, and never reconnect. Failure handling:
 *  - OK (incl. an organic install with no utm params) -> mark captured.
 *  - FEATURE_NOT_SUPPORTED / DEVELOPER_ERROR / PERMISSION_ERROR / unknown -> mark captured
 *    (retrying cannot help — device/store limitation or a caller bug).
 *  - SERVICE_UNAVAILABLE or a thrown RemoteException while reading -> do NOT mark captured, so the
 *    next cold start retries.
 *
 * Threading: [capture] does a SharedPreferences disk read, so callers should invoke it off the
 * cold-start main thread (GistiApplication dispatches it on its IO application scope). The
 * InstallReferrerStateListener callbacks, however, are delivered by the Play library on the MAIN
 * thread — so [capture] (IO) and the callbacks (main) touch [client]/[attemptedReconnect] from two
 * different threads. Those fields are therefore @Volatile for cross-thread visibility. Work inside
 * the callbacks is light.
 */
class InstallReferrerCapture(
    private val context: Context,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger? = null,
) {

    // Written on the IO thread in capture(), read/written on the main thread in the listener
    // callbacks — @Volatile guarantees the callback sees the client set by capture().
    @Volatile
    private var attemptedReconnect = false

    @Volatile
    private var client: InstallReferrerClient? = null

    /** Idempotent entry point. No-op once the referrer has already been captured for this install. */
    fun capture() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CAPTURED, false)) return

        runCatching {
            val referrerClient = InstallReferrerClient.newBuilder(context).build()
            client = referrerClient
            referrerClient.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    handleSetupFinished(responseCode, prefs)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    // One reconnect attempt only — never loop.
                    if (attemptedReconnect) return
                    attemptedReconnect = true
                    runCatching { client?.startConnection(this) }
                        .onFailure { logger?.warning(TAG, "reconnect failed: ${it.message}") }
                }
            })
        }.onFailure { e ->
            logger?.error(TAG, "startConnection failed: ${e.message}", e)
            // Release the client if it was built before startConnection threw — never leak a binding.
            endConnection()
        }
    }

    private fun handleSetupFinished(responseCode: Int, prefs: SharedPreferences) {
        when (responseCode) {
            InstallReferrerClient.InstallReferrerResponse.OK -> {
                runCatching {
                    val referrerString = client?.installReferrer?.installReferrer.orEmpty()
                    val props = parseReferrer(referrerString)
                    if (props.isNotEmpty()) {
                        analytics.setUserProperties(props)
                        logger?.info(TAG, "install referrer captured: ${props.keys}")
                    } else {
                        logger?.debug(TAG, "install referrer had no utm/gclid params (organic install)")
                    }
                    // Mark captured on any successful read (empty referrer is a valid answer).
                    markCaptured(prefs)
                }.onFailure { e ->
                    // RemoteException etc. Do NOT mark captured — allow a retry on next start.
                    logger?.error(TAG, "reading install referrer failed: ${e.message}", e)
                }
                endConnection()
            }

            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                // Play Store on this device cannot return a referrer — retrying will never help.
                logger?.warning(TAG, "install referrer FEATURE_NOT_SUPPORTED — marking captured")
                markCaptured(prefs)
                endConnection()
            }

            InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                // Transient — do NOT mark captured so the next cold start retries.
                logger?.warning(TAG, "install referrer SERVICE_UNAVAILABLE — will retry next start")
                endConnection()
            }

            else -> {
                // DEVELOPER_ERROR / PERMISSION_ERROR / unknown — a caller/config bug; retry won't fix it.
                logger?.error(TAG, "install referrer setup failed: code=$responseCode")
                markCaptured(prefs)
                endConnection()
            }
        }
    }

    /**
     * Parse the URL-encoded referrer query string
     * (e.g. `utm_source=google-play&utm_medium=cpc&gclid=abc123`) into a property map, keeping only
     * the tracked utm_* / gclid keys with non-blank values.
     */
    private fun parseReferrer(raw: String): Map<String, Any> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, Any>()
        raw.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val key = decode(pair.substring(0, idx))
            val value = decode(pair.substring(idx + 1))
            if (value.isBlank()) return@forEach
            if (key in TRACKED_KEYS) result[key] = value
        }
        return result
    }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrElse { e ->
            logger?.warning(TAG, "Failed to URL-decode referrer param, using raw: $s (${e.message})")
            s
        }

    private fun markCaptured(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_CAPTURED, true).apply()
    }

    private fun endConnection() {
        runCatching { client?.endConnection() }
            .onFailure { e -> logger?.warning(TAG, "endConnection failed: ${e.message}") }
        client = null
    }

    private companion object {
        const val TAG = "InstallReferrer"
        const val PREFS = "install_attribution"
        const val KEY_CAPTURED = "referrer_captured"

        val TRACKED_KEYS: Set<String> = setOf(
            AnalyticsParams.UTM_SOURCE,
            AnalyticsParams.UTM_MEDIUM,
            AnalyticsParams.UTM_CAMPAIGN,
            AnalyticsParams.UTM_CONTENT,
            AnalyticsParams.UTM_TERM,
            AnalyticsParams.GCLID,
        )
    }
}
