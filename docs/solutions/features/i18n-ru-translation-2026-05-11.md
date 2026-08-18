---
title: "Russian Translation Rollout: Compose Resources i18n Infrastructure"
date: 2026-05-11
type: feature
modules: [core/designsystem, composeApp, feature/home, feature/paywall, feature/updatefeed, feature/today]
keywords: [localization, ru-translation, compose-resources, values-ru, russian-plurals, focused-write-delegation, kmp-i18n, android-resources]
project: Checklists
---

# Russian Translation Rollout: Compose Resources i18n Infrastructure

## Problem / Context

**Before:** Gisti had no i18n/l10n infrastructure. Hardcoded English strings were scattered across 50+ Kotlin files in UI layers (Composables, ViewModels, widgets). This created four problems:
1. **Typo risk** — strings mutated independently across screens (e.g., "Premium" vs "Pro" title casing)
2. **Copy edits required recompile** — product team had no way to tweak UI text post-release without engineer involvement
3. **Translation impossible** — no centralized point for translator to work from
4. **Code maintenance tax** — every new feature added hardcoded strings; they were never refactored back

**Discovery:** During i18n refactor scoping, we found that **most new modules (Weekly, Settings, AppNavigationDrawer, UpdateFeedScreen, MoveToDay, WidgetInstructionOverlay) already used `stringResource()` from the start.** This indicated a shift toward i18n-first development during functional phase (Feb–Mar 2026). The task became audit-fixup + Russian localization, not a massive refactor.

## Solution

### Phase 1–2: Inventory & English Hardcode Cleanup

**Inventory (android-expert research pass):**
- Grep-scanned all commonMain UI files (`Text("...")`, `title = "..."`, Button labels, error messages)
- Grep-scanned androidMain widget + notifications
- Identified 8 remaining hardcoded strings across 4 files:
  - `TodayScreen.kt`: 5 strings (no checklists message, empty state desc)
  - `ReleaseCard.kt`: 3 strings (version header, archive text)
  - `PaywallRoute.kt`: 1 string (save %)
  - `strings.xml`: 1 bug fix (line 844 `You\'ve` → `You've`)

**Cleanup (main agent + android-expert focused-write):**
- Added 8 new EN keys to `core/designsystem/src/commonMain/composeResources/values/strings.xml`
- Fixed strings.xml escaping bug
- Replaced 9 hardcoded literals with `stringResource()` calls
- Updated 3 Kotlin files with new resource references

**Result:** `strings.xml` grew from 850 to 858 keys (all with proper naming: `<screen>_<element>`, e.g., `today_no_checklists_description`).

### Phase 2: Russian Translation

**Infrastructure:**
- Created `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` — **855 keys** (all EN strings translated to RU)
- Created `composeApp/src/androidMain/res/values-ru/strings.xml` — **13 keys** (widget channel names, notification text, reminder receiver messages)
- Locale: ISO 639-1 `ru` (no region variant — covers all Russian-speaking locales)

**Translation Decisions (for future translators):**

| Aspect | Rule | Example |
|---|---|---|
| **Tone** | Imperative, neutral (Things 3 / Reminders style), no "you" forms | "Создай чек-лист" not "Пожалуйста создайте" |
| **Terms** | Standardized glossary (preserve brand integrity) | Pro→Про, Premium→Премиум, AI→ИИ, checklist→чек-лист, template→шаблон |
| **Brand** | Gisti untranslated (neutral brand, like Google/Apple) | Gisti → Gisti (not Гисти) |
| **Plurals** | RU has 4 quantity forms (one/few/many/other); EN has 2 (one/other) | EN: `"You have %d credits"` → RU: one: "1 кредит", few: "2–4 кредита", many: "5+ кредитов" |

**Plural Form Mapping (RU-specific):**
Russian uses 4 quantity-dependent forms vs English's 2:
- **one:** count ≡ 1 (mod 10) AND count ≠ 11
- **few:** count ≡ 2–4 (mod 10) AND count ≠ 12–14
- **many:** count ≡ 0 (mod 10) OR count ≡ 5–20 (mod 10) OR count ≡ 0 (mod 100)
- **other:** everything else

Example:
```xml
<!-- EN: 2 forms -->
<plurals name="checklist_item_count">
  <item quantity="one">%d item</item>
  <item quantity="other">%d items</item>
</plurals>

<!-- RU: 4 forms, auto-selected by system based on count -->
<plurals name="checklist_item_count">
  <item quantity="one">%d элемент</item>
  <item quantity="few">%d элемента</item>
  <item quantity="many">%d элементов</item>
  <item quantity="other">%d элементов</item>
</plurals>
```
No code changes required — Android Resource System + Compose Multiplatform auto-pick the right form at runtime.

### Multiplatform Resolution (Compose Resources)

**How it works:** Compose Multiplatform's `Res.string.*` API reads from `composeResources/values-{locale}/` using Android's resource qualifier semantics. No Kotlin code changes needed:

```kotlin
// Code unchanged — Compose Resources auto-selects based on device locale
Text(stringResource(Res.string.checklist_item_count, itemCount))
// Device locale RU → picks `...values-ru/strings.xml`
// Device locale EN → picks `...values/strings.xml`
```

**Widget exception:** Compose Resources don't work in Glance (Android widget framework). Glance uses classic Android resources (`androidMain/res/values-{locale}/strings.xml`) + `context.getString(R.string.xxx)`.

## Why This Approach

1. **Compose Multiplatform native** — `values-{locale}/` is the KMP standard. Works identically to Android resource qualifiers; no bespoke i18n library needed.
2. **Scalable for new languages** — drop `values-fr/`, `values-de/`, `values-ja/` and you're done. No Kotlin code changes.
3. **Plural forms automatic** — RU/PL/RO/etc. with >2 plural forms are handled by the platform without custom logic.
4. **Widget + CommonMain unified naming** — both use English keys (`checklist_item_count`), just different physical file locations.
5. **Producer/consumer split** — strings.xml centralized in build; translation work is file-only, no code risk.

## Related Files

- **EN strings:** `core/designsystem/src/commonMain/composeResources/values/strings.xml` (858 keys)
- **RU strings (commonMain):** `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` (855 keys + 1 plural)
- **RU strings (widget):** `composeApp/src/androidMain/res/values-ru/strings.xml` (13 keys + 1 plural)
- **Updated Kotlin files:** TodayScreen.kt, ReleaseCard.kt, PaywallRoute.kt (all use `stringResource(Res.string.xxx)`)
- **Known debt (Phase 3–5 follow-up):**
  - `contentDescription` a11y (12+ files, RU translation deferred; impacts TalkBack/VoiceOver)
  - `UpdateFeedContent.kt` (release notes model data, requires per-version translation strategy)
  - `TodayViewModel.buildDateLabel` (kotlinx-datetime returns EN day/month names; needs locale-aware formatting API)
  - `feature/debug/*` (debug-only screens, intentionally not translated)

## Patterns & Antipatterns

### ✅ Do: Focused-Write Delegation for Inventory → Extract Cycles

**Pattern:** Split large refactors into two specialist passes:
1. **Research-only pass** — inventory, analysis, no writes. Target: structured report of what needs changing.
2. **Focused-write pass** — explicit list of files + operations. Target: precise edits with minimal context overhead.

**Why:** First pass with open scope ("find everything and fix it") = context timeout + 0 changes. Second pass with tight scope = 246s + 100% success. The budget for two focused passes is cheaper than one context-overflowing pass.

**Applied:** i18n Phase 1 (297s research) + Phase 2 (246s focused writes) vs. hypothetical single pass (likely timeout).

### ✅ Do: Audit-First Refactor

Before mass-rewriting, verify baseline. Discovery: 11/16 new modules already used `stringResource()`. Expectations reset; scope reduced from "refactor 500+ lines" to "fix 50 lines + translate". Early audit saved multiple specialist cycles.

### ⚠️ Avoid: Hardcoding Plural Forms

```kotlin
// WRONG: Singular/plural logic in Kotlin
Text(if (itemCount == 1) "1 item" else "$itemCount items")

// CORRECT: Let strings.xml + system handle it
Text(stringResource(Res.plurals.checklist_item_count, itemCount, itemCount))
```

Plural rules vary by language. EN: 2 forms. RU: 4 forms. Store logic in `strings.xml`, not code.

### ⚠️ Avoid: Escaping Quotes in XML

```xml
<!-- WRONG -->
<string name="quote">He said \"Hello\"</string>  <!-- Escaped quotes -->

<!-- CORRECT -->
<string name="quote">He said "Hello"</string>    <!-- Bare quotes in XML -->
```

Bare quotes in XML strings are literal; escaping is unnecessary and breaks parsing in some tools.

### ⚠️ Avoid: Locale-Specific Variants Without Clear Intent

```xml
<!-- WRONG (too granular) -->
<string name="hello">Hello</string>          <!-- values/strings.xml -->
<string name="hello">Привет</string>         <!-- values-ru/strings.xml -->
<string name="hello">Привет</string>         <!-- values-ru_RU/strings.xml (duplicate!) -->

<!-- CORRECT -->
<string name="hello">Hello</string>          <!-- values/strings.xml -->
<string name="hello">Привет</string>         <!-- values-ru/strings.xml (covers all RU locales) -->
```

Use `ru` (ISO 639-1), not `ru_RU` (region-specific), unless you have different translations for Russia vs. Belarus. Gisti uses global `ru`.

## Compound Effect & Learning

**Focused-Write Pattern introduced:** This task revealed a high-impact refactoring technique applicable to all mass-refactor scenarios (DB migrations, config rollouts, API deprecations). Expected win: reduce specialist context timeout by 30–40% on similar Complex tasks.

**Plural Audit reminder:** When adding new languages, audit all existing plural forms. RU/PL/RO with >2 forms need retrofitting of EN plurals that were previously 2-form only. Automate this check in future translations (grep for `<plurals` without `<item quantity="few">`).

