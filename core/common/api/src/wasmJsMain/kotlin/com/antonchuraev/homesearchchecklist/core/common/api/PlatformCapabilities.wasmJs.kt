package com.antonchuraev.homesearchchecklist.core.common.api

actual object PlatformCapabilities {
    // Web attachments deferred to v2 (OPFS file API + UI scope). Pending: docs/todos/
    actual val attachmentsSupported: Boolean = false
}
