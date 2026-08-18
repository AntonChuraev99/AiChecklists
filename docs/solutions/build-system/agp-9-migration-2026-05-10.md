---
title: "AGP 9.x Migration — Atomic Refactor Pattern for KMP Projects"
date: 2026-05-10
type: architecture
modules: [composeApp, androidApp, core/common/api, core/common/impl, core/datastore/api, core/datastore/impl, core/designsystem, core/navigation/api, core/navigation/impl, core/remoteconfig/api, core/remoteconfig/impl, feature/analyze, feature/checklist, feature/create, feature/debug, feature/home, feature/onboarding, feature/paywall, feature/sharing, feature/splash, feature/updatefeed, feature/user, notification, widget, csat]
keywords: [agp-9, kmp-multiplatform, gradle-migration, kotlin-multiplatform-plugin, firebase-bom-workaround, android-kotlin-multiplatform-library, withHostTest, atomic-refactor, build-system]
project: gisti-checklists
---

# AGP 9.x Migration — Atomic Refactor Pattern for KMP Projects

## Problem / Context

Android Gradle Plugin (AGP) 9.0 introduced breaking changes for Kotlin Multiplatform (KMP) projects:

1. **Hard plugin incompatibility check**: `com.android.library` + `kotlin.multiplatform` plugins cannot coexist. Previous workaround `enableLegacyVariantApi=true` was removed in AGP 9.0.
2. **New unified KMP plugin**: `com.android.kotlin.multiplatform.library` replaces `com.android.library`, but requires **both** plugins to be present together (not as a replacement).
3. **Source-set DSL changes**: Firebase BOM `platform()` does not work in `androidMainImplementation{}` blocks (Kotlin issue KT-58759).
4. **New KMP compilation requirement**: `withHostTest{}` block is mandatory in `android{}` for `commonTest` compilation on AGP 9.

Gisti AI Checklists (KMP project with 22 library modules + composeApp) required migration from AGP 8.11.2 → 9.2.0 and Gradle 8.14.3 → 9.5.0. Initial attempt (Iteration 2) hit the hard plugin incompatibility wall and had to be deferred. Subsequent retry (Iteration 3) succeeded by applying **atomic refactor pattern** — all 22 modules updated in single commit.

## Solution

### Atomic Refactor Pattern

**Core principle:** All library modules must be migrated **simultaneously**, not incrementally. No intermediate state can exist where some modules use old plugin syntax and others use new syntax — the hard plugin incompatibility check prevents mixed states.

**Process:**
1. Create feature branch (e.g., `feat/agp9-migration`)
2. Update gradle wrapper and AGP/Kotlin versions globally
3. **Atomic batch:** Modify all 22 `build.gradle.kts` files in single commit
4. Validate: build, test, commit
5. Code review & merge

### File-by-File Changes (Pattern)

#### libs.versions.toml
```toml
[versions]
agp = "9.2.0"                    # was 8.11.2
gradle = "9.5.0"                # was 8.14.3
kotlin = "2.3.20"               # compatible with AGP 9.x

[plugins]
# OLD (remove): androidLibrary = { id = "com.android.library", version.ref = "agp" }
# NEW: unified plugin
androidKmpLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
androidKmpApplication = { id = "com.android.kotlin.multiplatform.application", version.ref = "agp" }
```

#### gradle/wrapper/gradle-wrapper.properties
```properties
distributionUrl=https://services.gradle.org/distributions/gradle-9.5.0-bin.zip
```

#### Module build.gradle.kts (22 library modules)

**BEFORE (AGP 8.x):**
```kotlin
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
}

android {
    // Old DSL
    compileSdk = 34
    defaultConfig { ... }
    buildTypes { ... }
}

kotlin {
    androidTarget()
    iosArm64()
    iosX64()
    wasmJs()
    
    sourceSets {
        androidMain.dependencies { ... }
    }
}
```

**AFTER (AGP 9.x):**
```kotlin
@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

plugins {
    alias(libs.plugins.androidKmpLibrary)    // NEW unified plugin
    alias(libs.plugins.kotlinMultiplatform)  // KEEP BOTH (do not remove)
}

android {
    // Renamed: android {} → androidLibrary {}
    compileSdk = 34
    defaultConfig { ... }
    buildTypes { ... }
    
    // NEW: required for commonTest on AGP 9
    withHostTest {}
}

kotlin {
    androidTarget()
    iosArm64()
    iosX64()
    wasmJs()
    
    sourceSets {
        androidMain.dependencies {
            // Firebase BOM workaround (KT-58759): cannot use platform() in source-set blocks
            // add() explicitly instead
            add("implementation", platform(libs.firebase.bom))
        }
    }
}
```

**Key changes:**
- `alias(libs.plugins.androidLibrary)` → `alias(libs.plugins.androidKmpLibrary)`
- **DO NOT remove** `alias(libs.plugins.kotlinMultiplatform)` (both must coexist)
- `@file:Suppress("DEPRECATION", "OPT_IN_USAGE")` at top (for Kotlin 2.3 warnings on old DSL)
- `android {}` → `androidLibrary {}`
- Add `withHostTest {}` inside `android {}`
- Firebase BOM placement: `add("androidMainImplementation", platform(...))`

#### New androidApp/ Module

Extract application entry point into new module.

**androidApp/build.gradle.kts:**
```kotlin
@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

plugins {
    alias(libs.plugins.androidKmpApplication)
    alias(libs.plugins.kotlinMultiplatform)
}

android {
    compileSdk = 34
    namespace = "com.antonchuraev.aichecklists"
    
    defaultConfig {
        applicationId = "com.antonchuraev.aichecklists"
        minSdk = 26
        targetSdk = 34
        versionCode = 40
        versionName = "1.14.5"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    withHostTest {}
}

kotlin {
    androidTarget()
    
    sourceSets {
        androidMain.dependencies {
            implementation(projects.composeApp)
            // Other dependencies
        }
    }
}
```

**Files that move to androidApp/:**
- `MainActivity.kt`
- `GistiApplication.kt`
- `ConsentManager.kt`
- Widget receivers (`WidgetUpdateReceiver`, etc.)
- `drawable/` resource folder
- `mipmap/` resource folder
- `AndroidManifest.xml` with application tag

**Files that STAY in composeApp/androidMain/:**
- `AppBuildConfig.android.kt` (expect/actual)
- `PlatformModule.android.kt` (expect/actual)
- `Analytics.kt` (expect/actual)
- `CrashlyticsAppLogger.android.kt` (expect/actual)
- `csat/InAppReviewLauncher.android.kt` (expect/actual)
- All KMP library expect/actual code

This separation maintains clean module boundary: library stays reusable; application-specific code lives in androidApp.

### Key Patterns & Workarounds

#### 1. kotlin.multiplatform Plugin Coexistence

**Mistake:** Removing `alias(libs.plugins.kotlinMultiplatform)` when migrating to `com.android.kotlin.multiplatform.library`.

**Why it fails:** AGP 9 unified plugin requires both plugins to work together. They are not mutually exclusive; they complement each other.
- `com.android.kotlin.multiplatform.library` — AGP-specific Android/KMP integration
- `kotlin.multiplatform` — Kotlin compiler & multiplatform support

**Correct pattern:**
```kotlin
plugins {
    alias(libs.plugins.androidKmpLibrary)      // NEW
    alias(libs.plugins.kotlinMultiplatform)    // KEEP
}
```

#### 2. Firebase BOM in Source-Set Blocks

**Problem:** `platform()` syntax does not work in `androidMainImplementation{}` blocks.

**Kotlin issue:** KT-58759 — source-set blocks have restricted DSL that doesn't support `platform()` function.

**Workaround:**
```kotlin
sourceSets {
    androidMain.dependencies {
        // WRONG:
        // implementation(platform(libs.firebase.bom))  // → Gradle error
        
        // CORRECT:
        add("androidMainImplementation", platform(libs.firebase.bom))
    }
}
```

The `add()` function bypasses the restricted DSL and works correctly.

#### 3. withHostTest{} Block for commonTest

**Problem:** commonTest sources fail to compile on AGP 9 without explicit `withHostTest {}` block.

**Why:** AGP 9 KMP plugin changed how it handles host-side testing (unit tests that run on JVM, not on device). Explicit enablement is required.

**Pattern:**
```kotlin
android {
    compileSdk = 34
    namespace = "..."
    
    // ... other config ...
    
    withHostTest {}  // REQUIRED for commonTest on AGP 9
}
```

Apply to all modules that have `src/commonTest/` sources.

#### 4. Deprecated DSL Warnings with @file:Suppress

**Problem:** Kotlin 2.3 and Compose Multiplatform 1.10 emit deprecation warnings on old AGP 8.x DSL syntax, even though the code still works under AGP 9.

**Why:** New unified plugin syntax is preferred, but old syntax still functional for compatibility.

**Pattern:**
```kotlin
@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

plugins { ... }

// Rest of build.gradle.kts
```

This suppresses warnings during build, allowing cleanup of deprecated syntax to happen incrementally if desired.

#### 5. Gradle Version Validation

**Mistake:** Pinning to non-existent version (e.g., Gradle 9.1.1).

**Reality:** Gradle 9.x versions are: 9.0.0, 9.1.0, 9.2.0, 9.3.0, 9.4.0, 9.5.0, ... (no 9.1.1).

**Best practice:** Check [Gradle releases](https://gradle.org/releases/) before pinning. As of 2026-05-10, latest stable is **9.5.0**.

### Build Validation Checklist

After atomic refactor commit:

- [ ] `./gradlew :androidApp:assembleDebug` — produces valid APK
- [ ] `./gradlew :composeApp:wasmJsBrowserDistribution` — produces web bundle (~17 MiB)
- [ ] `./gradlew testAndroidHostTest` — all unit tests pass (commonTest on JVM)
- [ ] `git diff --name-only HEAD~1 HEAD -- ':(exclude)docs/*' | wc -l` — expect 80+ files touched (22 build.gradle.kts + androidApp + gradle config)
- [ ] No breaking API changes to source code (pure build-system refactor)

### Iteration Log (Real-World Debugging)

**Iteration 1 (INIT):** Baseline established, plugin mapping documented.

**Iteration 2 (kmp-expert assessment):** Hard wall discovered — incremental migration impossible. Deferred.

**Iteration 3 (atomic retry — 9 build-fix cycles):**

1. **kotlin.multiplatform plugin error** → kmp-expert had removed plugin thinking unified plugin replaces it. Fixed: restored plugin on all 22 modules.

2. **Firebase BOM placement error** → Direct `platform()` in `androidMainImplementation{}` → Gradle error. Fixed: wrapped in `add()`.

3–4. **Missing withHostTest{}** → 8 modules with `src/commonTest/` failed to compile. Fixed: added to all 11 modules needing it.

5–9. **Deprecated DSL warnings** → Kotlin 2.3 spammed stderr with warnings. Fixed: added `@file:Suppress`, module-by-module validation.

**Final state:** 9 cycles from initial atomic attempt to green build. Iteration 3 succeeded because Iteration 2 established clear atomic requirement.

## Why Exactly This Approach

### Why not incremental migration?

AGP 9.0 hard plugin incompatibility check makes incremental migration impossible. No intermediate state where old and new coexist.

### Why extract androidApp now?

composeApp is a Kotlin Multiplatform library in AGP 9 world — it should not contain application-specific code (MainActivity, Application class, drawable resources, manifests). Separation of concerns:
- **composeApp**: reusable library for all targets (Android, iOS, wasmJs)
- **androidApp**: Android-only application entry point

This pattern scales better if iOS or wasmJs apps ever need their own entry points.

### Why keep both plugins?

AGP 9 unified plugin does not replace `kotlin.multiplatform` — they work together:
- `com.android.kotlin.multiplatform.library` — Android/AGP specifics
- `kotlin.multiplatform` — Kotlin compiler multiplatform setup

Removing either causes compilation errors.

## Connected Files

Key files modified in migration:
- **gradle/wrapper/gradle-wrapper.properties** — Gradle version bump
- **gradle/libs.versions.toml** — AGP, Kotlin, Gradle versions + plugin aliases
- **composeApp/build.gradle.kts** — plugin rename, androidLibrary{}, withHostTest{}
- **androidApp/build.gradle.kts** — NEW module, com.android.kotlin.multiplatform.application
- **core/\*/build.gradle.kts** (10 modules) — plugin rename, androidLibrary{}, withHostTest{}
- **feature/\*/build.gradle.kts** (12 modules) — plugin rename, androidLibrary{}, withHostTest{}
- **notification/build.gradle.kts, widget/build.gradle.kts, csat/build.gradle.kts** — plugin rename, androidLibrary{}, withHostTest{}

## Learnings for Future KMP Projects

1. **AGP 9+ always requires atomic refactor** for library→KMP migrations. Plan for dedicated session, not incremental.

2. **kotlin.multiplatform plugin is not legacy** in AGP 9 — it's required alongside the unified library plugin.

3. **Firebase BOM in KMP source-sets needs workaround** — this is not intuitive from official docs. Document it early.

4. **withHostTest{} is mandatory** for any KMP module with commonTest sources under AGP 9. Verify presence in all modules.

5. **Gradle version reality-check** — never assume a version exists without checking Gradle releases page.

6. **File inventory matters** — decide early what goes in library vs. what goes in application. expect/actual actuals stay in library; implementation code goes to app module.

7. **Diagnostic: `@file:Suppress` is not cleanup, it's scaffolding** — add it during migration to unblock builds, but plan to address deprecated DSL separately (via a series of incremental small PRs after migration is stable).

---

## Appendix: Plugin ID Mapping (Quick Reference)

| Old (AGP 8.x) | New (AGP 9.x) | Purpose |
|---|---|---|
| `com.android.library` | `com.android.kotlin.multiplatform.library` | KMP library module |
| `com.android.application` | `com.android.kotlin.multiplatform.application` | KMP application (new) |
| `android {}` block | `androidLibrary {}` block | Android-specific config |
| (no equivalent) | `withHostTest {}` | Enable commonTest on JVM |

**Required alongside:**
- Both library/application modules MUST have `plugins { ... kotlin.multiplatform ... }`
- Both must coexist; do not remove either

---

**Commit reference:** 15c83bba (2026-05-10) — "build(agp): migrate to AGP 9.2.0 + Gradle 9.5.0 + android{} → androidLibrary{}"

**Branch:** feat/agp9-migration
