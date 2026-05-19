package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePickerLauncher(
    type: FilePickerType,
    onResult: (FilePickerResult?) -> Unit
): FilePickerLauncher {
    val utTypes = when (type) {
        FilePickerType.IMAGE -> listOf(UTTypeImage)
        FilePickerType.PDF -> listOf(UTTypePDF)
        FilePickerType.TEXT -> listOf(UTTypePlainText, UTTypeText)
        FilePickerType.AUDIO -> listOf(UTTypeAudio)
    }

    return remember(type) {
        FilePickerLauncher {
            showDocumentPicker(utTypes, onResult)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun showDocumentPicker(
    utTypes: List<UTType?>,
    onResult: (FilePickerResult?) -> Unit
) {
    val validTypes = utTypes.filterNotNull()
    if (validTypes.isEmpty()) {
        onResult(null)
        return
    }

    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = validTypes
    )

    val delegate = DocumentPickerDelegate(onResult)
    picker.delegate = delegate
    picker.allowsMultipleSelection = false

    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate(
    private val onResult: (FilePickerResult?) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url != null) {
            val result = FilePickerResult(
                filePath = url.absoluteString ?: "",
                fileName = url.lastPathComponent ?: "unknown",
                mimeType = null
            )
            onResult(result)
        } else {
            onResult(null)
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}
