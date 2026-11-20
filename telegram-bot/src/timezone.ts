/**
 * The user's time zone for the AI chat — pure helpers, no I/O.
 *
 * Source of truth: the IANA string in `users/{google_uid}.timezone` (Phase 4 makes the app write
 * it; until then `/timezone` in the bot does). It feeds two things:
 *  - `timezone_offset_minutes` of every `chat_agent` call (the model resolves "tomorrow at 9" with it);
 *  - `set_item_reminder`, whose `when_iso` has no offset — it is the user's LOCAL wall time.
 *
 * `/timezone` asks «what time is it for you now?» and offers the current local time of a fixed set
 * of offsets. A whole-hour offset is stored as `Etc/GMT∓N` — the Etc sign is INVERTED (POSIX):
 * UTC+3 is `Etc/GMT-3`. +5:30 (India) has no Etc zone and is stored as `Asia/Kolkata`.
 */

import { encodeCallback } from "./callback";
import type { Strings } from "./i18n";
import type { InlineButton, InlineKeyboard } from "./telegram";

/** Offsets (minutes east of UTC) offered by `/timezone`, west → east. */
export const TZ_CHOICES: readonly number[] = [
  -480, -420, -360, -300, -180, 0, 60, 120, 180, 240, 300, 330, 360, 420, 480, 540, 600, 720,
];

const TZ_BUTTONS_PER_ROW = 3;

/** True when `tz` is an IANA zone this runtime knows. */
export function isValidZone(tz: unknown): tz is string {
  if (typeof tz !== "string" || tz.length === 0 || tz.length > 64) return false;
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

/** The zone stored for an offered offset: `Etc/GMT∓N` for whole hours (sign inverted), Kolkata for +5:30. */
export function zoneForOffset(minutes: number): string | null {
  if (minutes === 330) return "Asia/Kolkata";
  if (!Number.isInteger(minutes) || minutes % 60 !== 0 || minutes < -720 || minutes > 840) return null;
  const h = minutes / 60;
  if (h === 0) return "Etc/GMT";
  return `Etc/GMT${h > 0 ? "-" : "+"}${Math.abs(h)}`;
}

interface WallTime {
  y: number;
  mo: number;
  d: number;
  h: number;
  mi: number;
  s: number;
}

function wallTime(tz: string, ms: number): WallTime {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(new Date(ms));
  const get = (type: string): number => Number(parts.find((p) => p.type === type)?.value ?? "0");
  return { y: get("year"), mo: get("month"), d: get("day"), h: get("hour") % 24, mi: get("minute"), s: get("second") };
}

/** Minutes east of UTC that `tz` observes at the instant `ms`. */
export function offsetMinutesAt(tz: string, ms: number): number {
  const base = Math.floor(ms / 1000) * 1000;
  const w = wallTime(tz, base);
  return Math.round((Date.UTC(w.y, w.mo - 1, w.d, w.h, w.mi, w.s) - base) / 60_000);
}

/** The instant a wall-clock time in `tz` denotes (a DST gap/overlap is resolved by re-checking the offset once). */
export function wallTimeToEpoch(tz: string, y: number, mo: number, d: number, h: number, mi: number, s: number): number {
  const guess = Date.UTC(y, mo - 1, d, h, mi, s);
  const first = offsetMinutesAt(tz, guess);
  let at = guess - first * 60_000;
  const second = offsetMinutesAt(tz, at);
  if (second !== first) at = guess - second * 60_000;
  return at;
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

/** "14:05" in `tz`. */
export function formatHm(ms: number, tz: string): string {
  const w = wallTime(tz, ms);
  return `${pad2(w.h)}:${pad2(w.mi)}`;
}

/** "2026-06-01 09:00" in `tz` — the `details` time of a set reminder. */
export function formatLocal(ms: number, tz: string): string {
  const w = wallTime(tz, ms);
  return `${w.y}-${pad2(w.mo)}-${pad2(w.d)} ${pad2(w.h)}:${pad2(w.mi)}`;
}

/** "UTC+3", "UTC-5", "UTC+5:30", "UTC+0". */
export function offsetLabel(minutes: number): string {
  const sign = minutes < 0 ? "-" : "+";
  const abs = Math.abs(minutes);
  const m = abs % 60;
  return `UTC${sign}${Math.floor(abs / 60)}${m ? `:${pad2(m)}` : ""}`;
}

const ISO_RE =
  /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(Z|[+-]\d{2}(?::?\d{2})?)?$/i;

export type WhenParse = { kind: "ok"; at: number } | { kind: "invalid" } | { kind: "tz_unknown" };

/**
 * `set_item_reminder.when_iso` → epoch ms. Mirrors the app's `AgentToolCallMapper.parseIsoToEpochMs`:
 * an explicit offset / `Z` is absolute, a bare local date-time is the user's wall time (the app uses
 * the device zone, the bot the stored zone). Unparseable → `invalid` (the app's mapper returns null →
 * `unknown_tool`). No stored zone → `tz_unknown`, whatever the string: with no zone the server
 * assumed offset 0, so even an explicit offset from the model may be off by the user's real offset.
 */
export function parseWhenIso(iso: string, tz: string | null): WhenParse {
  const m = ISO_RE.exec(iso.trim());
  if (!m) return { kind: "invalid" };
  const [y, mo, d, h, mi] = [m[1], m[2], m[3], m[4], m[5]].map(Number) as [number, number, number, number, number];
  const s = m[6] ? Number(m[6]) : 0;
  if (mo < 1 || mo > 12 || d < 1 || h > 23 || mi > 59 || s > 59) return { kind: "invalid" };
  const probe = new Date(Date.UTC(y, mo - 1, d));
  if (probe.getUTCMonth() !== mo - 1 || probe.getUTCDate() !== d) return { kind: "invalid" }; // 2026-02-30
  if (tz === null) return { kind: "tz_unknown" };

  const off = m[7];
  if (off) {
    const local = Date.UTC(y, mo - 1, d, h, mi, s);
    if (off.toUpperCase() === "Z") return { kind: "ok", at: local };
    const om = /^([+-])(\d{2}):?(\d{2})?$/.exec(off)!;
    const minutes = (Number(om[2]) * 60 + Number(om[3] ?? "0")) * (om[1] === "-" ? -1 : 1);
    return { kind: "ok", at: local - minutes * 60_000 };
  }
  return { kind: "ok", at: wallTimeToEpoch(tz, y, mo, d, h, mi, s) };
}

/** `/timezone`: the question plus one button per offered offset, labelled with its current local time. */
export function renderTimezonePicker(now: number, current: string | null, s: Strings): { text: string; keyboard: InlineKeyboard } {
  const buttons: InlineButton[] = [];
  for (const minutes of TZ_CHOICES) {
    const zone = zoneForOffset(minutes);
    if (!zone) continue;
    buttons.push({
      text: `${formatHm(now, zone)} · ${offsetLabel(minutes)}`,
      callback_data: encodeCallback({ t: "tz", offset: minutes }),
    });
  }
  const rows: InlineButton[][] = [];
  for (let i = 0; i < buttons.length; i += TZ_BUTTONS_PER_ROW) rows.push(buttons.slice(i, i + TZ_BUTTONS_PER_ROW));
  const currentLabel = current ? offsetLabel(offsetMinutesAt(current, now)) : null;
  return { text: s.timezoneQuestion(currentLabel), keyboard: { inline_keyboard: rows } };
}
