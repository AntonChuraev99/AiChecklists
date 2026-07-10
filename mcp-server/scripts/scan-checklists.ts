/**
 * Diagnostic: collection-group scan of ALL checklists (name field only), find ones whose name
 * contains any given substring (case-insensitive), and show the parent user's identity fields.
 * Usage: tsx scripts/scan-checklists.ts купить апки
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { getAccessToken } from "../src/firestore";

const needles = process.argv.slice(2).map((s) => s.toLowerCase());
if (needles.length === 0) { console.error("usage: tsx scripts/scan-checklists.ts <substr> [substr...]"); process.exit(1); }

const sa = readFileSync(`${homedir()}/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json`, "utf8");
const env = { FIREBASE_SERVICE_ACCOUNT: sa };
const base = "https://firestore.googleapis.com/v1/projects/aichecklists-40230/databases/(default)/documents";
const token = await getAccessToken(env);

const resp = await fetch(`${base}:runQuery`, {
  method: "POST",
  headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify({
    structuredQuery: {
      from: [{ collectionId: "checklists", allDescendants: true }],
      select: { fields: [{ fieldPath: "name" }] },
      limit: 8000,
    },
  }),
});
const rows = (await resp.json()) as Array<{ document?: { name: string; fields?: { name?: { stringValue?: string } } } }>;
const docs = rows.filter((r) => r.document);
console.log(`total checklists scanned: ${docs.length}`);

const matches = docs.filter((r) => {
  const nm = (r.document!.fields?.name?.stringValue ?? "").toLowerCase();
  return needles.some((n) => nm.includes(n));
});
console.log(`matches for [${needles.join(", ")}]: ${matches.length}`);

const seenUsers = new Set<string>();
for (const r of matches) {
  const path = r.document!.name;
  const parts = path.split("/");
  const uid = parts[parts.length - 3]!;
  const nm = r.document!.fields?.name?.stringValue ?? "";
  console.log(`  "${nm}" -> user_id=${uid}`);
  if (seenUsers.has(uid)) continue;
  seenUsers.add(uid);
  const uResp = await fetch(`${base}/users/${uid}`, { headers: { Authorization: `Bearer ${token}` } });
  const u = (await uResp.json()) as { fields?: Record<string, { stringValue?: string }> };
  const f = u.fields ?? {};
  console.log(
    `     identity: google_email=${f["google_email"]?.stringValue ?? "MISSING"}, ` +
      `google_uid=${f["google_uid"]?.stringValue ?? "MISSING"}, ` +
      `device_id=${f["device_id"]?.stringValue ?? "-"}, platform=${f["platform"]?.stringValue ?? "-"}`,
  );
}
