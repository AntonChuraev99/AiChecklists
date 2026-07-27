package com.antonchuraev.homesearchchecklist.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A parsed gallery deep-link: the template to create plus the campaign that brought the user.
 *
 * [utm] is captured at parse time because that is the ONLY moment it exists — the query string is
 * gone from the app's state a frame later, and Amplitude's campaign autocapture is deliberately
 * OFF (see [com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams.UTM_SOURCE]).
 * Dropping it here is what made every gallery install look like direct traffic.
 *
 * @property slug the gallery template slug (Firestore doc id / landing URL segment).
 * @property utm whitelisted utm_* params (see AnalyticsUtm.from) — empty when the link had none.
 */
data class GalleryDeepLink(
    val slug: String,
    val utm: Map<String, String> = emptyMap(),
)

/**
 * App-scoped, platform-agnostic hand-off for a gallery deep-link
 * (`https://app.gisti-ai.com/?g=create&template={slug}`).
 *
 * Platform entry points parse the link and push it in via [submit]:
 * - wasmJs `main.kt` parses `kotlinx.browser.window.location.search` at boot,
 * - Android `MainActivity` reads `intent.data` query params in `onCreate` (cold) and
 *   `onNewIntent` (warm).
 *
 * `App.kt` observes [pending] in a `LaunchedEffect`, and on a non-null link emits
 * `gallery_deeplink_opened`, invokes `CreateChecklistFromGalleryTemplateUseCase` → navigates to
 * the created checklist (or emits `gallery_deeplink_failed` + shows a snackbar), then calls
 * [consume] so a recompose / re-collect never re-fires it.
 *
 * A [StateFlow] (not a Channel) is used deliberately: a link submitted BEFORE App.kt's collector
 * mounts (cold start — the platform entry runs first) is retained as the latest value and picked
 * up when the collector starts. [consume] resets to null; a subsequent distinct link (e.g. warm
 * `onNewIntent`) re-emits normally.
 */
class PendingGalleryDeepLink {

    private val _pending = MutableStateFlow<GalleryDeepLink?>(null)
    val pending: StateFlow<GalleryDeepLink?> = _pending.asStateFlow()

    /** Called by a platform entry point with a parsed gallery link. Latest wins. */
    fun submit(link: GalleryDeepLink) {
        _pending.value = link
    }

    /**
     * Called by App.kt after handling [handled] so it is not processed twice.
     *
     * Compare-and-set, not a blind `value = null`: a warm `onNewIntent` can submit a NEWER link
     * while the previous one is still being created, and an unconditional clear would swallow it
     * silently.
     *
     * ⚠️ This alone does not make the call safe from a `finally` — on cancellation the stored link
     * still equals [handled], so the CAS would succeed and drop it. The caller additionally skips
     * the call when its coroutine was cancelled; see the `isActive` guard in `App.kt`.
     */
    fun consume(handled: GalleryDeepLink) {
        _pending.compareAndSet(expect = handled, update = null)
    }
}
