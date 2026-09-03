---
title: "Hardcoded Strings in ViewModels → Compose Resources (i18n Fix)"
date: 2026-06-07
type: bug-fix
modules: [feature/create, feature/analyze, feature/home, core/designsystem]
keywords: [i18n, localization, Compose Resources, getString, hardcoded strings, RU/EN mismatch, ViewModel]
project: gisti-checklists
---

# Hardcoded RU/EN Strings in ViewModels → Compose Resources

## Problem / Context

User reported (screenshot): the "New Checklist" screen displayed a validation error **"Введите название чек-листа"** (Russian) when the app UI was set to English. Root cause: error messages and default names were hardcoded as string literals in ViewModels (`CreateChecklistViewModel`, `AnalyzeViewModel`, `FillDetailViewModel`, `ChecklistDetailViewModel`, `CreateWeeklyChecklistUseCase`) instead of using the project's `getString(Res.string.key)` pattern. This pins one language regardless of the system locale.

The hardcoding pattern was **inconsistent**: some VMs used the correct pattern (e.g., `TemplatePreviewViewModel`, `PaywallViewModel`, `SplashViewModel`), while others had literal strings hardcoded, creating scattered user-facing strings across 5 modules.

## Solution

**Step 1: Add string keys to Compose Resources**  
Added 9 new keys to `core/designsystem/src/commonMain/composeResources/`:
- `values/strings.xml` (EN)
- `values-ru/strings.xml` (RU)

Reused existing keys where applicable:
- `analyze_error_no_input` (already existed)
- `error_create_checklist_failed` (already existed)

New keys added:
- `create_checklist_empty_name_error` (EN: "Name required", RU: "Введите название чек-листа")
- `analyze_error_invalid_input` (RU variant of existing error)
- `analyze_default_checklist_name` (EN: "New Checklist", RU: "Новый список")
- `create_fill_default_name` (EN: "Fill Item", RU: "Заполнить элемент")
- `checklist_default_name` (EN: "My Checklist", RU: "Мой список")
- `weekly_checklist_default_name` (EN: "My Week", RU: "Моя неделя")
- plus 3 additional error/default variants for analyze flow

**Step 2: Replace hardcoded strings with `getString()` calls**

Five files affected:

1. **`CreateChecklistViewModel:147`** — wrapped validation in `viewModelScope.launch { getString(Res.string.create_checklist_empty_name_error) }`

2. **`AnalyzeViewModel`** — 9 hardcoded strings (7 RU errors, 2 inconsistent EN defaults):
   - Error messages: "Не удалось проанализировать…", "Ошибка при обработке файла…" → `getString`
   - Default name "New Checklist" → `getString(Res.string.analyze_default_checklist_name)`
   - Non-coroutine call sites wrapped in `viewModelScope.launch { }`

3. **`FillDetailViewModel:146`** — wrapped in `launch { getString(Res.string.create_fill_default_name) }`

4. **`ChecklistDetailViewModel:857`** — wrapped in `launch { getString(Res.string.checklist_default_name) }`

5. **`CreateWeeklyChecklistUseCase` (domain layer)** — **pattern change**: added `name: String` parameter instead of calling `getString` directly (Compose Resources don't belong in domain layer and don't resolve in plain unit tests). Callers:
   - `App.kt` (Composable scope): captures `stringResource(Res.string.weekly_checklist_default_name)` before calling use case
   - `TemplatesViewModel` (non-Composable): calls `getString` in a `launch` block, passes result to use case

**Step 3: Update tests**

`CreateWeeklyChecklistUseCaseTest` updated to pass default name explicitly — no longer mocks `getString` behavior (impossible in domain layer).

**Step 4: Prevent recurrence**

Added explicit rule to project `CLAUDE.md` (Project Language section):
- "No hardcoded user-facing strings in Kotlin code. Use `getString(Res.string.x)` from Compose Resources, even in non-Composable contexts — wrap in `viewModelScope.launch { }`."

Expanded rule `compose-resources-kmp.md` with section "Strings from non-Composable code" documenting the suspend-wrapper pattern and domain-layer strategy (pass as param, don't call getString).

## Why This Approach

**1. Suspend-wrapper pattern:**  
`getString` is `suspend`, so non-coroutine call sites (e.g., `onSaveClick()`, property defaults) must use `viewModelScope.launch { }` to acquire strings. This is standard in Compose (see existing VMs like `PaywallViewModel`).

**2. Domain layer cleanliness:**  
`CreateWeeklyChecklistUseCase` is a plain Kotlin use case; it should not depend on Compose Resources. Instead, the name is **passed as a parameter** by callers in UI layers (Composables + ViewModels) that have access to `getString`. This keeps domain code testable on the JVM and maintains separation of concerns.

**3. Reuse existing keys:**  
Where applicable, reused existing string keys to avoid duplication and leverage existing translations.

**4. Mandatory-rule enforcement:**  
Without a documented rule, future devs might hardcode strings again. The CLAUDE.md rule makes this a project standard, not a one-off fix.

## Connected Files

- `core/designsystem/src/commonMain/composeResources/values/strings.xml` (9 new EN keys)
- `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` (9 new RU keys)
- `feature/create/presentation/create/CreateChecklistViewModel.kt`
- `feature/create/presentation/templates/TemplatesViewModel.kt`
- `feature/create/domain/usecase/CreateWeeklyChecklistUseCase.kt`
- `feature/create/commonTest/.../CreateWeeklyChecklistUseCaseTest.kt`
- `feature/analyze/presentation/AnalyzeViewModel.kt`
- `feature/home/presentation/detail/ChecklistDetailViewModel.kt`
- `feature/home/presentation/fill/FillDetailViewModel.kt`
- `composeApp/commonMain/kotlin/.../App.kt`
- `CLAUDE.md` (Project Language section + compose-resources-kmp rule)
- `.claude/rules/compose-resources-kmp.md` (new "Strings from non-Composable code" section)

## Validation

- `:androidApp:compileDebugKotlin` → Res keys regenerated, exit 0
- `:composeApp:compileKotlinWasmJs` → BUILD SUCCESSFUL
- `:feature:create:testAndroidHostTest` + `:feature:home:testAndroidHostTest` → 44s, all tests pass
- No hardcoded Cyrillic user-facing strings remain in *.kt source files (lexicons, regex, and comments intentionally untouched)
