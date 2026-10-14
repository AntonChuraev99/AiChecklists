---
title: "Push: FCM token retry on FIS_AUTH_ERROR, dismiss tracking for local pushes"
summary: "A failed FCM token fetch silently removed the user from server pushes; local pushes never reported a swipe. Bounded WorkManager retry, setDeleteIntent, notifications_enabled on push_received."
date: 2026-10-14
type: bug-fix
modules: [composeApp/androidMain, core/common/api]
keywords: [push, notification, FIS_AUTH_ERROR, FirebaseMessaging, WorkManager, PushTokenRetryWorker, push_dismissed, setDeleteIntent, android]
project: Checklists
---

# Push: FCM token retry on FIS_AUTH_ERROR, dismiss tracking for local pushes

**Суть:** a token fetch failing with `FIS_AUTH_ERROR` now enqueues `PushTokenRetryWorker`
(bounded exponential backoff) instead of a non-fatal per launch. Local retention pushes carry a
delete intent, so `push_dismissed` exists for them; `push_received` carries `notifications_enabled`.

## Проблема / Контекст

`FIS_AUTH_ERROR` (`IOException` from the Play services RPC, источник: Crashlytics stack trace) in `PushTokenRepositoryAndroid.touchLastActive` left
the device without a token, so the server never targeted it. The API key allows Firebase
Installations: the cause is device-side. Local pushes (`RetentionPushReceiver`) had
no delete intent; `push_received` fired with notifications off.

## Решение

- `PushTokenRetryWorker` + `PushTokenRetryPolicy`: unique work `push_token_retry`, `KEEP`,
  `NetworkType.CONNECTED`, exponential backoff, `MAX_ATTEMPTS` runs. `AppLogger.error` fires once, on
  exhaustion; the exhaustion time in `AppDatastore` holds new chains for `COOLDOWN_AFTER_EXHAUSTION_MS`
  (`PushTokenRetryWorker.enqueueIfDue`).
- Without a token, the foreground sends `UserApiService.markUserActive` (`fcm_token: null`).
- `lastActiveAt` moves only from `MainActivity.onResume` (`touchLastActive`, throttled by
  `PushActivityPolicy`); process start, `onNewToken` and the worker send `mark_active: false` — the
  promo's own delivery wakes the process.
- WorkManager is built only on the failure branch (`AppStartupWorkManagerTest`).
- `RetentionPushReceiver.showRetentionPush` sets a delete intent to the existing
  `PushDismissReceiver` with the same `push_type`; IDs and channels unchanged.
- `PushDisplayability` computes `notifications_enabled` (app toggle and channel importance) for
  `push_received` in both receivers.

## Почему именно так

`KEEP`, not `REPLACE`: an affected device fails on every launch, and `REPLACE` would reset the
backoff to attempt zero forever. The stored token is not resent — the server clears it on
`UNREGISTERED`, and a resend would revive a dead token.

## Связанные файлы

- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/push/PushTokenRetryWorker.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/push/PushTokenRepositoryAndroid.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/retention/RetentionPushReceiver.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/push/PushDisplayability.kt`
