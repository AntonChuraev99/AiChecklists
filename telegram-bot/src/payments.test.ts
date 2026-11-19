import { describe, expect, it, vi } from "vitest";
import { parseRemoteConfig, parseRemoteConfigFields } from "./backend";
import { LANGS, t } from "./i18n";
import { PLAY_STORE_URL } from "./links";
import {
  applyPaymentBody,
  decidePreCheckout,
  invoiceParams,
  isRenewal,
  parseApplyPayment,
  parsePremiumStatus,
  paymentReplyText,
  preCheckoutAnswer,
  PREMIUM_PAYLOAD,
  SUBSCRIPTION_PERIOD_SEC,
  type PremiumStatus,
} from "./payments";
import { renderAppView, renderPremiumView } from "./views";

const q = (over: Partial<{ currency: string; total_amount: number; invoice_payload: string }> = {}) => ({
  currency: "XTR",
  total_amount: 100,
  invoice_payload: PREMIUM_PAYLOAD,
  ...over,
});

describe("decidePreCheckout", () => {
  it("accepts our payload in Stars at today's price from a linked user", () => {
    expect(decidePreCheckout(q(), true, 100)).toEqual({ ok: true });
  });

  it("refuses a price mismatch (price changed after the invoice was made)", () => {
    expect(decidePreCheckout(q({ total_amount: 100 }), true, 150)).toEqual({ ok: false, reason: "price_changed" });
  });

  it("refuses an unlinked Telegram account", () => {
    expect(decidePreCheckout(q(), false, 100)).toEqual({ ok: false, reason: "not_linked" });
  });

  it("refuses a non-Stars currency and a foreign payload", () => {
    expect(decidePreCheckout(q({ currency: "USD" }), true, 100)).toEqual({ ok: false, reason: "wrong_currency" });
    expect(decidePreCheckout(q({ invoice_payload: "other" }), true, 100)).toEqual({ ok: false, reason: "unknown_payload" });
  });

  it("builds answerPreCheckoutQuery params: ok, or ok:false with a localized error_message", () => {
    expect(preCheckoutAnswer("id1", { ok: true }, t("en"))).toEqual({ pre_checkout_query_id: "id1", ok: true });
    expect(preCheckoutAnswer("id2", { ok: false, reason: "not_linked" }, t("ru"))).toEqual({
      pre_checkout_query_id: "id2",
      ok: false,
      error_message: t("ru").preCheckoutNotLinked,
    });
    expect(preCheckoutAnswer("id3", { ok: false, reason: "unavailable" }, t("hi")).error_message).toBe(
      t("hi").preCheckoutUnavailable,
    );
  });
});

describe("invoiceParams", () => {
  it("is a Stars subscription: XTR, empty provider_token, one price = price from premium_status, 30-day period", () => {
    const status = parsePremiumStatus({ linked: true, is_premium: false, price_stars: 120 }, 100);
    const p = invoiceParams(t("en"), status.priceStars);
    expect(p).toMatchObject({
      currency: "XTR",
      provider_token: "",
      payload: PREMIUM_PAYLOAD,
      subscription_period: 2592000,
      prices: [{ label: t("en").invoiceLabel, amount: 120 }],
    });
    expect(SUBSCRIPTION_PERIOD_SEC).toBe(30 * 24 * 60 * 60);
    expect(new TextEncoder().encode(PREMIUM_PAYLOAD).length).toBeLessThanOrEqual(128);
  });
});

describe("parsePremiumStatus", () => {
  it("reads every field and falls back to the configured price", () => {
    expect(
      parsePremiumStatus(
        {
          success: true,
          linked: true,
          is_premium: true,
          source: "telegram",
          telegram_premium_until_ms: 1_800_000_000_000,
          subscription_state: "canceled",
          price_stars: 100,
        },
        999,
      ),
    ).toEqual({
      linked: true,
      isPremium: true,
      source: "telegram",
      telegramPremiumUntilMs: 1_800_000_000_000,
      subscriptionState: "canceled",
      priceStars: 100,
    });
    const bare = parsePremiumStatus({ linked: false, source: "weird", subscription_state: "x", price_stars: "100" }, 100);
    expect(bare).toMatchObject({ linked: false, isPremium: false, source: null, subscriptionState: null, priceStars: 100 });
  });
});

describe("applyPaymentBody", () => {
  it("forwards a successful_payment to apply_payment with numeric ids and explicit flags", () => {
    expect(
      applyPaymentBody(42, {
        currency: "XTR",
        total_amount: 100,
        invoice_payload: PREMIUM_PAYLOAD,
        subscription_expiration_date: 1_790_000_000,
        is_recurring: true,
        is_first_recurring: true,
        telegram_payment_charge_id: "stxCHARGE",
        provider_payment_charge_id: "",
      }),
    ).toEqual({
      telegram_user_id: 42,
      telegram_payment_charge_id: "stxCHARGE",
      total_amount: 100,
      currency: "XTR",
      invoice_payload: PREMIUM_PAYLOAD,
      is_recurring: true,
      is_first_recurring: true,
      subscription_expiration_date: 1_790_000_000,
    });
  });

  it("defaults the optional subscription fields", () => {
    expect(
      applyPaymentBody(42, { currency: "XTR", total_amount: 100, invoice_payload: "p", telegram_payment_charge_id: "c" }),
    ).toMatchObject({ is_recurring: false, is_first_recurring: false, subscription_expiration_date: null });
  });

  it("tells a renewal from the first charge", () => {
    const base = { currency: "XTR", total_amount: 100, invoice_payload: "p", telegram_payment_charge_id: "c" };
    expect(isRenewal({ ...base, is_recurring: true, is_first_recurring: true })).toBe(false);
    expect(isRenewal({ ...base, is_recurring: true })).toBe(true);
    expect(isRenewal(base)).toBe(false);
  });
});

describe("/premium view", () => {
  const offer: PremiumStatus = {
    linked: true,
    isPremium: false,
    source: null,
    telegramPremiumUntilMs: null,
    subscriptionState: null,
    priceStars: 100,
  };

  it("offer: perks with the remote-config cap, every-platform line, a 'Subscribe for N ⭐' invoice button", () => {
    const v = renderPremiumView(offer, 777, t("en"), "en", "https://t.me/$invoice");
    expect(v.text).toContain("up to 777");
    expect(v.text).toContain("Telegram");
    expect(v.text).toContain("Android");
    expect(v.text).toContain("web");
    expect(v.keyboard.inline_keyboard).toEqual([[{ text: "Subscribe for 100 ⭐", url: "https://t.me/$invoice" }]]);
  });

  it("store premium: already active, no invoice", () => {
    const v = renderPremiumView({ ...offer, isPremium: true, source: "store" }, 300, t("ru"), "ru", null);
    expect(v.text).toBe(t("ru").premiumStore);
    expect(v.keyboard.inline_keyboard).toEqual([]);
  });

  it("telegram premium: until-date and renewal state", () => {
    const v = renderPremiumView(
      { ...offer, isPremium: true, source: "telegram", telegramPremiumUntilMs: Date.UTC(2026, 11, 19), subscriptionState: "active" },
      300,
      t("en"),
      "en",
      null,
    );
    expect(v.text).toContain("19 December 2026");
    expect(v.text).toContain("renews automatically");
  });
});

describe("/app", () => {
  it("links to the exact Play listing with Telegram attribution", () => {
    expect(PLAY_STORE_URL).toBe(
      "https://play.google.com/store/apps/details?id=com.antonchuraev.aichecklists&referrer=utm_source%3Dtelegram%26utm_medium%3Dbot",
    );
    const v = renderAppView(t("hi"));
    expect(v.keyboard.inline_keyboard).toEqual([[{ text: t("hi").btnOpenPlay, url: PLAY_STORE_URL }]]);
  });
});

describe("parseRemoteConfig", () => {
  it("reads the Stars price and premium cap, with contract defaults", () => {
    expect(parseRemoteConfig({ telegram_premium_price_stars: 150, premium_daily_credits_cap: 250 })).toMatchObject({
      priceStars: 150,
      premiumDailyCap: 250,
    });
    expect(parseRemoteConfig(null)).toMatchObject({ priceStars: 100, premiumDailyCap: 300 });
  });

  it("takes only a positive integer price (Python price_stars_or_default), else the default", () => {
    expect(parseRemoteConfig({ telegram_premium_price_stars: 150.5 }).priceStars).toBe(100);
    expect(parseRemoteConfig({ telegram_premium_price_stars: 0 }).priceStars).toBe(100);
    expect(parseRemoteConfig({ telegram_premium_price_stars: "150" }).priceStars).toBe(100);
    // other fields keep their parsing: a fractional positive number is still accepted there
    expect(parseRemoteConfig({ ai_action_cost: 2.5 }).actionCost).toBe(2.5);
  });

  it("from Firestore: a price stored as a double (even 150.0) is the default, like Python; an integer is used", () => {
    expect(parseRemoteConfigFields({ telegram_premium_price_stars: { doubleValue: 150 } }).priceStars).toBe(100);
    expect(parseRemoteConfigFields({ telegram_premium_price_stars: { doubleValue: 150.5 } }).priceStars).toBe(100);
    expect(parseRemoteConfigFields({ telegram_premium_price_stars: { integerValue: "150" } }).priceStars).toBe(150);
    expect(parseRemoteConfigFields(null).priceStars).toBe(100);
    expect(
      parseRemoteConfigFields({ ai_action_cost: { doubleValue: 25 }, premium_daily_credits_cap: { integerValue: "250" } }),
    ).toMatchObject({ actionCost: 25, premiumDailyCap: 250, priceStars: 100 });
  });
});

describe("apply_payment outcome → message", () => {
  const until = Date.UTC(2026, 11, 19);

  it("parses applied / unassigned / no-date answers", () => {
    expect(parseApplyPayment({ applied: true, status: "applied", telegram_premium_until_ms: until }, "c")).toEqual({
      kind: "applied",
      untilMs: until,
    });
    expect(
      parseApplyPayment({ applied: false, status: "unassigned", telegram_premium_until_ms: null, duplicate: true }, "c"),
    ).toEqual({ kind: "unlinked" });
    const err = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(parseApplyPayment({ applied: true, status: "applied" }, "c")).toEqual({ kind: "pending" });
    err.mockRestore();
  });

  it("unlinked → the honest 'not connected, link and it is added' notice in every language, never 'shortly'", () => {
    for (const lang of LANGS) {
      const text = paymentReplyText({ kind: "unlinked" }, false, lang);
      expect(text).toBe(t(lang).paymentUnlinked);
      expect(text).not.toBe(t(lang).paymentPending);
      expect(text).toContain("/start");
      expect(text).toContain("/paysupport");
    }
  });

  it("applied → confirmation or renewal with the date; pending → 'shortly'", () => {
    expect(paymentReplyText({ kind: "applied", untilMs: until }, false, "en")).toBe(t("en").paymentConfirmed("19 December 2026"));
    expect(paymentReplyText({ kind: "applied", untilMs: until }, true, "en")).toBe(t("en").paymentRenewed("19 December 2026"));
    expect(paymentReplyText({ kind: "pending" }, false, "ru")).toBe(t("ru").paymentPending);
  });
});
