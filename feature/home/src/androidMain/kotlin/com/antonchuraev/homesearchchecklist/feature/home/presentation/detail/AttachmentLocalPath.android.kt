package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

/**
 * Android: [path] is the absolute file path inside filesDir — already valid locally, return as-is.
 */
internal actual fun resolveAttachmentLocalPath(path: String, storagePath: String?): String = path
