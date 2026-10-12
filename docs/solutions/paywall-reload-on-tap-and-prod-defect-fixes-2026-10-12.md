---
title: "Paywall: reload the catalog on a Buy tap, plus three production defect fixes"
date: 2026-10-12
category: bug-fix
project: gisti-ai-checklists
modules: [feature/paywall, feature/aichat, feature/home, firebase-functions, core/common, core/designsystem]
keywords: [paywall, revenuecat, getOfferings, purchase_product_not_found, products_reload_on_tap, HorizontalPager, IndexOutOfBounds, generate_checklist, JSONDecodeError, classify_chat_intent]
---

# Paywall: reload the catalog on a Buy tap, plus three production defect fixes

Four defects from the 2026-10-12 healthcheck, each with a red-first reproduction test.

## 1. A Buy tap on an unloaded catalog was a dead end

**Cause.** `PaywallViewModel.purchase()` treated "no product for the selected plan" as
terminal. The catalog was requested once, in `init`, and never again.

**Fix.** A tap with an empty catalog starts a single-flight reload.

- Success: prices appear with a notice (`paywall_prices_loaded`). The purchase is **not**
  started automatically — the app must not begin a purchase before it has shown a price.
- Failure: `PaywallErrorCode.isRetryableCatalogError()` splits network and store hiccups
  (`paywall_reload_failed_retry`) from "billing unavailable" (`paywall_billing_unavailable`).
  The button keeps working.
- One new event, `products_reload_on_tap`. The reload does **not** emit
  `products_load_success` / `products_load_failed`, so the per-open series stays comparable.

RevenueCat does not cache a failed offerings fetch, so nothing needs resetting before a retry;
only network and store errors can succeed on retry, so there is no automatic backoff loop.

Test seam: `getString` does not resolve in a JVM host test, so the ViewModel takes a
`resolveString` parameter.

## 2. Attachment viewer crashed when the list shrank

**Cause.** `AttachmentFullscreenViewer` indexed the list with `pagerState.currentPage`.
`PagerState` reads `pageCount` live but corrects `currentPage` only after the next measure
pass, so right after a delete the index can point past the end.

**Fix.** `clampedViewerPage()` in the body, `getOrNull` in the page lambda, a pager `key` by
attachment id, and closing an emptied viewer from a `LaunchedEffect`.

## 3. `generate_checklist` did not retry an empty Gemini response

One retry on `json.JSONDecodeError`; a second failure re-raises into the existing handler, so
the refund stays in one place per outcome.

## 4. Chat sent over-long text to the Layer 2 classifier

`AiChatRepositoryImpl.classifyViaLayer2` skips the classifier above `CLASSIFIER_MAX_INPUT_LEN`
and escalates to Layer 3 directly. The constant mirrors the server cap in
`firebase-functions/main.py`; drift is safe both ways because the server still answers 400 and
the client still escalates.

## Pitfalls

- A reload spinner must not reuse `isPurchasing`: that flag also locks Back and Close.
- `pagerState.currentPage` is not bounds-safe across a list change; the pager `key` lambda
  must tolerate an out-of-range index too.

## Neighbours left untouched

- `analyze_and_fill_checklist` and `classify_chat_intent` share the old no-retry shape.
- Onboarding paywall steps show neither the error nor the notice text.
- The standalone paywall button has no loading indicator during a reload.
