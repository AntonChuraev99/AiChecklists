package com.antonchuraev.homesearchchecklist

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.antonchuraev.homesearchchecklist.coil.OpfsImageFetcher
import com.antonchuraev.homesearchchecklist.coil.OpfsKeyer
import com.antonchuraev.homesearchchecklist.deeplink.PendingGalleryDeepLink
import com.antonchuraev.homesearchchecklist.di.appModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin

/**
 * Parses a gallery deep-link slug from a `window.location.search` string.
 *
 * Recognises `?g=create&template=<slug>` (e.g. from
 * `https://app.gisti-ai.com/?g=create&template=5-day-paris-packing-list`) and returns the
 * trimmed, non-blank slug. Returns `null` when the `g=create` marker is absent, `g` has any
 * other value, or the `template` slug is missing / blank. Never throws on malformed input.
 */
private fun parseGalleryDeepLinkSlug(search: String): String? {
    val query = search.removePrefix("?")
    if (query.isBlank()) return null
    val params = HashMap<String, String>()
    for (pair in query.split("&")) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val key = pair.substring(0, eq)
        if (!params.containsKey(key)) params[key] = pair.substring(eq + 1)
    }
    if (params["g"] != "create") return null
    return params["template"]?.trim()?.takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = startKoin {
        modules(appModule)
    }.koin

    // Gallery deep-link (https://app.gisti-ai.com/?g=create&template={slug}): parse the slug at
    // boot and hand it to PendingGalleryDeepLink. The holder is a StateFlow, so a slug submitted
    // before ComposeViewport mounts is retained; App.kt's collector picks it up, invokes
    // CreateChecklistFromGalleryTemplateUseCase, navigates to the created checklist, then consumes.
    parseGalleryDeepLinkSlug(window.location.search)?.let { slug ->
        koin.get<PendingGalleryDeepLink>().submit(slug)
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
