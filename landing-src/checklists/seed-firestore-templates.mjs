#!/usr/bin/env node
// ---------------------------------------------------------------------------
// Seed the public `gallery_templates/{slug}` Firestore collection from the
// SEO-gallery dataset — the deterministic seed source for the gallery deep-link
// ("Use this checklist" → create-from-template, no AI credit).
//
// Reads   landing-src/checklists/gallery-templates.seed.json  (built by generate flow)
// Writes  projects/<pid>/databases/(default)/documents/gallery_templates/{slug}
// Auth    service account JSON (Firestore REST + self-signed RS256 JWT, zero deps).
//
// Idempotent: PATCH keyed by slug — re-run after the gallery grows to sync.
// Rules: gallery_templates is public-read / no client write (firestore.rules).
//
//   SA_PATH=~/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json node landing-src/checklists/seed-firestore-templates.mjs
// ---------------------------------------------------------------------------
import { readFileSync } from "node:fs";
import { createSign } from "node:crypto";
import { homedir } from "node:os";

const SA_PATH = (process.env.SA_PATH || "~/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json").replace(/^~/, homedir());
const SEED = new URL("./gallery-templates.seed.json", import.meta.url);

const sa = JSON.parse(readFileSync(SA_PATH, "utf8"));
const docs = JSON.parse(readFileSync(SEED, "utf8"));
const PID = sa.project_id;

const b64url = (s) => Buffer.from(s).toString("base64url");

// Firestore REST typed-value encoder.
function enc(v) {
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(enc) } };
  if (v && typeof v === "object") return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)])) } };
  return { nullValue: null };
}

async function accessToken() {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now, exp: now + 3600,
  }));
  const sig = createSign("RSA-SHA256").update(`${header}.${claim}`).sign(sa.private_key).toString("base64url");
  const jwt = `${header}.${claim}.${sig}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const j = await res.json();
  if (!j.access_token) throw new Error(`token exchange failed: ${JSON.stringify(j)}`);
  return j.access_token;
}

async function main() {
  const token = await accessToken();
  let ok = 0, fail = 0;
  for (const d of docs) {
    const url = `https://firestore.googleapis.com/v1/projects/${PID}/databases/(default)/documents/gallery_templates/${encodeURIComponent(d.slug)}`;
    const body = { fields: { slug: enc(d.slug), category: enc(d.category), title: enc(d.title), ordered: enc(!!d.ordered), items: enc(d.items) } };
    const res = await fetch(url, {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (res.ok) { ok++; }
    else { fail++; console.error(`  ! ${d.slug}: ${res.status} ${(await res.text()).slice(0, 160)}`); }
  }
  console.log(`gallery_templates seeded: ${ok} ok, ${fail} failed (project ${PID})`);
  if (fail) process.exit(1);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
