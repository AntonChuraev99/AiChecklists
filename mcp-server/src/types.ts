/** Shared worker types for the Gisti MCP server. */
import type { OAuthHelpers } from "@cloudflare/workers-oauth-provider";

/**
 * Identity proven by the upstream Google OAuth leg, surfaced on the McpAgent as this.props.
 * The index signature satisfies McpAgent's `Record<string, unknown>` props constraint.
 */
export interface Props {
  claims: {
    sub: string;
    email: string;
    name: string;
  };
  [key: string]: unknown;
}

/** Worker bindings (wrangler.jsonc + secrets). */
export interface Env {
  /** KV store the OAuth provider uses for grants/tokens. */
  OAUTH_KV: KVNamespace;
  /** Injected by @cloudflare/workers-oauth-provider into handlers. */
  OAUTH_PROVIDER: OAuthHelpers;
  /** Durable Object namespace backing the McpAgent sessions. */
  MCP_OBJECT: DurableObjectNamespace;
  // ── secrets ──
  GOOGLE_OAUTH_CLIENT_ID: string;
  GOOGLE_OAUTH_CLIENT_SECRET: string;
  /** Service-account JSON key (one JSON string) for Firestore REST. */
  FIREBASE_SERVICE_ACCOUNT: string;
  /**
   * Amplitude project 786722 API key (analytics.ts). OPTIONAL on purpose: when it is unset the
   * server runs exactly as before, minus the `mcp_*` events — analytics must never be a hard
   * dependency of a tool call. Same value as the Cloud Functions' `AMPLITUDE_SERVER_API_KEY`.
   */
  AMPLITUDE_SERVER_API_KEY?: string;
}
