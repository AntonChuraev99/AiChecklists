@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

/**
 * The browser's IANA zone from `Intl.DateTimeFormat().resolvedOptions().timeZone`, or null.
 * Any JS failure (no Intl, a throwing polyfill) is swallowed on the JS side and reads as null.
 */
@JsFun(
    """() => {
        try {
            const z = Intl.DateTimeFormat().resolvedOptions().timeZone;
            return (typeof z === 'string' && z.length > 0) ? z : null;
        } catch (e) {
            return null;
        }
    }"""
)
private external fun jsResolvedTimeZone(): String?

/**
 * wasmJs [DeviceTimeZoneProvider]: kotlinx-datetime on Wasm reports the fixed-offset `SYSTEM` zone,
 * which is not an IANA id, so the zone comes from `Intl` instead. Read on every call (cheap) so a
 * zone change while the tab is open reaches the next foreground sync.
 *
 * `Etc/Unknown` is what Chromium's ICU answers when it cannot detect the host zone: it passes the
 * `Area/Location` shape check but the server's `zoneinfo` does not know it (400 `invalid_timezone`),
 * so it reads as "no zone" — the zone is then simply not sent.
 */
internal class BrowserDeviceTimeZoneProvider(private val logger: AppLogger) : DeviceTimeZoneProvider {
    private var lastUnusable: String? = null

    override fun ianaZoneId(): String? {
        val raw = jsResolvedTimeZone()
        val zone = raw?.takeIf { it != UNKNOWN_ZONE }?.let(::ianaZoneOrNull)
        if (zone == null) {
            val shown = raw ?: "<none>"
            // Once per distinct value: this runs on every app foreground.
            if (lastUnusable != shown) {
                lastUnusable = shown
                logger.warning(TAG, "browser time zone unusable ('$shown') — reminder zone not sent")
            }
        } else {
            lastUnusable = null
        }
        return zone
    }

    private companion object {
        const val TAG = "DeviceTimeZone"
        const val UNKNOWN_ZONE = "Etc/Unknown"
    }
}
