import { describe, expect, it } from "vitest";
import { BOT_COMMANDS, BOT_DESCRIPTIONS, formatDate, LANGS, pickLang, t } from "./i18n";

describe("pickLang", () => {
  it("maps Telegram language codes to EN / RU / HI", () => {
    expect(pickLang("ru")).toBe("ru");
    expect(pickLang("ru-RU")).toBe("ru");
    expect(pickLang("hi")).toBe("hi");
    expect(pickLang("HI_in")).toBe("hi");
    expect(pickLang("en")).toBe("en");
  });

  it("falls back to EN for anything else", () => {
    expect(pickLang(undefined)).toBe("en");
    expect(pickLang(null)).toBe("en");
    expect(pickLang("")).toBe("en");
    expect(pickLang("uk")).toBe("en");
    expect(pickLang("pt-br")).toBe("en");
  });
});

describe("strings", () => {
  it("every language defines every string, none empty", () => {
    const keys = Object.keys(t("en")).sort();
    for (const lang of LANGS) {
      const s = t(lang);
      expect(Object.keys(s).sort()).toEqual(keys);
      for (const [k, v] of Object.entries(s)) {
        if (typeof v === "string") expect(v.length, `${lang}.${k}`).toBeGreaterThan(0);
      }
    }
  });

  it("uses Russian plural forms", () => {
    const s = t("ru");
    expect(s.created("X", 1)).toContain("1 пункт.");
    expect(s.created("X", 3)).toContain("3 пункта");
    expect(s.created("X", 5)).toContain("5 пунктов");
    expect(s.created("X", 11)).toContain("11 пунктов");
    expect(s.created("X", 21)).toContain("21 пункт.");
    expect(s.btnAi(20)).toContain("20 кредитов");
  });

  it("fits createInvoiceLink limits (title 1-32, description 1-255) in every language", () => {
    for (const lang of LANGS) {
      const s = t(lang);
      expect(s.invoiceTitle.length).toBeGreaterThan(0);
      expect(s.invoiceTitle.length).toBeLessThanOrEqual(32);
      expect(s.invoiceDescription.length).toBeGreaterThan(0);
      expect(s.invoiceDescription.length).toBeLessThanOrEqual(255);
      expect(s.help).toContain("/premium");
      expect(s.help).toContain("/app");
      expect(s.paySupport("a@b.c", "https://gisti-app.web.app/terms")).toContain("https://gisti-app.web.app/terms");
    }
  });

  it("formats dates per language in UTC", () => {
    const ms = Date.UTC(2026, 11, 19, 23, 30);
    expect(formatDate(ms, "en")).toBe("19 December 2026");
    expect(formatDate(ms, "ru")).toContain("2026");
    expect(formatDate(ms, "ru")).toContain("19");
  });

  it("fits Telegram's profile limits", () => {
    for (const lang of LANGS) {
      expect(BOT_DESCRIPTIONS[lang].description.length).toBeLessThanOrEqual(512);
      expect(BOT_DESCRIPTIONS[lang].short.length).toBeLessThanOrEqual(120);
      for (const c of BOT_COMMANDS[lang]) {
        expect(c.command).toMatch(/^[a-z0-9_]{1,32}$/);
        expect(c.description.length).toBeGreaterThan(0);
        expect(c.description.length).toBeLessThanOrEqual(256);
      }
    }
  });
});
