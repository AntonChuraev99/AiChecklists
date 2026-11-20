import { describe, expect, it } from "vitest";
import { decodeCallback } from "./callback";
import { t } from "./i18n";
import {
  formatHm,
  formatLocal,
  isValidZone,
  offsetLabel,
  offsetMinutesAt,
  parseWhenIso,
  renderTimezonePicker,
  TZ_CHOICES,
  wallTimeToEpoch,
  zoneForOffset,
} from "./timezone";

const NOW = Date.UTC(2026, 10, 20, 11, 5, 0); // 2026-11-20 11:05 UTC

describe("zoneForOffset — Etc/GMT sign is inverted", () => {
  it("maps whole hours to Etc/GMT∓N and +5:30 to Asia/Kolkata", () => {
    expect(zoneForOffset(180)).toBe("Etc/GMT-3"); // UTC+3
    expect(zoneForOffset(-300)).toBe("Etc/GMT+5"); // UTC-5
    expect(zoneForOffset(720)).toBe("Etc/GMT-12");
    expect(zoneForOffset(0)).toBe("Etc/GMT");
    expect(zoneForOffset(330)).toBe("Asia/Kolkata");
    expect(zoneForOffset(345)).toBeNull();
  });

  it("every offered zone is valid and observes exactly its offset", () => {
    for (const minutes of TZ_CHOICES) {
      const zone = zoneForOffset(minutes)!;
      expect(isValidZone(zone), zone).toBe(true);
      expect(offsetMinutesAt(zone, NOW), zone).toBe(minutes);
    }
  });
});

describe("offsets and formatting", () => {
  it("validates IANA names", () => {
    expect(isValidZone("Europe/Moscow")).toBe(true);
    expect(isValidZone("Mars/Base")).toBe(false);
    expect(isValidZone("")).toBe(false);
    expect(isValidZone(42)).toBe(false);
  });

  it("computes the current offset of a DST zone", () => {
    expect(offsetMinutesAt("America/New_York", Date.UTC(2026, 6, 1, 12))).toBe(-240);
    expect(offsetMinutesAt("America/New_York", Date.UTC(2026, 0, 15, 12))).toBe(-300);
    expect(offsetMinutesAt("Asia/Kolkata", NOW)).toBe(330);
  });

  it("formats labels and local times", () => {
    expect(offsetLabel(180)).toBe("UTC+3");
    expect(offsetLabel(-300)).toBe("UTC-5");
    expect(offsetLabel(330)).toBe("UTC+5:30");
    expect(offsetLabel(0)).toBe("UTC+0");
    expect(formatHm(NOW, "Etc/GMT-3")).toBe("14:05");
    expect(formatLocal(NOW, "Asia/Kolkata")).toBe("2026-11-20 16:35");
  });

  it("turns a wall time into the instant it denotes", () => {
    expect(wallTimeToEpoch("America/New_York", 2026, 7, 1, 9, 0, 0)).toBe(Date.UTC(2026, 6, 1, 13, 0, 0));
    expect(wallTimeToEpoch("America/New_York", 2026, 1, 15, 9, 0, 0)).toBe(Date.UTC(2026, 0, 15, 14, 0, 0));
  });
});

describe("parseWhenIso", () => {
  it("reads a bare local date-time in the user's zone", () => {
    expect(parseWhenIso("2026-06-01T09:00:00", "Etc/GMT-3")).toEqual({ kind: "ok", at: Date.UTC(2026, 5, 1, 6, 0, 0) });
    expect(parseWhenIso("2026-06-01T09:00", "Asia/Kolkata")).toEqual({ kind: "ok", at: Date.UTC(2026, 5, 1, 3, 30, 0) });
  });

  it("honours an explicit offset or Z", () => {
    expect(parseWhenIso("2026-06-01T09:00:00Z", "Etc/GMT-3")).toEqual({ kind: "ok", at: Date.UTC(2026, 5, 1, 9) });
    expect(parseWhenIso("2026-06-01T09:00:00+05:30", "Etc/GMT")).toEqual({ kind: "ok", at: Date.UTC(2026, 5, 1, 3, 30) });
  });

  it("refuses with timezone_unknown when no zone is stored, and invalid on garbage", () => {
    expect(parseWhenIso("2026-06-01T09:00:00", null)).toEqual({ kind: "tz_unknown" });
    expect(parseWhenIso("2026-06-01T09:00:00Z", null)).toEqual({ kind: "tz_unknown" });
    expect(parseWhenIso("tomorrow 9am", "Etc/GMT")).toEqual({ kind: "invalid" });
    expect(parseWhenIso("2026-02-30T09:00:00", "Etc/GMT")).toEqual({ kind: "invalid" });
    expect(parseWhenIso("2026-06-01", "Etc/GMT")).toEqual({ kind: "invalid" });
  });
});

describe("renderTimezonePicker", () => {
  it("offers one button per offset, labelled with its local time, carrying the offset", () => {
    const v = renderTimezonePicker(NOW, "Etc/GMT-3", t("en"));
    const buttons = v.keyboard.inline_keyboard.flat();
    expect(buttons).toHaveLength(TZ_CHOICES.length);
    const plus3 = buttons.find((b) => "callback_data" in b && decodeCallback(b.callback_data)?.t === "tz" && b.text.endsWith("UTC+3"));
    expect(plus3?.text).toBe("14:05 · UTC+3");
    for (const b of buttons) {
      const cb = "callback_data" in b ? decodeCallback(b.callback_data) : null;
      expect(cb?.t).toBe("tz");
      if (cb?.t === "tz") expect(TZ_CHOICES).toContain(cb.offset);
    }
    expect(v.text).toContain("UTC+3");
  });
});
