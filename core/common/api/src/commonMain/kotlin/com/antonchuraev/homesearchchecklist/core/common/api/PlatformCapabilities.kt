package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform-level feature flags resolved at compile time via expect/actual.
 * UI layers gate attachment entry-points on [attachmentsSupported] to avoid
 * presenting features backed by [AttachmentStorage] stubs.
 */
expect object PlatformCapabilities {
    /** True on platforms where [AttachmentStorage] has a real implementation (currently Android only). */
    val attachmentsSupported: Boolean
}
