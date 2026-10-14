# Retention: come-back nudge delivery, always-on activation bundle, notifications opt-in

**Статус:** Done
**Дата старта:** 2026-10-14
**Start SHA:** d2211a95
**Project:** Checklists
**Тип:** bug-fix
**Затронутые модули:** composeApp (retention, activation, App), feature/home (detail, inbox, notifications), feature/splash, feature/create, feature/analyze, feature/onboarding, feature/checklist, core/common/api, core/remoteconfig, core/designsystem

## Цель (продуктовая)

New users rarely come back after the first day, and no first-day action predicts a return. The return channel is effectively off: on the runtime-permission Android versions POST_NOTIFICATIONS is almost never granted (the app asked only when a reminder was set), and the come-back nudge never displayed in prod. Measurements live in gitignored notes, not here (public repo).

Goal: the come-back nudge reaches people who have notifications on; everyone new is offered notifications plus a "remind me" time right after their first own checklist; anyone with notifications off sees a dismissible banner on checklist screens.

Primary metric: D1+ return (`screen_view`) of new users who saw a screen. Guardrail: share with a D0 action does not drop. Evaluate 4 weeks after release.

## Технический план

1. Come-back nudge (`retention/*`): gate on foreground-only "last seen", reschedule instead of consuming while the user is still active (bounded window), re-arm pending alarm on app start and `MY_PACKAGE_REPLACED`, fallback target selection. Pure `ComebackGate` with tests.
2. Activation bundle always on: delete the `activation_bundle_v1` Remote Config flag (owner decision 2026-10-14, overrides the 2026-09-17 lock); the OFF-arm auto-seeded starter checklist is removed.
3. `ActivationReminderSheet` reworked (design-expert spec): preview of the new checklist, two time options, permission step, settings fallback; opened after the first own checklist from any create path.
4. `NotificationsOffBanner` on checklist detail and Inbox: shown when notifications are off (Android only), × hides for the session, hidden forever after dismissals in 3 sessions, CTA → system dialog or app notification settings after permanent denial.
5. Analytics: `notification_permission_requested/result` at every request site, banner events, `retention_comeback_rescheduled`.

## Лог итераций

### Итерация 1 — 2026-10-14 — product-expert: fresh retention readout + options → return channel is off; nudge never shown; permission almost never granted on 13+
### Итерация 2 — 2026-10-14 — android-platform-expert: come-back delivery fix → ComebackGate + foreground last-seen + re-arm; tests green
**Почему так:** `lastActiveMs` was written on every process start including background wakes (and the nudge alarm's own cold start), and the alarm was armed at minute 0 of D0, so the "already returned" gate skipped almost everything and consumed the one-shot. The same write skewed PushTimingResolver's preferred hour. Hypothesis not patched: the lower fired/scheduled ratio after 1.21.1 is likely an analytics-flush effect of the Amplitude SDK upgrade (background `eventOutOfSession` not flushed before the process is frozen), not lost delivery.
### Итерация 3 — 2026-10-14 — design-expert: DESIGN_SPEC banner + sheet rework → prototype frames, spec fixes for 320dp / fontScale 2.0
### Итерация 4 — 2026-10-14 — feature-expert: bundle flag removed, sheet decoupled & triggered from all create paths, banner logic, permission analytics → build green
### Итерация 5 — 2026-10-14 — compose-expert: InlineNudgeCard extracted (RecurringNudgeCard pixel-identical), banner + sheet per spec, Roborazzi matrix → green
### Итерация 6 — 2026-10-14 — feature-expert: owner asked to include the first Inbox task (v2 default) → `ReminderTarget.Item`, banner on Inbox; no sheet request on web/iOS
### Итерация 7 — 2026-10-14 — feature-expert: InboxAddTaskRowTest regression → resume check moved from InboxContent to InboxRoute (screen onIntent carries user actions only)
### Итерация 8 — 2026-10-14 — task-gate review fixes (feature-expert + android-platform-expert) → all confirmed findings fixed, tests green
**Почему так:** the review showed that a back/outside dismiss of the system dialog leaves `shouldShowRequestPermissionRationale` false, so "denied + no rationale" is not proof of a permanent denial; permanent is now recorded only when the rationale flips true→false or the OS answers without a dialog. Also: the Item target was lost on process death (memory-only pending state) — now saved with `ReminderTargetSaver`.

## Выводы

- Come-back nudge: the gate reads a foreground-only timestamp, reschedules instead of consuming while the user is active, and pending alarms are re-armed on app start / boot / package replace. Not verified on a device yet; prod check = `push_received` with `push_type=comeback` and `retention_comeback_rescheduled` after release.
- The lower fired/scheduled ratio after 1.21.1 is left as a hypothesis (analytics flush in a background-woken process), not patched.
- Activation bundle is unconditional, `activation_bundle_v1` is gone from code and `init.js.template`; the Firebase RC client template still holds the unused parameter (console cleanup is a separate step). The OFF-arm auto-seeded starter checklist no longer exists.
- Opt-in sheet after the first own checklist or Inbox/Today task, once per install, Android only; banner on checklist detail and Inbox.
- Pre-existing failure, not from this task: `AnalyzeSourcePickerScreenshotTest.analyze_voiceSelected_320dp` (Koin not started) fails on master as well.
- Time labels stay 24h like the rest of the app (no system 12/24h formatter exists).

## Предложения по улучшению агентов

- feature-expert: first draft classified "denied + no rationale" as a permanent denial; a note on Android 11+ dialog-dismiss semantics belongs in its platform pitfalls.
- feature-expert: put a lifecycle-resume intent through the screen's `onIntent`, which the screen contract reserves for user actions; route-level `sendIntent` is the pattern (ChecklistDetailScreen precedent).
