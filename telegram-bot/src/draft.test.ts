import { describe, expect, it } from "vitest";
import {
  addParts,
  aiInputLength,
  aiPlan,
  aiRequestId,
  classifyMessage,
  DEBOUNCE_MS,
  freeOption,
  MAX_PARTS,
  newDraft,
  nextDraftId,
  parseCommand,
  type Draft,
  type DraftPart,
} from "./draft";
import type { TgMessage } from "./telegram";

const chat = { id: 42, type: "private" as const };

function msg(extra: Partial<TgMessage>): TgMessage {
  return { message_id: 1, chat, from: { id: 42 }, ...extra };
}

function draftWith(parts: DraftPart[]): Draft {
  const d = newDraft({
    id: "abc",
    chatId: 42,
    user: { id: 42, username: null, firstName: null, languageCode: "en" },
    lang: "en",
    cloudId: "cloud",
    now: 0,
  });
  addParts(d, parts, null, 0);
  return d;
}

describe("classifyMessage", () => {
  it("recognises commands only with a bot_command entity at offset 0", () => {
    expect(
      classifyMessage(msg({ text: "/start@gisti_app_bot tok_EN-123", entities: [{ type: "bot_command", offset: 0, length: 20 }] })),
    ).toEqual({ type: "command", command: "start", arg: "tok_EN-123" });
    expect(classifyMessage(msg({ text: "/usr/bin is a path" })).type).toBe("content");
  });

  it("treats a lone URL as a link and other text as text", () => {
    expect(classifyMessage(msg({ text: "https://example.com/recipe" }))).toEqual({
      type: "content",
      parts: [{ kind: "link", url: "https://example.com/recipe" }],
      mediaGroupId: null,
    });
    expect(classifyMessage(msg({ text: "see https://example.com" })).type).toBe("content");
  });

  it("takes the largest photo and keeps the caption and album id", () => {
    const c = classifyMessage(
      msg({
        photo: [
          { file_id: "small", file_size: 10 },
          { file_id: "big", file_size: 1000 },
        ],
        caption: "receipt",
        media_group_id: "g1",
      }),
    );
    expect(c).toEqual({
      type: "content",
      parts: [
        { kind: "image", fileId: "big", size: 1000, origin: "photo" },
        { kind: "text", text: "receipt" },
      ],
      mediaGroupId: "g1",
    });
  });

  it("accepts voice/audio, JPEG and text documents; refuses PDF, other images, video and >14 MB", () => {
    expect(classifyMessage(msg({ voice: { file_id: "v", file_size: 5 } }))).toMatchObject({
      parts: [{ kind: "audio", mime: "audio/ogg", origin: "voice" }],
    });
    expect(classifyMessage(msg({ audio: { file_id: "a", file_size: 13 * 1024 * 1024 } }))).toMatchObject({
      parts: [{ kind: "audio", origin: "audio" }],
    });
    expect(classifyMessage(msg({ photo: [{ file_id: "p", file_size: 15 * 1024 * 1024 }] }))).toEqual({
      type: "reject",
      reason: "too_big",
    });
    expect(classifyMessage(msg({ document: { file_id: "d", mime_type: "text/plain", file_name: "a.txt" } }))).toMatchObject({
      parts: [{ kind: "textfile" }],
    });
    expect(classifyMessage(msg({ document: { file_id: "d", mime_type: "image/jpeg" } }))).toMatchObject({
      parts: [{ kind: "image", origin: "document" }],
    });
    expect(classifyMessage(msg({ document: { file_id: "d", mime_type: "application/pdf" } }))).toEqual({
      type: "reject",
      reason: "pdf",
    });
    expect(classifyMessage(msg({ document: { file_id: "d", mime_type: "image/png" } }))).toEqual({
      type: "reject",
      reason: "image_as_file",
    });
    expect(classifyMessage(msg({ video: { file_id: "x" } }))).toEqual({ type: "reject", reason: "unsupported" });
    expect(classifyMessage(msg({ audio: { file_id: "a", file_size: 21 * 1024 * 1024 } }))).toEqual({
      type: "reject",
      reason: "too_big",
    });
  });
});

describe("parseCommand", () => {
  it("lowercases the command and trims the argument", () => {
    expect(parseCommand("/Lists")).toEqual({ command: "lists", arg: "" });
    expect(parseCommand("/start   abc  ")).toEqual({ command: "start", arg: "abc" });
  });
});

describe("draft", () => {
  it("pushes the debounce deadline out on every addition and caps parts", () => {
    const d = draftWith([]);
    addParts(d, [{ kind: "text", text: "a" }], null, 1000);
    expect(d.dueAt).toBe(1000 + DEBOUNCE_MS);
    addParts(d, Array.from({ length: MAX_PARTS + 3 }, () => ({ kind: "text" as const, text: "x" })), "g", 2000);
    expect(d.parts).toHaveLength(MAX_PARTS);
    expect(d.overflow).toBe(true);
    expect(d.mediaGroupIds).toEqual(["g"]);
    expect(d.dueAt).toBe(2000 + DEBOUNCE_MS);
  });

  it("offers the free option only for text-only drafts, joining messages line-wise", () => {
    const text = draftWith([
      { kind: "text", text: "Trip:\n- passport" },
      { kind: "text", text: "tickets" },
    ]);
    expect(freeOption(text, "Default")).toEqual({ title: "Trip", items: ["passport", "tickets"], truncated: false });
    expect(freeOption(draftWith([{ kind: "link", url: "https://x.y" }]), "Default")).toBeNull();
    expect(
      freeOption(draftWith([{ kind: "image", fileId: "f", size: 1, origin: "photo" }, { kind: "text", text: "a" }]), "Default"),
    ).toBeNull();
  });

  it("plans the AI call: image first, captions as notes", () => {
    const plan = aiPlan(
      draftWith([
        { kind: "text", text: "for Saturday" },
        { kind: "image", fileId: "p1", size: 1, origin: "photo" },
        { kind: "audio", fileId: "a1", size: 1, mime: "audio/ogg", origin: "voice" },
      ]),
    );
    expect(plan?.inputType).toBe("image_base64");
    expect(plan?.file).toEqual({ fileId: "p1", kind: "image", mime: "image/jpeg" });
    expect(plan?.prompt).toContain("for Saturday");
    expect(plan?.input).toBe("photo");
  });

  it("plans a link as input_type url and plain text as input_type text", () => {
    expect(aiPlan(draftWith([{ kind: "link", url: "https://r.example" }]))).toMatchObject({
      inputType: "url",
      inputData: "https://r.example",
      file: null,
      input: "url",
    });
    expect(aiPlan(draftWith([{ kind: "text", text: "milk" }, { kind: "text", text: "eggs" }]))).toMatchObject({
      inputType: "text",
      inputData: "milk\n\neggs",
      input: "text",
    });
  });

  it("reports the analytics input of file plans by where the file came from", () => {
    expect(aiPlan(draftWith([{ kind: "audio", fileId: "v", size: 1, mime: "audio/ogg", origin: "voice" }]))?.input).toBe("voice");
    expect(aiPlan(draftWith([{ kind: "audio", fileId: "a", size: 1, mime: "audio/mpeg", origin: "audio" }]))?.input).toBe("audio");
    expect(aiPlan(draftWith([{ kind: "image", fileId: "j", size: 1, origin: "document" }]))?.input).toBe("document");
    expect(aiPlan(draftWith([{ kind: "textfile", fileId: "t", size: 1 }]))?.input).toBe("document");
  });

  it("measures input length like the CF (binary → prompt only)", () => {
    expect(aiInputLength("image_base64", "abc", "x".repeat(10_000))).toBe(3);
    expect(aiInputLength("text", "abc", "12345")).toBe(8);
  });

  it("derives a stable request_id and strictly increasing draft ids", () => {
    expect(aiRequestId(42, "abc")).toBe("tg-42-abc");
    const a = nextDraftId(0, 1000);
    const b = nextDraftId(a.numeric, 1000);
    expect(b.numeric).toBeGreaterThan(a.numeric);
    expect(a.id).toBe((1000).toString(36));
  });
});
