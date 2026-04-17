package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator

/**
 * Parses gisti:// deeplinks and delegates to AppNavigator.
 * Uses a manual URI parser to avoid java.net.URI (not available in all KMP targets).
 */
class UpdateFeedDeepLinkHandler(private val navigator: AppNavigator) {

    fun handle(deepLink: String?): Boolean {
        if (deepLink.isNullOrBlank()) return false
        val uri = parseUri(deepLink) ?: return false
        if (uri.scheme != "gisti") return false
        return when (uri.host) {
            "paywall" -> {
                navigator.navigateToPaywall(uri.queryParam("source") ?: "update_feed")
                true
            }
            "templates" -> {
                navigator.navigateToTemplatesScreen()
                true
            }
            "analyze" -> {
                navigator.navigateToAnalyzeScreen()
                true
            }
            "create" -> {
                navigator.navigateToCreateChecklistScreen(templateId = null)
                true
            }
            "subscription_status" -> {
                navigator.navigateToSubscriptionStatus()
                true
            }
            "update_feed" -> {
                navigator.navigateToUpdateFeed()
                true
            }
            else -> false
        }
    }

    private fun parseUri(raw: String): SimpleUri? {
        // Expected format: scheme://host[?key=value&key2=value2]
        val schemeSeparator = "://"
        val schemeEnd = raw.indexOf(schemeSeparator)
        if (schemeEnd < 0) return null
        val scheme = raw.substring(0, schemeEnd)
        val rest = raw.substring(schemeEnd + schemeSeparator.length)
        val queryStart = rest.indexOf('?')
        val host = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val queryString = if (queryStart >= 0) rest.substring(queryStart + 1) else ""
        val queryParams = if (queryString.isNotEmpty()) {
            queryString.split("&").mapNotNull { param ->
                val eqIndex = param.indexOf('=')
                if (eqIndex > 0) {
                    param.substring(0, eqIndex) to param.substring(eqIndex + 1)
                } else null
            }.toMap()
        } else emptyMap()
        return SimpleUri(scheme = scheme, host = host, queryParams = queryParams)
    }

    private data class SimpleUri(
        val scheme: String,
        val host: String,
        val queryParams: Map<String, String>
    ) {
        fun queryParam(key: String): String? = queryParams[key]
    }
}
