package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun ShareLauncher(
    textContent: String?,
    pdfFilePath: String?,
    onShareComplete: () -> Unit
) {
    LaunchedEffect(textContent, pdfFilePath) {
        val itemsToShare = mutableListOf<Any>()

        textContent?.let { itemsToShare.add(it) }
        pdfFilePath?.let { path ->
            val url = NSURL.fileURLWithPath(path)
            itemsToShare.add(url)
        }

        if (itemsToShare.isNotEmpty()) {
            val activityController = UIActivityViewController(
                activityItems = itemsToShare,
                applicationActivities = null
            )

            UIApplication.sharedApplication.keyWindow?.rootViewController?.let { rootController ->
                rootController.presentViewController(
                    activityController,
                    animated = true,
                    completion = null
                )
            }
        }

        onShareComplete()
    }
}
