// Hostname canonicalization in front of static assets.
//
// Redirects ONLY the explicit legacy/alias hosts — preview deploys
// (<version>-checklists.gisti.workers.dev) must keep serving assets,
// so a deny-all "anything not canonical" rule would break them.
//
// Wired up in wrangler.jsonc as "main" together with
// assets.run_worker_first: true (without it, requests matching an asset
// bypass this script entirely).
const CANONICAL_HOST = "gisti-ai.com";
const REDIRECT_HOSTS = new Set([
  "checklists.gisti.workers.dev", // legacy production URL
  "www.gisti-ai.com", // www alias → apex
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
    return env.ASSETS.fetch(request);
  },
};
