---
paths:
  - "**/composeResources/**"
  - "**/build.gradle.kts"
  - "**/strings.xml"
---

# Compose Resources in KMP-Android library modules + Localization

## androidResources opt-in (AGP 9 / AKMP DSL)

After AGP 9 migration the new AKMP DSL (`kotlin { android { ... } }`) **does not enable Android resources by default**. Any KMP-library module that ships its own `composeResources/` (files, drawables, fonts, values) MUST opt in explicitly — otherwise `Res.readBytes`/`Res.getDrawable` throws `MissingResourceException` at runtime (assets never get packed into the APK).

```kotlin
kotlin {
    android {
        namespace = "..."
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
        androidResources {           // ← REQUIRED for any module with composeResources/
            enable = true
        }
    }
}
```

Currently present in `composeApp`, `core/designsystem`, `feature/create` (the only modules with their own `composeResources/`). The other 19 KMP-library modules don't need it today, but **add it the moment you add `src/commonMain/composeResources/`**. Full root-cause: `docs/solutions/build-system/agp9-feature-module-androidresources-fix-2026-05-11.md`.

## Localization

Strings live in `core/designsystem/src/commonMain/composeResources/values/strings.xml`.

```kotlin
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource

stringResource(Res.string.your_key)
```

Naming: prefix with screen (`main_`, `create_`, `analyze_`, `paywall_`), snake_case. Primary language is **English only** — RU localization only on explicit request or when fixing existing RU strings.

## Strings from non-Composable code (ViewModel / coroutine / domain)

`stringResource()` is `@Composable` — it can NOT be called from a ViewModel, UseCase, or coroutine. Use the **suspend** `getString()`:

```kotlin
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString

// inside viewModelScope.launch { } or any suspend fun:
_screenState.update { it.copy(error = getString(Res.string.error_save_failed)) }
```

- **Synchronous call site** (e.g. `onSaveClick()` validating a blank name) — `getString` is suspend, so wrap it: `viewModelScope.launch { _screenState.update { it.copy(nameError = getString(...)) } }`.
- **`App.kt` / `LaunchedEffect`** needing a value before the coroutine — resolve with `stringResource` in Composable scope, capture into a `val` (existing pattern), then use it inside the effect.
- **Domain layer (UseCase) must NOT call `getString`** — Compose Resources don't belong in domain and don't resolve in plain unit tests. Add a `name: String` param; let presentation pass `getString(Res.string.x)`.
- **Never hardcode** a user-facing literal in Kotlin (errors, snackbars, default names like `"New Checklist"`). A literal pins one language — the 2026-06-07 RU-on-EN bug ("Введите название чек-листа" on the English UI). Keep as literals only the NON-user-facing strings: parser lexicons (`RuIntentLexicon`, `RuDateLexicon`), regex, log tags, analytics event keys.

## ⚠️ Escaping — do NOT use Android `\'` (recurring bug)

`composeResources/**/strings.xml` is parsed by `org.jetbrains.compose.resources`, **not AAPT** — escaping rules differ from Android `res/values/`. Write apostrophes and quotes **literally**. The only backslash escapes honored are `\n`, `\t`, `\uXXXX`.

```xml
<!-- ✅ CORRECT — bare apostrophe/quote -->
<string name="a">This can't be undone</string>
<string name="b">Here's your "checklist"!</string>

<!-- ❌ WRONG — Android-style escape renders the backslash on screen: can\'t -->
<string name="a">This can\'t be undone</string>
```

Why it recurs: Android muscle-memory adds `\'` (mandatory in AAPT). In Compose Resources `\'` shows up at runtime as a literal `\'` (visible backslash). Every existing string uses bare `'` — `don't`, `What's`, `You've`, `I'm`, `Couldn't` — **match them**. For XML metacharacters use entities (`&amp;`, `&lt;`, `&gt;`), never a backslash. Applies to **all** `values*/strings.xml` (EN + `values-ru` + any locale).
