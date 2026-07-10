/** Verify the union resolve returns the real checklists. Usage: tsx scripts/verify-resolve.ts <email> */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { getUserChecklists, resolveUserIds } from "../src/firestore";

const email = process.argv[2];
const sa = readFileSync(`${homedir()}/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json`, "utf8");
const env = { FIREBASE_SERVICE_ACCOUNT: sa };

const ids = await resolveUserIds(env, email!);
console.log("candidate user_ids:", ids);
const union = new Map<string, string>();
for (const id of ids) {
  const cls = await getUserChecklists(env, id);
  console.log(`  ${id}: ${cls.length} checklist(s)`);
  for (const c of cls) union.set(c.cloudId, c.name);
}
console.log(`union unique checklists: ${union.size}`);
for (const name of union.values()) console.log(`  • ${name}`);
