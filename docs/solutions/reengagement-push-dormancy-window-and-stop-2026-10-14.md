---
title: "Re-engagement push: dormancy window, no-reaction stop, foreground-only activity"
summary: "The server re-engagement push kept hitting one long-gone cohort and a background wake counted as activity. A bounded dormancy window and a no-reaction stop for that push type; lastActiveAt only on foreground."
date: 2026-10-14
type: bug-fix
modules: [firebase-functions, composeApp/androidMain, feature/user]
keywords: [push, notification, send_promotions_batch, reengagement, register_push_token, mark_active, lastActiveAt, promosSinceActive, no_reaction_stop, android]
project: Checklists
---

# Re-engagement push: dormancy window, no-reaction stop, foreground-only activity

**Суть:** `send_promotions_batch` applies the 3–14 day window and the 3-send stop only to
`push_type: "reengagement"` (`applies_reengagement_rules`). `register_push_token` writes
`lastActiveAt` only when `mark_active` is true, and the app sends true only on a foreground resume.

## Проблема / Контекст

Re-engagement had no upper dormancy bound, so every cooldown cycle re-targeted the same long-gone
users. `lastActiveAt` moved on every process wake — the promo's own FCM delivery wakes the process —
so "dormant" and "no reaction" could not be told apart.

## Решение

- `resolve_inactivity_window(data, push_type)`: re-engagement defaults to
  `DEFAULT_MAX_INACTIVE_DAYS`; other types keep no upper bound; only an explicit `max <= min` is a 400.
- `promos_since_activity` + `MAX_PROMOS_WITHOUT_ACTIVITY`: skip reason `no_reaction_stop`; the
  counter `promosSinceActive` (written by `promo_sent_fields`) resets when `lastActiveAt` is newer
  than `lastPromoSentAt`.
- `plan_push_registration`: `fcm_token` optional (activity-only path), `mark_active` defaults to true
  so old clients are unchanged.

## Почему именно так

A counter on the user doc, not the run log: `promo_push_log` keeps one aggregate row per run.
The Scheduler body is unchanged — the code default turns the window on. Server ships before the app,
since an old server rejects `fcm_token: null`. Client side: `push-token-retry-and-local-push-dismiss-2026-10-14.md`.

## Связанные файлы

- `firebase-functions/push_promotions.py`
- `firebase-functions/main.py`
- `docs/solutions/push-token-retry-and-local-push-dismiss-2026-10-14.md`
