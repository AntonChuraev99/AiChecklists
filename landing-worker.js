// Landing worker: canonicalize the host (www → apex) and the path (add the
// trailing slash), otherwise serve the static landing.
//
// Paired with wrangler.landing.jsonc (run_worker_first: true, so every request
// reaches this script before the asset match). Unknown paths fall through to
// env.ASSETS.fetch, which returns a real 404 (not_found_handling: "none") —
// correct for SEO (the app SPA fallback lives on the app worker, not here).
import { serveVideo } from "./landing-range.js";

const CANONICAL = "gisti-ai.com";
const VIDEO_CACHE_CONTROL = "public, max-age=31536000, immutable";

// Cloudflare's asset router already normalizes /mcp → /mcp/, but with a **307**
// (temporary). Google does not consolidate a temporary redirect: it kept
// https://gisti-ai.com/mcp as the Google-chosen canonical and reported the
// declared canonical https://gisti-ai.com/mcp/ as "Duplicate, Google chose a
// different canonical" (GSC email 2026-08-09). Emitting a 301 here — before the
// asset match — is what actually merges the two URLs.
// Extensionless final segment == a directory-style page; anything with a dot
// (/robots.txt, /sitemap.xml, /tailwind.css, the IndexNow key file) is an asset
// and must be served as-is.
function canonicalPathname(pathname) {
  // /checklists/x/y/index.html → /checklists/x/y/ (the asset router answers this
  // one with a 307 too).
  if (pathname.endsWith("/index.html")) {
    return pathname.slice(0, -"index.html".length);
  }
  const lastSegment = pathname.slice(pathname.lastIndexOf("/") + 1);
  if (lastSegment !== "" && !lastSegment.includes(".")) {
    return pathname + "/";
  }
  return pathname;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    let redirect = false;

    if (url.hostname === "www.gisti-ai.com") {
      url.hostname = CANONICAL;
      url.protocol = "https:";
      url.port = "";
      redirect = true;
    }

    const pathname = canonicalPathname(url.pathname);
    if (pathname !== url.pathname) {
      url.pathname = pathname;
      redirect = true;
    }

    // One hop even when both the host and the path need fixing.
    if (redirect) return Response.redirect(url.toString(), 301);

    // Hero video assets are versioned by file name (hero-loop-v1.*): a new cut
    // ships under a new name, so the browser may keep these for a year. Without
    // this the asset router sends max-age=0 and every visit revalidates ~2.5 MB.
    // The worker also answers Range itself (206/416): Static Assets behind
    // run_worker_first returns a full 200, and Safari won't play mp4 without 206.
    if (url.pathname.startsWith("/video/")) {
      return serveVideo(request, (r) => env.ASSETS.fetch(r), VIDEO_CACHE_CONTROL);
    }

    return env.ASSETS.fetch(request); // static landing; unknown paths → 404
  },
};
