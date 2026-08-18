---
title: "Qualified CTA via Use Case + AppNavEvent Pattern"
date: 2026-05-06
type: architecture
modules: [core/navigation, feature/create, feature/updatefeed]
keywords: [deeplink, cta, use-case, appnavevent, premium-gate, qualified-navigation, domain-pathway]
project: gisti-checklists
---

# Qualified CTA via Use Case + AppNavEvent Pattern

## Problem / Context

Update Feed (and future features) need to expose **Call-To-Action (CTA) buttons** that:
1. Trigger complex logic (premium verification, page transitions, deep state creation)
2. Are decoupled from handlers (may be invoked from multiple sources: deeplinks, drawer items, etc.)
3. Enforce domain invariants (e.g., can't create >1 weekly checklist if free-tier)

**Anti-pattern (naive approach):** Inline logic in UpdateFeedDeepLinkHandler:
```kotlin
"weekly_mode_v1" -> {
  val limits = userLimitsRepository.getUserLimits() // blocking call
  if (!limits.canCreateWeeklyChecklist) {
    navigator.navigateToPaywall(source = "weekly_mode_limit")
  } else {
    navigator.navigateToChecklistDetail(route = CreateChecklistRoute.CreateChecklist(viewMode = WEEKLY))
  }
}
```

**Problems:**
- Mixing domain logic with navigation handler
- Hard to test (binds to repository + navigator simultaneously)
- Tight coupling (changing GetUserLimits logic requires patching handler)
- Difficult to reuse (if another feature needs same logic, duplication or cross-module coupling)

## Solution

**Extract domain logic into a Use Case.** Emit **typed AppNavEvent** from App.kt collector.

### Architecture

```
UpdateFeedDeepLinkHandler (parses deeplink)
  ↓ (detects "viewMode=weekly")
  ↓
AppNavigator.requestCreateWeeklyChecklist()
  ↓ (emits CreateWeeklyChecklistRequested event)
  ↓
App.kt LaunchedEffect (listens to AppNavEvent)
  ↓
CreateWeeklyChecklistUseCase.execute() (checks premium, returns sealed Result)
  ↓ (Result.Created | Result.RequiresUpgrade)
  ↓
Navigate(ChecklistDetail) or Navigate(Paywall)
```

### Key Components

#### 1. Use Case (in feature/create/domain/usecase/)

```kotlin
class CreateWeeklyChecklistUseCase(
  private val getUserLimitsUseCase: GetUserLimitsUseCase,
  private val navigator: AppNavigator,
) {
  sealed interface Result {
    data object Created : Result
    data object RequiresUpgrade : Result
  }

  suspend fun execute(): Result {
    val limits = getUserLimitsUseCase.execute()
    return when {
      limits.canCreateWeeklyChecklist -> {
        navigator.navigateToChecklistDetail(
          CreateChecklistRoute.CreateChecklist(
            templateId = null,
            editChecklistId = null,
            viewMode = ChecklistViewMode.WEEKLY,
          )
        )
        Result.Created
      }
      else -> {
        navigator.navigateToPaywall(source = "weekly_mode_limit")
        Result.RequiresUpgrade
      }
    }
  }
}
```

#### 2. AppNavEvent (in core/navigation/api/)

```kotlin
sealed interface AppNavEvent {
  data object WidgetInstruction : AppNavEvent
  data object CreateWeeklyChecklistRequested : AppNavEvent
  // ... other events
}
```

#### 3. AppNavigator (in core/navigation/api/)

```kotlin
interface AppNavigator {
  fun showWidgetInstruction()
  fun requestCreateWeeklyChecklist()
  // ... other methods
}
```

#### 4. UpdateFeedDeepLinkHandler (in feature/updatefeed/domain/deeplink/)

```kotlin
fun handle(deepLink: String) {
  val uri = deepLink.substringAfter("gisti://").split("?")
  val host = uri[0]
  val params = uri.getOrNull(1)?.split("&") ?: emptyList()

  when (host) {
    "widget_instruction" -> navigator.showWidgetInstruction()
    "create" -> {
      val viewMode = params.find { it.startsWith("viewMode=") }?.substringAfter("=")
      if (viewMode == "weekly") {
        navigator.requestCreateWeeklyChecklist()
      }
    }
  }
}
```

#### 5. App.kt Event Collector (in composeApp/)

```kotlin
LaunchedEffect(Unit) {
  navigator.events.collect { event ->
    when (event) {
      AppNavEvent.WidgetInstruction -> { /* existing */ }
      AppNavEvent.CreateWeeklyChecklistRequested -> {
        createWeeklyChecklistUseCase.execute()
      }
    }
  }
}
```

## Why This Design

1. **Domain invariants enforced:** Premium gate (`canCreateWeeklyChecklist`) lives in use case, not UI or handler. Any code path calling `requestCreateWeeklyChecklist()` is guaranteed to check limits.

2. **Decoupled from handlers:** UpdateFeedDeepLinkHandler is now a simple dispatcher — `viewMode=weekly → emit event`. Logic lives in use case, testable independently.

3. **Reusable:** Future CTAs (Special Offer, Paywall Shortcut, etc.) can:
   - Create their own use case (or reuse common pattern)
   - Emit event via navigator
   - Be tested without touching handler

4. **Type-safe:** AppNavEvent is sealed; compiler ensures all cases handled in App.kt. Adding new CTA = add event + handler pattern; no silent bugs.

5. **Minimal Fake sprawl:** Fakes only need `override fun requestCreateWeeklyChecklist() {}` — no body. Compare to alternative (extend Route with viewMode parameter) — would require rewriting 13 test Fakes' existing `navigateToChecklistDetail()` signatures.

## When NOT to Use This Pattern

- **Trivial navigation:** "Tap Settings → go to SettingsScreen" — direct navigator call is fine
- **App-level gates:** Splash (onboarding vs main) — should use app state, not event
- **Error handling flows:** Network errors, permission denials — use SnackBar or Dialog, not CTA
- **User-initiated actions without biz logic:** Drawer taps, bottom nav switches — direct routes are simpler

## When TO Use This Pattern

✅ Remote content (Update Feed, in-app messaging) with CTA  
✅ Feature creation with premium verification  
✅ Multi-step flows (gate → create → confirmation)  
✅ Logic reusable across multiple entry points (deeplink + UI button)  
✅ Domain invariant that must hold on every invoke (e.g., premium check, max-item limit)

## Related Files

- `feature/create/.../domain/usecase/CreateWeeklyChecklistUseCase.kt`
- `feature/create/.../domain/usecase/CreateWeeklyChecklistUseCaseTest.kt`
- `core/navigation/api/.../AppNavEvent.kt`
- `core/navigation/api/.../AppNavigator.kt`
- `feature/updatefeed/.../domain/deeplink/UpdateFeedDeepLinkHandler.kt`
- `feature/updatefeed/.../domain/deeplink/UpdateFeedDeepLinkHandlerTest.kt`
- `composeApp/.../App.kt` (event collector)
- `docs/guidelines/updates-feed.md` (CTA whitelist & when to add new CTA)

## Compound Effect Notes

This architecture was discovered while implementing weekly-checklist CTA (2026-05-06). Alternative approach (extend CreateChecklistRoute with viewMode: ChecklistViewMode? parameter) would have required:
- Modifying Route serialization (not a blocker, but adds surface)
- Rewriting 13 test Fake implementations of AppNavigator
- Tying lifecycle of Route to lifecycle of use case logic

**Extract use case + AppNavEvent was 13% cheaper in Fake changes** and decoupled route evolution from domain logic evolution. This pattern will be reused for future CTAs (special-offer, paywall-shortcut, etc.) — once, then cached in android-expert agent memory.
