/**
 * Message classification and the draft model — pure, no I/O.
 *
 * A "draft" collects the content messages a user sends within the debounce window (~2.5 s, longer
 * while an album keeps arriving) and turns into ONE prompt with buttons:
 *   - text only            → «Checklist from lines» (free) + «Checklist with AI (N credits)»;
 *   - photo / voice / audio / text file / link → AI only.
 *
 * What `generate_checklist` (firebase-functions/main.py) actually accepts decides what is offered:
 *   input_type ∈ text | url | image_base64 | audio_base64 | none, ONE input per call.
 *   - image_base64 is always decoded as image/jpeg → only Telegram photos (always JPEG) and JPEG
 *     documents; other image files get "send it as a photo".
 *   - audio_base64 is sent to Gemini as audio/mp4 today (the CF ignores any mime field); we still
 *     send `mime_type` so a CF that starts honouring it needs no worker change.
 *   - url is passed to the model as the URL string (the CF does not fetch the page) — same as the app.
 *   - no PDF input exists → PDFs are politely refused.
 *   - text files (.txt/.md/.csv) are read here and sent as input_type text.
 */

import type { ChecklistInput } from "./analytics";
import type { DraftCounts, Lang } from "./i18n";
import { parseLines, type ParsedLines } from "./lines";
import {
  MAX_FILE_BYTES,
  type TgEntity,
  type TgFileRef,
  type TgMessage,
  type TgPhotoSize,
  type TgSuccessfulPayment,
} from "./telegram";

/** Debounce window after the last content message before the prompt is sent. */
export const DEBOUNCE_MS = 2500;

/** An album item arriving this long after its draft was prompted is still merged into it. */
export const ALBUM_MERGE_MS = 60_000;

/** Parts kept per draft; more are dropped and the prompt says so. */
export const MAX_PARTS = 20;

export type DraftPart =
  | { kind: "text"; text: string }
  | { kind: "link"; url: string }
  | { kind: "image"; fileId: string; size: number; origin: "photo" | "document" }
  | { kind: "audio"; fileId: string; size: number; mime: string; origin: "voice" | "audio" | "document" }
  | { kind: "textfile"; fileId: string; size: number };

export type RejectReason = "too_big" | "pdf" | "image_as_file" | "unsupported";

export type Classified =
  | { type: "command"; command: string; arg: string }
  | { type: "content"; parts: DraftPart[]; mediaGroupId: string | null }
  | { type: "reject"; reason: RejectReason }
  /** Stars payment completed (service message) → apply_payment. */
  | { type: "payment"; payment: TgSuccessfulPayment }
  /** Service message that needs no reply (e.g. refunded_payment — refund.mjs revokes server-side). */
  | { type: "service" };

const URL_ONLY = /^https?:\/\/\S+$/i;
const TEXT_FILE_EXT = /\.(txt|md|markdown|csv)$/i;
const TEXT_FILE_MIME = new Set(["text/plain", "text/markdown", "text/x-markdown", "text/csv"]);

function isCommand(text: string, entities: TgEntity[] | undefined): boolean {
  return (entities ?? []).some((e) => e.type === "bot_command" && e.offset === 0) && text.startsWith("/");
}

/** "/start@gisti_app_bot abc" → { command: "start", arg: "abc" }. */
export function parseCommand(text: string): { command: string; arg: string } {
  const m = /^\/([A-Za-z0-9_]+)(?:@[A-Za-z0-9_]+)?(?:\s+([\s\S]*))?$/.exec(text.trim());
  if (!m) return { command: "", arg: "" };
  return { command: (m[1] ?? "").toLowerCase(), arg: (m[2] ?? "").trim() };
}

function textPart(text: string): DraftPart | null {
  const s = text.trim();
  if (!s) return null;
  if (URL_ONLY.test(s)) return { kind: "link", url: s };
  return { kind: "text", text: s };
}

function largestPhoto(photos: TgPhotoSize[]): TgPhotoSize | null {
  // Telegram lists sizes ascending; the last one is the original resolution.
  return photos.length > 0 ? photos[photos.length - 1]! : null;
}

function tooBig(ref: { file_size?: number }): boolean {
  return (ref.file_size ?? 0) > MAX_FILE_BYTES;
}

function classifyDocument(doc: TgFileRef): DraftPart | RejectReason {
  if (tooBig(doc)) return "too_big";
  const mime = (doc.mime_type ?? "").toLowerCase();
  const name = doc.file_name ?? "";
  const size = doc.file_size ?? 0;
  if (mime === "image/jpeg" || /\.jpe?g$/i.test(name)) return { kind: "image", fileId: doc.file_id, size, origin: "document" };
  if (mime.startsWith("image/")) return "image_as_file";
  if (mime.startsWith("audio/")) return { kind: "audio", fileId: doc.file_id, size, mime, origin: "document" };
  if (mime === "application/pdf" || /\.pdf$/i.test(name)) return "pdf";
  if (TEXT_FILE_MIME.has(mime) || TEXT_FILE_EXT.test(name)) return { kind: "textfile", fileId: doc.file_id, size };
  return "unsupported";
}

/** Decide what one private-chat message is: a command, draft content, or something we refuse. */
export function classifyMessage(msg: TgMessage): Classified {
  if (msg.successful_payment) return { type: "payment", payment: msg.successful_payment };
  if (msg.refunded_payment) return { type: "service" };
  if (typeof msg.text === "string") {
    if (isCommand(msg.text, msg.entities)) return { type: "command", ...parseCommand(msg.text) };
    const p = textPart(msg.text);
    return p ? { type: "content", parts: [p], mediaGroupId: null } : { type: "reject", reason: "unsupported" };
  }

  const parts: DraftPart[] = [];
  if (msg.photo && msg.photo.length > 0) {
    const ph = largestPhoto(msg.photo)!;
    if (tooBig(ph)) return { type: "reject", reason: "too_big" };
    parts.push({ kind: "image", fileId: ph.file_id, size: ph.file_size ?? 0, origin: "photo" });
  } else if (msg.voice) {
    if (tooBig(msg.voice)) return { type: "reject", reason: "too_big" };
    parts.push({
      kind: "audio",
      fileId: msg.voice.file_id,
      size: msg.voice.file_size ?? 0,
      mime: msg.voice.mime_type ?? "audio/ogg",
      origin: "voice",
    });
  } else if (msg.audio) {
    if (tooBig(msg.audio)) return { type: "reject", reason: "too_big" };
    parts.push({
      kind: "audio",
      fileId: msg.audio.file_id,
      size: msg.audio.file_size ?? 0,
      mime: msg.audio.mime_type ?? "audio/mpeg",
      origin: "audio",
    });
  } else if (msg.document) {
    const d = classifyDocument(msg.document);
    if (typeof d === "string") return { type: "reject", reason: d };
    parts.push(d);
  } else {
    // video, video_note, animation, sticker, location, contact, poll, …
    return { type: "reject", reason: "unsupported" };
  }

  if (typeof msg.caption === "string") {
    const c = textPart(msg.caption);
    if (c) parts.push(c);
  }
  return { type: "content", parts, mediaGroupId: msg.media_group_id ?? null };
}

// ── Draft ───────────────────────────────────────────────────────────────────────

export type DraftStatus = "collecting" | "prompted" | "creating" | "done";

export interface DraftUser {
  id: number;
  username: string | null;
  firstName: string | null;
  languageCode: string | null;
}

export interface Draft {
  id: string;
  chatId: number;
  user: DraftUser;
  lang: Lang;
  status: DraftStatus;
  parts: DraftPart[];
  /** True when parts beyond MAX_PARTS were dropped. */
  overflow: boolean;
  mediaGroupIds: string[];
  createdAt: number;
  updatedAt: number;
  /** When the collecting draft is due to be prompted (debounce deadline). */
  dueAt: number;
  promptMessageId: number | null;
  /** Fixed checklist id: a retried create re-writes the SAME document instead of a duplicate. */
  cloudId: string;
  flushAttempts: number;
}

/** A per-chat draft id, strictly increasing and short (base36 millis). Also the request_id suffix. */
export function nextDraftId(lastNumeric: number, now: number): { id: string; numeric: number } {
  const numeric = Math.max(now, lastNumeric + 1);
  return { id: numeric.toString(36), numeric };
}

export function newDraft(args: {
  id: string;
  chatId: number;
  user: DraftUser;
  lang: Lang;
  cloudId: string;
  now: number;
}): Draft {
  return {
    id: args.id,
    chatId: args.chatId,
    user: args.user,
    lang: args.lang,
    status: "collecting",
    parts: [],
    overflow: false,
    mediaGroupIds: [],
    createdAt: args.now,
    updatedAt: args.now,
    dueAt: args.now + DEBOUNCE_MS,
    promptMessageId: null,
    cloudId: args.cloudId,
    flushAttempts: 0,
  };
}

/** Append parts (capped at MAX_PARTS) and push the debounce deadline out. Mutates `draft`. */
export function addParts(draft: Draft, parts: DraftPart[], mediaGroupId: string | null, now: number): void {
  for (const p of parts) {
    if (draft.parts.length >= MAX_PARTS) {
      draft.overflow = true;
      break;
    }
    draft.parts.push(p);
  }
  if (mediaGroupId && !draft.mediaGroupIds.includes(mediaGroupId)) draft.mediaGroupIds.push(mediaGroupId);
  draft.updatedAt = now;
  draft.dueAt = now + DEBOUNCE_MS;
}

export function draftCounts(parts: DraftPart[]): DraftCounts {
  const c: DraftCounts = { texts: 0, photos: 0, audios: 0, files: 0, links: 0 };
  for (const p of parts) {
    if (p.kind === "text") c.texts++;
    else if (p.kind === "image") c.photos++;
    else if (p.kind === "audio") c.audios++;
    else if (p.kind === "textfile") c.files++;
    else c.links++;
  }
  return c;
}

function fileParts(parts: DraftPart[]): DraftPart[] {
  return parts.filter((p) => p.kind === "image" || p.kind === "audio" || p.kind === "textfile");
}

/** More than one file in the draft — the CF takes one, so the prompt warns. */
export function hasMultipleFiles(draft: Draft): boolean {
  return fileParts(draft.parts).length > 1;
}

/**
 * The free option: only for a text-only draft (links count as non-text) that yields ≥1 item.
 * All text parts are joined line-wise, so "one item per message" works as well as one long list.
 */
export function freeOption(draft: Draft, defaultTitle: string): ParsedLines | null {
  if (draft.parts.length === 0 || draft.parts.some((p) => p.kind !== "text")) return null;
  const text = draft.parts.map((p) => (p.kind === "text" ? p.text : "")).join("\n");
  const parsed = parseLines(text, defaultTitle);
  return parsed.items.length > 0 ? parsed : null;
}

/** One generate_checklist call, before any file is downloaded. */
export interface AiPlan {
  inputType: "text" | "url" | "image_base64" | "audio_base64";
  prompt: string;
  /** Ready input for text / url plans. */
  inputData: string;
  /** For file plans: what to download (image/audio → base64, textfile → UTF-8 text). */
  file: { fileId: string; kind: "image" | "audio" | "textfile"; mime: string } | null;
  /** Where the input came from — the `input` property of `tg_checklist_created`. */
  input: ChecklistInput;
}

function withNotes(prompt: string, notes: string): string {
  return notes ? `${prompt}\n\nUser's notes:\n${notes}` : prompt;
}

/**
 * What the AI button sends. Priority: image > audio > text file > link > plain text. The prompts
 * mirror the app's own (AnalyzeRepositoryImpl.getPromptFromInput); captions and extra messages ride
 * along as notes.
 */
export function aiPlan(draft: Draft): AiPlan | null {
  const texts = draft.parts.flatMap((p) => (p.kind === "text" ? [p.text] : []));
  const links = draft.parts.flatMap((p) => (p.kind === "link" ? [p.url] : []));
  const file = fileParts(draft.parts)[0];

  if (file && file.kind !== "text" && file.kind !== "link") {
    const notes = [...texts, ...links].join("\n\n");
    if (file.kind === "image") {
      return {
        inputType: "image_base64",
        prompt: withNotes("Create a checklist based on this image", notes),
        inputData: "",
        file: { fileId: file.fileId, kind: "image", mime: "image/jpeg" },
        input: file.origin,
      };
    }
    if (file.kind === "audio") {
      return {
        inputType: "audio_base64",
        prompt: withNotes("Create a checklist from this voice recording", notes),
        inputData: "",
        file: { fileId: file.fileId, kind: "audio", mime: file.mime },
        input: file.origin,
      };
    }
    return {
      inputType: "text",
      prompt: withNotes("Create a checklist from this text file", notes),
      inputData: "",
      file: { fileId: file.fileId, kind: "textfile", mime: "text/plain" },
      input: "document",
    };
  }

  const [firstLink, ...otherLinks] = links;
  if (firstLink) {
    return {
      inputType: "url",
      prompt: withNotes(`Create a checklist from the content at: ${firstLink}`, [...texts, ...otherLinks].join("\n\n")),
      inputData: firstLink,
      file: null,
      input: "url",
    };
  }

  if (texts.length === 0) return null;
  return {
    inputType: "text",
    prompt: "Create a checklist based on this content",
    inputData: texts.join("\n\n"),
    file: null,
    input: "text",
  };
}

/**
 * The length the CF compares with `ai_analysis_max_input_length`: prompt only for binary inputs,
 * prompt + input_data otherwise (main.py generate_checklist). Checked here so an over-long input is
 * refused with a clear message instead of a generic 400.
 */
export function aiInputLength(inputType: AiPlan["inputType"], prompt: string, inputData: string): number {
  return inputType === "image_base64" || inputType === "audio_base64" ? prompt.length : prompt.length + inputData.length;
}

/** request_id for generate_checklist: stable per draft → a retry never double-charges. */
export function aiRequestId(chatId: number, draftId: string): string {
  return `tg-${chatId}-${draftId}`;
}
