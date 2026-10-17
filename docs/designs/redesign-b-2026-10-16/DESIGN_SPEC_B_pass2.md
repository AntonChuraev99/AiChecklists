# DESIGN_SPEC — Redesign B, pass 2: "Add a task" capture sheet + AI chat

- **Platform:** Android + Web (Compose Multiplatform, wasmJs). Material 3. One UI for both.
- **Method:** claude-design. The owner canvas is a single HTML file the main agent publishes (local file `redesign-b-pass2-canvas.html` in the session scratchpad). Base system: `docs/designs/redesign-b-2026-10-16/DESIGN_SPEC_B.md` + `docs/designs/redesign-b-2026-10-16/DESIGN_SPEC_B_owner_decisions.md`. This file is a **delta**. Anything not named here stays as B pass 1.
- **Abbreviations:** DS = `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/`, HOME = `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/`, CHAT = `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/`.
- **Recommended:** Dock **B "Composer card"** + Chat **B "Actions inside the turn"**. Variants A and C are below for comparison, each as a short delta.

---

## 0. Tokens

**No new colour, type or shape tokens.** Everything maps to existing B roles:

| Use | Token |
|---|---|
| Composer card fill | `AppSurface.card()` (`#FFFDF9` / `#221F1B`) |
| Chips inside the card, planner cells, step numbers | `AppSurface.recessed()` (`#EFE9DF` / `#2D2924`) |
| Applied / selected chip | `colorScheme.primaryContainer` / `onPrimaryContainer`, icon `primary` |
| Source pills on the chrome | `AppSurface.bottomChromeRaised()` (`#FFFDF9` / `#2D2924`), no border |
| Type tiles, source icon circles | `GistiColors.paletteTint(i)` / `paletteContent(i)`. 0 green · 1 blue · 2 peach · 3 lilac |
| Destructive tile / step number | `colorScheme.errorContainer` / `colorScheme.error` |
| Card hairline (not focused) | 1dp `colorScheme.outline` (= `AppChatColors.controlOutline()`) |
| Card focus ring | 2dp `colorScheme.primary` |
| Inner dividers | 1dp `colorScheme.outlineVariant` (= `AppChatColors.contentOutline()`) |
| Card / group shape | `AppShapeTokens.Group` (24dp) |
| Chip shape | `AppShapeTokens.Pill` |
| Plate | `gistiDockColor()` / `AppSurface.bottomChrome()` + `AppShapeTokens.SheetTop`. **Unchanged** |

Type: text in the field `bodyLarge` · chips `labelLarge` (lead chip `FontWeight.SemiBold`) · source label `labelLarge` · sources heading `labelMedium` `onSurfaceVariant` · prompt header in chat `titleMedium` (16/24, Medium) · object rows and steps `bodyMedium` · options rows `bodyLarge` + meta `bodySmall` `onSurfaceVariant`. Spacing only from `AppDimens` (4/8/12/16/24) and `AppDensity`.

**Dark-theme rule (found on the canvas):** inside the composer card the fill is `AppSurface.card()`, **not** `bottomChromeRaised()`. In dark, `bottomChromeRaised()` = `surfaceContainerHigh` = `recessed()`, so recessed chips on a raised card disappear (same value). Card `#221F1B` + chip `#2D2924` is the same pair the Inbox rows use.

---

## 1. Surface 1 — "Add a task" capture sheet (`QuickCaptureDock`)

Hosts (all change together, because they share one component): Inbox `HOME/inbox/InboxScreen.kt:475`, detail and project `HOME/detail/ChecklistDetailScreen.kt:4745` (`DetailCapturePlate`), Calendar `HOME/calendar/CalendarScreen.kt:400`.

### 1.1 Variant B — Composer card (recommended)

```
QuickCaptureDock  Surface(gistiDockColor, SheetTop)            ← unchanged plate, seam divider unchanged
└ Column
   ├ ComposerCard  Surface(AppSurface.card(), Group 24, border 1dp outline | 2dp primary when field focused)
   │   padding: outer inset start/end 12 (SpacingMd), top 12. Clip to Group.
   │   ├ [attachments slot]  Row, padding 12/12/0, spacedBy 8           ← detail only (staged files)
   │   ├ Text row  Row(CenterVertically), min height 52, end padding 4
   │   │   ├ AddItemInputField(style = Embedded)  weight 1, text start 16, vertical 12, bodyLarge, max 4 lines then scroll
   │   │   │     highlightRange unchanged (primaryContainer behind the phrase)
   │   │   ├ trailingToggle   (Inbox/Calendar: ImportantStarToggle · detail: attach IconButton)   48 target
   │   │   └ "+" disc 40 visual / 48 target (existing TrailingActionVisual/Target)
   │   ├ HorizontalDivider 1dp outlineVariant, inset start 16 / end 16
   │   └ [attributes slot]  min height 48, horizontal padding 8
   │        Inbox/Calendar: DueRailSection(horizontalPadding = 8.dp)   (rail + DuePlannerPanel)
   │        Detail:          ItemCreateChipsRow(horizontalPadding = 8.dp)  (same chip set as today)
   ├ [belowInput slot]  padding start/end 12, top 8                          ← Inbox/Calendar only
   │   SourceRowSection: heading labelMedium onSurfaceVariant, padding 4/2/4/6; SourcePillGrid (rungs unchanged)
   └ bottom spacer 6 (with sources) / 12 (without)
```

**Components**

| Element | Project component | Change | States |
|---|---|---|---|
| Plate | `QuickCaptureDock` (`DS/components/QuickCaptureDock.kt:210`) | New slots `attributes` and `attachments` rendered INSIDE the card; `aboveInput` stays (outside, above the card) for compatibility and becomes unused by the three hosts | — |
| Field | `AddItemInputField` (`DS/components/AddItemInputField.kt:108`) | New param `style: AddItemFieldStyle = Outlined`; `Embedded` = no OutlinedTextField ring (all border colours `Transparent`), no own background, reports focus via `onFocusChanged` to the card. **`Outlined` stays byte-identical** for its 4 page-plane callers (Analyze preview, Template preview, Create checklist) | empty / text / highlight |
| "+" | inside `AddItemInputField` | unchanged (40/48, `primary` when text, `surfaceVariant` when blank) | enabled / disabled |
| Lead chip "When?" | `DueLeadChip` (`DS/components/DueRail.kt:471`) | min height 32 visual in 48 target, Pill, padding start 10 / end 8. No date: `recessed` + `Icons.Outlined.Event` 18 `onSurfaceVariant` + label + `ExpandMore`/`ExpandLess` 18. Has date: `primaryContainer` + `Icons.Outlined.Alarm` 18 `primary` + label `onPrimaryContainer` + clear `Close` 18 (clear keeps its own 48 target and its existing a11y label) | none / date / repeat / expanded |
| Preset chips | `DuePresetPill` (`DueRail.kt:575`) | `recessed` fill, **no border**, label `onSurface`, padding 12 | default / pressed |
| Rail row | `DueRailRow` (`DueRail.kt:250`) | horizontal padding param = 8 inside the card; `scrollableEdgeFade` fades to `AppSurface.card()` (was the chrome) | scroll |
| Planner | `DuePlannerPanel` / `DuePlannerCell` / `DuePlannerSettingsCard` (`DueRail.kt:651…969`) | renders inside the card under the rail. Cells: `recessed` fill (was raised on chrome), same corner rule (`duePlannerCellShape`), selected = `primaryContainer` + 2dp `primary` ring. Settings card: **no own container** — rows sit on the card, top hairline `outlineVariant` full card width, inset divider between Time and Repeat at 48. "Done" text button end, padding 12 | collapsed / expanded |
| Detail chips | `ItemCreateChipsRow` (`ChecklistDetailScreen.kt:4570`) → `GistiSelectableChipRow` (`DS/components/gisti/GistiPromptChips.kt:443`) | chip look as presets: `recessed`, no border; selected = `primaryContainer` + `Check` 16 (replaces the leading bell). Add `style` param to `GistiSelectableChipRow` so `TaskCreateChipsRow` keeps today's look unless it opts in | default / selected |
| Staged attachments | `ItemCreateAttachmentStrip` (`ChecklistDetailScreen.kt:4651`; second caller `:1923` keeps today's look — add a `tileSize` param, default 64) | moved into the card's `attachments` slot, tiles 56 (was 64), corner 14, remove button: 24 visual `onSurface` circle with `surface` × 16, **48 target** (today 20dp, fails 48) | 0 / 1..n |
| Source pills | `SourcePill` (`DS/components/SourceRow.kt:401`) | fill `bottomChromeRaised`, **no border**, min height 44 visual / 48 target, leading 24dp circle `paletteTint(i)` with 14dp icon `paletteContent(i)`: Photo 2 · PDF 3 · Link 1 · Voice 0. `SourcePillGrid` rungs (`:230`, `sourcePillRungs` `:337`) unchanged | default / pressed |
| Sources heading | `SourceRowSection` (`SourceRow.kt:93`) | `labelMedium` (was larger), `onSurfaceVariant` | — |

**States (what the user sees)**

| State | Visible |
|---|---|
| Opened, empty | card with 2dp `primary` ring, placeholder, star (Inbox) or attach (detail), disabled "+"; rail "When? ⌄ · Tonight · Tomorrow · Weekend…"; sources row under the card; keyboard up |
| Text + recognised date | phrase tinted in the text; lead chip "Tomorrow 09:00" with a close icon on `primaryContainer`; preset list drops nothing (a parsed phrase is not an applied preset); "+" enabled |
| Detail: chips + attachments | thumbnails at the top of the card, text, attach + "+", chips row under the divider; no sources row (as today) |
| Due rail expanded | card grows upward: text row, rail (applied preset removed from the rail), grid, Time/Repeat rows, Done; sources folded (`CollapsibleSourceRow`, unchanged); **keyboard hidden** (see 1.4) |
| Loading / error | none of its own: the dock is a local draft. Add failure is the host's snackbar, unchanged |
| Disabled | "+" disabled while blank (unchanged); the whole dock is not composed when there is no target page (Inbox `captureDockRenders`, unchanged) |

**Height:** with both rows at 360dp / fontScale 1.0 the plate is ≈ 200dp (canvas), same as today's measured 201–213dp. The card adds 4dp of ring and saves the rail's separate 48dp row gap.

### 1.2 Variant A — Soft plate (delta to today, no new slots)

Slot order unchanged (rail → field → sources). Only restyle: preset/lead chips `bottomChromeRaised` (cream) without border, lead chip with the Event icon; field `Outlined` style gets min height 56, container `bottomChromeRaised`, focused ring 2dp `primary`; source pills as in B; planner cells `bottomChromeRaised`, settings card `bottomChromeRaised` + Group 24. Touches `DueRail.kt`, `SourceRow.kt`, the `QuickCaptureDock` field padding. Cheapest; does not echo the grouped-container look.

### 1.3 Variant C — Warm sheet with destination (not recommended)

Plate fill `colorScheme.surfaceContainerLow` instead of the chrome, header row "New task" + read-only destination chip (dot + page/checklist title) + close, composer card as B, sources as a 4-column tile group (40dp tile + label under, card fill, Group 24). **Reopens** the frozen chrome tone (spec B §0 "Not reopened") and needs 2 new strings (`capture_sheet_title` (не проверено — proposed for variant C, never created), destination is data). +56dp height.

### 1.4 Behaviour proposal (all variants)

When the planner expands, clear focus (keyboard down); on Done / collapse, request focus again. Without it, planner + keyboard overflow a 360×800 window (true today too: the `DueRailScreenshotTest` expanded goldens are taller than the space left above a ~260dp keyboard (приблизительно)). UI-local (focus manager) → `@compose-expert`. A device check decides whether the IME already closes on its own.

### 1.5 Function preservation — capture sheet

| # | Function today | Where in B |
|---|---|---|
| Q1 | Text input, multiline, IME Done adds | `AddItemInputField(Embedded)`, same keyboard actions |
| Q2 | "+" add, enabled only with text; stays open after add | unchanged |
| Q3 | Autofocus + keyboard on open (`LaunchedEffect`) | unchanged; card ring shows focus |
| Q4 | Smart-Add phrase highlight (`highlightRange`) | unchanged |
| Q5 | Lead chip: no date / date / repeat label, clear × with repeat-aware a11y label | `DueLeadChip` restyled, same callbacks and labels |
| Q6 | 3 rail presets, applied preset dropped from the rail, scrolls with edge fade | unchanged logic (`DueRailSection`), fade colour changes |
| Q7 | Planner: 5 presets + Pick date, Time, Repeat, Done; sheets via `DraftDueSheetHost` | same component inside the card |
| Q8 | Important star toggle in the input row (owner decision 2026-09-03) | unchanged seat (text row trailing) |
| Q9 | Sources: Photo / PDF / Link / Voice + heading; fold while planner open; rung grid at large font | unchanged logic, restyled pills |
| Q10 | Detail: reminder chips (1h / Tomorrow morning / Tonight / Pick time / Important / Repeat), token preview chip, attach, staged attachments with remove | same `ItemCreateChipsRow` + strip, inside the card |
| Q11 | BACK / scrim tap closes, draft kept (`OnItemCreateDismissed`, iteration 8) | unchanged |
| Q12 | Lift over the keyboard (bottomBar slot), scrim shoulder colour + dock-top measurement in `modifier` | unchanged: card is a child of the same plate |
| Q13 | Nav bar hidden while open (`captureOpen`) | unchanged |
| Q14 | Calendar host: preset pre-applied ("Tonight") shown in lead chip | unchanged |

### 1.6 State needs (`@feature-expert`)

None for variant B as specified. Optional, owner decision O1 (§4): unify the detail chips with the Inbox rail — map `ChecklistDetailState` item-create fields (`itemCreateReminderPreset`, `itemCreateReminderAt`, `itemCreateRepeat`, `itemCreateImportant`, `parsedToken`) onto `TaskDraft` + `DraftDueUiState`, add `DraftDueIntent` handling and a `DraftDueSheetHost` to the detail screen, then the detail host passes `DueRailSection` + `ImportantStarToggle` + attach. Preset sets differ today (detail has "Tomorrow morning", Inbox "Tomorrow"), so this is a product choice, not a restyle.

---

## 2. Surface 2 — AI chat, pass 2

Files: `CHAT/components/AiChoiceResponse.kt`, `AiChoiceChip.kt`, `ChatMessageBubble.kt` (post-action chips), `CHAT/ChatEmptyState.kt`, `ChatInputRow.kt`. Both planes: everything resolves through `AppChatColors` (page = ChatScreen, bottom chrome = App.kt dock and full overlay). No new behaviour: **tap = execute** stays.

### 2.1 Variant B — Actions inside the turn (recommended)

**Proposal turn (`AiChoiceResponse`, non-blank prompt):**

```
Row(Bottom): AssistantAvatarSlot(28) · gap 8 · Bubble (AssistantBubbleShape 20/20/20/8, AppChatColors.raised(), 1dp contentOutline, max width 300, clip)
└ Column
   ├ Header Row(CenterVertically) padding 14/12/14/4, gap 10:  type tile 28 (circle, paletteTint / errorContainer, 16dp filled icon) + prompt (ChatMarkdownText, titleMedium-weight 500, onSurface)
   ├ ObjectRows (unchanged anatomy) padding 14/6/14/4
   ├ BatchActionList (restyled, below) padding 14/8/14/6
   ├ MemoryRow (which-list only, restyled, below)
   ├ HorizontalDivider 1dp contentOutline, full bubble width, top 8
   └ Actions  padding 12/10/12/12
        • short options → FlowRow spacedBy 8 (h) / 4 (v): Primary chip, Default chips, then Escape chip LAST in the same flow
        • options with meta (which-list / move target) → OptionRows list, then Escape chip under it (padding top 0)
        • editing → EditField + Save chip (unchanged)
```

- **Type tile** (decorative, `contentDescription = null`). Mapping by the primary option's action / content:
  create checklist → `paletteTint(0)` + `Icons.Outlined.Checklist` · add items → `paletteTint(1)` + `Icons.Outlined.AddTask` · reminder → `paletteTint(2)` + `Icons.Outlined.Alarm` · batch → `paletteTint(3)` + `Icons.AutoMirrored.Outlined.PlaylistAddCheck` · which list / move → `paletteTint(1)` + `Icons.AutoMirrored.Outlined.FormatListBulleted` · destructive-only (delete) → `errorContainer` + `Icons.Outlined.Delete` in `error`. Unknown kind → no tile, header = prompt only. Needs a kind from state (§2.5).
- **Chips (`AiChoiceChip`)**, min height 40 visual in 48 target, Pill, label `labelLarge` SemiBold, padding 16 (Escape 12):
  - Primary: `primary` / `onPrimary` (unchanged). Max one per block.
  - **Default: `AppSurface.recessed()` fill / `onSurface` label, leading icon `primary`** (was `primaryContainer`). On the chrome plane: `AppChatColors` fill for chips must stay visible on `bottomChromeRaised` bubbles → use `recessed()` in both (light `#EFE9DF` on `#FFFDF9`; dark `#2D2924` on the dark bubble `#2D2924`… **collision**: in dark on the chrome plane the bubble is `bottomChromeRaised` = `surfaceContainerHigh`, so a Default chip there takes `surfaceContainerHighest`). Implement as `AppChatColors.chipFill()` = page plane `recessed()`, chrome plane dark `surfaceContainerHighest`, chrome plane light `recessed()`.
  - Destructive: `error` / `onError` + delete icon (unchanged).
  - **Escape: no fill, no border, label `onSurfaceVariant`** (was outlined, own row). Lives in the same flow.
  - Add: unchanged (dashed outline).
  - Executing: unchanged (spinner on the chosen chip, others 38% — источник: `AiChoiceChip.kt` `dimAlpha` 0.38f).
- **OptionRows** (new private composable in `AiChoiceResponse.kt`, replaces `ChoiceChips` when any option has `meta != null`): rows min 48, padding start 16 / end 14, gap 12: leading 10dp dot `GistiColors.avatarContent(option.seedId)` (fallback when no seed: `Icons.AutoMirrored.Outlined.FormatListBulleted` 18 `onSurfaceVariant`), label `bodyLarge` `onSurface` max 2 lines Ellipsis, meta trailing `bodySmall` `onSurfaceVariant` (meta never dropped). Inset divider between rows at start 38. Each row `Modifier.clickable(role = Role.Button, onClickLabel = label)`; executing row swaps the dot for an 18dp `CircularProgressIndicator`, others dimmed to the same `DisabledAlpha` as chips. Collapse-to-sent pill keeps `label` only (unchanged rule).
- **MemoryRow**: `AppSwitch` (DS) trailing, label `bodyMedium` `onSurfaceVariant` leading, row min 48, padding 14, whole row `toggleable(role = Role.Switch)`, switch `onCheckedChange = null`. Never pre-checked (unchanged). Inside the bubble, above the divider.
- **BatchActionList**: each step Row gap 10: 20dp circle `recessed()` with the index `labelSmall` Bold `onSurfaceVariant` (single step: no circle, as today's "no number for one"), text `bodyMedium` `onSurface`. Destructive step: circle `errorContainer` with `Icons.Outlined.Delete` 14 `error`, text `error` (icon + colour, never colour alone). Never truncated (unchanged).
- **Post-action turn** (`ChatMessageBubble` action chips, pass 1 §2.9): same divider + Actions area inside the bubble. Undo = Escape style with `Icons.AutoMirrored.Outlined.Undo`; "Move to another list" / "Open checklist" / "Ask AI" / "Retry" = Default; "Become Pro" = Primary.

**Empty state (`ChatEmptyStateFull`, `CHAT/ChatEmptyState.kt:135`)** — compact (dock) variant unchanged:

```
Column(fillMaxSize, verticalArrangement = Bottom, verticalScroll), padding horizontal 16, bottom 12, max width 480 (as today)
├ SparkleTile 48 circle (gradient)          ← hidden when fontScale ≥ 1.3
├ title  chat_empty_title     headlineSmall Bold onSurface, start-aligned, top 12, heading()
├ subtitle chat_empty_subtitle bodyMedium onSurfaceVariant, top 4
└ group (top 16): 4 rows, each Modifier.groupSegment(groupPositionOf(i, 4))   ← one anatomy with the Inbox
     row min 56, padding 12, gap 12: 32dp tile paletteTint/Content (create 0 Checklist · attachment 3 AttachFile · reminder 2 Notifications · ask 1 ChatBubbleOutline, 18dp),
     text bodyMedium onSurface max 2 lines, trailing NorthEast 18 onSurfaceVariant (decorative). Tap = onPrefill (unchanged).
```

**Input (`ChatInputRow`, Credits leading, pass 1 §2.10):** container shape becomes `Pill` at one line and keeps 28dp corners when it grows; border 1dp `outline` → **2dp `primary` while focused**, same rule as the composer card. Placeholder `maxLines = 2`, no Ellipsis (RU "Сообщение Gisti" wraps at 320dp, recorded open item of iteration 10). Credits chip, attach, mic, send: unchanged.

**States**

| State | Visible |
|---|---|
| Empty | greeting + 4-row group above the input, credits chip in the field |
| Loading (processing) | typing bubble with avatar (unchanged) |
| Pending choice | one bubble: tile + question, object, divider, actions |
| Executing | chosen chip / row spinner, others dimmed (unchanged, `AiChoiceChip.kt` `dimAlpha`) |
| Success | answer bubble, action chips inside, feedback row on latest (unchanged) |
| Error / out of credits | answer bubble with Retry (Default) / Become Pro (Primary) inside (unchanged routing via `emitInsufficientCredits`) |
| Editing | edit field + Save inside the actions area (unchanged) |

### 2.2 Variant A — Polish in place (delta)

Actions stay under the bubble (`padding(start = ChatAvatarLayout.Slot)`). Default chips → `recessed`; Escape → ghost chip, same FlowRow; MemoryRow → `AppSwitch` row outside the bubble; empty state as B; input unchanged. No type tiles, no OptionRows.

### 2.3 Variant C — Full-width widgets (not recommended)

Code-placed proposals render as full-width cards (no avatar indent): eyebrow (24dp tile + type label `labelMedium` Bold `onSurfaceVariant`), body, footer of two equal text buttons split by a 1dp vertical divider (Cancel | primary action in `primary`). Needs new strings for every eyebrow label, turns chips into dialog buttons (reverses the July "chips, not a form" decision) and splits the visual language between bubble and widget.

### 2.4 Function preservation — chat

| # | Function today | Where in B |
|---|---|---|
| C1 | Tap a chip executes immediately (D1 contract) | unchanged, chips + option rows |
| C2 | One Primary per block, Destructive with icon, Escape, Add | same roles, Escape restyled and moved into the flow |
| C3 | Object rows with emphasis, preview cap 6 (screen) / 3 (dock) + "…and N more" | unchanged |
| C4 | Batch: full list never truncated, destructive lines marked | unchanged logic, numbered circles |
| C5 | Which-list: meta disambiguates same names; sent pill shows label only | OptionRows keep meta; collapse unchanged |
| C6 | Remember my choice: toggle not chip, never pre-checked, row target | `AppSwitch` row, same semantics role change Checkbox → Switch |
| C7 | Inline edit + Save | unchanged |
| C8 | Executing spinner + dim | unchanged |
| C9 | Avatar on the last reply of a run, no label (pass 1) | unchanged; the pending block carries the avatar |
| C10 | Post-action Undo / Move / Open checklist / Retry / Become Pro | inside the bubble, same callbacks |
| C11 | Empty state: 4 prefill suggestions, crossfade to the list on first send | same strings, same `onPrefill`, group look |
| C12 | Credits chip → pricing sheet → Get more | unchanged |
| C13 | Attach, hold-to-record, transcribing, send | unchanged |
| C14 | Dock (App.kt) compact surfaces | same components on the chrome plane; `compact = true` caps unchanged |

### 2.5 State needs (`@feature-expert`)

| Need | Today | Ask |
|---|---|---|
| Type of a proposal for the header tile | not explicit; derivable from `ChatChoice.options` actions, `objectRows` kinds, `batchItems` | add `ChatChoice.kind: ChoiceKind?` (`CreateChecklist, AddItems, Reminder, Batch, PickList, Move, Delete`) filled where the choice is built; null → no tile |
| Project colour dot on OptionRows | `ChoiceOption(id, label, meta, role)` | add `seedId: Long?` (checklist id) for destination options; null → list icon |
| Everything else | — | none |

---

## 3. Risks (320dp / fontScale 1.5 / RU / HI)

Measured on the canvas at 320dp × fontScale 1.5 (`check-narrow.png`, see Проверка):

1. **Source pills at 320dp with fontScale 1.5 truncate to "P…"** when forced four across. Keep `SourcePillGrid` rungs (2×2 / single column). Do not put the 4 pills in a fixed `Row`.
2. **Planner cells at 320dp with fontScale 1.5 overflow** ("Tomorrow" wider than a third of the card, which is 8dp narrower than today's full-width panel). Rule: `columns = if (innerWidth < 3 × 96.dp × fontScale) 2 else 3` (`DuePlannerColumns` becomes computed in `BoxWithConstraints`). 2 columns → 3 rows, keyboard is hidden in this state (§1.4).
3. **Placeholder / short text wraps to 2 lines** ("Добавить задачу" at 1.5): allowed. Text row is `heightIn(min = 52)`, never `height`.
4. **Chat empty at RU × 1.5:** headline 2 lines, group needs scroll. Body is `verticalScroll`, avatar hidden at ≥ 1.3.
5. **Chat header subtitle wraps to 2 lines at 1.5** (pre-existing, `ChatHeader`): `maxLines = 1` + Ellipsis. Not part of this pass, noted.
6. **HI:** chip and row labels use `heightIn` + line boxes from `labelLarge` / `bodyLarge`; header prompt is never uppercased. Option-row labels max 2 lines.
7. **Long prompt in the header row** wraps beside the 28dp tile; tile aligns `Top` with 2dp offset when the prompt is > 1 line.
8. **9999 credits:** chip grows, placeholder wraps (2 lines).
9. **Dark, chrome plane chips:** the `chipFill()` rule in §2.1. A wrong fill = invisible Default chips in the dock.

---

## 4. Handoff

- **Target:** `@compose-expert` for everything in §1 B and §2 B. `@feature-expert` only for §2.5 (two optional fields) and, if the owner says yes to O1, §1.6.
- **Order:**
  1. DS: `AddItemInputField(style)`, `QuickCaptureDock` slots, `DueRail.kt` chip/cell/settings restyle + computed columns, `SourcePill` restyle, `GistiSelectableChipRow(style)`. Goldens: re-record `SourceRowScreenshotTest`, `DueRailScreenshotTest`, `QuickCaptureDockRowsTest`, add composer frames (empty / text+date / expanded) at 320 / 360 / 412, light + dark, fontScale 1.0 / 1.5, EN / RU / HI.
  2. Hosts: Inbox + Calendar pass `DueRailSection` to `attributes`; detail passes `ItemCreateChipsRow` to `attributes` and the attachment strip to `attachments`. Re-record the active-plate frames of `ChecklistDetailRedesignScreenshotTest` and the `captureOpen` frames of `V2ShellBottomBarScreenshotTest`.
  3. Chat: `AiChoiceChip` colours + `AppChatColors.chipFill()`, `AiChoiceResponse` header / divider / actions / OptionRows / MemoryRow, `ChatMessageBubble` action area, `ChatEmptyStateFull`, `ChatInputRow` focus ring + placeholder lines. Re-record all frames of `AiChatVariantsScreenshotTest`, `AiChoiceResponseScreenshotTest`, `ChatScreenRedesignScreenshotTest`, `ChatSurfacePlanesScreenshotTest.*` (chrome plane!).
- **Screenshot matrix (must be viewed before hand-back):** 320×640 and 412×915 · light / dark · fontScale 1.0 / 1.5 / 2.0 · EN / RU / HI. Edge data: 4-line task text; parsed phrase + repeat staged; 4 staged attachments; planner expanded at 320dp, fontScale 1.5; source grid at 200dp × 2.0 (existing frame); batch of 6 steps with 2 destructive; which-list with 6 options and a 40-char list name; credits 9999; empty chat RU × 1.5; the dock (chrome plane) dark with a pending choice.
- **Grabli:**
  1. `AddItemInputField` has 4 page-plane callers. `Embedded` must be opt-in; `Outlined` frames must not change.
  2. The composer is still inside the same `QuickCaptureDock` Surface: do not move the `modifier` (scrim shoulder colour + `onGloballyPositioned` dock-top) onto the card.
  3. Default chip on the chrome plane in dark (§2.1, risk 9).

## 5. Owner questions (also in the main agent's reply)

- **O1.** Detail dock: (a) visual only now, same chip set (recommended for this pass) · (b) unify with the Inbox rail ("When?" + presets + planner + star + sources) — feature work.
- **O2.** Escape "Cancel" in the same row as the actions (recommended) · or keep it on its own row as decided in July.
- **O3.** Hide the keyboard while the planner is open (recommended) · keep today's behaviour.

## 6. Проверка

One picture pass of the canvas (Playwright, local Chromium), frames viewed:
- Canvas sections at 1640px: `<scratchpad>/pass2/shots/canvas-dock-b.png`, `canvas-dock-a.png`, `canvas-dock-c.png`, `canvas-chat-b.png`, `canvas-chat-a.png`, `canvas-chat-c.png`, `canvas-now0.png` (dock now), `canvas-now4.png` (chat now).
- Phone-width page, dark scheme: `mobile-*.png` (no horizontal scroll, frames shrink).
- Narrow check, recommended variants: `check-narrow.png` — 320dp, fontScale 1.5, EN / RU, dark expanded planner, RU detail, chat batch / RU empty / RU which-list, 412 create.

Fixed in the spec after looking: composer fill `card()` not `bottomChromeRaised()` (dark chips vanished); applied preset dropped from the rail in the expanded state; source pills must keep the rung grid; planner computed columns; chat empty scroll + avatar hide; RU placeholder 2 lines; chat header subtitle wrap (noted, out of scope). Not checked: real IME behaviour, motion, HI glyph rendering in Compose (the canvas uses the browser's Devanagari font).

## 7. Эталон

Raster frames for side-by-side: `canvas-dock-b.png` (dock, 4 states light + 3 dark) and `canvas-chat-b.png` (chat, 4 states light + 3 dark). "Before": the goldens embedded in `canvas-now0.png` / `canvas-now4.png` (paths in each caption).
