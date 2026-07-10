/**
 * Live read-path smoke test — runs firestore.ts against PRODUCTION Firestore using the
 * real service-account key (SA auth is runtime-agnostic; no Cloudflare needed).
 * Proves: SA-JWT mint → token exchange → Firestore REST runQuery (resolve by email) →
 * list checklists. Usage: tsx scripts/smoke-read.ts <google-email>
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { getAccessToken, getChecklist, getUserChecklists, resolveUserIdByEmail } from "../src/firestore";
import { parseTemplateItems } from "../src/model";

const email = process.argv[2];
if (!email) {
  console.error("usage: tsx scripts/smoke-read.ts <google-email>");
  process.exit(1);
}

const sa = readFileSync(`${homedir()}/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json`, "utf8");
const env = { FIREBASE_SERVICE_ACCOUNT: sa };

const t0 = Date.now();
const userId = await resolveUserIdByEmail(env, email);
console.log(`resolveUserIdByEmail("${email}") -> ${userId}  (${Date.now() - t0}ms)`);

if (!userId) {
  console.log("No linked user for that email — token+runQuery worked, just no match.");
  process.exit(0);
}

// Raw diagnostics (bypass isDeleted filter + inspect the user doc) to explain a 0 result.
const base = "https://firestore.googleapis.com/v1/projects/aichecklists-40230/databases/(default)/documents";
const token = await getAccessToken(env);
const rawResp = await fetch(`${base}/users/${userId}/checklists?pageSize=100`, {
  headers: { Authorization: `Bearer ${token}` },
});
const raw = (await rawResp.json()) as { documents?: unknown[] };
console.log(`raw checklists subcollection docs: ${raw.documents?.length ?? 0} (status ${rawResp.status})`);
const uResp = await fetch(
  `${base}/users/${userId}?mask.fieldPaths=google_email&mask.fieldPaths=device_id&mask.fieldPaths=platform&mask.fieldPaths=created_at`,
  { headers: { Authorization: `Bearer ${token}` } },
);
const u = (await uResp.json()) as { fields?: Record<string, unknown> };
console.log("user doc fields:", JSON.stringify(u.fields ?? {}));

const checklists = await getUserChecklists(env, userId);
console.log(`getUserChecklists -> ${checklists.length} checklist(s):`);
for (const c of checklists.slice(0, 8)) {
  console.log(`  • ${c.name} [${c.cloudId}] — ${c.fills.length} fill(s), deleted=${c.isDeleted}`);
}

if (checklists[0]) {
  const one = await getChecklist(env, userId, checklists[0].cloudId);
  console.log(`getChecklist("${checklists[0].cloudId}") -> ${one ? `ok, itemsJson ${one.itemsJson.length} chars` : "null"}`);
}

// Decoder verification on REAL data — collection-group find ANY one checklist, decode via the
// production getChecklist path, print STRUCTURE ONLY (counts, no names/text) for privacy.
const cgResp = await fetch(`${base}:runQuery`, {
  method: "POST",
  headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify({
    structuredQuery: { from: [{ collectionId: "checklists", allDescendants: true }], limit: 1 },
  }),
});
if (cgResp.ok) {
  const cg = (await cgResp.json()) as Array<{ document?: { name: string } }>;
  const hit = cg.find((r) => r.document?.name)?.document;
  if (hit) {
    const parts = hit.name.split("/"); // .../users/{uid}/checklists/{cid}
    const cid = parts.pop()!;
    parts.pop(); // "checklists"
    const uid = parts.pop()!;
    const cl = await getChecklist(env, uid, cid);
    if (cl) {
      const items = parseTemplateItems(cl.itemsJson);
      const folders = items.filter((i) => i.type === "FOLDER").length;
      console.log(
        `decoder on a real checklist: templateItems=${items.length} (folders=${folders}), ` +
          `fills=${cl.fills.length}, itemsJson=${cl.itemsJson.length} chars, viewMode=${cl.viewMode} — decode OK (no content shown)`,
      );
    }
  }
} else {
  console.log(`collection-group probe skipped (status ${cgResp.status})`);
}
