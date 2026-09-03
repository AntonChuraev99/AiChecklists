---
title: "Improve onboarding: grid categories + template auto-skip"
type: feat
date: 2026-03-12
---

# Improve onboarding: grid categories + template auto-skip

## Enhancement Summary

**Deepened on:** 2026-03-12
**Sections enhanced:** All
**Research agents used:** framework-docs-researcher, architecture-strategist, performance-oracle, pattern-recognition-specialist, ui-designer, learnings-researcher, code-simplicity-reviewer

### Key Improvements from Research
1. **Architecture fix**: Do NOT call `handleTemplateSelected()` from `handleStyleSelected()` — use single atomic `_screenState.update{}` instead (prevents double emission, AnimatedContent flash, MVI violation)
2. **Grid approach changed**: Use `FlowRow(maxItemsInEachRow = 2)` instead of `LazyVerticalGrid` — avoids nested scroll conflict, simpler for 10 fixed items
3. **Explicit state flag**: Add `wasTemplateStepSkipped: Boolean` to state instead of implicit `availableTemplates.size` check
4. **Existing modifier bug found**: `graphicsLayer` before `clip` causes scale to render outside card bounds — fix during this work
5. **Accessibility gaps**: Add `stateDescription`, `selected`, `selectableGroup()` for TalkBack

### Decisions Log
| Decision | Original | Deepened | Reason |
|---|---|---|---|
| Grid component | `LazyVerticalGrid` | `FlowRow` | Avoids nested scroll crash, simpler for 10 items |
| Auto-skip impl | Call `handleTemplateSelected()` | Single atomic `_screenState.update` | MVI compliance, prevents double emission |
| Back nav check | `availableTemplates.size == 1` | `state.wasTemplateStepSkipped` | Resilient to Remote Config changes |
| Min card height | 80dp | 96dp | Font scaling headroom (1.3x) |
| Scale animation | 1.02f | 1.01f | Prevent overlap at 12dp grid gap |
| Phases | 5 | 3 | Independent changes, no artificial boundaries |
| `preferredTemplateId` | Add to new entries | Don't add | Dead code — never read in ViewModel |
| `getExtraItems()` | Add content for new categories | Refactor to non-nullable, defer content | Compile-time safety without blocking |
| Debounce guard | In risks | Removed | Step change removes composable instantly |

---

## Overview

Enhance the interactive onboarding flow with two main changes:
1. **Step 1 (CategorySelection)**: Expand from 6 to 10 categories, switch from vertical list to 2-column grid layout
2. **Step 3 (TemplateSelection)**: Auto-skip when only 1 template matches the selected category

Step 2 (StyleSelection) remains unchanged.

## Problem Statement

- Step 1 shows only 6 categories as full-width cards — wastes screen space and limits user choice
- Step 3 forces the user to tap the only available template when there's just one option — unnecessary friction

## Proposed Solution

### Step 1 — Grid layout with 10 categories

Replace vertical `Column` iteration with `FlowRow(maxItemsInEachRow = 2)`. Cards switch from horizontal `Row` to vertical `Column` layout: emoji centered on top, title below.

> **Why `FlowRow` instead of `LazyVerticalGrid`?** For 10 fixed items, `LazyVerticalGrid` adds lazy composition overhead with zero benefit (no recycling needed). More critically, `LazyVerticalGrid` inside the existing `Column(Modifier.verticalScroll(...))` causes a runtime crash (`IllegalStateException: infinity maximum height`). `FlowRow` works naturally inside `verticalScroll` and requires no structural refactoring.

**10 categories (final set, based on research of Todoist, Checklist.com, Any.do, TickTick):**

| # | Enum value | Icon | Label (EN) | `templateCategories` |
|---|---|---|---|---|
| 1 | `WORK` | 💼 | Work | `["work"]` |
| 2 | `SHOPPING` | 🛒 | Shopping | `["shopping"]` |
| 3 | `TRAVEL` | ✈️ | Travel | `["travel"]` |
| 4 | `FITNESS` | 🏋️ | Fitness | `["fitness"]` |
| 5 | `HOME` | 🏠 | Home | `["real_estate", "home"]` |
| 6 | `COOKING` | 🍳 | Cooking | `["cooking"]` |
| 7 | `FINANCE` | 💰 | Finance | `["finance"]` |
| 8 | `EVENTS` | 🎉 | Events | `["events"]` |
| 9 | `HEALTH` | ❤️ | Health | `["health"]` |
| 10 | `EDUCATION` | 📚 | Education | `["education"]` |

> **`preferredTemplateId` removed from new entries** — this field is dead code (never read in ViewModel). Existing entries keep it for backwards compatibility but new entries should not propagate dead code. Consider removing from existing entries in a follow-up.

**Breaking changes from current categories:**
- `TRAVEL` drops `"events"` from `templateCategories` (was `["travel", "events"]`, becomes `["travel"]`)
- `TRAVEL` label: "Travel & Events" → "Travel"
- `HEALTH` icon: 💪 → ❤️ (Fitness takes the physical activity connotation)
- `HEALTH` label: "Health & Fitness" → "Health"
- New `EVENTS` category takes over `"events"` template category
- New categories require new template category strings in Remote Config: `"fitness"`, `"cooking"`, `"finance"`

**Grid card layout (Column, ~96dp min height):**

```
┌─────────────┐  ┌─────────────┐
│      ✈️      │  │      🛒      │
│    Travel    │  │   Shopping   │
└─────────────┘  └─────────────┘
┌─────────────┐  ┌─────────────┐
│      🏠      │  │      🍳      │
│     Home     │  │   Cooking    │
└─────────────┘  └─────────────┘
```

**Card specifications:**
- Emoji: 28sp, centered horizontally
- Title: `titleMedium` (16sp), centered, `TextAlign.Center`, `Modifier.fillMaxWidth()`
- Min height: **96dp** (handles up to 1.3x font scaling without clipping)
- Selection state: 2dp primary border, primaryContainer 15% alpha bg, **1.01f** scale (reduced from 1.02f to prevent overlap at 12dp gap)
- Grid spacing: `AppDimens.SpacingMd` (12dp) horizontal and vertical
- Title text: `maxLines = 2`, `overflow = TextOverflow.Ellipsis`
- Selected text color: `MaterialTheme.colorScheme.onSurface` (Gray900) — **not Blue700** (contrast issue on selected background)

**Grid implementation with FlowRow:**

```kotlin
import androidx.compose.foundation.layout.FlowRow

// Inside existing Column + verticalScroll:
FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    maxItemsInEachRow = 2
) {
    OnboardingCategory.entries.forEach { category ->
        CategoryCard(
            category = category,
            isSelected = category == selectedCategory,
            onClick = { onCategorySelected(category) },
            modifier = Modifier.weight(1f)  // equal width per column
        )
    }
}
```

### Step 3 — Auto-skip when single template

When `availableTemplates.size == 1` after filtering by category, skip TemplateSelection entirely and go directly to Customize.

**Auto-skip trigger location:** Inside `handleStyleSelected()`, after style is stored in state.

> **CRITICAL: Do NOT call `handleTemplateSelected()` from `handleStyleSelected()`.** This violates MVI single-responsibility (one intent → one state transition), causes **double `_screenState` emission** (AnimatedContent briefly flashes TemplateSelection), and **breaks existing tests** (`onStyleSelected_advancesToTemplateSelection` would fail for single-template categories).

**Correct implementation — single atomic state update:**

```kotlin
private fun handleStyleSelected(style: OrganizingStyle) {
    val state = _screenState.value
    val templates = state.availableTemplates

    if (templates.size == 1) {
        // Auto-skip: single atomic update, no handler chaining
        val template = templates.first()
        val items = applyStyleToItems(template.items, style, state.selectedCategory)
        _screenState.update {
            it.copy(
                selectedStyle = style,
                selectedTemplate = template,
                customizedItems = items.map { text -> CustomizableItem(text = text) },
                checklistName = template.name,
                currentStep = InteractiveOnboardingStep.Customize,
                wasTemplateStepSkipped = true
            )
        }
        trackStep("style_selected", "style" to style.name)
        trackStep("template_selected", "template" to template.id, "auto_skipped" to "true")
    } else {
        _screenState.update {
            it.copy(
                selectedStyle = style,
                currentStep = InteractiveOnboardingStep.TemplateSelection
            )
        }
        trackStep("style_selected", "style" to style.name)
    }
}
```

**New state field:**

```kotlin
// In InteractiveOnboardingState:
val wasTemplateStepSkipped: Boolean = false
```

> **Why explicit flag instead of `availableTemplates.size == 1`?** The implicit size check is fragile — template count can change if Remote Config updates mid-session, or if fallback produces 1 template for a category with 0 real templates. An explicit flag makes the intent clear, testable, and resilient to data changes.

**Back navigation when Step 3 was skipped:**

```kotlin
InteractiveOnboardingStep.Customize -> {
    val target = if (state.wasTemplateStepSkipped)
        InteractiveOnboardingStep.StyleSelection
    else
        InteractiveOnboardingStep.TemplateSelection
    _screenState.update {
        it.copy(
            currentStep = target,
            wasTemplateStepSkipped = false,
            selectedTemplate = null,
            customizedItems = emptyList(),
            checklistName = ""
        )
    }
}
```

**Progress bar:** Accepts the visual jump from 2/7 to 4/7 when Step 3 is skipped.

**Analytics:** Both `style_selected` and `template_selected` events fire for auto-skip path (preserves funnel data).

## Technical Considerations

### Architecture impacts

- `InteractiveOnboardingScreenContract.kt`: Extend `OnboardingCategory` enum with 4 new entries, add `wasTemplateStepSkipped` to state
- `InteractiveOnboardingViewModel.kt`: Modify `handleStyleSelected()` for auto-skip (single atomic update), modify `handleBack()` using explicit flag, refactor `getExtraItems()` to non-nullable parameter
- `CategorySelectionStep.kt`: Replace `Column { forEach }` with `FlowRow`, modify `CategoryCard` layout from Row to Column
- `strings.xml`: 4 new + 2 modified string resources
- `TemplatesRepositoryImpl.kt`: Add `categoryNames` entries

### FlowRow instead of LazyVerticalGrid

`FlowRow(maxItemsInEachRow = 2)` from `androidx.compose.foundation.layout` (available in commonMain since Compose Foundation 1.4 / CMP 1.6+):
- Works inside existing `Column + verticalScroll` — no nested scroll crash
- Items use `Modifier.weight(1f)` for equal column widths
- Available in Compose Multiplatform 1.9.x — no KMP issues
- Odd item count: last item fills half-width (add `Spacer(Modifier.weight(1f))` if needed)

### Existing modifier order bug (fix during this work)

**Current code in both `CategoryCard` and `StyleCard`:**
```kotlin
.graphicsLayer(scaleX = scale, scaleY = scale)  // BEFORE clip — BUG
.clip(shape)
.clickable(onClick = onClick)
```

**Problem:** `graphicsLayer` before `clip` means the scale animation renders outside the card's rounded-corner boundary. In the current full-width list this is invisible due to spacing, but in a dense grid it causes visible overlap.

**Fix:** Move `clip` before `graphicsLayer`:
```kotlin
.clip(shape)
.graphicsLayer(scaleX = scale, scaleY = scale)  // AFTER clip — correct
.clickable(onClick = onClick)
```

### Accessibility enhancements

Current cards only set `contentDescription`. Add proper TalkBack support:

```kotlin
.semantics(mergeDescendants = true) {
    contentDescription = categoryTitle
    stateDescription = if (isSelected) "Selected" else "Not selected"
    selected = isSelected
    role = Role.Tab  // single-select group
}
```

Add `Modifier.selectableGroup()` on the `FlowRow` parent to tell accessibility services these are mutually exclusive options.

### Double padding prevention

Parent Column has `padding(horizontal = AppDimens.ScreenPaddingHorizontal)`. `FlowRow` inherits this width. Grid cards use `Modifier.weight(1f)` — no additional horizontal padding on cards.

### System insets

`InteractiveOnboardingScreen` does NOT use `AppScaffold` — manually applies `statusBarsPadding()` and `navigationBarsPadding()`. These are on the root Column, not on step content. No changes needed here.

### `getExtraItems()` — refactor to non-nullable

**Current problem:** `getExtraItems(category: OnboardingCategory?)` — the nullable parameter means `when` has a `null ->` catch-all branch. Adding new enum values silently falls through to `null -> emptyList()` with **no compile error**. CHAOTIC becomes identical to DETAILED for new categories.

**Fix:** Refactor to `getExtraItems(category: OnboardingCategory)` (non-nullable). Add placeholder branches for new categories now, fill with real content later:

```kotlin
private fun getExtraItems(category: OnboardingCategory): List<String> = when (category) {
    OnboardingCategory.TRAVEL -> listOf(...)
    OnboardingCategory.HOME -> listOf(...)
    OnboardingCategory.SHOPPING -> listOf(...)
    OnboardingCategory.WORK -> listOf(...)
    OnboardingCategory.HEALTH -> listOf(...)
    OnboardingCategory.EDUCATION -> listOf(...)
    // New — placeholder, fill with real content in follow-up:
    OnboardingCategory.FITNESS -> emptyList()
    OnboardingCategory.COOKING -> emptyList()
    OnboardingCategory.FINANCE -> emptyList()
    OnboardingCategory.EVENTS -> emptyList()
}
```

Now adding a future enum entry without a branch causes a **compile error** — the `when` is exhaustive on a non-nullable enum.

### Remote Config dependency

New categories (Fitness, Cooking, Finance) require templates in Firebase Remote Config with matching `category` strings. Publish templates **before** releasing the app update.

### String escaping

Per documented learning: Compose Resources uses bare apostrophes (`Children's`, not `Children\'s`). Only `&` needs escaping (`&amp;`).

### Scrolling behavior

Screen height math on Pixel 9 (~429dp available, ~80dp for bars):
- Available for content: ~357dp
- Grid content (5 rows × 96dp + 4 gaps × 12dp + header ~100dp): ~628dp
- **Scrolling is expected and correct** — `verticalScroll` already handles this

## Acceptance Criteria

### Functional Requirements

- [x] Step 1 displays 10 categories in a 2-column grid
- [x] Each grid card shows emoji centered on top, title below
- [x] Selected card has primary border, tinted background, 1.01f scale animation
- [x] Tapping a category auto-advances to Step 2
- [x] `TRAVEL` no longer includes "events" templates
- [x] `EVENTS` category shows "events" templates
- [x] `FITNESS`, `COOKING`, `FINANCE` have proper `templateCategories` mapping
- [x] Step 3 is auto-skipped when exactly 1 template matches
- [x] Auto-skipped template is auto-selected and style is applied via single state update
- [x] `wasTemplateStepSkipped = true` in state after auto-skip
- [x] Back from Customize after auto-skip → goes to StyleSelection (not TemplateSelection)
- [x] Back from Customize normally → goes to TemplateSelection
- [x] Progress bar still renders (accepts jump for skipped step)
- [x] Analytics: both `style_selected` and `template_selected` events fire for auto-skip path

### Non-Functional Requirements

- [x] Grid cards meet 48dp minimum touch target (96dp height, ~158dp width)
- [ ] `contentDescription` + `stateDescription` + `selected` on all grid cards for TalkBack
- [ ] `selectableGroup()` on FlowRow for accessibility
- [x] No nested scrollable conflict (FlowRow inside verticalScroll = safe)
- [x] No double padding in grid layout
- [x] System insets (status bar, nav bar) properly handled
- [ ] Grid renders correctly on 360dp-wide screens
- [x] Modifier order fixed: `clip` before `graphicsLayer` on all selection cards

### Quality Gates

- [x] ViewModel unit tests written BEFORE implementation (testing mandate)
- [x] Project builds successfully (`./gradlew composeApp:assembleDebug`)
- [x] Existing unit tests pass (42 tests in InteractiveOnboardingViewModelTest)
- [x] New unit tests cover auto-skip and back navigation (5 tests added)
- [ ] Update `OnboardingFlowTest` E2E tests (4 tests) + `skipOnboardingAndGoToMain()` in `BaseUiTest`

## Implementation Plan

### Phase 1: Tests + data model (Contract + strings)

**Files:**
- `InteractiveOnboardingViewModelTest.kt` — new test cases
- `InteractiveOnboardingScreenContract.kt` — extend enum + add state field
- `strings.xml` — 4 new + 2 modified string resources
- `TemplatesRepositoryImpl.kt` — add new category names

**New test cases (write BEFORE code):**
- `handleStyleSelected_singleTemplate_autoSkipsToCustomize` — verifies `currentStep == Customize`, `wasTemplateStepSkipped == true`, `selectedTemplate != null`
- `handleStyleSelected_multipleTemplates_goesToTemplateSelection` — guards normal path
- `handleBack_fromCustomize_afterAutoSkip_returnsToStyleSelection` — uses 1-template fixture
- `handleBack_fromCustomize_normalPath_returnsToTemplateSelection` — guards normal back path
- `handleCategorySelected_newCategories_filtersCorrectly` — verifies template filtering for new categories

**State extension:**
```kotlin
data class InteractiveOnboardingState(
    // ... existing fields ...
    val wasTemplateStepSkipped: Boolean = false
)
```

**Enum extension (4 new entries, no `preferredTemplateId` on new entries):**
```kotlin
// Modified:
TRAVEL(Res.string.onboarding_interactive_category_travel, "✈️", listOf("travel"), "travel_packing"),
HEALTH(Res.string.onboarding_interactive_category_health, "❤️", listOf("health"), "doctor_visit"),

// New (preferredTemplateId = "" since it's dead code):
FITNESS(Res.string.onboarding_interactive_category_fitness, "🏋️", listOf("fitness"), ""),
COOKING(Res.string.onboarding_interactive_category_cooking, "🍳", listOf("cooking"), ""),
FINANCE(Res.string.onboarding_interactive_category_finance, "💰", listOf("finance"), ""),
EVENTS(Res.string.onboarding_interactive_category_events, "🎉", listOf("events"), ""),
```

**String resources (new):**
```xml
<string name="onboarding_interactive_category_fitness">Fitness</string>
<string name="onboarding_interactive_category_cooking">Cooking</string>
<string name="onboarding_interactive_category_finance">Finance</string>
<string name="onboarding_interactive_category_events">Events</string>
```

**String resources (modified):**
```xml
<string name="onboarding_interactive_category_travel">Travel</string>          <!-- was: Travel & Events -->
<string name="onboarding_interactive_category_health">Health</string>           <!-- was: Health & Fitness -->
```

**TemplatesRepositoryImpl.kt `categoryNames`:**
```kotlin
"fitness" to "Fitness",
"cooking" to "Cooking",
"finance" to "Finance"
```

### Phase 2: ViewModel logic (auto-skip + back nav)

**Files:**
- `InteractiveOnboardingViewModel.kt`

**Changes:**
1. `handleStyleSelected()` — single atomic state update for auto-skip path (see code in Proposed Solution)
2. `handleBack()` at `Customize` — use `state.wasTemplateStepSkipped` flag for routing
3. `getExtraItems()` — refactor parameter to non-nullable `OnboardingCategory`, add empty branches for new categories
4. Clear `wasTemplateStepSkipped = false` when navigating back past StyleSelection

### Phase 3: UI (grid layout + accessibility + modifier fix)

**Files:**
- `CategorySelectionStep.kt` — FlowRow grid + card layout change
- `StyleSelectionStep.kt` — fix modifier order bug (graphicsLayer/clip)

**Changes to CategorySelectionStep.kt:**
1. Replace `Column { entries.forEach { CategoryCard(...) } }` with `FlowRow(maxItemsInEachRow = 2)`
2. Modify `CategoryCard` layout from `Row` to `Column` (emoji on top, title below)
3. Add `Modifier.weight(1f)` on each card, `Modifier.heightIn(min = 96.dp)`
4. Fix modifier order: `clip` → `graphicsLayer` → `clickable`
5. Reduce scale animation from 1.02f to 1.01f
6. Add accessibility: `stateDescription`, `selected`, `role = Role.Tab`
7. Add `Modifier.selectableGroup()` on FlowRow
8. Selected text color: `onSurface` instead of Blue700
9. Title: `maxLines = 2`, `overflow = TextOverflow.Ellipsis`

**Changes to StyleSelectionStep.kt (modifier fix only):**
1. Fix modifier order: `clip` → `graphicsLayer` → `clickable`

**Post-Phase 3: Remote Config templates (not a code task):**
- Add templates with `category: "fitness"` to Remote Config
- Add templates with `category: "cooking"` to Remote Config
- Add templates with `category: "finance"` to Remote Config
- Ensure at least 1 template per new category (to avoid fallback)
- Ideally 2+ templates per category to show Step 3

## Dependencies & Risks

| Risk | Impact | Mitigation |
|---|---|---|
| New categories have 0 templates in Remote Config | Auto-skip with generic fallback | Publish templates before releasing the app update |
| TRAVEL loses "events" templates, may have only 1 template left | Auto-skip travel, fewer choices | Verify travel template count in Remote Config |
| Grid card text overflow on narrow screens | Visual bug | `maxLines = 2`, `overflow = TextOverflow.Ellipsis` |
| Scale animation overlap in grid | Visual glitch | Reduced to 1.01f, fix modifier order |
| Existing test `onStyleSelected_advancesToTemplateSelection` may break | Test failure | Update test fixture to use multi-template category |
| `FlowRow` requires Compose Foundation 1.4+ | Build failure on older CMP | Verified: project uses CMP 1.9.3, Foundation 1.4+ included |

## References

### Internal References
- `InteractiveOnboardingScreenContract.kt` — state, enums, models
- `InteractiveOnboardingViewModel.kt` — state machine, template filtering
- `CategorySelectionStep.kt` — current list layout to refactor
- `StyleSelectionStep.kt` — modifier order bug to fix
- `InteractiveOnboardingViewModelTest.kt` — 30 existing tests to extend
- `TemplatesRepositoryImpl.kt:24-34` — `categoryNames` map

### Institutional Learnings Applied
- `docs/solutions/ui/design-system-patterns.md` — double padding prevention, system insets for non-AppScaffold screens
- `docs/solutions/ui-improvements/compose-resources-string-escaping.md` — bare apostrophes in string resources
- `docs/solutions/ui-bugs/checklist-detail-overflow-menu-quick-add-bugs.md` — testing mandate: tests before code
- `docs/solutions/test-failures/e2e-test-suite-stabilization.md` — update OnboardingFlowTest + BaseUiTest
- `docs/solutions/architecture/mvi-pattern.md` — single atomic state update, navigation after state update

### External Research
- Todoist Templates (Popular category) — Travel, Meal Planning, Student Planning in top 8
- Checklist.com — Finance, Events, Fitness as separate navigation categories
- Any.do — Families, Events, Shopping as primary use cases
- Jetpack Compose LazyVerticalGrid docs — nested scroll conflict, GridItemSpan
- Compose Foundation FlowRow API — maxItemsInEachRow, weight
- Compose Multiplatform compatibility — FlowRow/LazyVerticalGrid KMP-safe in commonMain
