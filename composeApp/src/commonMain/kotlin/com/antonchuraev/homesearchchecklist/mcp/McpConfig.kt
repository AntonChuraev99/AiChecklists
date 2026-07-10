package com.antonchuraev.homesearchchecklist.mcp

/**
 * Technical (non-localized) constants for the Gisti MCP screen.
 *
 * Mirrors the [com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig]
 * pattern: infrastructure URLs live in code, NOT in `strings.xml`, because they are
 * server endpoints / documentation links, not user-facing copy that would ever be
 * localized.
 */
object McpConfig {
    /** Remote MCP server endpoint the user copies into their MCP client. */
    const val MCP_ENDPOINT_URL = "https://gisti-mcp.gisti.workers.dev/mcp"

    /** Public connection guide opened by the "Connection guide" CTA (external browser). */
    const val CONNECTION_GUIDE_URL = "https://gisti-ai.com/mcp"
}
