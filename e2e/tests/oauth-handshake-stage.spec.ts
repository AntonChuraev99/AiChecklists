import { test, expect } from "@playwright/test";

/**
 * Root-swap migration verification (docs/plans/2026-07-01-landing-root-swap-migration-plan.md).
 *
 * Runs against the STAGE app worker on app.gisti-ai.com (authDomain=app.gisti-ai.com,
 * built + deployed separately from prod apex). Point Playwright at it:
 *   GISTI_WEB_URL=https://app.gisti-ai.com npx playwright test oauth-handshake-stage --project=chrome
 *
 * Goal: prove Google sign-in INITIATES same-origin on the new subdomain — the exact
 * thing the migration risks. We do NOT complete a real Google login (that needs creds);
 * the break (authDomain ≠ origin / proxy missing) manifests at handshake initiation:
 * the /__/auth/* helper 404s or goes cross-origin and signInWithPopup throws
 * auth/unauthorized-domain / storage-partition. We exercise the REAL production bridge
 * globalThis.__googleSignIn (what the wasmJs app itself calls), not an injected harness.
 */
test.describe("Root-swap: OAuth handshake same-origin on app.gisti-ai.com", () => {
  test("firebase /__/auth/* helper is proxied same-origin (not SPA, not 404)", async ({
    request,
  }) => {
    const handler = await request.get("/__/auth/handler");
    expect(handler.status(), "/__/auth/handler must proxy (200)").toBe(200);
    const body = await handler.text();
    // Must be Firebase's auth handler, NOT the app's SPA index (that would mean the
    // proxy is bypassed and OAuth would break).
    expect(body).not.toContain("Gisti — AI Checklists");
    expect(body.toLowerCase()).toContain("firebase");

    const iframe = await request.get("/__/auth/iframe.js");
    expect(iframe.status(), "/__/auth/iframe.js must proxy (200)").toBe(200);
    expect((iframe.headers()["content-type"] || "")).toContain("javascript");
  });

  test("app boots with authDomain=app.gisti-ai.com and auth bridge ready", async ({ page }) => {
    await page.goto("/");
    await page.waitForFunction(
      () => typeof (globalThis as any).__googleSignIn === "function",
      { timeout: 40_000 }
    );
    // init.js is served from the same origin — read its baked authDomain.
    const initJs = await page.evaluate(async () => {
      const r = await fetch("/init.js");
      return r.text();
    });
    expect(initJs).toContain('authDomain: "app.gisti-ai.com"');
    // Firebase auth must finish initializing without throwing unauthorized-domain.
    const authReady = await page.evaluate(async () => {
      try {
        await (globalThis as any).__authReadyPromise;
        return "ready";
      } catch (e) {
        return `error: ${String(e)}`;
      }
    });
    expect(authReady, "auth init must not fail").toBe("ready");
  });

  test("__googleSignIn opens the Google popup via the same-origin helper (no unauthorized-domain)", async ({
    page,
  }) => {
    const consoleErrors: string[] = [];
    page.on("console", (m) => {
      if (m.type() === "error") consoleErrors.push(m.text());
    });

    await page.goto("/");
    await page.waitForFunction(
      () => typeof (globalThis as any).__googleSignIn === "function",
      { timeout: 40_000 }
    );
    await page.evaluate(() => (globalThis as any).__authReadyPromise?.catch?.(() => {}));

    // Inject a real button so the click is a trusted user gesture (signInWithPopup
    // needs one, else the popup is blocked — that would be a false negative).
    await page.evaluate(() => {
      const b = document.createElement("button");
      b.id = "__oauth_test_btn";
      b.textContent = "signin";
      Object.assign(b.style, { position: "fixed", top: "0", left: "0", zIndex: "999999" });
      b.addEventListener("click", () => {
        try {
          (globalThis as any).__googleSignIn();
        } catch (e) {
          (globalThis as any).__oauthThrow = String(e);
        }
      });
      document.body.appendChild(b);
    });

    const popupPromise = page.waitForEvent("popup", { timeout: 25_000 }).catch(() => null);
    await page.click("#__oauth_test_btn");
    const popup = await popupPromise;

    const syncThrow = await page.evaluate(() => (globalThis as any).__oauthThrow ?? null);
    expect(syncThrow, "__googleSignIn must not throw synchronously").toBeNull();

    expect(popup, "sign-in popup must open — handshake initiated same-origin").not.toBeNull();
    if (popup) {
      await popup.waitForLoadState("domcontentloaded").catch(() => {});
      // First hop is app.gisti-ai.com/__/auth/handler (same-origin proxy); it then
      // 302s to accounts.google.com. Either proves the handshake reached Google.
      await page.waitForTimeout(5_000);
      const purl = popup.url();
      console.log("[oauth] popup url:", purl);
      // Must reach Google's REAL consent screen — NOT an OAuth error page.
      // redirect_uri_mismatch here means the GCP OAuth 2.0 client is missing
      // https://app.gisti-ai.com/__/auth/handler in its Authorized redirect URIs
      // (separate from Firebase authorized domains). This is the make-or-break
      // cutover gate: fix the OAuth client, then this must go green.
      expect(purl, "popup must not be a Google OAuth error page (redirect_uri_mismatch)").not.toMatch(
        /\/signin\/oauth\/error|authError=|redirect_uri_mismatch/i
      );
      expect(purl).toMatch(/accounts\.google\.com|app\.gisti-ai\.com\/__\/auth|firebaseapp\.com/);
    }

    const badDomain = consoleErrors.filter((e) => /unauthorized-domain|auth\/unauthorized/i.test(e));
    console.log("[oauth] console errors:", consoleErrors.slice(0, 10));
    expect(badDomain, "no auth/unauthorized-domain error").toHaveLength(0);
  });
});
