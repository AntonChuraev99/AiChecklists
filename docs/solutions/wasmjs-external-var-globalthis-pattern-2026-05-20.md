---
title: "Kotlin/Wasm external var in ESM modules — use globalThis js() wrapper instead"
date: 2026-05-20
type: bug-fix
modules: [core/designsystem, core/remoteconfig]
keywords: [wasmJs, ESM strict-mode, external var, globalThis, ReferenceError, Kotlin/Wasm interop, top-level bindings, js-expression]
project: gisti-ai-checklists
---

# Kotlin/Wasm `external var` in ESM modules causes strict-mode ReferenceError

## Проблема / Контекст

Production wasmJs-сборка на https://checklists.gisti.workers.dev/ выпадала с ошибкой:
```
ReferenceError: __customLocale is not defined
  at <anonymous>
  (at composeApp.js line N)
```

Корневая причина: `external var` на top-level Kotlin-файла, который компилируется в ESM-модуль, транслируется в **bare-identifier assignment** в strict mode, а не в обращение к window/globalThis.

```kotlin
// ❌ BAD — transpiles to bare assignment in ESM strict mode
private external var __customLocale: String?

// Usage in function:
fun LocalAppLocale.provides() = ... {
    __customLocale = value  // ← translates to: __customLocale = value
                            //   (no globalThis prefix, strict mode → ReferenceError)
}
```

dev-сервер не воспроизводил ошибку из-за разницы между dev и production Wasm-трансляцией.

## Решение

Заменить `external var` на пару `js()` функций, явно обращающихся к `globalThis`:

```kotlin
// ✅ CORRECT — js() expression explicitly targets globalThis
private fun readCustomLocale(): String? = js("globalThis.__customLocale")

private fun writeCustomLocale(value: String?) {
    js("globalThis.__customLocale = value")
}

// Usage in function — clean call-site:
fun LocalAppLocale.provides() = {
    value = {
        Pair(
            LocalLocale provides (readCustomLocale() ?: Locale.ENGLISH),
            { writeCustomLocale(it) }
        )
    }
}
```

**Почему именно так:**

1. **`js()` ограничена одним выражением** — для read/write разносим на две функции.
2. **`globalThis` явен** — больше нет амбигуити ESM strict-mode, транслируется в `globalThis["__customLocale"]`.
3. **Паттерн уже существует в проекте** — см. `core/remoteconfig/.../RemoteConfigFactory.wasmJs.kt:20`:
   ```kotlin
   private fun fetchRemoteConfig(): Promise<*> = 
       js("globalThis.__rcFetchPromise ?? Promise.resolve(false)")
   ```
4. **Шим в index.html работает с window, Kotlin с globalThis** — это один объект в браузере, разные имена.

## Примеры

**Before (broken):**
```kotlin
// File: core/designsystem/src/wasmJsMain/kotlin/.../AppLocale.wasmJs.kt
private external var __customLocale: String?

actual fun LocalAppLocale.provides() = {
    value = {
        Pair(
            LocalLocale provides __customLocale?.let(Locale::parse) ?: Locale.ENGLISH,
            { __customLocale = it.toLanguageTag() }
        )
    }
}
// Production error: ReferenceError: __customLocale is not defined ❌
```

**After (fixed):**
```kotlin
private fun readCustomLocale(): String? = js("globalThis.__customLocale")

private fun writeCustomLocale(value: String?) {
    js("globalThis.__customLocale = value")
}

actual fun LocalAppLocale.provides() = {
    value = {
        Pair(
            LocalLocale provides (readCustomLocale() ?: Locale.ENGLISH),
            { writeCustomLocale(it) }
        )
    }
}
// Production: works ✅
```

## Связанные файлы

- `core/designsystem/src/wasmJsMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/AppLocale.wasmJs.kt` — fixed 2026-05-20, commit 69e3afb6
- `core/remoteconfig/.../RemoteConfigFactory.wasmJs.kt:20` — reference implementation of `js()` for globalThis read
- `composeApp/src/wasmJsMain/resources/init.js.template` — shim that writes to `window.__customLocale`

## Уроки для будущего

1. **dev vs production Wasm-трансляция разные** — dev-сервер может не воспроизводить strict-mode ошибки, которые вылетают в production. Рекомендация: smoke-test на `wasmJsBrowserDistribution` (production-сборке) для PR'ов, трогающих wasmJs interop, перед мержем.

2. **top-level external bindings опасны в ESM** — отдавай предпочтение `js()` функциям для любых глобалов (переменные, функции, объекты).

3. **Pattern repeat** — эта ошибка повторилась после language-switching фичи (2026-05-18). Similar issue был с `navigatorLanguageRaw()` top-level. Рекомендация: добавить чек в docs/guidelines (или agent-память wasmjs-expert) с явным заголовком **«NEVER use top-level external var in KMP files for globalThis interop»**.
