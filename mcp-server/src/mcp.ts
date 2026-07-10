/**
 * Gisti MCP agent — checklist tools over the caller's own Google identity.
 *
 * Phase 0 (read):   list_checklists / get_checklist / search_checklists.
 * Phase 1 (AI write): create_checklist_ai / fill_checklist_ai — via the existing Cloud Functions
 *                     (server-only Gemini key), then the result is written to Firestore here.
 * Phase 2 (CRUD):   toggle_item / add_item / rename_item / edit_note / delete_item / reorder_items /
 *                   rename_checklist / delete_checklist / create_checklist_empty — direct Firestore.
 *
 * Identity (the #1 trap of this data model, cf. firestore.ts):
 *   - READS fan out across the union of the caller's user docs (device-doc + google_uid-doc).
 *   - AI credit calls charge `creditUserId` (the device/credit doc — what the app sends to CFs).
 *   - CREATES write to `writeUserId` (the google_uid data doc — cross-device source of truth).
 *   - MUTATIONS read-modify-write the SAME doc the checklist was found in (`ownerId`), never
 *     migrating data between docs.
 *
 * All writes mirror the Kotlin sync contract byte-for-byte (encode.ts + mutate.ts, gated by the
 * contract test) and honor dirty-parent + templateItemId + monotonic updatedAt invariants.
 */
import { McpAgent } from "agents/mcp";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { Env, Props } from "./types";
import {
  findChecklistWithOwner,
  getUserChecklists,
  resolveUserContext,
  resolveUserIds,
  writeChecklist,
  type UserContext,
} from "./firestore";
import { analyzeAndFillChecklist, CfError, cfErrorHint, generateChecklist } from "./cf";
import {
  addItem,
  applyFilledItems,
  buildAiChecklist,
  createEmptyChecklist,
  deleteItem,
  editNote,
  flattenFillForAi,
  renameChecklist,
  renameItem,
  reorderItems,
  softDeleteChecklist,
  toggleItem,
} from "./mutate";
import {
  defaultFill,
  leafItemCount,
  parseFillItems,
  parseTemplateItems,
  type ChecklistSyncData,
} from "./model";

const SIGN_IN_HINT =
  "No linked Gisti account for this Google email. Open the Gisti app and sign in with Google first, then reconnect.";

function textResult(text: string) {
  return { content: [{ type: "text" as const, text }] };
}

/** One-line summary for the list view. */
function summarizeChecklist(c: ChecklistSyncData): string {
  const count = leafItemCount(c);
  const reminder = c.reminderAt ? " ⏰" : "";
  return `• ${c.name} — ${count} item${count === 1 ? "" : "s"} [id: ${c.cloudId}]${reminder}`;
}

/** Detailed view: the default fill's items with checked state + note, exposing each item's id. */
function renderChecklist(c: ChecklistSyncData): string {
  const lines: string[] = [`Checklist: ${c.name} (id: ${c.cloudId})`];
  const fill = defaultFill(c);
  if (fill) {
    const items = parseFillItems(fill.itemsJson);
    lines.push(`Items (${items.length}):`);
    for (const it of items) {
      const id = it.templateItemId ?? it.id;
      const note = it.note ? `  — ${it.note}` : "";
      lines.push(`  ${it.checked ? "[x]" : "[ ]"} ${it.text}  (id: ${id})${note}`);
    }
  } else {
    const items = parseTemplateItems(c.itemsJson).filter((i) => i.type === "ITEM");
    lines.push(`Items (${items.length}):`);
    for (const it of items) lines.push(`  [ ] ${it.text}  (id: ${it.id})`);
  }
  return lines.join("\n");
}

/** Union checklists across candidate user_ids, dedup by cloudId (freshest updatedAt wins). */
async function collectChecklists(env: Env, ids: string[]): Promise<ChecklistSyncData[]> {
  const byId = new Map<string, ChecklistSyncData>();
  for (const uid of ids) {
    for (const c of await getUserChecklists(env, uid)) {
      const prev = byId.get(c.cloudId);
      if (!prev || c.updatedAt > prev.updatedAt) byId.set(c.cloudId, c);
    }
  }
  return [...byId.values()];
}

export class GistiMCP extends McpAgent<Env, unknown, Props> {
  server = new McpServer({ name: "Gisti Checklists", version: "0.2.0" });

  /** All candidate user_ids for the caller (read fan-out), or [] if not linked. */
  private async userIds(): Promise<string[]> {
    const email = this.props?.claims.email;
    if (!email) return [];
    return resolveUserIds(this.env, email);
  }

  /** The write/credit identity split for the caller, or null if not linked. */
  private async context(): Promise<UserContext | null> {
    const email = this.props?.claims.email;
    if (!email) return null;
    return resolveUserContext(this.env, email);
  }

  async init() {
    this.registerReadTools();
    this.registerAiTools();
    this.registerCrudTools();
  }

  // ── Phase 0: read ──────────────────────────────────────────────────────────────
  private registerReadTools() {
    this.server.tool(
      "list_checklists",
      "List all of the signed-in user's checklists (name, item count, id).",
      {},
      async () => {
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const checklists = await collectChecklists(this.env, ids);
        if (checklists.length === 0) return textResult("You have no checklists yet.");
        return textResult(checklists.map(summarizeChecklist).join("\n"));
      },
    );

    this.server.tool(
      "get_checklist",
      "Get one checklist by its id, with its items, their checked state, notes, and item ids.",
      { checklistId: z.string().describe("The checklist id (cloudId) from list_checklists.") },
      async ({ checklistId }) => {
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ids, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        return textResult(renderChecklist(owned.checklist));
      },
    );

    this.server.tool(
      "search_checklists",
      "Search the user's checklists by a text query over names and item text.",
      { query: z.string().describe("Text to match against checklist names and item text.") },
      async ({ query }) => {
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const q = query.trim().toLowerCase();
        if (!q) return textResult("Empty query.");
        const checklists = await collectChecklists(this.env, ids);
        const hits = checklists.filter((c) => {
          if (c.name.toLowerCase().includes(q)) return true;
          const fill = defaultFill(c);
          const items = fill ? parseFillItems(fill.itemsJson) : parseTemplateItems(c.itemsJson);
          return items.some((it) => it.text.toLowerCase().includes(q));
        });
        if (hits.length === 0) return textResult(`No checklists match "${query}".`);
        return textResult(hits.map(summarizeChecklist).join("\n"));
      },
    );
  }

  // ── Phase 1: AI write (via Cloud Functions) ─────────────────────────────────────
  private registerAiTools() {
    this.server.tool(
      "create_checklist_ai",
      "Create a brand-new checklist from a natural-language prompt using AI (spends AI credits).",
      {
        prompt: z.string().describe("What the checklist should be about, e.g. 'packing list for a ski trip'."),
        locale: z.string().optional().describe("Output language fallback: 'ru' for Russian, else English."),
      },
      async ({ prompt, locale }) => {
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        try {
          const res = await generateChecklist({
            userId: ctx.creditUserId,
            prompt,
            requestId: crypto.randomUUID(),
            locale,
          });
          const checklist = buildAiChecklist(res.checklistName, res.items, Date.now());
          await writeChecklist(this.env, ctx.writeUserId, checklist);
          const n = leafItemCount(checklist);
          return textResult(
            `✓ Created "${checklist.name}" with ${n} item${n === 1 ? "" : "s"}. (id: ${checklist.cloudId})\n` +
              (res.summary ? `${res.summary}\n` : "") +
              `AI credits left: ${res.aiCredits}`,
          );
        } catch (e) {
          if (e instanceof CfError) return textResult(cfErrorHint(e));
          throw e;
        }
      },
    );

    this.server.tool(
      "fill_checklist_ai",
      "Fill in a checklist's items (checked + notes) by having AI analyze some input text/url/media (spends AI credits).",
      {
        checklistId: z.string().describe("The checklist id (cloudId) to fill."),
        input: z.string().describe("The content to analyze: notes text, a URL, or base64 media data."),
        inputType: z
          .enum(["text", "url", "image_base64", "audio_base64"])
          .optional()
          .describe("How to interpret `input`. Default 'text'."),
      },
      async ({ checklistId, input, inputType }) => {
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ctx.allIds, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        const items = flattenFillForAi(owned.checklist, Date.now());
        if (items.length === 0) return textResult("That checklist has no items to fill.");
        try {
          const res = await analyzeAndFillChecklist({
            userId: ctx.creditUserId,
            checklistId: owned.checklist.cloudId,
            checklistName: owned.checklist.name,
            items,
            inputType: inputType ?? "text",
            inputData: input,
            requestId: crypto.randomUUID(),
          });
          const next = applyFilledItems(owned.checklist, res.filledItems, Date.now());
          await writeChecklist(this.env, owned.ownerId, next);
          const done = res.filledItems.filter((f) => f.checked).length;
          return textResult(
            `✓ Filled "${owned.checklist.name}": ${done}/${items.length} checked.\n` +
              (res.summary ? `${res.summary}\n` : "") +
              `AI credits left: ${res.aiCredits}`,
          );
        } catch (e) {
          if (e instanceof CfError) return textResult(cfErrorHint(e));
          throw e;
        }
      },
    );
  }

  // ── Phase 2: CRUD (direct Firestore) ────────────────────────────────────────────
  private registerCrudTools() {
    /** Locate the checklist, apply a pure mutation, write it back to its owning doc. */
    const mutate = async (
      checklistId: string,
      fn: (c: ChecklistSyncData, now: number) => ChecklistSyncData | null,
      ok: (c: ChecklistSyncData) => string,
      missing = "That item was not found in the checklist.",
    ) => {
      const ctx = await this.context();
      if (!ctx) return textResult(SIGN_IN_HINT);
      const owned = await findChecklistWithOwner(this.env, ctx.allIds, checklistId);
      if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
      const next = fn(owned.checklist, Date.now());
      if (!next) return textResult(missing);
      await writeChecklist(this.env, owned.ownerId, next);
      return textResult(ok(next));
    };

    this.server.tool(
      "toggle_item",
      "Check or uncheck an item in a checklist.",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
        checked: z.boolean().describe("true = done/checked, false = not done."),
      },
      ({ checklistId, itemId, checked }) =>
        mutate(
          checklistId,
          (c, now) => toggleItem(c, itemId, checked, now),
          () => `✓ Marked item as ${checked ? "done" : "not done"}.`,
        ),
    );

    this.server.tool(
      "add_item",
      "Add a new item to a checklist, optionally inside a folder.",
      {
        checklistId: z.string().describe("The checklist id."),
        text: z.string().describe("The new item's text."),
        parentId: z.string().optional().describe("A folder item id to nest under; omit for top level."),
      },
      async ({ checklistId, text, parentId }) => {
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ctx.allIds, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        const { checklist, itemId } = addItem(owned.checklist, text, parentId ?? null, Date.now());
        await writeChecklist(this.env, owned.ownerId, checklist);
        return textResult(`✓ Added "${text}". (id: ${itemId})`);
      },
    );

    this.server.tool(
      "rename_item",
      "Change an item's text.",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
        text: z.string().describe("The new text."),
      },
      ({ checklistId, itemId, text }) =>
        mutate(checklistId, (c, now) => renameItem(c, itemId, text, now), () => `✓ Renamed item to "${text}".`),
    );

    this.server.tool(
      "edit_note",
      "Set or clear the note attached to an item.",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
        note: z.string().describe("The note text; pass an empty string to clear it."),
      },
      ({ checklistId, itemId, note }) =>
        mutate(
          checklistId,
          (c, now) => editNote(c, itemId, note, now),
          () => (note ? "✓ Note updated." : "✓ Note cleared."),
        ),
    );

    this.server.tool(
      "delete_item",
      "Delete an item from a checklist. Deleting a folder promotes its children up one level.",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
      },
      ({ checklistId, itemId }) =>
        mutate(checklistId, (c, now) => deleteItem(c, itemId, now), () => "✓ Item deleted."),
    );

    this.server.tool(
      "reorder_items",
      "Reorder a checklist's items to the given id order.",
      {
        checklistId: z.string().describe("The checklist id."),
        orderedItemIds: z.array(z.string()).describe("Item ids in the desired new order."),
      },
      ({ checklistId, orderedItemIds }) =>
        mutate(
          checklistId,
          (c, now) => reorderItems(c, orderedItemIds, now),
          () => "✓ Items reordered.",
          "None of those item ids are in the checklist.",
        ),
    );

    this.server.tool(
      "rename_checklist",
      "Rename a checklist.",
      {
        checklistId: z.string().describe("The checklist id."),
        name: z.string().describe("The new checklist name."),
      },
      ({ checklistId, name }) =>
        mutate(checklistId, (c, now) => renameChecklist(c, name, now), () => `✓ Renamed checklist to "${name}".`),
    );

    this.server.tool(
      "delete_checklist",
      "Delete a whole checklist (soft delete — it is removed from the app on all devices).",
      { checklistId: z.string().describe("The checklist id.") },
      ({ checklistId }) =>
        mutate(checklistId, (c, now) => softDeleteChecklist(c, now), (c) => `✓ Deleted "${c.name}".`),
    );

    this.server.tool(
      "create_checklist_empty",
      "Create a checklist manually from a name and a list of item texts (no AI, no credits).",
      {
        name: z.string().describe("The checklist name."),
        items: z.array(z.string()).optional().describe("Initial item texts (may be empty)."),
      },
      async ({ name, items }) => {
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const checklist = createEmptyChecklist(name, items ?? [], Date.now());
        await writeChecklist(this.env, ctx.writeUserId, checklist);
        const n = leafItemCount(checklist);
        return textResult(`✓ Created "${name}" with ${n} item${n === 1 ? "" : "s"}. (id: ${checklist.cloudId})`);
      },
    );
  }
}
