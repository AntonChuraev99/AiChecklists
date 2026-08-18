# Smart Add Local Parser — RU/EN Natural-Language Date/Time/Repeat Detection

**Дата:** 2026-05-13
**Категория:** feature
**Сложность:** Standard
**Impact:** Medium
**Project:** Checklists (Gisti AI Checklists)
**Active doc:** [`docs/active/smart-add-local-parser-2026-05-13.md`](../../active/smart-add-local-parser-2026-05-13.md)
**Parent brainstorm:** [`docs/brainstorms/2026-05-06-competitor-research-next-feature-brainstorm.md`](../../brainstorms/2026-05-06-competitor-research-next-feature-brainstorm.md) (Step 4a)
**Deferred Step 4b:** [`docs/todos/2026-05-13-ai-chat-assistant.md`](../../todos/2026-05-13-ai-chat-assistant.md) — AI Chat Assistant for semantic planning

---

## Problem

User types «купить молоко завтра 7 утра» into `AddItemInputField` on the checklist detail screen. The app should:

1. Detect RU/EN date/time/repeat phrases **locally** (no LLM, no network call, $0 credits cost).
2. Render an inline chip preview above the input field («📅 Завтра 07:00», «🔁 Ежедневно»).
3. On Add, populate `ChecklistFillItem.itemReminderAt / itemRepeatRule / itemRepeatTimeOfDayMinutes` and schedule the reminder via the existing `ReminderScheduler`.
4. The recognized phrase **remains in the item text** (user keeps their wording).

**Why not an AI flow:** parsing date/time/repeat from natural language is **syntax**, not semantics. Competitors (Todoist Quick Add, TickTick Smart Date, Microsoft To Do, Fantastical) all do this with regex + lexicons. Adding LLM would burn user credits silently on every input — violation of the «implicit + metered side-effect» pattern. Semantic planning («распланируй мне неделю», «создай чеклист для переезда») is a separate surface deferred to Step 4b with explicit credit consent.

## Solution

Three-phase delegation chain producing a domain-layer parser + design-system chip + ViewModel integration.

### Phase 1 — Domain parser (`feature/checklist`, commonMain)

Pure Kotlin in `feature/checklist/src/commonMain/.../domain/parser/`:

```
domain/parser/
├── SmartDateParser.kt              # public interface
├── SmartDateParserImpl.kt          # regex + lexicon implementation (~700 LOC)
├── model/
│   └── ParsedDateToken.kt          # data class + ChipDisplay sealed shape + enums
└── lexicon/
    ├── RuDateLexicon.kt            # RU phrase dictionary
    └── EnDateLexicon.kt            # EN phrase dictionary
```

Public API:

```kotlin
interface SmartDateParser {
    fun parse(input: String, now: Long, locale: ParserLocale): ParsedDateToken?
}

data class ParsedDateToken(
    val display: ChipDisplay,        // sealed: OneShot(dateLabel, timeLabel) | Repeat(rule, timeOfDayMinutes)
    val itemReminderAt: Long?,        // unix millis (one-shot OR next-fire for recurring)
    val itemRepeatRule: ReminderRepeatRule?,
    val itemRepeatTimeOfDayMinutes: Int?,
    val startIndex: Int,
    val endIndex: Int                  // [start, end) range for strip on add
)
```

Returns **single best match** (not a list) — reduces re-render churn.

### Phase 2 — Chip preview UI (`core/designsystem`)

`core/designsystem/.../components/TokenChipPreview.kt` — Material 3 `AssistChip` with `primaryContainer/onPrimaryContainer` palette. **Accepts flat `String` + `isRepeat: Boolean` only** — no knowledge of domain types (see «Cross-module dep direction» pattern below).

`core/designsystem/.../components/AddItemInputField.kt` — new optional slot `leadingPreview: (@Composable () -> Unit)? = null`. Backward-compatible: default `null` → old layout. Padding chip→input lives **inside** `AnimatedVisibility` content (`padding(bottom = SpacingMd)`) — otherwise `shrinkVertically` leaves a 12dp jump at the end of the collapse animation. Same pattern as Updates Feed `ReleaseCard`.

Localized strings: 22 new EN + 22 RU `smart_add_chip_*` keys in `strings.xml`; new `plurals.xml` files (EN: 2 forms × 4 groups, RU: 4 forms × 4 groups). RU requires `one/few/many/other` for "минут"/"часов"/"дней"/"недель"; without dedicated `plurals.xml` Compose Multiplatform can't resolve plural forms.

### Phase 3 — ViewModel + Screen integration (`feature/home`)

- **State** (`ChecklistDetailScreenContract.Content`): `pendingItemInput: String`, `parsedToken: ParsedDateToken?`, `smartAddHintActive: Boolean`.
- **Intents:** `OnItemInputChanged(text)`, `OnAddItemWithParse` (replaces old `OnAddItem` — single canonical intent with internal branching).
- **Parser observation:** `_pendingItemInput: MutableStateFlow<String>` + `@OptIn(FlowPreview::class).debounce(200).collect { parser.parse(...) }`. 200ms is the lower bound of «invisible to user» (IDE autocomplete threshold). Parser itself is <1ms.
- **On submit:** if `parsedToken != null` → strip the matched substring from text, set reminder fields on `ChecklistFillItem`, schedule via `ReminderScheduler` (alarm in androidMain behind repo interface, same boundary as per-item-reminders).
- **Stranded preposition detection:** if input has reminder phrase but stripped text is blank → emit `SNACKBAR_SMART_ADD_HINT_ADD_ITEM_TEXT`. If input ends with bare preposition («купить молоко в») → emit `SNACKBAR_SMART_ADD_HINT_ADD_TIME_AFTER_PREP`.
- **Dual-update:** `repo.updateFill()` + `repo.updateChecklistTemplate()` both called, sync with Edit screen via `observeChecklistById(id): Flow` pattern from 2026-05-12.

### ChipDisplayFormatter — feature-layer formatter

`feature/checklist/.../ui/smartadd/ChipDisplayFormatter.kt`:

```kotlin
@Composable
fun resolveChipLabel(display: ChipDisplay): String { /* stringResource lookups */ }
fun ChipDisplay.containsRepeat(): Boolean = this is ChipDisplay.Repeat
```

Lives on the feature side, not in `core:designsystem` — see «Cross-module dep direction» below.

## Architecture

### Cross-module dep direction (feature → core, NEVER reverse)

**The trap:** initial plan placed `resolveChipLabel(ChipDisplay): String` formatter in `core:designsystem`. This forces `core:designsystem → feature:checklist` (where the sealed class `ChipDisplay` lives) — **reverse direction**, breaks layered architecture, blocks reuse of design-system.

**The fix:** `TokenChipPreview` in `core:designsystem` accepts only flat primitives (`String` label, `Boolean isRepeat`). Domain-aware formatter `ChipDisplayFormatter` lives in `feature/checklist/.../ui/smartadd/`. Design-system stays generic-primitives layer; knowledge of domain models is feature-layer responsibility.

**Generalization:** this rule applies to all future preview-chips, status indicators, badges, banners that need to render domain state. Always pass primitives across the layer boundary; transform domain → primitive inside the feature.

### Past-time-today guard

`resolveTimeToday()` adjusts «сегодня в 7 утра» to «tomorrow at 07:00» if `now > 07:00 today`. Critical correctness rule — without it, user sets a reminder in the past, alarm never fires, looks like a bug. **Only applies** to TODAY and bare clock-time; not applied to TOMORROW+ where date offset already covers the case.

### Cyrillic-aware regex boundary

Standard `\b` works only for ASCII word characters → `\bзавтра\b` matches inside «завтракать». Solution: explicit lookaround `(?<![а-яёa-z\d])phrase(?![а-яёa-z\d])`. Applied to every lexicon entry.

### Manual ASCII fold (no java.util.Locale)

`commonMain` has no `java.util.Locale`. `String.lowercase()` without explicit locale picks up the default locale on JVM → Turkish-İ regression (`I.lowercase()` returns `ı` not `i` in Turkish locale). Manual fold:

```kotlin
private fun foldAscii(s: String): String = buildString {
    for (ch in s) append(if (ch in 'A'..'Z') (ch.code + 32).toChar() else ch)
}
```

Deterministic, KMP-safe, sufficient for ASCII-only lexicon keys (RU lexicon stays Cyrillic as-is — Russian doesn't have the same casing trap).

### Longer phrases first (lexicon sort order)

Lexicon sets sorted descending by length. Otherwise «завтра» captures the prefix of «послезавтра» and lexicon traversal falls through. Same logic in EN: «tomorrow» before «today», «day after tomorrow» before both.

### Bare-hour-after-preposition (added in hotfix)

Regex initially required clock format `H:MM` or am/pm suffix. «каждое воскресенье в 12» / «meeting at 9» — bare digit after preposition — fell through. Added sub-pattern `bareHourAfterPreposition` with lookahead-dictionary of prepositions (RU: `в|на`, EN: `at|on`) and explicit hour range `1..23`.

### endIndex overshoot bug (hotfix)

`tryParseRepeatWeekday()` computed `endIndex = prefixRange.last + 1 + (lower.length - afterPrefix.length) + matchedEntry.key.length` — double-added the prefix offset (already accounted for in `lower.length - afterPrefix.length`). Inputs like «каждое воскресенье в 12» clamped endIndex to the full input length → ViewModel strip produced blank text → silent-skip path swallowed the add. Fix: `(lower.length - afterPrefix.length) + matchedEntry.key.length`. The silent-skip-when-blank ViewModel path **hid** this bug for the full Phase 3 cycle — a textbook example of one bug masking another.

## Reusable patterns

### 1. Local rule-based NL parser before reaching for LLM

When the problem is **syntax** (fixed phrase patterns, finite vocabulary, deterministic mapping), regex + lexicons beat LLM on every metric: latency (<1ms vs 500-2000ms), cost ($0 vs metered), reliability (deterministic vs probabilistic), testability (unit tests vs eval suite).

When the problem is **semantics** (open-ended planning, content generation, novel composition), LLM is unavoidable. The Step 4a/4b split formalizes this — local parser for dates/times/repeats, deferred AI surface for «plan my week».

**Litmus test:** can you enumerate the input space with bounded effort? If yes → parser. If no → LLM (with explicit credit consent).

### 2. Cross-module dep direction — feature → core, never reverse

Design-system components accept **flat primitives only** (`String`, `Boolean`, `Int`, `Color`). Domain-aware formatting lives on the feature side. If a design-system component starts importing domain types from a feature module — refactor the API to pass primitives + move formatter out.

Applies to: preview chips, status indicators, badges, banners, empty-states with content-specific copy, action lists rendering domain enums.

### 3. Silent-skip → SideEffect emission

Any `if (x.isBlank()) return` or `if (condition) return` in a UI-handler without user feedback is a **bug**, not safe behavior. Replacement: emit a SideEffect (snackbar, toast, dialog). Silent ignoring of user action == regression — user doesn't understand what broke or what to do next.

Precedent stack:
- 2026-05-05 — Premium Limit Silent Failure (entitlement mismatch + Loading→Content race)
- 2026-05-11 — Silent emptyList() Anti-Pattern (templates disappeared)
- 2026-05-13 — Smart Add stranded-preposition silent skip (this work)

Third instance in 30 days on the same category → strong candidate for a global rule in `~/.claude/CLAUDE.md`.

### 4. Auto-dismiss snackbar on input-change with normalized comparison

User-action-invalidates-prior-feedback pattern. Snackbar emitted with `SnackbarDuration.Short` (4s) hides after timeout — too long for «correct your input» hints. Solution:

```kotlin
val pendingItemInput by viewModel.pendingItemInput.collectAsState()
val smartAddHintActive by viewModel.smartAddHintActive.collectAsState()
var lastNormalized by remember { mutableStateOf("") }

LaunchedEffect(pendingItemInput) {
    val current = normalizeForHintComparison(pendingItemInput)
    if (smartAddHintActive && current != lastNormalized) {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
    lastNormalized = current
}

private fun normalizeForHintComparison(s: String) =
    s.trim().replace(Regex("\\s+"), " ")
```

Normalization (trim + collapse whitespace) prevents accidental dismiss on trailing-space edits while user is still typing. Generalizable to any feedback-on-input scenario (debounced search, validation messages, autocomplete hints).

### 5. Snackbar imePadding for Android 15+ edge-to-edge

`SnackbarHost` in `Scaffold` defaults to `WindowInsets.systemBars` padding, but **not** IME insets. On Android 15+ with predictive IME animation, snackbar renders **under** the keyboard when input is focused. Always add `Modifier.imePadding()` to `SnackbarHost` if the snackbar can appear during text input:

```kotlin
Scaffold(
    snackbarHost = {
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.imePadding()
        )
    },
    // ...
)
```

## Files touched

```
M  core/designsystem/src/commonMain/composeResources/values-ru/strings.xml       (+29)
M  core/designsystem/src/commonMain/composeResources/values/strings.xml          (+30)
M  core/designsystem/.../components/AddItemInputField.kt                         (leadingPreview slot)
+  core/designsystem/src/commonMain/composeResources/values-ru/plurals.xml       (NEW)
+  core/designsystem/src/commonMain/composeResources/values/plurals.xml          (NEW)
+  core/designsystem/.../components/TokenChipPreview.kt                          (NEW)
M  feature/checklist/.../di/ChecklistFeatureModule.kt                            (+3)
+  feature/checklist/.../domain/parser/SmartDateParser.kt                        (NEW)
+  feature/checklist/.../domain/parser/SmartDateParserImpl.kt                    (NEW, ~700 LOC)
+  feature/checklist/.../domain/parser/lexicon/RuDateLexicon.kt                  (NEW)
+  feature/checklist/.../domain/parser/lexicon/EnDateLexicon.kt                  (NEW)
+  feature/checklist/.../domain/parser/model/ParsedDateToken.kt                  (NEW)
+  feature/checklist/.../ui/smartadd/ChipDisplayFormatter.kt                     (NEW)
+  feature/checklist/src/commonTest/.../domain/parser/SmartDateParserTest.kt     (NEW, 42 tests)
M  feature/home/.../di/HomeFeatureModule.kt                                      (+1)
M  feature/home/.../detail/ChecklistDetailScreenContract.kt                      (+14)
M  feature/home/.../detail/ChecklistDetailViewModel.kt                           (+189)
M  feature/home/.../detail/ChecklistDetailScreen.kt                              (+164)
M  feature/home/src/commonTest/.../detail/<6 test files>                         (FakeSmartDateParser, intent rename)
+  feature/home/src/commonTest/.../detail/ChecklistDetailSmartAddTest.kt         (NEW, 12 tests)
```

## Test count breakdown

| Layer | File | Tests |
|---|---|---|
| Parser | `SmartDateParserTest.kt` | 42 (10 RU happy + 10 EN happy + 5 false-positive + 5 edge + 5 bare-hour-prep + 5 endIndex + 2 misc) |
| ViewModel | `ChecklistDetailSmartAddTest.kt` | 12 (parse-update, strip+schedule, plain-add backward-compat, double-fire guard, dismiss-token, dual-update, snackbar emit ×2 hints, endIndex fixture) |
| Regression | 6 existing detail test files | Updated with `FakeSmartDateParser` injection + intent rename |
| **Total new** | — | **54 new tests** |

All tests green on `compileDebugKotlin` + `testAndroidHostTest`.

## Iterations (8 total, see active doc for full log)

1. @kotlin-expert — Phase 1 parser logic + 35 tests
2. @mobile-design-expert — Phase 2 chip + slot + strings + plurals
3. @android-expert — Phase 3 ViewModel + Screen wiring + tests
4. main agent — Hotfix bare-hour-after-preposition + 5 new tests
5. @android-expert — Hotfix endIndex overshoot + snackbar SideEffect + 12 ViewModel tests
6. main agent — Hotfix snackbar `imePadding` (Android 15 IME overlap)
7. main agent — Hotfix auto-dismiss snackbar on input change
8. main agent — Hotfix whitespace-aware dismissal (normalized snapshot compare)

## Keywords

`smart-add-local-parser` `nl-parser` `kmp` `commonMain` `regex` `lexicon` `ru` `en` `bilingual` `chip-preview` `assistchip` `material3` `snackbar-feedback` `imePadding` `silent-skip-antipattern` `cross-module-dep-direction` `feature-to-core` `cyrillic-word-boundary` `lookaround-regex` `ascii-fold` `turkish-i-trap` `past-time-guard` `debounce-200ms` `dual-update` `addiltemtputfield` `reminder-scheduler` `paritem-reminders` `zero-ai-cost` `todoist-quick-add` `ticktick-smart-date` `endindex-bug` `bare-hour-preposition` `auto-dismiss-snackbar` `whitespace-aware-comparison`
