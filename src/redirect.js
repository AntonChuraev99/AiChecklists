// Hostname canonicalization in front of static assets.
//
// Redirects ONLY the explicit legacy/alias hosts — preview deploys
// (<version>-checklists.gisti.workers.dev) must keep serving assets,
// so a deny-all "anything not canonical" rule would break them.
//
// Wired up in wrangler.jsonc as "main" together with
// assets.run_worker_first: true (without it, requests matching an asset
// bypass this script entirely).
// Root-swap 2026-07-01: the app moved to app.gisti-ai.com; apex + www now serve the
// static SEO landing (separate worker). This worker serves ONLY app.gisti-ai.com.
const CANONICAL_HOST = "app.gisti-ai.com";
const REDIRECT_HOSTS = new Set([
  "checklists.gisti.workers.dev", // legacy production URL → app.gisti-ai.com
  // www.gisti-ai.com is no longer ours — it belongs to the landing worker now.
]);

// Firebase Auth helper origin. We reverse-proxy /__/auth/* to it so the OAuth
// handler + iframe are served from gisti-ai.com (SAME-ORIGIN as the app) instead
// of the default <project>.firebaseapp.com (a third-party origin). Chrome 130+ on
// mobile partitions third-party iframe storage (Privacy Sandbox) and blocks its
// cookies, silently breaking signInWithPopup/signInWithRedirect. Serving the helper
// same-origin via this proxy removes the blocker — the officially supported fix
// (firebase.google.com/docs/auth/web/redirect-best-practices, "reverse proxy").
// Pairs with firebaseConfig.authDomain === "app.gisti-ai.com" (build.gradle.kts).
const FIREBASE_AUTH_HOST = "aichecklists-40230.firebaseapp.com";

// Digital Asset Links for Android App Links on app.gisti-ai.com. Served by the WORKER
// (not a static asset) so it survives every wasm rebuild — the assets dir is build
// output and gets wiped — and is never shadowed by the SPA not_found fallback (which
// would return the empty-canvas index.html and fail Google's verifier). Lets the gallery
// deep-link (https://app.gisti-ai.com/?g=create&template=…) open the installed app.
// Fingerprints:
//  [0] Play App Signing key SHA-256 — matches Play Console → App integrity → "Digital Asset
//      Links JSON" (Play re-signs uploads with this key, so Play-installed builds present it).
//      Required for production. If Play rotates the app-signing key, add the new hash too.
//  [1] Upload-key SHA-256 (local gisti-release.keystore) — so a LOCAL installRelease build
//      also auto-verifies App Links during on-device testing. Kept intentionally.
const ASSETLINKS_JSON = JSON.stringify([
  {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: "com.antonchuraev.aichecklists",
      sha256_cert_fingerprints: [
        "2C:05:D3:91:76:3C:C9:C2:7C:5E:42:1B:E2:3A:42:DD:6C:7E:E7:EB:86:F9:97:E1:70:A3:A2:10:3B:E4:99:80",
        "E9:A4:2B:7F:34:2B:43:EE:15:24:57:FE:9E:76:40:44:0B:CD:40:29:F9:12:B2:6B:6D:C3:7B:EF:73:59:04:D2",
      ],
    },
  },
]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (REDIRECT_HOSTS.has(url.hostname)) {
      url.hostname = CANONICAL_HOST;
      url.protocol = "https:";
      url.port = "";
      return Response.redirect(url.toString(), 301);
    }
    // Android App Links verification file — must return the JSON directly (200, no
    // redirect) before the asset/SPA fallback can turn it into index.html.
    if (url.pathname === "/.well-known/assetlinks.json") {
      return new Response(ASSETLINKS_JSON, {
        headers: { "content-type": "application/json", "cache-control": "no-cache" },
      });
    }
    // Transparent reverse-proxy for Firebase Auth's helper endpoints. MUST run
    // before the asset fallback — otherwise /__/auth/* hits the SPA not_found
    // handler and returns index.html, breaking the OAuth handshake. This is a
    // pass-through (NOT a 301): the browser must see the response as coming from
    // gisti-ai.com (first-party) so storage/cookies are shared with the app.
    if (url.pathname.startsWith("/__/auth/")) {
      const target = new URL(request.url);
      target.hostname = FIREBASE_AUTH_HOST;
      target.protocol = "https:";
      target.port = "";
      // new Request(url, request) copies method, headers and body; fetch sets the
      // Host header from the target URL.
      return fetch(new Request(target.toString(), request));
    }
    const assetResponse = await env.ASSETS.fetch(request);
    // Force revalidation on the SPA shell (index.html) and Compose Resources. Both live
    // at STABLE URLs: index.html via the SPA fallback, and .cvr/drawables/fonts under
    // /composeResources/. Cloudflare Assets gives them max-age (the .cvr had 86400), so
    // for up to that long after a deploy a returning browser serves a STALE shell/bundle
    // without asking the server → users miss the new build and newly-added strings or
    // drawables render empty (recurring; fresh in incognito, stale in a returning
    // profile). With the existing ETag, revalidation is a cheap 304 when unchanged and
    // pulls fresh bytes the moment they change. Hashed wasm/js keep their immutable
    // caching — their URL changes on every build, so they can never go stale.
    const isComposeResource = url.pathname.includes("/composeResources/");
    const isHtml = (assetResponse.headers.get("content-type") || "").includes("text/html");
    if (isComposeResource || isHtml) {
      const headers = new Headers(assetResponse.headers);
      headers.set("Cache-Control", "no-cache");
      // COOP on the HTML DOCUMENT (not scripts) — COOP governs the browsing
      // context, so only the top-level index.html that calls signInWithPopup
      // needs it. The `_headers` /* rule does NOT reach the SPA not_found
      // fallback that synthesises index.html for `/` and deep-links, so the
      // opener document shipped WITHOUT COOP and the Firebase popup's
      // window.close() stayed blocked (login silently failed). Setting it here,
      // in the run_worker_first path, guarantees every HTML response carries it.
      // `same-origin-allow-popups` keeps cross-origin isolation OFF, so the
      // SAH-pool OPFS driver (no SharedArrayBuffer needed) is unaffected. See
      // _headers strategy comment + firebase-js-sdk#8541.
      if (isHtml) {
        headers.set("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
      }
      return new Response(assetResponse.body, {
        status: assetResponse.status,
        statusText: assetResponse.statusText,
        headers,
      });
    }
    return assetResponse;
  },
};
