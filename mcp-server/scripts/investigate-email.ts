/**
 * Diagnostic: list ALL user docs for a given google_email + each one's checklist count.
 * Reveals device/google divergence (multiple docs, checklists under a non-first one).
 * Usage: tsx scripts/investigate-email.ts <google-email>
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { getAccessToken, getUserChecklists } from "../src/firestore";

const email = process.argv[2];
if (!email) { console.error("usage: tsx scripts/investigate-email.ts <email>"); process.exit(1); }

const sa = readFileSync(`${homedir()}/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json`, "utf8");
const env = { FIREBASE_SERVICE_ACCOUNT: sa };
const base = "https://firestore.googleapis.com/v1/projects/aichecklists-40230/databases/(default)/documents";
const token = await getAccessToken(env);

const resp = await fetch(`${base}:runQuery`, {
  method: "POST",
  headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify({
    structuredQuery: {
      from: [{ collectionId: "users" }],
      where: { fieldFilter: { field: { fieldPath: "google_email" }, op: "EQUAL", value: { stringValue: email } } },
    },
  }),
});
const rows = (await resp.json()) as Array<{ document?: { name: string; fields?: Record<string, { stringValue?: string }> } }>;
const docs = rows.filter((r) => r.document);
console.log(`docs with google_email=${email}: ${docs.length}`);
let guid = "";
for (const r of docs) {
  const d = r.document!;
  const id = d.name.split("/").pop()!;
  const cls = await getUserChecklists(env, id);
  const f = d.fields ?? {};
  guid = f["google_uid"]?.stringValue ?? guid;
  console.log(
    `  ${id}: checklists=${cls.length}, device_id=${f["device_id"]?.stringValue ?? "-"}, ` +
      `platform=${f["platform"]?.stringValue ?? "-"}, google_uid=${(f["google_uid"]?.stringValue ?? "").slice(0, 14)}, ` +
      `created=${f["created_at"]?.stringValue ?? "-"}`,
  );
}

// Sweep ALL docs with the same google_uid (Firebase uid) — the app's resolution key.
if (guid) {
  console.log(`\n--- all docs with google_uid=${guid} ---`);
  const r2 = await fetch(`${base}:runQuery`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "users" }],
        where: { fieldFilter: { field: { fieldPath: "google_uid" }, op: "EQUAL", value: { stringValue: guid } } },
      },
    }),
  });
  const rows2 = (await r2.json()) as Array<{ document?: { name: string; fields?: Record<string, { stringValue?: string }> } }>;
  for (const r of rows2.filter((x) => x.document)) {
    const d = r.document!;
    const id = d.name.split("/").pop()!;
    const cls = await getUserChecklists(env, id);
    const f = d.fields ?? {};
    console.log(
      `  ${id}: checklists=${cls.length}, google_email=${f["google_email"]?.stringValue ?? "-"}, ` +
        `device_id=${f["device_id"]?.stringValue ?? "-"}, platform=${f["platform"]?.stringValue ?? "-"}`,
    );
  }
}
