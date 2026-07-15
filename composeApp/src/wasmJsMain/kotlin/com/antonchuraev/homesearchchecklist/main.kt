package com.antonchuraev.homesearchchecklist

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.antonchuraev.homesearchchecklist.coil.OpfsImageFetcher
import com.antonchuraev.homesearchchecklist.coil.OpfsKeyer
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsUtm
import com.antonchuraev.homesearchchecklist.deeplink.GalleryDeepLink
import com.antonchuraev.homesearchchecklist.deeplink.PendingGalleryDeepLink
import com.antonchuraev.homesearchchecklist.di.appModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin

/**
 * Splits a `window.location.search` string into decoded key -> value pairs.
 *
 * First occurrence of a repeated key wins. Percent-escapes are decoded via `decodeURIComponent`
 * ('+' is mapped to a space first — the browser does NOT treat it as a space, but the gallery's
 * generated links and most campaign tooling do). Never throws on malformed input: a value that
 * `decodeURIComponent` rejects (e.g. a lone '%') falls back to its raw form rather than killing
 * app boot — a broken utm must never cost the user the checklist they came for.
 */
private fun parseQuery(search: String): Map<String, String> {
    val query = search.removePrefix("?")
    if (query.isBlank()) return emptyMap()
    val params = HashMap<String, String>()
    for (pair in query.split("&")) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val key = decodeOrRaw(pair.substring(0, eq))
        if (!params.containsKey(key)) params[key] = decodeOrRaw(pair.substring(eq + 1))
    }
    return params
}

private fun decodeOrRaw(raw: String): String =
    runCatching { decodeUriComponent(raw.replace("+", " ")) }.getOrDefault(raw)

/** `decodeURIComponent` — throws URIError on a malformed escape; callers must guard. */
private fun decodeUriComponent(value: String): String =
    js("decodeURIComponent(value)")

/**
 * Parses a gallery deep-link from a `window.location.search` string.
 *
 * Recognises `?g=create&template=<slug>` (e.g. from
 * `https://app.gisti-ai.com/?g=create&template=5-day-paris-packing-list&utm_source=gallery`) and
 * returns the trimmed slug plus any whitelisted utm_* params. Returns `null` when the `g=create`
 * marker is absent, `g` has any other value, or the `template` slug is missing / blank.
 */
private fun parseGalleryDeepLink(search: String): GalleryDeepLink? {
    val params = parseQuery(search)
    if (params["g"] != "create") return null
    val slug = params["template"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return GalleryDeepLink(slug = slug, utm = AnalyticsUtm.from { params[it] })
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = startKoin {
        modules(appModule)
    }.koin

    // Gallery deep-link (https://app.gisti-ai.com/?g=create&template={slug}&utm_*): parse the link
    // at boot and hand it to PendingGalleryDeepLink. The holder is a StateFlow, so a link submitted
    // before ComposeViewport mounts is retained; App.kt's collector picks it up, emits the funnel
    // events, invokes CreateChecklistFromGalleryTemplateUseCase, navigates, then consumes.
    // utm_* is captured HERE because this is the only place it exists — the query string is not
    // part of any later app state, and Amplitude campaign autocapture is deliberately off.
    parseGalleryDeepLink(window.location.search)?.let { link ->
        koin.get<PendingGalleryDeepLink>().submit(link)
    }

    // Teach the singleton Coil ImageLoader how to read "opfs://..." attachment paths
    // (OPFS-backed image previews). setSafe never overwrites an already-created loader,
    // so this is a no-op if Compose has somehow initialized one first.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(OpfsImageFetcher.Factory())
                add(OpfsKeyer())
            }
            // Diagnostic: surface Coil's decode/fetch errors to the browser console so a failed
            // OPFS image preview (BrokenImage) shows WHY (decoder error vs missing fetcher).
            .logger(coil3.util.DebugLogger())
            .build()
    }

    ComposeViewport(document.body!!) {
        App()
    }
}
