/**
 * Gisti MCP agent — Phase 0 read tools (list / get / search).
 *
 * Identity comes from the upstream Google leg as `this.props.claims.email`, resolved
 * to the internal user_id via Firestore (`google_email`). All data access is REST
 * (see firestore.ts). Write tools land in Phase 1/2 — design doc.
 */
import { McpAgent } from "agents/mcp";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { Env, Props } from "./types";
import { getChecklist, getUserChecklists, resolveUserIds } from "./firestore";
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

/** Detailed view: the default fill's items with checked state, or the bare template. */
function renderChecklist(c: ChecklistSyncData): string {
  const lines: string[] = [`Checklist: ${c.name} (id: ${c.cloudId})`];
  const fill = defaultFill(c);
  if (fill) {
    const items = parseFillItems(fill.itemsJson);
    lines.push(`Items (${items.length}):`);
    for (const it of items) lines.push(`  ${it.checked ? "[x]" : "[ ]"} ${it.text}`);
  } else {
    const items = parseTemplateItems(c.itemsJson).filter((i) => i.type === "ITEM");
    lines.push(`Items (${items.length}):`);
    for (const it of items) lines.push(`  [ ] ${it.text}`);
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

/** Fetch a checklist by id across candidate user_ids (first non-deleted hit). */
async function getChecklistAcross(env: Env, ids: string[], cloudId: string): Promise<ChecklistSyncData | null> {
  for (const uid of ids) {
    const c = await getChecklist(env, uid, cloudId);
    if (c && !c.isDeleted) return c;
  }
  return null;
}

export class GistiMCP extends McpAgent<Env, unknown, Props> {
  server = new McpServer({ name: "Gisti Checklists", version: "0.1.0" });

  /** All candidate user_ids for the caller (device-doc + google-uid-doc), or [] if not linked. */
  private async userIds(): Promise<string[]> {
    const email = this.props?.claims.email;
    if (!email) return [];
    return resolveUserIds(this.env, email);
  }

  async init() {
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
      "Get one checklist by its id, with its items and their checked state.",
      { checklistId: z.string().describe("The checklist id (cloudId) from list_checklists.") },
      async ({ checklistId }) => {
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const checklist = await getChecklistAcross(this.env, ids, checklistId);
        if (!checklist) return textResult(`No checklist found with id ${checklistId}.`);
        return textResult(renderChecklist(checklist));
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
}
