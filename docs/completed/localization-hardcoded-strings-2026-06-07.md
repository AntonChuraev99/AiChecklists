---
status: done
date: 2026-06-07
start_sha: 351955e2
type: bugfix
complexity: standard→complex
impact: medium
modules: [feature/create, feature/analyze, feature/home, composeApp, core/designsystem]
---

# Hardcoded RU user-facing strings → Compose Resources

## Цель

Устранить захардкоженные русскоязычные (и непоследовательно англоязычные) user-facing
строки в Kotlin-коде. Симптом (скриншот пользователя): экран "New Checklist" на английском
интерфейсе показывает ошибку валидации "Введите название чек-листа" по-русски.

Корень: строки ошибок/дефолтные имена записаны строковыми литералами прямо в ViewModel,
вместо `getString(Res.string.x)` (паттерн, уже принятый в `TemplatePreviewViewModel`,
`AnalyzeResultPreviewViewModel`, `PaywallViewModel`, `SplashViewModel`).

## Технический план

1. Добавить 9 ключей строк в `core/designsystem` `values/strings.xml` (EN) + `values-ru/strings.xml` (RU).
   Переиспользовать существующие `analyze_error_no_input`, `error_create_checklist_failed`.
2. `CreateChecklistViewModel:147` — обернуть в `viewModelScope.launch` + `getString`.
3. `AnalyzeViewModel` — 9 строк (ошибки + дефолтные имена); вне-корутинные обернуть/перенести в launch.
4. `FillDetailViewModel:146`, `ChecklistDetailViewModel:857` — обернуть в launch + `getString`.
5. `CreateWeeklyChecklistUseCase` — добавить `name: String` параметр (domain чист);
   `App.kt` + `TemplatesViewModel` передают `getString(weekly_checklist_default_name)`.
6. Обновить тесты (`CreateWeeklyChecklistUseCaseTest`).
7. `CLAUDE.md` + rule `compose-resources-kmp` — правило против hardcoded user-facing строк.

## Лог итераций

### Iteration 1 — 2026-06-07 — main-agent
**Что сделано:** Identified 13 user-facing hardcoded strings across 5 ViewModels/UseCases (CreateChecklistViewModel, AnalyzeViewModel, FillDetailViewModel, ChecklistDetailViewModel, CreateWeeklyChecklistUseCase). Root cause: string literals instead of `getString(Res.string.key)` pattern.

**Почему так:** Pattern was already established in sibling VMs (TemplatePreviewViewModel, PaywallViewModel, SplashViewModel), but not consistently applied. Hardcoding pins one language regardless of system locale → UX breaks when switching to EN or RU.

**Решение:** 
- Added 9 new string keys to `core/designsystem` `values/strings.xml` (EN) + `values-ru/strings.xml` (RU); reused 2 existing keys
- Wrapped non-coroutine call sites in `viewModelScope.launch { getString() }` (standard pattern for suspend functions in ViewModels)
- Changed `CreateWeeklyChecklistUseCase` to accept `name: String` parameter (domain layer cleanliness; Compose Resources don't resolve in JVM tests)
- Updated callers: `App.kt` (Composable, `stringResource`) + `TemplatesViewModel` (launch block, `getString`)
- Updated test to pass name explicitly
- Added prevention rule to `CLAUDE.md` + expanded `compose-resources-kmp.md`

### Iteration 2 — 2026-06-07 — validation
**Что сделано:** Verified all changes compile and pass tests.

**Результаты:** 
- `:androidApp:compileDebugKotlin` → Res regenerated, exit 0
- `:composeApp:compileKotlinWasmJs` → BUILD SUCCESSFUL
- `:feature:create:testAndroidHostTest` + `:feature:home:testAndroidHostTest` → 44s, all tests pass
- No hardcoded Cyrillic user-facing strings remain (lexicons, regex, Russian comments left untouched — not bugs)

## Выводы

1. **Root cause:** Inconsistent application of established `getString(Res.string.key)` pattern. 13 sites across 5 modules had reverted to string literals, creating scattered single-language hardcodes.

2. **Key insight — domain layer strategy:** `CreateWeeklyChecklistUseCase` needs the default name as a **parameter**, not a direct `getString()` call. This keeps domain code JVM-testable and Compose-Resource-independent. Pattern: UI layers (Composables, ViewModels) capture strings and pass to domain; domain remains pure Kotlin.

3. **Suspend-wrapper pattern:** For non-coroutine contexts (property initializers, validation methods), standard practice is `viewModelScope.launch { getString(...) }`. Already used in PaywallViewModel + SplashViewModel; now documented as a rule.

4. **Prevention matters:** Without explicit CLAUDE.md rule, recurrence is likely. Added a "no hardcoded user-facing strings" rule to Project Language section + expanded compose-resources-kmp rule with concrete examples.

5. **Legitimate Cyrillic untouched:** RuIntentLexicon, RuDateLexicon, regex char classes, parser comments, AiSenderLabel (already using stringResource) were NOT modified — correctly identified as non-bugs.

## Предложения по улучшению агентов

- [ ] **detekt guard for hardcoded Cyrillic strings:** A custom detekt rule could flag Cyrillic/RU string literals in `*Main.kt` files (outside test, lexicon, regex contexts) at write-time. Would catch this class of bugs earlier, since `*ViewModel.kt` files don't auto-load the compose-resources-kmp rule (rule files only load on matching path patterns). Implementation: flag non-comment Cyrillic in `[А-Яа-яЁё]` outside allowed zones (test files, _Lexicon classes, regex patterns). Medium effort, high payoff for RU/multi-locale projects.
