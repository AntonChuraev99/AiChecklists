package com.antonchuraev.homesearchchecklist.attribution

import android.content.Context
import android.content.SharedPreferences
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import java.net.URLDecoder

/**
 * Captures the Google Play Install Referrer (utm_source / utm_medium / utm_campaign / utm_content /
 * utm_term + gclid) ONCE per install and publishes it two ways:
 *  - as user-properties via [AnalyticsTracker.setUserProperties] — sticky segmentation dimensions,
 *    what today's dashboards read;
 *  - as the one-shot [AnalyticsEvents.Attribution.INSTALL_ATTRIBUTED] event, fired for EVERY
 *    install including organic ones. The properties never expire and carry no date, so they cannot
 *    answer "who arrived from ads on this day"; the event can, because it is timestamped.
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
 *  - SERVICE_UNAVAILABLE / SERVICE_DISCONNECTED, or a thrown RemoteException while reading -> do
 *    NOT mark captured, so the next cold start retries. Both codes are transient; marking either
 *    one captured loses that install's attribution forever, with no event and nothing to grep.
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
    /**
     * Test seam. The Play library builds its client through a static factory, so without this the
     * only reachable response codes in a host test are the ones a machine without Play Services
     * happens to produce — never OK with a real referrer payload. Production callers keep the
     * default and the real client.
     */
    private val clientFactory: (Context) -> InstallReferrerClient = {
        InstallReferrerClient.newBuilder(it).build()
    },
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
            val referrerClient = clientFactory(context)
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
                    // Unconditional, including the organic/empty case: this event is the acquisition
                    // timeline itself, and skipping the unattributed installs would leave the paid
                    // cohort without the denominator it is judged against.
                    analytics.event(
                        AnalyticsEvents.Attribution.INSTALL_ATTRIBUTED,
                        attributionEventParams(props),
                    )
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

            InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE,
            InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED,
            -> {
                // Both are TRANSIENT (service down / the binding died mid-call) — do NOT mark
                // captured, so the next cold start retries. SERVICE_DISCONNECTED used to fall into
                // the `else` below and burn the one-shot: the flag was set, no event had been sent,
                // and the install lost its attribution permanently and silently.
                logger?.warning(TAG, "install referrer transient failure code=$responseCode — will retry next start")
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
     * Maps the captured referrer keys onto the event's param names, dropping every key the referrer
     * did not carry.
     *
     * No defaults on purpose. An absent campaign written as `""` / `"organic"` / `"unknown"` is
     * indistinguishable in Amplitude from a real value of that name: it silently inflates whichever
     * segment it lands in, and no report reveals that the value was invented. An absent key is
     * filterable; a defaulted one is not.
     */
    private fun attributionEventParams(props: Map<String, Any>): Map<String, Any> {
        val params = LinkedHashMap<String, Any>()
        EVENT_PARAM_BY_REFERRER_KEY.forEach { (referrerKey, eventParam) ->
            props[referrerKey]?.let { params[eventParam] = it }
        }
        // Always present — see AnalyticsParams.IS_PAID for why the verdict follows the gclid and
        // never the medium.
        params[AnalyticsParams.IS_PAID] = props.containsKey(AnalyticsParams.GCLID)
        return params
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

        /**
         * Referrer key -> event-param name. Single list: [TRACKED_KEYS] is derived from it, so a
         * key can never be parsed-and-persisted as a user-property while silently missing from the
         * event (or the reverse).
         */
        val EVENT_PARAM_BY_REFERRER_KEY: Map<String, String> = linkedMapOf(
            AnalyticsParams.UTM_SOURCE to AnalyticsParams.SOURCE,
            AnalyticsParams.UTM_MEDIUM to AnalyticsParams.MEDIUM,
            AnalyticsParams.UTM_CAMPAIGN to AnalyticsParams.CAMPAIGN,
            AnalyticsParams.UTM_CONTENT to AnalyticsParams.CONTENT,
            AnalyticsParams.UTM_TERM to AnalyticsParams.TERM,
            AnalyticsParams.GCLID to AnalyticsParams.GCLID,
        )

        val TRACKED_KEYS: Set<String> = EVENT_PARAM_BY_REFERRER_KEY.keys
    }
}
