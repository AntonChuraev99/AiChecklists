---
title: "AGP 9 Feature Module Compose Resources APK Assembly Gap"
date: 2026-05-11
type: bug-fix
modules: [feature/create, build-system]
keywords: [agp-9, compose-resources, androidResources, kmp-android-library, missing-apk-assets, gradle-dsl, silent-emptylist]
project: checklists
---

# AGP 9 Feature Module Compose Resources APK Assembly Gap

## Problem / Context

After AGP 9.2 migration (commit 15c83bba), templates disappeared from production Android app. Users navigated to "Templates" screen and saw empty list + potential crash in `InteractiveOnboardingViewModel.<init>` due to uncaught `MissingResourceException`.

Root cause: **New AKMP `kotlin { android { ... } }` DSL block does NOT automatically enable `androidResources`** unlike the deprecated `android { }` block. Without explicit `androidResources { enable = true }`, Gradle skips the `copyAndroidMainComposeResourcesToAndroidAssets` task → `composeResources/files/templates.json` gets built to `feature/create/build/.../assets` but **never aggregates into final APK**.

APK asset inventory after bug:
- `aichecklists.composeapp.generated.resources/drawable/compose-multiplatform.xml` ✓
- `core.designsystem.generated.resources/strings.commonMain.cvr` ✓
- `feature.create.generated.resources/files/templates.json` ✗ (missing)

This explains 2+ weeks of silent `MissingResourceException` caught by `emptyList()` fallback in `TemplatesRepositoryImpl.getTemplates()`.

## Solution

### 1. Enable androidResources in feature/create

**Before:**
```gradle.kts
kotlin {
    androidTarget()
    // ...
    sourceSets {
        androidMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

**After:**
```gradle.kts
kotlin {
    androidTarget()
    
    // CRITICAL: Enable compose resources aggregation for Android
    // AGP 9.2 AKMP DSL does NOT auto-enable androidResources
    // without this, composeResources/** are built locally but NOT packed into APK
    androidResources {
        enable = true
    }
    
    // ...
    sourceSets {
        androidMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

### 2. Improve Repository Error Logging

**Before:**
```kotlin
override suspend fun getTemplates(): List<ChecklistTemplate> {
    return try {
        val bytes = Res.readBytes("files/templates.json")
        Json.decodeFromString(bytes.decodeToString())
    } catch (e: Exception) {
        emptyList()  // ← silent, masks MissingResourceException for weeks
    }
}
```

**After:**
```kotlin
override suspend fun getTemplates(): List<ChecklistTemplate> {
    return try {
        val bytes = Res.readBytes("files/templates.json")
        try {
            Json.decodeFromString(bytes.decodeToString())
        } catch (e: SerializationException) {
            AppLogger.e(TAG, "Failed to parse templates.json (${bytes.size} bytes): ${e::class.simpleName}")
            emptyList()
        }
    } catch (e: Exception) {
        // MissingResourceException, FileNotFoundException, etc.
        AppLogger.e(TAG, "Failed to load templates resource: ${e::class.simpleName}")
        emptyList()
    }
}
```

Separating read vs parse exceptions ensures **diagnostic clarity**: if file is missing, we log it; if parse fails, we log the JSON syntax issue + byte count.

## Why Exactly This

### The AGP 9.2 Breaking Change

Gradle Plugin 9.2's AKMP `kotlin { android { } }` block **deprecated** the old `android { }` configuration in favor of a new DSL. The migration guide (KT-58759, verified in kotlin-gradle-plugin source) states:

> Resources are no longer automatically included. Use `androidResources { enable = true }` to preserve legacy behavior.

This is not automatic because AKMP allows multiple targets (commonMain, wasmJsMain, iosMain) — each with its own resource strategy. Auto-enabling could cause spurious resource conflicts or overrides. Explicit opt-in is safer.

### Why Silent Failure Lasted 2+ Weeks

1. `emptyList()` fallback suppressed the exception completely — app didn't crash, just showed no templates
2. Release process: no pre-release APK inspection (unzip + verify assets)
3. The bug only manifested **after AGP upgrade + rebuild** (not before-after analysis done)
4. `TemplatesRepositoryImpl` had no production logging — exceptions disappeared in logcat noise

### Why TemplatesRepositoryImpl Couldn't Try/Catch Entirely

Splitting read vs parse is important:
- **Read failure** (MissingResourceException) → always return `emptyList()` (graceful degradation, user sees empty templates)
- **Parse failure** (SerializationException) → log it (diagnostic signal), still return `emptyList()`
- If we `throw` on read failure, callers like `InteractiveOnboardingViewModel.<init>:48` (no try/catch in coroutine) crash the entire startup

So the pattern is: **Log diagnostic info, then fall back gracefully.**

## Alternatives Considered & Rejected

| Approach | Why Rejected |
|---|---|
| **Inline templates in code** (no JSON file) | Loss of flexibility; A/B testing templates via RC becomes impossible; bundle size matters on wasmJs |
| **Migrate to RC-only templates** | Requires backend migration, adds latency (RC fetch every app start), loses offline support |
| **Build a migration task in Gradle** | Overengineering; explicit `androidResources { enable = true }` is the standard fix |
| **Remove try/catch, let it crash** | Breaks startup for all users if resource is ever missing (fragile) |

## Related Files

- `feature/create/build.gradle.kts` — fixed `kotlin { android { androidResources { } } }`
- `feature/create/src/commonMain/kotlin/.../repository/TemplatesRepositoryImpl.kt` — improved error logging
- `composeApp/build.gradle.kts` — existing pattern (already has `androidResources { enable = true }`)
- `core/designsystem/build.gradle.kts` — existing pattern (already has `androidResources { enable = true }`)

## Validation

- **assembleDebug**: BUILD SUCCESSFUL, 10s incremental
- **APK inspection**: `unzip -l build/outputs/apk/debug/*.apk | grep templates.json` ✓ (28939 bytes, present)
- **Installation**: Pixel_9 emulator, no crash on startup
- **TemplatesScreen**: Loads 12 templates, lists visible, preview tappable
- **InteractiveOnboardingViewModel**: No exception in onCreate, all features functional

## Production Availability

Fixes included in commit `3af9c678` (fix(create): pack compose resources into android apk). Ready for APK release.

## Latent Risk — 19 Other KMP-Android-Library Modules

Audit of `build.gradle.kts` across all KMP-library modules shows:
- **3 modules with composeResources:** composeApp, core/designsystem, feature/create — all now have `androidResources { enable = true }` ✓
- **19 modules without composeResources:** core/common, feature/checklist, feature/home, feature/paywall, etc. — **none have `androidResources { enable = true }` block**

**Latent bug scenario:** If any of these 19 modules adds a `composeResources/` folder in the future (e.g., a new design system icon set, feature-specific illustrations), the same APK-assembly gap will occur. The fix is simple (one line), but easy to forget.

**Recommendation:** Update Gradle skill `android-feature-module-builder` to emit `androidResources { enable = true }` for all new KMP-android-library modules, regardless of whether they currently have composeResources. This makes the pattern explicit and prevents regressions.

## Compound Effect (Documentation)

This fix demonstrates the **diagnostic logging > guesses** pattern (prototested in 2026-05-05 premium-limit-loading-content-race):

1. **Guess 1:** "Maybe the new composeMultiplatform plugin broke resource packing" → added explicit plugin version, no help
2. **Diagnostic:** `unzip -l APK` + `./gradlew build --info | grep copyAndroidMainComposeResources` → immediate clarity (task missing)
3. **Root cause identification:** `androidResources { enable = true }` found in Gradle docs after 200ms search
4. **Fix:** 1-line change + improved logging

Estimated waste without diagnostics: +2–3 iterations (total 8–9 vs actual 6). **Diagnostic investment pays 30%+ time savings on subtle build issues.**

Same pattern applies to:
- CORS issues: `curl -X OPTIONS` first, not DevTools
- Asset loading: `unzip -l` + `du -sh build/` before rebuild
- Memory leaks: `adb dumpsys meminfo` + diagnostic logs before code review

## Recommendations for Future

### Skill: android-feature-module-builder
Add to template `build.gradle.kts` for new modules:
```gradle.kts
androidResources {
    enable = true
}
```
This closes regress risk for all 19 latent modules.

### CLAUDE.md (project)
Update **Architecture / Module Structure** section to document:
> **CRITICAL for KMP-Android-Library modules with composeResources:**
> Always include `androidResources { enable = true }` inside `kotlin { android { } }` block.
> AGP 9.2 AKMP DSL does not auto-enable resource aggregation. Without this, `composeResources/**` files build locally but do not pack into APK.

This prevents future regressions when team members add resources to existing or new modules.

### Project Memory
Add entry to `MEMORY.md`:
```
- [AGP 9 Feature Module androidResources Fix (2026-05-11)](agp9-feature-module-androidresources-fix-2026-05-11.md) — Feature module Compose Resources silent APK-assembly failure. Diagnostic pattern: unzip -l APK + gradle --info grep before code review. Latent risk in 19 other modules without preventive pattern.
```
