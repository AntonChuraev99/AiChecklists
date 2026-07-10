/**
 * Diagnostic: find a checklist by name across ALL users (collection group), then show the
 * parent user doc's identity fields. Reveals which user_id actually holds the data and how
 * it's keyed vs the Google identity. Usage: tsx scripts/find-checklist.ts "купить" "апки"
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { getAccessToken } from "../src/firestore";

const inputs = process.argv.slice(2);
if (inputs.length === 0) { console.error("usage: tsx scripts/find-checklist.ts <name> [name...]"); process.exit(1); }

const sa = readFileSync(`${homedir()}/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json`, "utf8");
const env = { FIREBASE_SERVICE_ACCOUNT: sa };
const base = "https://firestore.googleapis.com/v1/projects/aichecklists-40230/databases/(default)/documents";
const token = await getAccessToken(env);

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
const variants = [...new Set(inputs.flatMap((s) => [s, cap(s), s.toUpperCase()]))];

for (const nm of variants) {
  const resp = await fetch(`${base}:runQuery`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "checklists", allDescendants: true }],
        where: { fieldFilter: { field: { fieldPath: "name" }, op: "EQUAL", value: { stringValue: nm } } },
        limit: 5,
      },
    }),
  });
  const rows = (await resp.json()) as Array<{ document?: { name: string } }>;
  const hits = rows.filter((r) => r.document);
  if (hits.length === 0) { console.log(`"${nm}": no match`); continue; }
  for (const r of hits) {
    const path = r.document!.name; // .../users/{uid}/checklists/{cid}
    const parts = path.split("/");
    const uid = parts[parts.length - 3]!;
    const cid = parts[parts.length - 1]!;
    const uResp = await fetch(`${base}/users/${uid}`, { headers: { Authorization: `Bearer ${token}` } });
    const u = (await uResp.json()) as { fields?: Record<string, { stringValue?: string }> };
    const f = u.fields ?? {};
    console.log(
      `"${nm}" -> user_id=${uid} (checklist ${cid})\n` +
        `     google_email=${f["google_email"]?.stringValue ?? "MISSING"}, ` +
        `google_uid=${f["google_uid"]?.stringValue ?? "MISSING"}, ` +
        `device_id=${f["device_id"]?.stringValue ?? "-"}, platform=${f["platform"]?.stringValue ?? "-"}`,
    );
  }
}
