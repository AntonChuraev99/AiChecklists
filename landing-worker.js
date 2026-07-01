// Landing worker: canonicalize www → apex, otherwise serve the static landing.
//
// Paired with wrangler.landing.jsonc (run_worker_first: true, so every request
// reaches this script before the asset match). Unknown paths fall through to
// env.ASSETS.fetch, which returns a real 404 (not_found_handling: "none") —
// correct for SEO (the app SPA fallback lives on the app worker, not here).
const CANONICAL = "gisti-ai.com";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.hostname === "www.gisti-ai.com") {
      url.hostname = CANONICAL;
      url.protocol = "https:";
      url.port = "";
      return Response.redirect(url.toString(), 301);
    }
    return env.ASSETS.fetch(request); // static landing; unknown paths → 404
  },
};
