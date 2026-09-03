# Debug Menu Accessible in Release Builds

---
category: security
severity: high
tags: [debug, release-build, kmp, expect-actual, security]
date_solved: 2025-01-25
affected_files:
  - composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/AppBuildConfig.kt
  - composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/AppBuildConfig.android.kt
  - composeApp/src/iosMain/kotlin/com/antonchuraev/homesearchchecklist/AppBuildConfig.ios.kt
  - composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/MainActivity.kt
  - composeApp/src/iosMain/kotlin/com/antonchuraev/homesearchchecklist/MainViewController.kt
  - composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt
  - composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/GistiApplication.kt
---

## Problem

Debug menu (DebugScreen and StoreScreenshotScreen) was accessible in release APK builds. This posed a security risk as debug functionality could be accessed by end users.

### How Debug Menu Was Triggered

| Platform | Trigger Method |
|----------|----------------|
| Android | Volume key combo (Up-Down-Up within 500ms) |
| iOS | `navigateToDebugMenu()` function callable from Swift |

### Root Cause

No build type check existed in the codebase:

1. **DebugMenuDetector** was always instantiated and active in `MainActivity`
2. **Debug navigation routes** were always registered in `App.kt`
3. **navigateToDebugMenu()** function in iOS was always callable with no guard

## Solution

Created `AppBuildConfig` using the KMP expect/actual pattern to provide build-type detection across platforms.

### 1. Common Interface (expect)

```kotlin
// composeApp/src/commonMain/.../AppBuildConfig.kt
expect object AppBuildConfig {
    /**
     * Returns true if this is a debug build.
     * Used to conditionally enable debug features like debug menu.
     */
    val isDebug: Boolean
}
```

### 2. Android Implementation (actual)

```kotlin
// composeApp/src/androidMain/.../AppBuildConfig.android.kt
import com.antonchuraev.aichecklists.BuildConfig

actual object AppBuildConfig {
    actual val isDebug: Boolean = BuildConfig.DEBUG
}
```

Uses Android's generated `BuildConfig.DEBUG` which is `true` for debug builds and `false` for release.

### 3. iOS Implementation (actual)

```kotlin
// composeApp/src/iosMain/.../AppBuildConfig.ios.kt
import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

actual object AppBuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = run {
        val infoDictionary = NSBundle.mainBundle.infoDictionary
        val configuration = infoDictionary?.get("Configuration") as? String
        configuration?.contains("Debug", ignoreCase = true) == true
                || kotlin.native.Platform.isDebugBinary
    }
}
```

Checks both:
- Xcode build configuration name from Info.plist
- Kotlin/Native's `Platform.isDebugBinary` flag

### 4. Conditional Debug Features

#### MainActivity.kt (Android)

```kotlin
private val debugMenuDetector = if (AppBuildConfig.isDebug) {
    DebugMenuDetector { appNavigator.navigateToDebugMenu() }
} else {
    null
}

override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (debugMenuDetector?.onKeyDown(keyCode) == true) {
        return true
    }
    return super.onKeyDown(keyCode, event)
}
```

#### MainViewController.kt (iOS)

```kotlin
fun navigateToDebugMenu() {
    if (!AppBuildConfig.isDebug) {
        println("Debug menu is only available in debug builds")
        return
    }
    // ... navigation logic
}
```

#### App.kt (Navigation Routes)

```kotlin
NavHost(...) {
    // ... other routes

    // Debug screens - only available in debug builds
    if (AppBuildConfig.isDebug) {
        composable<AppNavRoute.Debug> {
            DebugScreen()
        }

        composable<AppNavRoute.StoreScreenshot> {
            StoreScreenshotScreen()
        }
    }

    // ... other routes
}
```

## Verification

### Android

1. Build release APK: `./gradlew composeApp:assembleRelease`
2. Install on device
3. Try volume key combo - should not trigger debug menu
4. Navigation to Debug route should fail (route not registered)

### iOS

1. Build with Release configuration in Xcode
2. Call `navigateToDebugMenu()` from Swift - should print warning and return
3. Navigation to Debug route should fail (route not registered)

## Key Takeaways

1. **Always guard debug features** with build-type checks in production apps
2. **Use expect/actual pattern** for platform-specific build configuration in KMP
3. **Defense in depth**: Guard at multiple levels:
   - Trigger mechanism (detector initialization)
   - Entry point (navigation function)
   - Route registration (NavHost)
4. **Android's BuildConfig.DEBUG** is the canonical way to check debug vs release
5. **iOS uses multiple signals**: Configuration name and `Platform.isDebugBinary`

## Related Files

| File | Purpose |
|------|---------|
| `AppBuildConfig.kt` | Common expect declaration |
| `AppBuildConfig.android.kt` | Android actual using BuildConfig.DEBUG |
| `AppBuildConfig.ios.kt` | iOS actual using NSBundle + Platform.isDebugBinary |
| `MainActivity.kt` | Conditional DebugMenuDetector instantiation |
| `MainViewController.kt` | Guard on navigateToDebugMenu() |
| `App.kt` | Conditional debug route registration |
| `GistiApplication.kt` | Uses isDebug for analytics configuration |
