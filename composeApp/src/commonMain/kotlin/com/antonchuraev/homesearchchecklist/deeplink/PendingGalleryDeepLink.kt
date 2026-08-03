package com.antonchuraev.homesearchchecklist.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

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
 * `App.kt` observes [pending] in a `LaunchedEffect` and hands each non-null link to
 * [handleGalleryDeepLink], which emits `gallery_deeplink_opened` (once per arrival, gated by
 * [markOpenedReported]), invokes `CreateChecklistFromGalleryTemplateUseCase` → navigates to the
 * created checklist (or emits `gallery_deeplink_failed` + shows a snackbar), then calls [consume]
 * so a recompose / re-collect never re-fires it.
 *
 * A [StateFlow] (not a Channel) is used deliberately: a link submitted BEFORE App.kt's collector
 * mounts (cold start — the platform entry runs first) is retained as the latest value and picked
 * up when the collector starts. [consume] resets to null; a subsequent distinct link (e.g. warm
 * `onNewIntent`) re-emits normally.
 */
class PendingGalleryDeepLink {

    private val _pending = MutableStateFlow<GalleryDeepLink?>(null)
    val pending: StateFlow<GalleryDeepLink?> = _pending.asStateFlow()

    /**
     * The arrival for which `gallery_deeplink_opened` has already been reported, or null when the
     * next arrival still owes one. Deliberately app-scoped state and NOT a `remember` in the
     * composable: the whole point is to survive the collector that reports it.
     *
     * A [MutableStateFlow] purely as a CAS-capable reference (`getAndUpdate` is atomic) — nothing
     * observes it, mirroring the compare-and-set already used by [consume].
     */
    private val openedReported = MutableStateFlow<GalleryDeepLink?>(null)

    /** Called by a platform entry point with a parsed gallery link. Latest wins. */
    fun submit(link: GalleryDeepLink) {
        _pending.value = link
    }

    /**
     * Claims the right to emit `gallery_deeplink_opened` for [link] — true exactly once per
     * arrival, for the caller that got there first.
     *
     * Why this exists: `opened` is emitted BEFORE the create suspends, while [consume] is
     * deliberately skipped when the handling coroutine is cancelled (Activity recreation mid-create
     * — see the `isActive` guard in `App.kt`). Those two facts together mean the next collector
     * re-processes the same link and, before this guard, re-emitted `opened`. Prod 2026-07-28:
     * 7 `gallery_deeplink_opened` -> 1 `checklist_created` -> 0 `failed`, across ~1.5 unique users.
     * Not 6 lost people — 1-2 people counted several times, which made the documented funnel
     * contract (`opened = created + failed`) unreachable by construction.
     *
     * NOT reset by [submit]: on Android a config-change recreate re-delivers the same launch intent
     * and re-submits an EQUAL link, so resetting there would re-open the very hole this closes.
     * It is reset by [consume] — the arrival is finished, so a later identical link is a genuinely
     * new arrival and gets its own `opened`.
     */
    fun markOpenedReported(link: GalleryDeepLink): Boolean =
        openedReported.getAndUpdate { link } != link

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
     *
     * Clearing [markOpenedReported]'s marker is gated on the SAME compare-and-set: if a newer link
     * had already replaced [handled], the marker belongs to that newer arrival and must survive.
     */
    fun consume(handled: GalleryDeepLink) {
        if (_pending.compareAndSet(expect = handled, update = null)) {
            openedReported.compareAndSet(expect = handled, update = null)
        }
    }
}
