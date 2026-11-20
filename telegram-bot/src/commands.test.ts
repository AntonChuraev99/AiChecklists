import { describe, expect, it } from "vitest";
import { COMMANDS, resolveCommand } from "./commands";
import { classifyMessage, parseCommand } from "./draft";
import { BOT_COMMANDS, LANGS, t } from "./i18n";

describe("commands", () => {
  it("parses the new commands, with and without the @bot suffix", () => {
    expect(parseCommand("/premium")).toEqual({ command: "premium", arg: "" });
    expect(parseCommand("/app@gisti_app_bot")).toEqual({ command: "app", arg: "" });
    expect(parseCommand("/PaySupport")).toEqual({ command: "paysupport", arg: "" });
  });

  it("resolves known commands and sends anything else to help", () => {
    for (const c of COMMANDS) expect(resolveCommand(c)).toBe(c);
    expect(resolveCommand("subscribe")).toBe("help");
    expect(resolveCommand("")).toBe("help");
  });

  it("every menu command has a handler, and the menu has premium → app → help in that order", () => {
    for (const lang of LANGS) {
      const names = BOT_COMMANDS[lang].map((c) => c.command);
      for (const n of names) expect(COMMANDS).toContain(n);
      const i = (n: string) => names.indexOf(n);
      expect(i("premium")).toBeGreaterThanOrEqual(0);
      expect(i("premium")).toBeLessThan(i("app"));
      expect(i("app")).toBeLessThan(i("help"));
    }
    expect(BOT_COMMANDS.en.find((c) => c.command === "premium")?.description).toBe("Subscription");
    expect(BOT_COMMANDS.ru.find((c) => c.command === "premium")?.description).toBe("Подписка");
    expect(BOT_COMMANDS.en.find((c) => c.command === "app")?.description).toBe("Mobile app");
    expect(BOT_COMMANDS.ru.find((c) => c.command === "app")?.description).toBe("Мобильное приложение");
  });

  it("Phase 2: /chat and /timezone have handlers, are in every menu and in /help", () => {
    expect(resolveCommand("chat")).toBe("chat");
    expect(resolveCommand("timezone")).toBe("timezone");
    expect(parseCommand("/chat what's due today?")).toEqual({ command: "chat", arg: "what's due today?" });
    for (const lang of LANGS) {
      const names = BOT_COMMANDS[lang].map((c) => c.command);
      expect(names).toContain("chat");
      expect(names).toContain("timezone");
      expect(t(lang).help).toContain("/chat");
      expect(t(lang).help).toContain("/timezone");
    }
  });

  it("classifies a successful_payment service message as a payment, refunded_payment as silent", () => {
    const chat = { id: 42, type: "private" as const };
    const payment = { currency: "XTR", total_amount: 100, invoice_payload: "premium_month_v1", telegram_payment_charge_id: "c" };
    expect(classifyMessage({ message_id: 1, chat, successful_payment: payment })).toEqual({ type: "payment", payment });
    expect(
      classifyMessage({
        message_id: 2,
        chat,
        refunded_payment: { currency: "XTR", total_amount: 100, invoice_payload: "p", telegram_payment_charge_id: "c" },
      }),
    ).toEqual({ type: "service" });
  });
});
