/**
 * Upstream OAuth leg — logs the user in with Google, then hands the proven identity
 * to @cloudflare/workers-oauth-provider via completeAuthorization(). The Worker is the
 * OAuth server to the MCP client; Google is the upstream IdP.
 *
 * Flow:
 *   GET /authorize  → parse the MCP client's request → redirect to Google consent
 *   GET /callback   → exchange code → read id_token claims → completeAuthorization()
 *
 * ⚠ Hardening (Phase 0 → prod): `state` here carries the base64 oauthReqInfo unsigned.
 * The provider still validates the client (clientId/redirectUri) on completeAuthorization,
 * but a signed/opaque state (KV-stored, like the official demo) should replace this before
 * the public launch. Tracked in the design doc hardening section.
 */
import { Hono } from "hono";
import type { AuthRequest } from "@cloudflare/workers-oauth-provider";
import type { Env, Props } from "./types";

const app = new Hono<{ Bindings: Env }>();

const GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

function callbackUrl(reqUrl: string): string {
  return new URL("/callback", reqUrl).href;
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
    return c.text(`Invalid authorization request: ${e instanceof Error ? e.message : "unknown"}`, 400);
  }
  if (!oauthReqInfo.clientId) return c.text("Invalid authorization request", 400);

  const url = new URL(GOOGLE_AUTH_URL);
  url.searchParams.set("client_id", c.env.GOOGLE_OAUTH_CLIENT_ID);
  url.searchParams.set("redirect_uri", callbackUrl(c.req.url));
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid email profile");
  url.searchParams.set("state", btoa(JSON.stringify(oauthReqInfo)));
  url.searchParams.set("access_type", "online");
  url.searchParams.set("prompt", "select_account");
  return c.redirect(url.href);
});

app.get("/callback", async (c) => {
  const code = c.req.query("code");
  const stateRaw = c.req.query("state");
  if (!code || !stateRaw) return c.text("Missing code or state", 400);

  let oauthReqInfo: AuthRequest;
  try {
    oauthReqInfo = JSON.parse(atob(stateRaw)) as AuthRequest;
  } catch {
    return c.text("Invalid state", 400);
  }
  if (!oauthReqInfo.clientId) return c.text("Invalid state", 400);

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
  if (!tokenResp.ok) return c.text(`Google token exchange failed: ${tokenResp.status}`, 502);

  const tok = (await tokenResp.json()) as { id_token?: string };
  if (!tok.id_token) return c.text("No id_token from Google", 502);
  const claims = decodeJwtPayload(tok.id_token);

  const email = typeof claims["email"] === "string" ? (claims["email"] as string) : "";
  const sub = typeof claims["sub"] === "string" ? (claims["sub"] as string) : "";
  const name = typeof claims["name"] === "string" ? (claims["name"] as string) : "";
  if (!email) return c.text("Google account has no email", 400);

  const props: Props = { claims: { sub, email, name } };
  const { redirectTo } = await c.env.OAUTH_PROVIDER.completeAuthorization({
    request: oauthReqInfo,
    userId: email,
    scope: oauthReqInfo.scope,
    metadata: { label: name || email },
    props,
  });
  return c.redirect(redirectTo);
});

export default app;
