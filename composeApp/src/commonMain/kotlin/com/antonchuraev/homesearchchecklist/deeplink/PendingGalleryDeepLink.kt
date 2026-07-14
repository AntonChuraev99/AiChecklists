package com.antonchuraev.homesearchchecklist.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped, platform-agnostic hand-off for a gallery deep-link
 * (`https://app.gisti-ai.com/?g=create&template={slug}`).
 *
 * Platform entry points parse the slug and push it in via [submit]:
 * - wasmJs `main.kt` parses `kotlinx.browser.window.location.search` at boot,
 * - Android `MainActivity` reads `intent.data` query params in `onCreate` (cold) and
 *   `onNewIntent` (warm).
 *
 * `App.kt` observes [pending] in a `LaunchedEffect`, and on a non-null slug invokes
 * `CreateChecklistFromGalleryTemplateUseCase` → navigates to the created checklist (or shows a
 * snackbar on NotFound/Error), then calls [consume] so a recompose / re-collect never re-fires it.
 *
 * A [StateFlow] (not a Channel) is used deliberately: a slug submitted BEFORE App.kt's collector
 * mounts (cold start — the platform entry runs first) is retained as the latest value and picked
 * up when the collector starts. [consume] resets to null; a subsequent distinct slug (e.g. warm
 * `onNewIntent`) re-emits normally.
 */
class PendingGalleryDeepLink {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Called by a platform entry point with a parsed gallery slug. Latest wins. */
    fun submit(slug: String) {
        _pending.value = slug
    }

    /** Called by App.kt after handling a slug so it is not processed twice. */
    fun consume() {
        _pending.value = null
    }
}
