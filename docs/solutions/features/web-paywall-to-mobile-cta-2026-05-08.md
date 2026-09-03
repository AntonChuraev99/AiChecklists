---
title: "wasmJs Paywall → Mobile App CTA Replacement"
date: 2026-05-08
type: feature
modules: [feature/paywall, core/designsystem]
keywords: [wasmjs, paywall, mobile-cta, google-play, expect-actual, kmp, source-sets]
project: Checklists
---

# wasmJs Paywall → Mobile App CTA Replacement

## Проблема / Контекст

On the web target (wasmJs), users who hit the paywall screen see a subscription offer designed for mobile in-app purchases (RevenueCat). This is not applicable to web: there's no RevenueCat integration, and users should instead be directed to install the mobile app where premium is available.

**Business motivation:** Web is a discovery and onboarding channel; conversion to premium happens on Android (Google Play) and iOS (App Store). Paywall screen on web should funnel users toward app installation, not confuse them with subscription UX.

## Решение

Implemented three architectural changes:

### 1. Narrow expect/actual flag (PaywallPlatform.kt)

Instead of expect/actual for entire composables (heavy, hard to test, breaks composability), introduced a narrow boolean:

```kotlin
// commonMain
internal expect val isWebPaywallTarget: Boolean

// mobileMain (covers android + ios via intermediate sourceSet)
internal actual val isWebPaywallTarget: Boolean = false

// wasmJsMain
internal actual val isWebPaywallTarget: Boolean = true
```

**Why this approach:**
- Single shared point of decision; easy to read and maintain
- No Compose expect/actual (those are brittle and compose-scope-breaking)
- Intermediate `mobileMain` sourceSet allows 1 expect + 2 actual instead of 1 expect + 3 actual
- Web-specific screens (WebInstallAppScreen) remain entirely in commonMain composable code — testable on any target

### 2. Early-return pattern in PaywallRoute

Moved `koinViewModel()` call **after** early-return check:

```kotlin
@Composable
fun PaywallRoute(
    source: String,
    navigateBack: () -> Unit,
    // Removed: viewModel: PaywallViewModel = koinViewModel()
) {
    if (isWebPaywallTarget) {
        WebInstallAppScreen(navigateBack = navigateBack)
        return
    }

    // Only on mobile targets — viewModel and RevenueCat never initialized on web
    val viewModel: PaywallViewModel = koinViewModel()
    PaywallScreen(viewModel, source, navigateBack)
}
```

**Critical detail:** Default parameter expressions (`= koinViewModel()`) are evaluated at function entry, before any early-return. Placing the DI call after the return prevents unnecessary Koin scope creation and WebPaywallRepositoryStub wake-up on the web target.

**Trade-off:** Removed default parameter from signature (breaking change for callsites, but all callers in `App.kt` passed no args anyway, so fixing callsite is safe).

### 3. WebInstallAppScreen composable

New commonMain composable with two sections:

```kotlin
internal const val GISTI_GOOGLE_PLAY_URL = 
    "https://play.google.com/store/apps/details?id=com.antonchuraev.aichecklists"

@Composable
fun WebInstallAppScreen(
    navigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Install Gisti on Mobile", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        
        // Android section: clickable card with button
        AppCard(modifier = Modifier.widthIn(max = 480.dp)) {
            Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Android, ...)
                    Text("Android", style = MaterialTheme.typography.labelLarge)
                }
                Text("Download on Google Play")
                AppButton(
                    text = "Get App",
                    onClick = { ... navigate to GISTI_GOOGLE_PLAY_URL ... }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        
        // iOS section: locked card (no CTA)
        AppCard(modifier = Modifier.widthIn(max = 480.dp)) {
            Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, ...)  // Apple icon unavailable; Schedule = Coming Soon
                    Text("iOS", style = MaterialTheme.typography.labelLarge)
                }
                Text("Coming soon...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

**Icon trap caught:** `Icons.Filled.Apple` is not available in material-icons-extended (trademark/brand restriction). Substituted with `Icons.Filled.Schedule` (clock), which is semantically appropriate for "Coming soon".

## Почему именно так

### expect/actual boolean vs. expect/actual composables

**Pattern comparison:**

| Approach | Cost | Testability | Reuse |
|---|---|---|---|
| **Expect/actual Boolean (chosen)** | Minimal (1 line × 3 files) | High (branch tested in commonMain) | High (can use flag anywhere) |
| **Expect/actual Composable** | ~100 lines per target | Low (not testable cross-target) | Low (locked to usage site) |

Expect/actual composables break composability — e.g., if another screen needs the same platform check, you duplicate or create a whole new expect/actual pair. Boolean flag inverts cost: cheap to add, composes naturally.

### Intermediate `mobileMain` sourceSet

Gisti already defines `mobileMain` to share code between Android and iOS (both use RevenueCat). This extends that pattern naturally:

```
sourceSet hierarchy:
  commonMain
    ├── androidMain
    ├── iosMain
    └── wasmJsMain
  
  intermediate (shares androidMain + iosMain):
    └── mobileMain
         ├── actual PaywallPlatform = false
         ├── RevenueCat-only Koin modules
         └── Audio/video players (Android/iOS only)
```

Avoids the 1-expect-3-actual explosion (would be required if wasmJs was at same level as android/ios without mobileMain bridge).

### Early-return after isWeb check

**Why NOT put composable call in expect/actual:**

Before: Temptation to do `expect fun PaywallContent()` with `actual PaywallScreen()` and `actual WebInstallAppScreen()`.

Cost:
- Can't call one from the other (composition boundary)
- Koin/ViewModel scopes don't compose
- Harder to refactor (UI unification later) because code is scattered

After: Single `PaywallRoute` composable decides **which screen** based on narrowly-scoped flag. Both screens are commonMain composables.

Benefit:
- Route layer can orchestrate common logic (analytics, nav) before branching
- Both screens are testable independently in commonMain
- Future refactoring (e.g., shared header, migration) stays in one place

## Примеры

### Before (web user experience)
```
Paywall Screen
├── [RevenueCat plans table]
├── [3-Day Free Trial banner]
└── [Start Free Trial button] ← doesn't work on web
```

### After (web user experience)
```
Web Install App Screen
├── Android section
│   ├── Android icon
│   ├── "Download on Google Play"
│   └── [Get App button] → https://play.google.com/store/...
└── iOS section
    ├── Schedule icon (Coming Soon)
    └── "We are cooking it. Stay tuned." (no button)
```

### Code paths by target

| Target | Flow |
|---|---|
| **Android** | App.kt → PaywallRoute → isWebPaywallTarget=false → PaywallScreen (RevenueCat) |
| **iOS** | App.kt → PaywallRoute → isWebPaywallTarget=false → PaywallScreen (RevenueCat) |
| **wasmJs** | App.kt → PaywallRoute → isWebPaywallTarget=true → WebInstallAppScreen (Google Play link) |

## Связанные файлы

- `feature/paywall/src/commonMain/kotlin/.../presentation/PaywallRoute.kt` — Early-return routing
- `feature/paywall/src/commonMain/kotlin/.../presentation/WebInstallAppScreen.kt` — New web-specific composable
- `feature/paywall/src/commonMain/kotlin/.../presentation/PaywallPlatform.kt` — Narrow boolean expect
- `feature/paywall/src/mobileMain/.../presentation/PaywallPlatform.mobile.kt` — Mobile actual
- `feature/paywall/src/wasmJsMain/.../presentation/PaywallPlatform.wasmJs.kt` — Web actual
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` — 8 new keys (paywall_web_install_*)

## Key Learnings for Future wasmJs Platform-Specific UX

1. **Narrow boolean flag > expect/actual composables** for platform checks
2. **Use intermediate sourceSet** (e.g., `mobileMain`) when 2+ targets share code, 1 target diverges
3. **DI (Koin) is evaluated at composition entry** — move `koinViewModel()` after early-return to prevent unneeded scope creation
4. **Icons.Filled.Apple unavailable** — use `Schedule`, `Watch`, or `Clock` for "Coming Soon"; check `material-icons-extended` whitelist before using brand icons
5. **MaxContentWidth for responsive** — desktop-friendly cards via `widthIn(max = 480.dp)` instead of `fillMaxWidth()`
