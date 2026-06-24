package com.antonchuraev.homesearchchecklist

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.antonchuraev.homesearchchecklist.coil.OpfsImageFetcher
import com.antonchuraev.homesearchchecklist.coil.OpfsKeyer
import com.antonchuraev.homesearchchecklist.di.appModule
import kotlinx.browser.document
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(appModule)
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
