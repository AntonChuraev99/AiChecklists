// Submit landing sitemap URLs to IndexNow (instant Bing / Yandex / AI-engine indexing).
// IndexNow key file must be live at https://gisti-ai.com/<key>.txt (landing/<key>.txt).
// Run AFTER deploying the landing worker:  node scripts/indexnow-ping.mjs
// Node 18+ (global fetch). Parses landing/sitemap.xml so it scales as the gallery grows.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const KEY = "97ad0db790d29b918031ba2a7e974886";
const HOST = "gisti-ai.com";
const KEY_LOCATION = `https://${HOST}/${KEY}.txt`;

const __dirname = dirname(fileURLToPath(import.meta.url));
const sitemap = readFileSync(join(__dirname, "..", "landing", "sitemap.xml"), "utf8");
const urlList = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1].trim());

if (urlList.length === 0) {
  console.error("No <loc> URLs found in landing/sitemap.xml");
  process.exit(1);
}

const body = { host: HOST, key: KEY, keyLocation: KEY_LOCATION, urlList };
const res = await fetch("https://api.indexnow.org/indexnow", {
  method: "POST",
  headers: { "Content-Type": "application/json; charset=utf-8" },
  body: JSON.stringify(body),
});

console.log(`IndexNow -> ${res.status} ${res.statusText} for ${urlList.length} URL(s):`);
urlList.forEach((u) => console.log("  " + u));

// IndexNow returns 200 (accepted) or 202 (accepted, pending verification).
if (res.status !== 200 && res.status !== 202) {
  console.error("Body:", await res.text());
  process.exit(1);
}
