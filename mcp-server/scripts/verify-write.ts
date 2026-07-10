/**
 * End-to-end write verification against PRODUCTION Firestore, through the REAL production code
 * path (firestore.writeChecklist / getChecklist + mutate.* + encode.*), NOT a reimplementation —
 * so it proves the exact bytes the deployed worker would write. Uses the service-account key
 * directly (no OAuth), like scripts/verify-resolve.ts proved the read path.
 *
 * It CREATES one clearly-marked test checklist, verifies the byte round-trip, toggles an item,
 * soft-deletes it via the tool path, then HARD-deletes the doc so prod is left pristine.
 *
 * Usage: tsx scripts/verify-write.ts <email>
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import {
  deleteChecklistDoc,
  getChecklist,
  resolveUserContext,
  writeChecklist,
} from "../src/firestore";
import { createEmptyChecklist, softDeleteChecklist, toggleItem } from "../src/mutate";
import { defaultFill, parseFillItems, parseTemplateItems } from "../src/model";

const email = process.argv[2];
if (!email) {
  console.error("Usage: tsx scripts/verify-write.ts <email>");
  process.exit(2);
}

const sa = readFileSync(`${homedir()}/.gisti-mcp-secrets/gisti-mcp-firestore-sa.json`, "utf8");
const env = { FIREBASE_SERVICE_ACCOUNT: sa };

let failures = 0;
function check(label: string, cond: boolean, extra?: string): void {
  console.log(`  ${cond ? "PASS" : "FAIL"}  ${label}${extra ? ` — ${extra}` : ""}`);
  if (!cond) failures++;
}

const ctx = await resolveUserContext(env, email);
if (!ctx) {
  console.error(`No linked Gisti user for ${email}. Sign in first.`);
  process.exit(1);
}
console.log("Resolved:", JSON.stringify(ctx));
console.log(`Write target (writeUserId): ${ctx.writeUserId}\n`);

const marker = `🧪 MCP write-test ${Date.now()}`;
const built = createEmptyChecklist(marker, ["alpha", "beta", "gamma"], Date.now());
const cloudId = built.cloudId;

console.log(`── CREATE ${cloudId} ("${marker}") ─────────────────────────────`);
await writeChecklist(env, ctx.writeUserId, built);
const readBack = await getChecklist(env, ctx.writeUserId, cloudId);
if (!readBack) {
  console.error("FATAL: created doc did not read back.");
  process.exit(1);
}
console.log("Read back doc:\n" + JSON.stringify(readBack, null, 2) + "\n");

check("name round-trips", readBack.name === marker);
check("template itemsJson byte-equal", readBack.itemsJson === built.itemsJson, `\n    wrote: ${built.itemsJson}\n    read : ${readBack.itemsJson}`);
check("fill itemsJson byte-equal", (defaultFill(readBack)?.itemsJson ?? "") === (defaultFill(built)?.itemsJson ?? ""));
check("default fill present", defaultFill(readBack)?.isDefault === true);
check("3 template items", parseTemplateItems(readBack.itemsJson).length === 3);
check("3 fill items mirror template", parseFillItems(defaultFill(readBack)!.itemsJson).length === 3);
check("foldersEnabled=false preserved", readBack.foldersEnabled === false);
check("viewMode Standard preserved", readBack.viewMode === "Standard");
check("not deleted", readBack.isDeleted === false);

console.log(`\n── TOGGLE first item → checked ─────────────────────────────`);
const firstId = parseTemplateItems(readBack.itemsJson)[0]!.id;
const toggled = toggleItem(readBack, firstId, true, Date.now());
if (!toggled) {
  console.error("FATAL: toggle returned null.");
  process.exit(1);
}
await writeChecklist(env, ctx.writeUserId, toggled);
const afterToggle = (await getChecklist(env, ctx.writeUserId, cloudId))!;
const toggledFill = parseFillItems(defaultFill(afterToggle)!.itemsJson);
check("first item now checked", toggledFill.find((f) => f.templateItemId === firstId)?.checked === true);
check("updatedAt advanced (dirty-parent)", afterToggle.updatedAt > readBack.updatedAt);

console.log(`\n── SOFT-DELETE via tool path ─────────────────────────────`);
const softDeleted = softDeleteChecklist(afterToggle, Date.now());
await writeChecklist(env, ctx.writeUserId, softDeleted);
const afterDelete = (await getChecklist(env, ctx.writeUserId, cloudId))!;
check("isDeleted=true after soft delete", afterDelete.isDeleted === true);

console.log(`\n── CLEANUP: hard-delete the test doc ─────────────────────────────`);
await deleteChecklistDoc(env, ctx.writeUserId, cloudId);
const afterHardDelete = await getChecklist(env, ctx.writeUserId, cloudId);
check("doc physically gone", afterHardDelete === null);

console.log(`\n${failures === 0 ? "✅ ALL CHECKS PASSED" : `❌ ${failures} CHECK(S) FAILED`}`);
process.exit(failures === 0 ? 0 : 1);
