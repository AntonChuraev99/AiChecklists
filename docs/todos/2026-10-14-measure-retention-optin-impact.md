---
status: open
blocking_reason: needs-release-and-data
resume_trigger: "Android release containing commit 470dd9b5 is at 100% in production AND 14 days passed (first read); 28 days (decision read)"
keywords: [retention, comeback, ComebackGate, POST_NOTIFICATIONS, ActivationReminderSheet, NotificationsOffBanner, activation_bundle_v1, healthcheck]
---

# Measure the impact of the retention opt-in release

Shipped in the branch `worktree-retention-comeback-delivery` (commit 470dd9b5): come-back nudge delivery fix, activation bundle always on, notification opt-in sheet after the first own checklist or Inbox task, notifications-off banner. Task log: `docs/archive/retention-reminder-optin-2026-10-14.md`.

Baselines, exact queries and success thresholds are numbers, so they live in the gitignored `docs/reports/verification/retention-optin-2026-10-14.md` (public repo). `/healthcheck` reads that file's `verification:` block (Step 8) and this todo (Step 9).

## What to measure (real devices only: `device_type is not (none)`, Android)

1. **Come-back nudge reaches people** — `push_received` with `push_type=comeback` appears at all; share of `retention_comeback_fired` that ends in a shown push; `retention_comeback_rescheduled` volume; `retention_comeback_skipped` by `reason`.
2. **Notification opt-in** — share of new users on runtime-permission Android versions with notifications on (`push_permission_state.notifications_enabled`); `notification_permission_result` granted share by `source` (`activation_sheet`, `banner`, `checklist_reminder`, `item_reminder`, `inbox`, `onboarding`). <!-- docs-leak-scan: reviewed — metric names only, no figures -->

3. **Sheet funnel** — `activation_reminder_optin` by `outcome`; `reminder_set` / `item_reminder_set` with `source=activation_sheet`.
4. **Banner** — `notif_banner_shown` / `notif_banner_cta` (by `action`) / `notif_banner_dismissed` (by `dismiss_count`), each by `screen`; grants with `source=banner`.
5. **Primary metric** — D1+ return (`screen_view`) of new users who saw a screen, release cohort vs the pre-release baseline. Guardrail: share of new users with a day-0 action (`item_checked` or `ai_chat_message_sent`) does not drop. <!-- docs-leak-scan: reviewed — metric names only, no figures -->
6. **Side effect of removing the auto-seeded starter checklist** — share of new users who create their first checklist / first Inbox task on day 0, vs baseline. <!-- docs-leak-scan: reviewed — metric names only, no figures -->

## Decision rules

- Item 1 shows zero comeback pushes after 14 days while fires exist → delivery still broken, reopen (check `MainActivity` foreground writes and the reschedule window).
- Item 2 does not move against baseline after 28 days → the sheet/banner copy or timing is not working; product review.
- Item 5 at 28 days: traffic is small, read it as a direction, not a verdict; compare with the confidence interval. <!-- docs-leak-scan: reviewed — no figures -->
- Also check the open hypothesis: lower fired/scheduled ratio since 1.21.1 = analytics flush in a background-woken process, not lost delivery (compare scheduled users per new user on 1.20.0 vs 1.21.x).
