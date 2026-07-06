package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

/**
 * Resolves the LOCAL path an image loader (Coil) or OPFS reader should use to render an attachment.
 *
 * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment.path] is
 * platform-specific (Android absolute file path / iOS file:// URI / web `opfs://…`) yet is synced
 * AS-IS through Firestore. So an attachment captured on Android and opened on the web carries an
 * Android `/data/user/0/…` path that the web OPFS fetcher cannot read — the loader falls through to
 * the default file-system fetcher which throws "Javascript does not have access to the device's file
 * system" (decode-stage failure). This resolver lets each platform reconstruct a path it can read
 * locally instead of trusting the foreign synced [path].
 *
 * The platform-independent [storagePath] (Firebase Storage object key
 * `users/<uid>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>`) is the reliable cross-device
 * anchor; the web actual derives its `opfs://attachments/<fillId>/<itemId>/<attachmentId>.<ext>`
 * local path from it (see `AttachmentCloudStorage.wasmJs.kt` path contract).
 *
 * MUST be used at every site that (a) feeds the path to Coil `.data(...)`, (b) probes/downloads the
 * local bytes, or (c) opens the file externally — otherwise the download target and the read model
 * diverge. Android/iOS actuals return [path] unchanged (it is already valid there).
 */
internal expect fun resolveAttachmentLocalPath(path: String, storagePath: String?): String
