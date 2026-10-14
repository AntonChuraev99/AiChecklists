---
title: "Come-back nudge never shown + notification opt-in for new users"
summary: "Process start counted as user activity, so the D1 nudge skipped itself; permission was only asked on reminder. Fixed with a foreground-only gate, opt-in sheet and banner."
date: 2026-10-14
type: bug-fix
modules: [composeApp, feature/home, core/common, feature/onboarding, feature/splash]
keywords: [retention, ComebackGate, RetentionPushReceiver, POST_NOTIFICATIONS, shouldShowRequestPermissionRationale, ActivationReminderSheet, NotificationsOffBanner, activation_bundle_v1, android]
project: Checklists
---

# Come-back nudge never shown + notification opt-in for new users

**Суть:** a process start is not user activity. Any "has the user come back" check must read a timestamp written only on foreground (`RetentionPrefs.recordForeground`, from `MainActivity` resume/stop), never from `Application.onCreate`. Permission state on Android 13+ cannot be derived from `shouldShowRequestPermissionRationale` alone — use `classifyNotificationDialogResult` + `NotificationPermissionLedger`.

## Проблема / Контекст

The D0→D1 come-back push never displayed in prod:
- `lastActiveMs` was written on every process start, including the nudge alarm's own cold start, so the "already returned" gate skipped almost every fire and consumed the one-shot.
- POST_NOTIFICATIONS was asked only when setting a reminder, so the rest were skipped as `permission_off`.

## Решение

- `ComebackGate.decide` (pure, `composeApp/src/androidMain/.../retention/ComebackGate.kt`) returns Show / Reschedule / Skip. While the user is still active the nudge is rescheduled to last-foreground + delay, bounded by a max window; the one-shot is consumed only on a final Show or Skip.
- `RetentionPushScheduler.restoreAll` re-arms a pending nudge on app start, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- Activation bundle is unconditional (`activation_bundle_v1` deleted). `ActivationReminderSheet` opens after the first own checklist or first Inbox task (`ReminderTarget`, restorable via `ReminderTargetSaver`).
- `NotificationsOffBanner` on checklist detail and Inbox, driven by `NotificationsBannerController`; session/dismiss rules in `NotificationsBannerSession`.

## Почему именно так

- `shouldShowRequestPermissionRationale` is false both before the first ask and after a permanent denial, and a back/outside dismiss on Android 11+ also leaves it false. Permanent denial is recorded only when provable: rationale flipped true→false, or the OS answered without showing a dialog (activity never paused). Otherwise the CTA would flip to Settings after a mere dismiss.
- Gate logic is a pure function so the red/green tests run on the JVM (`ComebackGateTest`).

## Связанные файлы

- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/retention/ComebackGate.kt`
- `core/common/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/common/api/NotificationPermissionDecision.kt`
- `core/common/api/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/core/common/api/NotificationPermissionLedger.kt`
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/notifications/NotificationsBannerController.kt`
- Task log: `docs/archive/retention-reminder-optin-2026-10-14.md`
