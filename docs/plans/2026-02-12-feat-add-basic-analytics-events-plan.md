---
title: "feat: Add basic Firebase Analytics events"
type: feat
date: 2026-02-12
---

# feat: Add basic Firebase Analytics events

## Overview

Firebase Analytics dependency already exists in `composeApp/build.gradle.kts`, but **zero events are being logged**. This plan adds ~20 basic analytics events covering screen views and key user actions (Android only). The analytics `userId` will be linked to the same `deviceId` used by RevenueCat for cross-service user correlation.

## Problem Statement / Motivation

Without analytics we are blind to:
- Which screens users actually visit
- Where they drop off in the onboarding → first checklist funnel
- How often AI features are used and which input types are popular
- Paywall conversion rate and purchase funnel
- Feature adoption (sharing, templates)

Firebase Analytics is **already a dependency** — we just need to wire it up.

## Proposed Solution

Create a single `object Analytics` in `composeApp/src/androidMain/`. No interfaces, no factories, no Koin. Direct Firebase calls. ~30 lines of infrastructure code.

### Architecture

```
composeApp/src/androidMain/kotlin/.../Analytics.kt   # One file. That's it.
```

**Why no interface/factory/Koin?**
- Only 1 implementation (Android). iOS not in scope.
- We're never swapping Firebase (same as RevenueCat — no abstraction there either).
- An iOS stub = proof the abstraction is unnecessary.
- Direct `object` calls are simpler to read, write, and debug.

### User ID Binding (like RevenueCat)

In `SplashViewModel`, alongside the existing `linkWithPaywall(userId)` call:

```kotlin
// SplashViewModel.kt — existing flow
// FIRST: Set userId BEFORE navigation (prevents unattributed screen_views)
Analytics.setUserId(cached.userId)

// THEN: Navigate
navigateTo(cached.isOnboardingPassed)

// Background: RevenueCat linking (fire and forget)
appScope.launch { linkWithPaywall(...) }
```

**Critical**: `setUserId()` must be called BEFORE `navigateTo()` to avoid race condition where screen_view events fire without userId attribution.

## Event Schema (~20 events)

### Screen Views (9 screens)

| Event | screen_name | Triggered In |
|-------|-------------|--------------|
| `screen_view` | `main` | `MainScreen` |
| `screen_view` | `checklist_detail` | `ChecklistDetailScreen` |
| `screen_view` | `create_checklist` | `CreateChecklistScreen` |
| `screen_view` | `templates` | `TemplatesScreen` |
| `screen_view` | `analyze` | `AnalyzeScreen` |
| `screen_view` | `analyze_result` | `AnalyzeResultPreviewScreen` |
| `screen_view` | `paywall` | `PaywallScreen` |
| `screen_view` | `share` | `ShareChecklistScreen` |
| `screen_view` | `onboarding` | `OnboardingScreen` |

**Implementation**: `LaunchedEffect(Unit)` in each screen composable:

```kotlin
@Composable
fun MainScreen() {
    LaunchedEffect(Unit) { Analytics.screenView("main") }
    // ...
}
```

### Business Events (11 events)

| Event | Parameters | Where Logged |
|-------|------------|--------------|
| `onboarding_completed` | — | `OnboardingViewModel.onIntent(Complete)` |
| `checklist_created` | `source` (manual/template/ai), `item_count` | `CreateChecklistViewModel`, `AnalyzeResultPreviewViewModel` |
| `checklist_deleted` | `checklist_id` | `ChecklistDetailViewModel` |
| `ai_analyze_started` | `input_type` (photo/pdf/text/link/voice) | `AnalyzeViewModel.onIntent(OnAnalyzeClick)` |
| `ai_analyze_completed` | `input_type`, `item_count` | `AnalyzeViewModel` (on success) |
| `ai_analyze_failed` | `input_type`, `error_type` | `AnalyzeViewModel` (on failure) |
| `fill_created` | `checklist_id`, `source` (ai/manual) | `AnalyzeResultPreviewViewModel` |
| `paywall_shown` | `trigger` (limit/manual) | `PaywallViewModel.init` |
| `purchase_completed` | `product_id` | `PaywallViewModel` (on success) |
| `purchase_failed` | `error_type` | `PaywallViewModel` (on error) |
| `share_checklist` | `format` (text/pdf) | `ShareViewModel` |

### Error Types

Use raw exception class names — no premature enum categorization:

```kotlin
// In AnalyzeViewModel catch block:
Analytics.event("ai_analyze_failed", mapOf(
    "input_type" to inputType,
    "error_type" to e.javaClass.simpleName  // "IOException", "HttpException", etc.
))
```

Can categorize in Firebase Console dashboards later if needed.

## Technical Considerations

### Debug Build Isolation

```kotlin
object Analytics {
    private val firebase by lazy {
        FirebaseAnalytics.getInstance(AppContextHolder.context).apply {
            setAnalyticsCollectionEnabled(!AppBuildConfig.isDebug)
        }
    }
}
```

Debug builds won't pollute production data. For local debugging, use Firebase DebugView:
```bash
adb shell setprop debug.firebase.analytics.app com.antonchuraev.aichecklists
```

### No PII in Events

Never log: checklist names, fill content, file paths, URLs. Only log: IDs, counts, types, error codes.

### Performance

Firebase Analytics batches events automatically (~1 hour or app backgrounding). No performance impact.

## Acceptance Criteria

- [ ] `Analytics` object created in `composeApp/src/androidMain/`
- [ ] `setUserId(deviceId)` called in `SplashViewModel` BEFORE navigation (same deviceId as RevenueCat)
- [ ] 9 screen_view events logged via `LaunchedEffect(Unit)`
- [ ] 11 business events logged in ViewModels
- [ ] Analytics collection disabled in debug builds
- [ ] No PII in any event parameters
- [ ] Events verified via Firebase DebugView

## Implementation

### Step 1: Create `Analytics.kt` (~5 min)

**File:** `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/Analytics.kt`

```kotlin
package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

object Analytics {

    private val firebase by lazy {
        FirebaseAnalytics.getInstance(AppContextHolder.context).apply {
            setAnalyticsCollectionEnabled(!AppBuildConfig.isDebug)
        }
    }

    fun setUserId(userId: String) {
        firebase.setUserId(userId)
    }

    fun screenView(name: String) {
        firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, name)
        }
    }

    fun event(name: String, params: Map<String, Any> = emptyMap()) {
        firebase.logEvent(name) {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Double -> param(key, value)
                }
            }
        }
    }
}
```

### Step 2: Set userId in SplashViewModel (~5 min)

**File:** `feature/splash/src/commonMain/.../SplashViewModel.kt`

```kotlin
init {
    startBackgroundSync()
    viewModelScope.launch {
        val cached = userDataRepository.getUserData()

        if (cached.userId.isNotBlank()) {
            // Set userId BEFORE navigation to avoid unattributed events
            Analytics.setUserId(cached.userId)
            navigateTo(cached.isOnboardingPassed)
        } else {
            val result = userDataRepository.ensureUserRegistered()
            val userData = result.getOrNull()?.userData ?: cached

            result.onSuccess { data ->
                Analytics.setUserId(data.userData.userId)
                appScope.launch { linkWithPaywall(data.userData.userId, isNewUser = data.isNewUser) }
            }

            navigateTo(userData.isOnboardingPassed)
        }
    }
}

private fun startBackgroundSync() {
    appScope.launch {
        val cached = userDataRepository.getUserData()
        if (cached.userId.isBlank()) return@launch

        // Ensure userId always set (covers app restart without full splash flow)
        Analytics.setUserId(cached.userId)

        launch { runCatching { userDataRepository.syncWithServer() } }
        launch { runCatching { linkWithPaywall(cached.userId, isNewUser = false) } }
    }
}
```

**Note**: `Analytics` is an Android object — calling it from `commonMain` requires `expect/actual` or conditional compilation. Since `SplashViewModel` is in `commonMain`, we have two options:

**Option A** (simplest): Move the `Analytics.setUserId()` call to the Android-specific `GistiApplication` or a platform-bridged init.

**Option B** (pragmatic): Create a minimal expect/actual just for the ViewModel calls:

```kotlin
// commonMain
expect fun trackAnalyticsUserId(userId: String)
expect fun trackScreenView(name: String)
expect fun trackEvent(name: String, params: Map<String, Any> = emptyMap())

// androidMain
actual fun trackAnalyticsUserId(userId: String) = Analytics.setUserId(userId)
actual fun trackScreenView(name: String) = Analytics.screenView(name)
actual fun trackEvent(name: String, params: Map<String, Any>) = Analytics.event(name, params)

// iosMain
actual fun trackAnalyticsUserId(userId: String) = Unit
actual fun trackScreenView(name: String) = Unit
actual fun trackEvent(name: String, params: Map<String, Any>) = Unit
```

### Step 3: Screen View Events (~15 min)

Add `LaunchedEffect(Unit)` to 9 screens.

**Files to modify:**

| File | screenName |
|------|-----------|
| `feature/home/src/.../MainScreen.kt` | `"main"` |
| `feature/home/src/.../detail/ChecklistDetailScreen.kt` | `"checklist_detail"` |
| `feature/create/src/.../create/CreateChecklistScreen.kt` | `"create_checklist"` |
| `feature/create/src/.../templates/TemplatesScreen.kt` | `"templates"` |
| `feature/analyze/src/.../AnalyzeScreen.kt` | `"analyze"` |
| `feature/analyze/src/.../preview/AnalyzeResultPreviewScreen.kt` | `"analyze_result"` |
| `feature/paywall/src/.../PaywallScreen.kt` | `"paywall"` |
| `feature/sharing/src/.../ShareScreen.kt` | `"share"` |
| `feature/onboarding/src/.../OnboardingScreen.kt` | `"onboarding"` |

Each screen gets:

```kotlin
LaunchedEffect(Unit) { trackScreenView("main") }
```

### Step 4: Business Events (~20 min)

Log events directly in ViewModels via top-level `trackEvent()` function.

**Files to modify:**

| ViewModel | Events |
|-----------|--------|
| `OnboardingViewModel` | `trackEvent("onboarding_completed")` |
| `CreateChecklistViewModel` | `trackEvent("checklist_created", mapOf("source" to "manual", "item_count" to count))` |
| `AnalyzeViewModel` | `trackEvent("ai_analyze_started", ...)`, `trackEvent("ai_analyze_completed", ...)`, `trackEvent("ai_analyze_failed", ...)` |
| `AnalyzeResultPreviewViewModel` | `trackEvent("checklist_created", mapOf("source" to "ai", ...))`, `trackEvent("fill_created", ...)` |
| `ChecklistDetailViewModel` | `trackEvent("checklist_deleted", mapOf("checklist_id" to id))` |
| `PaywallViewModel` | `trackEvent("paywall_shown", ...)`, `trackEvent("purchase_completed", ...)`, `trackEvent("purchase_failed", ...)` |
| `ShareViewModel` | `trackEvent("share_checklist", mapOf("format" to format))` |

No DI changes needed — just call top-level functions directly.

## Dependencies & Risks

| Risk | Mitigation |
|------|-----------|
| `setUserId` called after first `screen_view` | Fixed: userId set BEFORE `navigateTo()` |
| RevenueCat auto-sends purchase events → duplication | Monitor in Firebase Console; disable RC integration if needed |
| Firebase event name limit (40 chars, 500 event types) | Our ~20 events are well within limits |
| `firebase-analytics` dependency version | Already managed by Firebase BOM 33.7.0 |
| `Analytics` object used from commonMain | Solved via expect/actual top-level functions |

## Success Metrics

After deployment, verify in Firebase Console:
- [ ] Active users appear with device IDs (same as RevenueCat)
- [ ] Screen flow report shows realistic navigation paths
- [ ] Funnel: onboarding → main → first checklist created
- [ ] Funnel: paywall_shown → purchase_completed conversion rate
- [ ] AI feature usage by input_type distribution

## References

### Internal

- RevenueCat init: `feature/paywall/src/.../RevenueCatInitializer.kt`
- Device ID: `feature/user/src/.../DeviceIdProvider.kt`
- SplashViewModel (userId binding): `feature/splash/src/.../SplashViewModel.kt:76-98`
- GistiApplication: `composeApp/src/androidMain/.../GistiApplication.kt`

### External

- [Firebase Analytics KTX API](https://firebase.google.com/docs/analytics/get-started?platform=android)
- [Firebase event parameters limits](https://support.google.com/firebase/answer/9237506)
- [Firebase DebugView](https://firebase.google.com/docs/analytics/debugview)
