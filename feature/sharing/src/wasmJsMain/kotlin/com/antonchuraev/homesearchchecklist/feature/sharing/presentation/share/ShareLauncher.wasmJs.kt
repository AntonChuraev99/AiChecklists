@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.js.Promise
import kotlinx.coroutines.await

// ---------------------------------------------------------------------------
// JS bridge — delegates to globalThis.__shareText / __copyToClipboard in init.js.template
// ---------------------------------------------------------------------------

/**
 * Calls globalThis.__shareText(title, text).
 * Returns a Promise<Boolean> — true if native Web Share API was used, false if clipboard fallback.
 *
 * Transient activation: __shareText starts navigator.share() *synchronously* on the JS side
 * (it calls navigator.share() before any await). LaunchedEffect fires in the same Compose frame
 * as the onClick that changed textContent/pdfFilePath, so browser transient activation is intact.
 */
@JsFun("""(title, text) => {
    try {
        return globalThis.__shareText(title, text);
    } catch (e) {
        console.error('[ShareLauncher] __shareText call failed:', e);
        return Promise.resolve(false);
    }
}""")
private external fun jsShareText(title: String, text: String): Promise<JsAny?>

@JsFun("(v) => v === true")
private external fun jsAnyToBoolean(v: JsAny): Boolean

@JsFun("(msg) => { console.log(msg); }")
private external fun jsLog(msg: String)

@JsFun("(msg) => { console.warn(msg); }")
private external fun jsWarn(msg: String)

// ---------------------------------------------------------------------------
// actual composable
// ---------------------------------------------------------------------------

/**
 * Web implementation of ShareLauncher.
 *
 * - pdfFilePath non-null → PDF flow was already triggered via window.print().
 *   Nothing to do here, just signal completion.
 * - textContent non-null → try navigator.share() with text; fallback to clipboard.
 *   LaunchedEffect re-runs whenever textContent changes (new share request).
 */
@Composable
actual fun ShareLauncher(
    textContent: String?,
    pdfFilePath: String?,
    onShareComplete: () -> Unit
) {
    LaunchedEffect(textContent, pdfFilePath) {
        when {
            pdfFilePath != null -> {
                // Print dialog was already opened by PdfGenerator.generatePdf().
                // Just notify the ViewModel that the share flow is "done".
                onShareComplete()
            }
            textContent != null -> {
                runCatching {
                    val resultObj: JsAny? = jsShareText("Gisti Checklist", textContent)
                        .unsafeCast<Promise<JsAny?>>()
                        .await<JsAny?>()
                    val usedNativeShare = resultObj?.let { jsAnyToBoolean(it) } ?: false
                    if (usedNativeShare) {
                        jsLog("[ShareLauncher] native Web Share API used")
                    } else {
                        jsLog("[ShareLauncher] clipboard fallback used")
                    }
                }.onFailure { e ->
                    jsWarn("[ShareLauncher] share failed: ${e.message}")
                }
                onShareComplete()
            }
        }
    }
}
