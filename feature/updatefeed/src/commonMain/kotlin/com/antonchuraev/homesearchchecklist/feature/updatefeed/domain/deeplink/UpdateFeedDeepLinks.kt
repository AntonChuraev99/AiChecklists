package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink

/**
 * Centralized deeplinks used in Update Feed posts. Source of truth for
 * cross-referencing between UpdateFeedContent JSON, DeepLinkHandler, and
 * UI lock state (e.g. premium-gated CTAs).
 */
internal object UpdateFeedDeepLinks {
    const val CREATE_WEEKLY = "gisti://create?viewMode=weekly"
}
