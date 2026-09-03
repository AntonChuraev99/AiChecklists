---
title: "Android: Offload push-permission telemetry from cold-start main thread"
date: 2026-07-22
type: perf
modules: [androidMain, core/common/api]
keywords: [cold-start, main-thread, anr, telemetry, push-notifications, application-oncreate]
project: Gisti
---

# Cold-Start ANR Mitigation: emitPushPermissionState Offload

## Проблема / Контекст

`GistiApplication.onCreate()` calls `emitPushPermissionState()` synchronously on the main thread. This function:
- Reads `NotificationManagerCompat.areNotificationsEnabled()`
- Queries `NotificationManager.getNotificationChannel()` (Android 8.0+)
- Reads/writes SharedPreferences (drift-detection)
- Emits analytics event + user properties

On a cold start with I/O contention or slow disk, this pure-telemetry operation blocks the main thread for 50-200ms. Coupled with Firebase SDK init, WorkManager, Koin DI, and ReminderReceiver, the cumulative cold-start main thread is often > ANR threshold (5s on user-visible app, 10s on bg).

**Impact:** Rare but device-blocking crash on first-launch; users uninstall without seeing any content.

## Решение

Wrapped `emitPushPermissionState()` in `applicationScope.launch(Dispatchers.IO)`:

```kotlin
private fun emitPushPermissionState() {
    applicationScope.launch {  // Non-blocking, IO dispatcher
        val tracker: AnalyticsTracker = GlobalContext.getOrNull()?.getOrNull() ?: return@launch
        val notificationsEnabled = 
            NotificationManagerCompat.from(this@GistiApplication).areNotificationsEnabled()
        val promoImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(PushNotificationChannels.PROMOTIONS_CHANNEL_ID)
                ?.importance ?: 0
        } else {
            0
        }
        
        // Current state as user-properties + emit event only on drift
        tracker.setUserProperties(mapOf(...))
        
        val prefs = getSharedPreferences("push_perm_state", Context.MODE_PRIVATE)
        if (hasStateChanged(prefs)) {
            prefs.edit().putBoolean(...).apply()
            tracker.event(AnalyticsEvents.Push.PERMISSION_STATE, mapOf(...))
        }
    }
}
```

### Timing guarantee

- **onCreate() returns immediately** (before showFacts).
- **Notification channels created synchronously** (above the launch call) ⇒ `getNotificationChannel()` inside the coroutine will resolve correctly (happens-before).
- **Telemetry sampling async** but fire-and-forget (user doesn't wait for it).

## Почему именно так

### ✅ Offloaded (IO thread)
- Pure telemetry with no user-facing dependency
- Expensive operations (SharedPreferences I/O, analytics network) don't block cold start
- Dispatcher.IO ensures no thread starvation (pool of threads, not contention with main)

### ⚠️ Did NOT offload: RevenueCat.configure()
Left `RevenueCat.configure()` on main thread despite identical I/O profile. Reason: **returning premium user auto-restore race condition**. 

SplashViewModel later calls:
```kotlin
val isConfigured = RevenueCat.isConfigured() // single-shot, no await
if (!isConfigured) {
    // Fallback: attempt manual link
}
```

If we background RevenueCat.configure(), the single-shot `isConfigured()` may return false even though configure is in-flight. Result: returning-premium users skip auto-restore and see the paywall. **Monetization risk > ANR mitigation** (ANR is rare; monetization loss is guaranteed on this race).

**Deferred:** Future task to fix RevenueCat via `awaitPurchasesReady()` or similar, enabling safe backgrounding of configure.

## Примеры

**Before (main-thread block):**
```
onCreate() {
    // ...
    emitPushPermissionState() ← blocks main for 50-200ms
    // ...
    startActivity(...) ← delayed
}
// ANR threshold: 50ms + 200ms (telemetry) + 50ms (other init) + GMS + Firebase + Koin = 5+ seconds
```

**After (async telemetry):**
```
onCreate() {
    // ...
    emitPushPermissionState() ← launch { dispatch IO } returns immediately
    // ...
    startActivity(...) ← runs instantly (telemetry sampling in background)
}
// ANR threshold: 50ms (other init) + GMS + Firebase + Koin = <2 seconds
```

## Связанные файлы

- `composeApp/src/androidMain/kotlin/.../GistiApplication.kt` — emitPushPermissionState() wrapped in `applicationScope.launch(Dispatchers.IO)`
- Commits: 040d85fd (perf: sample push perm off main thread)

## Verification

Post-vc72 deployment:
- Crashlytics: ANR events on cold start should not increase
- GistiApplication.onCreate() should return to first activity within <2s (observable via logcat timestamps)
- `push_notifications_enabled` user-property should still populate within first ~5s (sample completes by then on typical device)

## Lessons Learned (для агентов)

- **Telemetry is not free:** Pure sampling can block cold-start if unthrottled. Profile onCreate() on slow devices (Android Go, API 24) before assuming synchronous init is safe.
- **Happens-before applies across coroutine boundaries:** If you call async(IO) AFTER synchronous setup (channels), the async code correctly sees the setup. No manual synchronization needed.
- **Single-shot checks without await are race conditions:** `isConfigured()` without `await` is not safe if the operation is async. Mark these as bugs in code-review (pattern: "check without await").
- **Monetization logic lives in async-hostile zone:** Avoid backgrounding RevenueCat / Google Play Billing / payment-related init; the gaps between "did I call configure?" and "am I configured?" are money leaks.
