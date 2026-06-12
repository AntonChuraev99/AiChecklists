"""CORS origin whitelist shared by all HTTP functions.

Kept dependency-free so unit tests can import it without initializing
firebase_admin (main.py creates a Firestore client at import time).
"""

ALLOWED_ORIGINS = {
    "https://gisti-ai.com",
    # The www alias 301-redirects to the apex before the app loads;
    # kept in the whitelist as belt-and-braces.
    "https://www.gisti-ai.com",
    # Local wasmJs dev server (./gradlew composeApp:wasmJsBrowserDevelopmentRun).
    "http://localhost:9090",
}

# Covers the legacy production URL (https://checklists.gisti.workers.dev) and
# Cloudflare Workers Builds preview deploys
# (https://<version>-checklists.gisti.workers.dev). The gisti.workers.dev
# subdomain belongs to this Cloudflare account, so any host under it is ours.
ALLOWED_ORIGIN_SUFFIX = ".gisti.workers.dev"


def origin_allowed(origin: str) -> bool:
    if origin in ALLOWED_ORIGINS:
        return True
    return origin.startswith("https://") and origin.endswith(ALLOWED_ORIGIN_SUFFIX)
