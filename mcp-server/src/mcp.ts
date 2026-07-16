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
 *   - MUTATIONS read-modify-write the SAME doc the checklist was found in (`ownerId`).
 *
 * Public-launch hardening (security.ts): opaque OAuth state, per-user rate limiting, deterministic
 * request_id (retry dedup), and a tracked() wrapper so a tool never leaks an unhandled error.
 *
 * Instrumentation (analytics.ts): every tool goes through `tracked()`, which emits the three
 * `mcp_*` Amplitude events off the critical path. Identity joins to the app's own Amplitude
 * profile via `creditUserId`; no checklist content is ever sent. See analytics.ts for both.
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
  createNamedFill,
  deleteItem,
  editNote,
  findFill,
  flattenFillForAi,
  listFills,
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
  type ChecklistItem,
  type ChecklistSyncData,
  type FillSyncData,
} from "./model";
import {
  RATE_TIERS,
  REQUEST_ID_BUCKET_MS,
  rateLimit,
  stableRequestId,
  type RateTier,
} from "./security";
import {
  failureReason,
  pseudonymousDeviceId,
  sendMcpEvent,
  type AnalyticsIdentity,
  type McpEvent,
} from "./analytics";

const SIGN_IN_HINT =
  "No linked Gisti account for this Google email. Open the Gisti app and sign in with Google first, then reconnect.";

type ToolResult = { content: Array<{ type: "text"; text: string }> };

function textResult(text: string): ToolResult {
  return { content: [{ type: "text" as const, text }] };
}

/** One-line summary for the list view. */
function summarizeChecklist(c: ChecklistSyncData): string {
  const count = leafItemCount(c);
  const reminder = c.reminderAt ? " ⏰" : "";
  return `• ${c.name} — ${count} item${count === 1 ? "" : "s"} [id: ${c.cloudId}]${reminder}`;
}

/**
 * Detailed view: the folder tree (from the template), each leaf showing its checked state + note
 * from the default fill (matched by templateItemId) and its id (the id CRUD tools speak). Orphans
 * (parentId pointing at a missing/non-folder node) render at the root so nothing is hidden.
 */
function renderChecklist(c: ChecklistSyncData, targetFill: FillSyncData | null = null): string {
  const template = parseTemplateItems(c.itemsJson);
  const fill = targetFill ?? defaultFill(c);
  const fillLabel = fill && !fill.isDefault ? ` — fill: ${fill.name || fill.cloudId}` : "";
  const lines: string[] = [`Checklist: ${c.name} (id: ${c.cloudId})${fillLabel}`];

  // Fallback: no template tree (legacy) → render the flat fill.
  if (template.length === 0) {
    const items = fill ? parseFillItems(fill.itemsJson) : [];
    lines.push(`Items (${items.length}):`);
    for (const it of items) {
      const note = it.note ? `  — ${it.note}` : "";
      lines.push(`  ${it.checked ? "[x]" : "[ ]"} ${it.text}  (id: ${it.templateItemId ?? it.id})${note}`);
    }
    return lines.join("\n");
  }

  const state = new Map<string, { checked: boolean; note: string | null }>();
  if (fill) {
    for (const fi of parseFillItems(fill.itemsJson)) {
      if (fi.templateItemId) state.set(fi.templateItemId, { checked: fi.checked, note: fi.note });
    }
  }

  const folderIds = new Set(template.filter((i) => i.type === "FOLDER").map((i) => i.id));
  const isRoot = (i: ChecklistItem): boolean => !i.parentId || !folderIds.has(i.parentId);
  const childrenOf = (id: string): ChecklistItem[] => template.filter((i) => !isRoot(i) && i.parentId === id);

  lines.push(`Items (${template.filter((i) => i.type === "ITEM").length}):`);
  const walk = (items: ChecklistItem[], depth: number): void => {
    const indent = "  ".repeat(depth + 1);
    for (const it of items) {
      if (it.type === "FOLDER") {
        lines.push(`${indent}📁 ${it.text}  (id: ${it.id})`);
        walk(childrenOf(it.id), depth + 1);
      } else {
        const st = state.get(it.id);
        const note = st?.note ? `  — ${st.note}` : "";
        lines.push(`${indent}${st?.checked ? "[x]" : "[ ]"} ${it.text}  (id: ${it.id})${note}`);
      }
    }
  };
  walk(template.filter(isRoot), 0);
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
  server = new McpServer({ name: "Gisti Checklists", version: "0.4.0" });

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

  // ── Instrumentation (analytics.ts) ─────────────────────────────────────────────
  //
  // Everything here is off the critical path: a tool call never awaits an event, and an analytics
  // failure can only produce a log line. See analytics.ts for the identity + privacy contracts.

  /** Memoised per DO instance (= per MCP session): the identity resolve costs a Firestore query. */
  private analyticsIdentity?: Promise<AnalyticsIdentity | null>;
  /** `mcp_session_started` fires once per DO instance, on the first tool call. */
  private sessionTracked = false;

  /**
   * The caller's Amplitude identity, resolved at most once per session. Cached because it is only
   * an analytics label — staleness within one session is harmless, and re-resolving per tool call
   * would add a Firestore round-trip to the very path this is supposed to stay out of.
   *
   * Never rejects: a resolve failure yields null (events dropped), so the memo can't poison the
   * session with a permanently rejected promise.
   */
  private identityForAnalytics(): Promise<AnalyticsIdentity | null> {
    this.analyticsIdentity ??= (async (): Promise<AnalyticsIdentity | null> => {
      const email = this.props?.claims.email;
      if (!email) return null;
      try {
        // `creditUserId` IS the users/{doc_id} the app passes to Amplitude's setUserId — the join
        // key. Null (unlinked email) → the caller is still counted under the pseudonymous id.
        const ctx = await resolveUserContext(this.env, email);
        return { userId: ctx?.creditUserId ?? null, deviceId: await pseudonymousDeviceId(email) };
      } catch (e) {
        console.warn("[gisti-mcp] analytics identity resolve failed:", e instanceof Error ? e.message : e);
        return null;
      }
    })();
    return this.analyticsIdentity;
  }

  /**
   * Fire-and-forget one event (plus `mcp_session_started` on the first call of the session).
   *
   * Returns void, not a promise, so a call site cannot accidentally `await` it back onto the
   * critical path. The Durable Object stays alive on its own while the fetch is in flight
   * ("waitUntil has no effect in Durable Objects" — Cloudflare docs), so no waitUntil is needed.
   */
  private track(event: McpEvent): void {
    void (async () => {
      const identity = await this.identityForAnalytics();
      if (!identity) return;
      // Check-and-set with no await between them → two concurrent tool calls can't double-fire.
      if (!this.sessionTracked) {
        this.sessionTracked = true;
        await sendMcpEvent(
          this.env,
          identity,
          { type: "mcp_session_started", linked: identity.userId !== null },
          Date.now(),
        );
      }
      await sendMcpEvent(this.env, identity, event, Date.now());
    })().catch((e) => console.error("[gisti-mcp] analytics failed:", e instanceof Error ? e.message : e));
  }

  /**
   * Wrap a tool handler so it (a) reports itself to analytics and (b) never throws out to the
   * transport: CfError → user-facing hint, anything else → logged + a generic message. Keeps a bad
   * request from surfacing as a raw 500 to the client.
   *
   * Instrumentation sits INSIDE the catch on purpose: the error is classified before it is turned
   * into a friendly string, so `mcp_tool_failed.reason` sees the real cause. Only the closed
   * `ToolFailureReason` vocabulary is uploaded — the message itself is logged, never sent.
   */
  private tracked<A>(
    toolName: string,
    handler: (args: A) => Promise<ToolResult>,
  ): (args: A) => Promise<ToolResult> {
    return async (args: A) => {
      this.track({ type: "mcp_tool_called", toolName });
      try {
        return await handler(args);
      } catch (e) {
        this.track({ type: "mcp_tool_failed", toolName, reason: failureReason(e) });
        if (e instanceof CfError) return textResult(cfErrorHint(e));
        console.error("[gisti-mcp] tool error:", e instanceof Error ? (e.stack ?? e.message) : e);
        return textResult("Something went wrong handling that request. Please try again.");
      }
    };
  }

  /** Best-effort per-user rate limit. Returns a deny message, or null when allowed / unauthenticated. */
  private async rateLimited(tier: RateTier): Promise<string | null> {
    const email = this.props?.claims.email;
    if (!email) return null;
    const { limit, windowSeconds } = RATE_TIERS[tier];
    const r = await rateLimit(this.env.OAUTH_KV, `${tier}:${email}`, limit, windowSeconds, Date.now());
    return r.allowed ? null : `You're doing that too fast — try again in about ${r.resetSeconds}s.`;
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
      this.tracked("list_checklists", async () => {
        const denied = await this.rateLimited("read");
        if (denied) return textResult(denied);
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const checklists = await collectChecklists(this.env, ids);
        if (checklists.length === 0) return textResult("You have no checklists yet.");
        return textResult(checklists.map(summarizeChecklist).join("\n"));
      }),
    );

    this.server.tool(
      "get_checklist",
      "Get one checklist by its id: its folder tree, each item's checked state, note, and id. Pass fillId to view a specific fill (session) instead of the default one.",
      {
        checklistId: z.string().describe("The checklist id (cloudId) from list_checklists."),
        fillId: z.string().optional().describe("A fill id from list_fills to view that session's state; omit for the default fill."),
      },
      this.tracked("get_checklist", async ({ checklistId, fillId }) => {
        const denied = await this.rateLimited("read");
        if (denied) return textResult(denied);
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ids, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        const fill = fillId ? findFill(owned.checklist, fillId) : null;
        if (fillId && !fill) return textResult(`No fill found with id ${fillId} in "${owned.checklist.name}".`);
        return textResult(renderChecklist(owned.checklist, fill));
      }),
    );

    this.server.tool(
      "search_checklists",
      "Search the user's checklists by a text query over names and item text.",
      { query: z.string().describe("Text to match against checklist names and item text.") },
      this.tracked("search_checklists", async ({ query }) => {
        const denied = await this.rateLimited("read");
        if (denied) return textResult(denied);
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
      }),
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
      this.tracked("create_checklist_ai", async ({ prompt, locale }) => {
        const denied = await this.rateLimited("ai");
        if (denied) return textResult(denied);
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const requestId = await stableRequestId(
          [ctx.creditUserId, "create", prompt, locale ?? ""],
          REQUEST_ID_BUCKET_MS,
          Date.now(),
        );
        const res = await generateChecklist({ userId: ctx.creditUserId, prompt, requestId, locale });
        const checklist = buildAiChecklist(res.checklistName, res.items, Date.now());
        await writeChecklist(this.env, ctx.writeUserId, checklist);
        const n = leafItemCount(checklist);
        return textResult(
          `✓ Created "${checklist.name}" with ${n} item${n === 1 ? "" : "s"}. (id: ${checklist.cloudId})\n` +
            (res.summary ? `${res.summary}\n` : "") +
            `AI credits left: ${res.aiCredits}`,
        );
      }),
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
        fillId: z.string().optional().describe("A fill id from list_fills to fill that session; omit for the default fill."),
      },
      this.tracked("fill_checklist_ai", async ({ checklistId, input, inputType, fillId }) => {
        const denied = await this.rateLimited("ai");
        if (denied) return textResult(denied);
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ctx.allIds, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        if (fillId && !findFill(owned.checklist, fillId)) {
          return textResult(`No fill found with id ${fillId} in "${owned.checklist.name}".`);
        }
        const items = flattenFillForAi(owned.checklist, Date.now(), fillId ?? null);
        if (items.length === 0) return textResult("That checklist has no items to fill.");
        const requestId = await stableRequestId(
          [ctx.creditUserId, "fill", checklistId, fillId ?? "", inputType ?? "text", input],
          REQUEST_ID_BUCKET_MS,
          Date.now(),
        );
        const res = await analyzeAndFillChecklist({
          userId: ctx.creditUserId,
          checklistId: owned.checklist.cloudId,
          checklistName: owned.checklist.name,
          items,
          inputType: inputType ?? "text",
          inputData: input,
          requestId,
        });
        const next = applyFilledItems(owned.checklist, res.filledItems, Date.now(), fillId ?? null);
        await writeChecklist(this.env, owned.ownerId, next);
        const done = res.filledItems.filter((f) => f.checked).length;
        return textResult(
          `✓ Filled "${owned.checklist.name}": ${done}/${items.length} checked.\n` +
            (res.summary ? `${res.summary}\n` : "") +
            `AI credits left: ${res.aiCredits}`,
        );
      }),
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
    ): Promise<ToolResult> => {
      const denied = await this.rateLimited("write");
      if (denied) return textResult(denied);
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
      "Check or uncheck an item in a checklist. Pass fillId to target a specific fill (session).",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
        checked: z.boolean().describe("true = done/checked, false = not done."),
        fillId: z.string().optional().describe("A fill id from list_fills; omit for the default fill."),
      },
      this.tracked("toggle_item", ({ checklistId, itemId, checked, fillId }) =>
        mutate(
          checklistId,
          (c, now) => toggleItem(c, itemId, checked, now, fillId ?? null),
          () => `✓ Marked item as ${checked ? "done" : "not done"}.`,
          "That item or fill was not found in the checklist.",
        ),
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
      this.tracked("add_item", async ({ checklistId, text, parentId }) => {
        const denied = await this.rateLimited("write");
        if (denied) return textResult(denied);
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ctx.allIds, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        const { checklist, itemId } = addItem(owned.checklist, text, parentId ?? null, Date.now());
        await writeChecklist(this.env, owned.ownerId, checklist);
        return textResult(`✓ Added "${text}". (id: ${itemId})`);
      }),
    );

    this.server.tool(
      "rename_item",
      "Change an item's text.",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
        text: z.string().describe("The new text."),
      },
      this.tracked("rename_item", ({ checklistId, itemId, text }) =>
        mutate(checklistId, (c, now) => renameItem(c, itemId, text, now), () => `✓ Renamed item to "${text}".`),
      ),
    );

    this.server.tool(
      "edit_note",
      "Set or clear the note attached to an item. Pass fillId to target a specific fill (session).",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
        note: z.string().describe("The note text; pass an empty string to clear it."),
        fillId: z.string().optional().describe("A fill id from list_fills; omit for the default fill."),
      },
      this.tracked("edit_note", ({ checklistId, itemId, note, fillId }) =>
        mutate(
          checklistId,
          (c, now) => editNote(c, itemId, note, now, fillId ?? null),
          () => (note ? "✓ Note updated." : "✓ Note cleared."),
          "That item or fill was not found in the checklist.",
        ),
      ),
    );

    this.server.tool(
      "delete_item",
      "Delete an item from a checklist. Deleting a folder promotes its children up one level.",
      {
        checklistId: z.string().describe("The checklist id."),
        itemId: z.string().describe("The item id from get_checklist."),
      },
      this.tracked("delete_item", ({ checklistId, itemId }) =>
        mutate(checklistId, (c, now) => deleteItem(c, itemId, now), () => "✓ Item deleted."),
      ),
    );

    this.server.tool(
      "reorder_items",
      "Reorder a checklist's items to the given id order.",
      {
        checklistId: z.string().describe("The checklist id."),
        orderedItemIds: z.array(z.string()).describe("Item ids in the desired new order."),
      },
      this.tracked("reorder_items", ({ checklistId, orderedItemIds }) =>
        mutate(
          checklistId,
          (c, now) => reorderItems(c, orderedItemIds, now),
          () => "✓ Items reordered.",
          "None of those item ids are in the checklist.",
        ),
      ),
    );

    this.server.tool(
      "rename_checklist",
      "Rename a checklist.",
      {
        checklistId: z.string().describe("The checklist id."),
        name: z.string().describe("The new checklist name."),
      },
      this.tracked("rename_checklist", ({ checklistId, name }) =>
        mutate(checklistId, (c, now) => renameChecklist(c, name, now), () => `✓ Renamed checklist to "${name}".`),
      ),
    );

    this.server.tool(
      "delete_checklist",
      "Delete a whole checklist (soft delete — it is removed from the app on all devices).",
      { checklistId: z.string().describe("The checklist id.") },
      this.tracked("delete_checklist", ({ checklistId }) =>
        mutate(checklistId, (c, now) => softDeleteChecklist(c, now), (c) => `✓ Deleted "${c.name}".`),
      ),
    );

    this.server.tool(
      "create_checklist_empty",
      "Create a checklist manually from a name and a list of item texts (no AI, no credits).",
      {
        name: z.string().describe("The checklist name."),
        items: z.array(z.string()).optional().describe("Initial item texts (may be empty)."),
      },
      this.tracked("create_checklist_empty", async ({ name, items }) => {
        const denied = await this.rateLimited("write");
        if (denied) return textResult(denied);
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const checklist = createEmptyChecklist(name, items ?? [], Date.now());
        await writeChecklist(this.env, ctx.writeUserId, checklist);
        const n = leafItemCount(checklist);
        return textResult(`✓ Created "${name}" with ${n} item${n === 1 ? "" : "s"}. (id: ${checklist.cloudId})`);
      }),
    );

    // ── Named fills (extra "sessions" of the same checklist) ──────────────────────
    this.server.tool(
      "list_fills",
      "List a checklist's fills (sessions): the default fill plus any extra named fills, each with its checked/total count and id. Use a fill id with get_checklist / toggle_item / edit_note / fill_checklist_ai to work on that session.",
      { checklistId: z.string().describe("The checklist id.") },
      this.tracked("list_fills", async ({ checklistId }) => {
        const denied = await this.rateLimited("read");
        if (denied) return textResult(denied);
        const ids = await this.userIds();
        if (ids.length === 0) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ids, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        const fills = listFills(owned.checklist);
        if (fills.length === 0) return textResult("That checklist has no fills.");
        return textResult(
          fills
            .map((f) => {
              const label = f.isDefault ? "Default fill" : f.name || "(unnamed fill)";
              return `• ${label} — ${f.checked}/${f.total} checked [fill id: ${f.fillId}]`;
            })
            .join("\n"),
        );
      }),
    );

    this.server.tool(
      "create_fill",
      "Create a new named fill — a fresh session of the checklist to check off independently (mirrors the current items, all unchecked). Use it for a repeatable checklist you run more than once.",
      {
        checklistId: z.string().describe("The checklist id."),
        name: z.string().describe("A name for the new fill/session, e.g. 'March trip'."),
      },
      this.tracked("create_fill", async ({ checklistId, name }) => {
        const denied = await this.rateLimited("write");
        if (denied) return textResult(denied);
        const trimmed = name.trim();
        if (!trimmed) return textResult("Please give the new fill a name.");
        const ctx = await this.context();
        if (!ctx) return textResult(SIGN_IN_HINT);
        const owned = await findChecklistWithOwner(this.env, ctx.allIds, checklistId);
        if (!owned) return textResult(`No checklist found with id ${checklistId}.`);
        const { checklist, fillId } = createNamedFill(owned.checklist, trimmed, Date.now());
        await writeChecklist(this.env, owned.ownerId, checklist);
        return textResult(`✓ Created fill "${trimmed}". (fill id: ${fillId})`);
      }),
    );
  }
}
