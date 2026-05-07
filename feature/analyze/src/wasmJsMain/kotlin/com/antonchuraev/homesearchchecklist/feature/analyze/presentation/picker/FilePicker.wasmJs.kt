@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// JS bridge — delegates to globalThis.__pickFile defined in init.js.template
// ---------------------------------------------------------------------------

@JsFun("""(accept) => {
    try {
        return globalThis.__pickFile(accept);
    } catch (e) {
        console.error('[FilePicker] __pickFile call failed:', e);
        return Promise.resolve(null);
    }
}""")
private external fun jsPickFile(accept: String): Promise<JsAny?>

// Property accessors for the result object { path, name, mimeType, size }
@JsFun("(obj) => obj ? obj.path : null")
private external fun jsGetPath(obj: JsAny): String?

@JsFun("(obj) => obj ? obj.name : null")
private external fun jsGetName(obj: JsAny): String?

@JsFun("(obj) => obj ? (obj.mimeType || null) : null")
private external fun jsGetMimeType(obj: JsAny): String?

// ---------------------------------------------------------------------------
// MIME type mapping
// ---------------------------------------------------------------------------

private fun FilePickerType.toAccept(): String = when (this) {
    FilePickerType.IMAGE -> "image/*"
    FilePickerType.PDF -> "application/pdf"
    FilePickerType.TEXT -> "text/plain,.txt,.md"
}

// ---------------------------------------------------------------------------
// actual composable
// ---------------------------------------------------------------------------

@Composable
actual fun rememberFilePickerLauncher(
    type: FilePickerType,
    onResult: (FilePickerResult?) -> Unit
): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    val accept = remember(type) { type.toAccept() }

    return remember(type) {
        FilePickerLauncher {
            // launch() is called from an onClick — we are inside a user gesture frame.
            // We launch a coroutine immediately; the JS Promise is created synchronously
            // (jsPickFile returns a Promise right away), so transient activation is preserved.
            scope.launch {
                val resultObj = runCatching {
                    jsPickFile(accept).await<JsAny?>()
                }.getOrNull()

                if (resultObj == null) {
                    onResult(null)
                    return@launch
                }

                val path = jsGetPath(resultObj)
                val name = jsGetName(resultObj)
                val mimeType = jsGetMimeType(resultObj)

                if (path != null && name != null) {
                    onResult(
                        FilePickerResult(
                            filePath = path,
                            fileName = name,
                            mimeType = mimeType
                        )
                    )
                } else {
                    onResult(null)
                }
            }
        }
    }
}
