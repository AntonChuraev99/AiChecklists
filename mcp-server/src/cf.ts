/**
 * Cloud Function client for the Gisti MCP server (Phase 1 — AI write).
 *
 * The two AI endpoints are the ONLY server-side path to Gemini (the key is
 * server-only — direct model calls are forbidden). The MCP calls them exactly like
 * the app does, then applies the results to `itemsJson` itself (encode.ts) and writes
 * Firestore (firestore.ts). So AI-write is NOT a pure proxy — see the design doc.
 *
 * Contract (confirmed 2026-07-10 against firebase-functions/main.py):
 *  - Both are POST JSON, region us-central1, `--allow-unauthenticated` (no ID token).
 *  - Success body is wrapped: `{ success: true, ... }`. Errors: HTTP 4xx/5xx with
 *    `{ success: false, error: "<msg>" }` (402 no-credits, 429 daily-limit, 503 off).
 *  - Credit reservation is idempotent on `request_id`: a retry with the same id returns
 *    the SAME `ai_credits` and does NOT double-charge (credit_reservations/{user}__{req}).
 *    So the caller MUST reuse one `request_id` per logical action across retries.
 *  - Credits live on the DEVICE-registration user doc (the google_email-matched one), so
 *    `userId` passed here must be that doc's id — NOT the google_uid data doc. The write
 *    target (checklists) is the google_uid doc. See firestore.ts `resolveUserContext`.
 */

const CF_BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net";

/** Input kinds accepted by analyze_and_fill_checklist (and generate_checklist). */
export type CfInputType = "text" | "url" | "image_base64" | "audio_base64" | "none";

/** A node in the tree generate_checklist returns (server assigns NO ids). */
export type GeneratedNode =
  | { text: string; checked?: boolean } // leaf
  | { text: string; type: "folder"; children: GeneratedNode[] }; // folder

export interface GenerateChecklistResult {
  checklistName: string;
  items: GeneratedNode[];
  summary: string;
  confidence: number;
  aiCredits: number;
}

/** One element of analyze_and_fill_checklist's `filled_items` (verbatim from the model). */
export interface FilledItem {
  index: number;
  text: string;
  checked: boolean;
  note: string | null;
}

export interface FillChecklistResult {
  filledItems: FilledItem[];
  summary: string;
  confidence: number;
  aiCredits: number;
}

/** A CF-level failure with the HTTP status and the server's error message. */
export class CfError extends Error {
  constructor(
    readonly status: number,
    readonly serverMessage: string,
    readonly fn: string,
  ) {
    super(`${fn} failed (${status}): ${serverMessage}`);
    this.name = "CfError";
  }
}

/** POST JSON to a CF, unwrap `{success,...}`, throw CfError on any non-success. */
async function callCf<T>(fn: string, body: unknown): Promise<T> {
  let resp: Response;
  try {
    resp = await fetch(`${CF_BASE}/${fn}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (e) {
    throw new CfError(0, `network error: ${e instanceof Error ? e.message : "unknown"}`, fn);
  }

  let data: Record<string, unknown> = {};
  try {
    data = (await resp.json()) as Record<string, unknown>;
  } catch {
    // Non-JSON body (unexpected) — fall through to status-based error below.
  }

  if (!resp.ok || data["success"] !== true) {
    const msg = typeof data["error"] === "string" ? (data["error"] as string) : resp.statusText;
    throw new CfError(resp.status, msg, fn);
  }
  return data as unknown as T;
}

function num(v: unknown, d: number): number {
  return typeof v === "number" ? v : d;
}

/**
 * generate_checklist — turn a prompt into a nested checklist tree.
 * `locale` "ru" → Russian fallback; anything else → English. `requestId` gates the
 * idempotent credit reservation and MUST be stable across retries of the same action.
 */
export async function generateChecklist(args: {
  userId: string;
  prompt: string;
  requestId: string;
  inputType?: CfInputType;
  inputData?: string;
  locale?: string;
}): Promise<GenerateChecklistResult> {
  const data = await callCf<Record<string, unknown>>("generate_checklist", {
    user_id: args.userId,
    prompt: args.prompt,
    input_type: args.inputType ?? "none",
    input_data: args.inputData ?? "",
    locale: args.locale ?? "en",
    request_id: args.requestId,
  });
  return {
    checklistName:
      typeof data["checklist_name"] === "string" ? (data["checklist_name"] as string) : "New Checklist",
    items: Array.isArray(data["items"]) ? (data["items"] as GeneratedNode[]) : [],
    summary: typeof data["summary"] === "string" ? (data["summary"] as string) : "",
    confidence: num(data["confidence"], 0.8),
    aiCredits: num(data["ai_credits"], 0),
  };
}

/**
 * analyze_and_fill_checklist — given a checklist's items and some input (notes / url /
 * base64 media), return per-item checked/note. Items are FLAT `{text,checked}` in and
 * `{index,text,checked,note}` out; the caller maps `filled_items` back onto the fill's
 * itemsJson by index (preserving ids + templateItemId). `locale` is NOT read by this CF.
 */
export async function analyzeAndFillChecklist(args: {
  userId: string;
  checklistId?: string;
  checklistName?: string;
  items: Array<{ text: string; checked: boolean }>;
  inputType: Exclude<CfInputType, "none">;
  inputData: string;
  requestId: string;
}): Promise<FillChecklistResult> {
  const data = await callCf<Record<string, unknown>>("analyze_and_fill_checklist", {
    user_id: args.userId,
    checklist: {
      id: args.checklistId,
      name: args.checklistName ?? "",
      items: args.items.map((i) => ({ text: i.text, checked: i.checked })),
    },
    input_type: args.inputType,
    input_data: args.inputData,
    request_id: args.requestId,
  });
  const rawFilled = Array.isArray(data["filled_items"]) ? (data["filled_items"] as unknown[]) : [];
  const filledItems: FilledItem[] = rawFilled.map((r, i) => {
    const o = (typeof r === "object" && r !== null ? r : {}) as Record<string, unknown>;
    return {
      index: typeof o["index"] === "number" ? (o["index"] as number) : i,
      text: typeof o["text"] === "string" ? (o["text"] as string) : "",
      checked: o["checked"] === true,
      note: typeof o["note"] === "string" ? (o["note"] as string) : null,
    };
  });
  return {
    filledItems,
    summary: typeof data["summary"] === "string" ? (data["summary"] as string) : "",
    confidence: num(data["confidence"], 0.8),
    aiCredits: num(data["ai_credits"], 0),
  };
}

/** Human-readable hint for the common CF failures, for surfacing in a tool result. */
export function cfErrorHint(e: CfError): string {
  if (e.status === 402) return `${e.serverMessage}`; // "Not enough credits. Need N. ..."
  if (e.status === 429) return `${e.serverMessage}`; // daily limit
  if (e.status === 503) return "AI is temporarily disabled. Try again later.";
  return `AI request failed: ${e.serverMessage}`;
}
