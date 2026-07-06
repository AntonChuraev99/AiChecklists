package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

/**
 * iOS: [path] is a local file:// URI — already valid locally, return as-is.
 */
internal actual fun resolveAttachmentLocalPath(path: String, storagePath: String?): String = path
