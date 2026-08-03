/**
 * Upstream OAuth leg — logs the user in with Google, then hands the proven identity
 * to @cloudflare/workers-oauth-provider via completeAuthorization(). The Worker is the
 * OAuth server to the MCP client; Google is the upstream IdP.
 *
 * Flow:
 *   GET /authorize  → parse the MCP client's request → redirect to Google consent
 *   GET /callback   → exchange code → read id_token claims → completeAuthorization()
 *
 * `state` is an OPAQUE one-time nonce backed by KV (storeAuthState/consumeAuthState): the
 * oauthReqInfo never leaves the server, and a forged/replayed nonce can't be consumed — CSRF
 * and tamper safe for the public launch.
 *
 * Instrumentation (analytics.ts): every terminal branch here emits one of `mcp_auth_started` /
 * `mcp_auth_completed` / `mcp_auth_failed`. Without them "nobody connected the MCP" and "people
 * tried and the OAuth leg rejected them" produce byte-identical data (zero `mcp_session_started`),
 * and the product decision about this server rests on telling those apart.
 */
import { Hono, type Context } from "hono";
import type { AuthRequest } from "@cloudflare/workers-oauth-provider";
import type { Env, Props } from "./types";
import { consumeAuthState, storeAuthState } from "./security";
import {
  authFlowIdentity,
  emailOnlyIdentity,
  googleTokenFailureReason,
  sendMcpEvent,
  type AnalyticsIdentity,
  type AuthFailureReason,
  type McpEvent,
} from "./analytics";

const app = new Hono<{ Bindings: Env }>();

const GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

function callbackUrl(reqUrl: string): string {
  return new URL("/callback", reqUrl).href;
}

/**
 * Fire-and-forget one auth event.
 *
 * Unlike the tool path (which runs inside a Durable Object, where `waitUntil` has no effect) this
 * runs in a plain Worker request, which CAN be torn down the moment the response is returned — so
 * `executionCtx.waitUntil` is required here, not cargo cult. It is guarded because `executionCtx`
 * throws when the handler is invoked outside a fetch event.
 */
function trackAuth(c: Context<{ Bindings: Env }>, identity: AnalyticsIdentity, event: McpEvent): void {
  const sent = sendMcpEvent(c.env, identity, event, Date.now()).catch((e) =>
    console.warn("[gisti-mcp] auth analytics failed:", e instanceof Error ? e.message : e),
  );
  try {
    c.executionCtx.waitUntil(sent);
  } catch {
    /* no execution context (non-fetch invocation) — the floating promise is the best we can do */
  }
}

/** Emit `mcp_auth_failed` and return the response for a rejected auth leg, in one call site. */
function authFailed(
  c: Context<{ Bindings: Env }>,
  reason: AuthFailureReason,
  message: string,
  status: 400 | 502,
): Response {
  trackAuth(c, authFlowIdentity(), { type: "mcp_auth_failed", reason });
  return c.text(message, status);
}

/** Decode a JWT payload (no signature check — the token came straight from Google over TLS). */
function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const part = jwt.split(".")[1] ?? "";
  let b64 = part.replace(/-/g, "+").replace(/_/g, "/");
  b64 += "=".repeat((4 - (b64.length % 4)) % 4);
  return JSON.parse(atob(b64)) as Record<string, unknown>;
}

app.get("/authorize", async (c) => {
  let oauthReqInfo: AuthRequest;
  try {
    oauthReqInfo = await c.env.OAUTH_PROVIDER.parseAuthRequest(c.req.raw);
  } catch (e) {
    // Unregistered/invalid client — a real MCP client registers via /register first.
    return authFailed(
      c,
      "invalid_request",
      `Invalid authorization request: ${e instanceof Error ? e.message : "unknown"}`,
      400,
    );
  }
  if (!oauthReqInfo.clientId) return authFailed(c, "missing_client_id", "Invalid authorization request", 400);

  const nonce = await storeAuthState(c.env.OAUTH_KV, oauthReqInfo);
  trackAuth(c, authFlowIdentity(), { type: "mcp_auth_started" });

  const url = new URL(GOOGLE_AUTH_URL);
  url.searchParams.set("client_id", c.env.GOOGLE_OAUTH_CLIENT_ID);
  url.searchParams.set("redirect_uri", callbackUrl(c.req.url));
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid email profile");
  url.searchParams.set("state", nonce);
  url.searchParams.set("access_type", "online");
  url.searchParams.set("prompt", "select_account");
  return c.redirect(url.href);
});

app.get("/callback", async (c) => {
  const code = c.req.query("code");
  const stateRaw = c.req.query("state");
  if (!code || !stateRaw) return authFailed(c, "missing_code_or_state", "Missing code or state", 400);

  const consumed = await consumeAuthState(c.env.OAUTH_KV, stateRaw);
  if (!consumed) return authFailed(c, "invalid_state", "Invalid or expired state", 400);
  const oauthReqInfo = consumed as AuthRequest;
  if (!oauthReqInfo.clientId) return authFailed(c, "state_missing_client_id", "Invalid state", 400);

  const tokenResp = await fetch(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id: c.env.GOOGLE_OAUTH_CLIENT_ID,
      client_secret: c.env.GOOGLE_OAUTH_CLIENT_SECRET,
      redirect_uri: callbackUrl(c.req.url),
      grant_type: "authorization_code",
    }),
  });
  if (!tokenResp.ok) {
    return authFailed(
      c,
      googleTokenFailureReason(tokenResp.status),
      `Google token exchange failed: ${tokenResp.status}`,
      502,
    );
  }

  // A non-JSON body or a malformed id_token used to throw out of the handler as a bare 500 — a
  // rejection with no event at all, i.e. exactly the invisible branch this instrumentation exists
  // to remove. Both are now one named, counted outcome.
  let claims: Record<string, unknown>;
  try {
    const tok = (await tokenResp.json()) as { id_token?: string };
    if (!tok.id_token) return authFailed(c, "no_id_token", "No id_token from Google", 502);
    claims = decodeJwtPayload(tok.id_token);
  } catch (e) {
    console.error("[gisti-mcp] id_token unreadable:", e instanceof Error ? e.message : e);
    return authFailed(c, "id_token_unreadable", "Could not read Google's response", 502);
  }

  const email = typeof claims["email"] === "string" ? (claims["email"] as string) : "";
  const sub = typeof claims["sub"] === "string" ? (claims["sub"] as string) : "";
  const name = typeof claims["name"] === "string" ? (claims["name"] as string) : "";
  if (!email) return authFailed(c, "no_email", "Google account has no email", 400);

  const props: Props = { claims: { sub, email, name } };
  let redirectTo: string;
  try {
    ({ redirectTo } = await c.env.OAUTH_PROVIDER.completeAuthorization({
      request: oauthReqInfo,
      userId: email,
      scope: oauthReqInfo.scope,
      metadata: { label: name || email },
      props,
    }));
  } catch (e) {
    console.error("[gisti-mcp] completeAuthorization failed:", e instanceof Error ? (e.stack ?? e.message) : e);
    return authFailed(c, "grant_failed", "Could not complete the authorization", 502);
  }

  // Emitted only once the grant actually exists, so `mcp_auth_completed` means "the client is
  // connected" and not merely "Google said yes". The email is known by now, so this event carries
  // the SAME pseudonymous device id the tool events use and Amplitude attaches it to the person;
  // `mcp_auth_started` cannot (no email exists yet) — compare the two as event totals.
  trackAuth(c, await emailOnlyIdentity(email), { type: "mcp_auth_completed" });
  return c.redirect(redirectTo);
});

export default app;
