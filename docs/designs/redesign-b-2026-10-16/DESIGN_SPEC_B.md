# DESIGN_SPEC — Direction B "Soft & tactile" (Gisti core screens)

- **Platform:** Android + Web (Compose Multiplatform, wasmJs), Material 3. One UI for both.
- **Method:** claude-design. Owner-approved mock: canvas https://claude.ai/artifact/LXZngnEKuQh3PsoPhSHTpM, row "B — Soft & tactile".
- **Code base:** worktree `` (= master `3f950d0d`). Paths below are relative to it.
- **Screens:** v2 Inbox (Home) + Compact shell bottom chrome · Checklist detail (Standard view) · AI chat (full screen).
- **Scope rule:** every structural change below is gated to the **v2 arm** (`useInlineAddRow == true` on detail; `V2NavigationShell` / `InboxScreen` are v2-only already). The classic (v1) layout keeps its structure and only inherits the global tokens.
- **Abbreviations:** DS = `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/`, HOME = `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/`, CHAT = `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/`, SHELL = `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/`.

---

## 0. Decisions this spec reopens (owner already approved B with these visible in the mock)

| # | Standing decision (where it is written) | What B does | Why it is acceptable |
|---|---|---|---|
| R1 | Pager **dots** replaced the project pill row (`InboxScreen.kt:1900-1925` KDoc: pills "read as one strip" and "compete with the bottom bar") | Project **chips** come back above the list | Answers both objections: no pinned chip (Inbox chip scrolls with the rest), selected chip uses an **ink** fill (not the nav bar's blue pill), auto-centres like the dots did. Dots failed the brief: the band ate ~25% of the first viewport and the 32dp targets fail 48dp. |
| R2 | Raised centre AI FAB with 2+2 split (`V2NavigationShell.kt:134-245`, `:611-737`) | Flat **Ask pill** inside the bottom plate, nav bar back to 4 equal tabs | Removes the notch the brief flagged. Same `onOpenChat`, same a11y name. |
| R3 | Light page cream `#FBFAF8`, card `#FFFFFF` (`Color.kt:72-80`) | Sand `#F4EFE7` page, cream `#FFFDF9` cards (whole neutral ramp, light + dark) | Core of "warmer surfaces". Global, see §1. |
| R4 | Card per task, `medium` 16dp (`TaskRow.kt:247-263`, `ChecklistDetailScreen.kt:2857`) | One **grouped container** (24dp) per section, rows inside with inset dividers | Fixes "every task is a heavy card". |
| R5 | Detail capture = inline row in the list + FAB (`ChecklistDetailScreen.kt:1665-1736`, `:1800-1830`) | **Bottom capture plate** (resting pill → existing `QuickCaptureDock`) | Fixes "two competing +". Same pattern Inbox already uses (row → dock). |

Not reopened: chrome tone `GistiColors.chrome` (`#DCE2EC` / `#191D25`), `AppShapeTokens.SheetTop` 28dp, no bundled font, brand blue `primary`, per-item actions only in `ItemDetailsSheet`, the 30/70 hit-zone split, **no count in Inbox section headers** (`InboxScreen.kt:1700`, `AppPlanNudge.kt:65-75`, see §2.3).

---

## 1. TOKENS — delta table

Legend: **G** = global (changes every screen that reads the token, both nav arms), **S** = scoped (only the new B components / the 3 screens + shell).

### 1.1 `theme/Color.kt` — neutral ramp (G). Brand roles (`primary*`, `secondary*`, `tertiary*`, `error*`) **unchanged**.

Replace the private `Warm*` vals (`:72-80`) and `CoolDark*` vals (`:84-91`) with the values below. Keep the variable names or rename to `Sand*` / `WarmDark*` — implementer's choice, but one name per value.

| Role (`LightColorScheme`, `:132-168`) | Old | New |
|---|---|---|
| `background`, `surface`, `surfaceBright` | `#FBFAF8` | `#F4EFE7` |
| `surfaceContainerLowest` (card / group) | `#FFFFFF` | `#FFFDF9` |
| `surfaceContainerLow` (sheet / modal) | `#F6F5F2` | `#F9F5EF` |
| `surfaceContainer` (recessed: chips, fills) | `#F1F0ED` | `#EFE9DF` |
| `surfaceContainerHigh` (track / pressed) | `#ECEAE6` | `#EAE3D8` |
| `surfaceContainerHighest` | `#E6E4DF` | `#E4DCD0` |
| `surfaceDim` | `#DEDCD6` | `#DDD5C8` |
| `surfaceVariant` | `#EDEBE6` | `#ECE5DA` |
| `outlineVariant` (hairlines) | `#E2E0DB` | `#E6DED1` |
| `outline` (control borders, checkbox ring) | `#79747E` | `#7A7266` |
| `onBackground`, `onSurface` | `#1D1B20` | `#24211C` |
| `onSurfaceVariant` | `#49454F` | `#5B554C` |

| Role (`DarkColorScheme`, `:179-215`) | Old | New |
|---|---|---|
| `background`, `surface`, `surfaceDim` | `#121317` | `#161412` |
| `surfaceContainerLowest` | `#0D0E11` | `#100E0C` |
| `surfaceContainerLow` (card in dark) | `#1A1C20` | `#221F1B` |
| `surfaceContainer` | `#1E2025` | `#26231F` |
| `surfaceContainerHigh` (recessed in dark) | `#26282E` | `#2D2924` |
| `surfaceContainerHighest` | `#2D2F35` | `#35302A` |
| `surfaceBright` | `#303239` | `#3A3530` |
| `outlineVariant` | `#2C2F36` | `#37322B` |
| `outline` | `#938F99` | `#8F877C` |
| `onBackground`, `onSurface` | `#E6E1E5` | `#EEE8E0` |
| `onSurfaceVariant` | `#CAC4D0` | `#C8BFB3` |
| `surfaceVariant` | `#49454F` (NeutralVariant30) | `#4A443C` |

Measured (script `scratchpad/contrast2.js`): onSurface/page 14.0:1 light, 15.1:1 dark · onSurfaceVariant on page / card / recessed / sheet ≥ 5.78:1 light, ≥ 7.95:1 dark · outline (checkbox ring) on page 4.15:1, on card 4.67:1, dark 4.63:1 · primary `#1565C0` on page 5.02:1, on card 5.66:1 · error on card 6.36:1 · card vs page ΔL\* +4.7 light / +5.5 dark (hairline `outlineVariant` carries the edge, ΔL\* −10.6 vs card).

⚠️ **Chrome separation drops in light:** `#DCE2EC` vs the new page is ΔL\* −4.9 (was −8.6). The chrome tone is frozen by decision, so the 28dp `SheetTop` corners carry the edge. Check on a device; do **not** retune the chrome and do **not** add a shadow (both rejected before — `Elevation.kt:249-254`).

### 1.2 `theme/Elevation.kt` (G)

| Token | Old | New | Why |
|---|---|---|---|
| `BOTTOM_CHROME_IDLE_ALPHA_LIGHT` (`:474`) | `0.82f` | `0.92f` | The new `onSurfaceVariant` at 0.82 on `#DCE2EC` = 3.87:1 (fails AA). At 0.92 ≈ 4.75:1. Dark 0.75 stays (5.80:1). |

The KDoc measurement tables in `AppSurface` (`:193-224`, `:449-453`) describe the old cream page. Update the numbers or mark the tables as "pre-B".

### 1.3 `theme/GistiColors.kt` (G) — contrast fixes

| Token | Old light → new light | Old dark → new dark | Measured |
|---|---|---|---|
| `AiFromLight` / `AiToLight` (`:31-32`) | `#2196F3`→`#5C6BC0` → **`#1A73D9`→`#4F5BB8`** | | white label 3.12 → **4.67** (start), 4.86 → **5.96** (end) |
| `AiFromDark` / `AiToDark` (`:33-34`) | | `#3E8DF0`→`#6E7CE2` → **`#2A6FD6`→`#5562C4`** | white label 3.35 → **4.84**, 3.72 → **5.34** |
| `star` (`:57`) | `#F4A923` → **`#A86400`** | `#F5B544` (keep) | 1.96 → **4.61** on card (it is now a sole-channel indicator on the row, §2.2) |
| `priorityIndicator` (`:73`) | `#C67C00` → **`#A86400`** (same value as `star`: one signal, one colour) | `#F5B544` (keep) | 3.29 → 4.61 |
| `tintContentLight` (`:124-131`, avatar monogram/icon) | `#2E9E5B #2196F3 #E8833A #7E5BD0 #D24B7E #D9A21E` → **`#1D6B40 #1565C0 #A04A12 #6340B5 #A52F5C #7E5800`** | dark list unchanged (all ≥ 6.3:1) | old 2.16–4.16 → new 5.03–6.10 on their tints (monogram is text) |

Tints (`tintsLight`/`tintsDark`) unchanged. Add **one public accessor pair** so features can pick a palette hue by index without a fake id: `fun paletteTint(index: Int)` / `fun paletteContent(index: Int)` (same lists, `index.mod(6)`). Used by the section header icons (§2.3).

`AppGradientButton`, `SparkleTile`, the dock sparkle and every other gradient reader pick the retune up automatically. No call-site edit.

### 1.4 `theme/Shape.kt` — `AppShapeTokens` (S, additions only; the `AppShapes` ladder is unchanged)

| New token | Value | Applies to |
|---|---|---|
| `Group` | `RoundedCornerShape(24.dp)` | grouped section container, Inbox skeleton group |
| `HeaderCard` | `= AppShapes.extraLarge` (28dp), named alias | detail header card |
| `ChatBubbleTail` | `8.dp` (`Dp`) | chat bubble tail corner (was 4dp literal in `ChatMessageBubble.kt:118,126`) |

### 1.5 `theme/Density.kt` — `AppDensity` (G for the first row, S for the rest)

| Token | Old | New | Note |
|---|---|---|---|
| `RowMinHeightCompact` (`:32`) | 44dp | **48dp** | G. A row is a touch target. 44 failed 48 for the 30% check zone. |
| `GroupRowPaddingHorizontal` | — | **16dp** | S. Row inset inside a group (cards used `RowPaddingHorizontal` 12). |
| `GroupRowGap` | — | **12dp** | S. Checkbox → text inside a group row. |
| `RoundCheckboxSize` | — | **22dp** visual, laid out in a 24dp box | S. |
| `GroupDividerInsetStart` | — | `GroupRowPaddingHorizontal + 24dp + GroupRowGap` = **52dp** | S. Derived, not a literal. |
| `GroupSpacing` | — | **8dp** | S. Gap between a section header and the next group's top edge is `SectionHeaderTop` (12) as today. |

### 1.6 `theme/Dimens.kt`, `theme/Type.kt`, `theme/TextStyles.kt` — **unchanged**

Every B text style maps onto an existing slot: Inbox headline = `headlineLarge` (28/36 Bold) · detail header title = `headlineSmall` (22/28 Bold) · section header = `AppTextStyles.sectionHeader` (as today) · task text = `AppTextStyles.taskTitle` · chips / meta = `labelLarge` / `labelMedium` · overline date = `bodyMedium` + `FontWeight.Medium` in `onSurfaceVariant`.

---

## 2. COMPONENTS

States shared by every interactive component: **pressed** = M3 state layer of the content colour at 10% (default `ripple()`), clipped to the component shape · **disabled** = content `onSurface` @ 38%, container `onSurface` @ 12% · **focused (keyboard/web)** = 2dp `primary` focus ring outside the shape. Dark theme = the same roles. No component below has its own hex. Everything resolves through `MaterialTheme.colorScheme`, `AppSurface`, `GistiColors`.

### 2.1 Grouped section container — `Modifier.groupSegment(position)` + `GroupPosition`
- **Where:** new `DS/components/AppGroupedSection.kt`.
- **Extends:** `AppCardDefaults` (`colors()` / `border()` / `flatElevation()`). Same fill and hairline, **split across lazy items**. Not a new card style.
- **Why a modifier, not a container composable:** the rows are separate `LazyColumn` items (reorder, swipe, `animateItem`), so a wrapping `Column` is impossible. Each item paints its own segment of one visual container.
- **API:** `enum class GroupPosition { Single, First, Middle, Last }` and `fun Modifier.groupSegment(position: GroupPosition, color: Color = AppSurface.card(), border: Color = MaterialTheme.colorScheme.outlineVariant): Modifier`. Helper `fun groupPositionOf(index: Int, size: Int)`.
- **Anatomy:** fill `AppSurface.card()`. 1dp `outlineVariant` hairline: left and right edges always, top edge on `First` and `Single`, bottom edge on `Last` and `Single`. Corner radius 24 (`AppShapeTokens.Group`) on the outer corners of `First`/`Last`/`Single`, square elsewhere. Clip the content to the segment shape so ripples and highlight stay inside.
- **Divider between rows:** drawn by the segment itself (not by the row content) at the bottom of `First`/`Middle`, from `GroupDividerInsetStart` to the end edge, 1dp `outlineVariant`. It must stay put while a row swipes (see §2.2 swipe).
- **States:** dragging (edit mode): the dragged row renders as `Single` with 16dp corners (`AppShapes.medium`), `AppSurface.card()`, 2dp `primary` border. That is the existing `ChecklistItemCard` drag treatment (`ChecklistDetailScreen.kt:2845-2856`). Neighbours re-segment live from `localNodes` order.
- **Light/dark:** card `#FFFDF9` / `#221F1B`, hairline `#E6DED1` / `#37322B`.

### 2.2 Task row with round checkbox — extend `TaskRow` (Inbox) and `ChecklistItemCard` (detail)
- **Where:** `HOME/components/TaskRow.kt:108` and `HOME/detail/ChecklistDetailScreen.kt:2662`. **Extend both. Do not write a third row.** New DS primitive `AppRoundCheckbox` in `DS/components/AppRoundCheckbox.kt` (it replaces M3 `Checkbox` only inside these two rows; M3 `Checkbox` stays everywhere else).
- **`AppRoundCheckbox(checked: Boolean, important: Boolean, modifier)`:** visual-only (`onCheckedChange` stays on the row's 30% overlay, as today, `TaskRow.kt:181-186`).
  - Unchecked: 22dp circle, 2dp `outline` ring, transparent.
  - Unchecked + important: ring `GistiColors.priorityIndicator`, fill = the same colour @ 12%.
  - Checked: filled `primary`, 16dp check glyph `onPrimary` (weight 700).
  - Layout box 24dp. No ripple of its own.
  - Semantics: none of its own; the row's overlay keeps `Role.Checkbox` via `toggleableState` on the 30% zone (add it if missing).
- **New parameter on both rows:** `container: RowContainer = RowContainer.Card` with `enum RowContainer { Card, Grouped }`. `Grouped` = no `Card`/border/shape of its own, padding `GroupRowPaddingHorizontal`, gap `GroupRowGap`, `AppRoundCheckbox` instead of `Checkbox`. The caller applies `groupSegment`. The `Card` path keeps today's rendering byte-for-byte, so the v1 arm and other callers are unchanged.
- **Grouped row anatomy (comfortable):** min height `RowMinHeightComfortable` 60 (2 lines) · checkbox · text column: title `AppTextStyles.taskTitle` (max 2 lines, Ellipsis) followed by an inline **star** 16dp `GistiColors.star` when `priority > 0`. The star is read-only with `contentDescription` = existing string used by the sheet's priority row (the "Important" label), merged into the row semantics. Meta row below (unchanged `TaskMetaRow`: `AppDueChip`, `AppSourceIcon`, attachments chip).
  - **Priority bar:** `Modifier.priorityBar` (`TaskRow.kt:387`) is **not drawn** in `Grouped`. Ring + star replace it, which keeps priority from being colour-only.
- **Compact (display option):** min height 48, single line at fontScale ≤ 1.05 (unchanged rule `TaskRow.kt:141`).
- **Detail extras stay** in `ChecklistItemCard` grouped mode: note preview, URL chip, `ItemMetaRow`, highlight overlay (`primaryContainer` fade, clipped to the segment), completion pop (`graphicsLayer` scale; keep it, it does not reflow), edit-mode wiggle + drag handle.
- **Checked:** text `onSurfaceVariant` + line-through (unchanged).
- **Swipe-to-delete (detail):** `SwipeableChecklistItemCard` wraps the row content. `SwipeDeleteBackground` (`:2643`) clips to the **segment shape** instead of `shapes.medium`. Divider and segment hairline are drawn on the static layer under the swiping content.
- **Hit zones:** unchanged 30/70 with long-press, across the full segment width.

### 2.3 Section header with icon — `AppSectionHeader`
- **Where:** new `DS/components/AppSectionHeader.kt`. Inbox replaces the body of `InboxSectionHeader` (`InboxScreen.kt:1708-1725`) with it. The detail "Completed" header reuses it (§2.8).
- **API:** `AppSectionHeader(label: String, icon: ImageVector, iconContainer: Color, iconContent: Color, modifier, count: Int? = null, trailing: (@Composable () -> Unit)? = null)`.
- **Anatomy:** Row, top padding `AppDensity.SectionHeaderTop` 12, bottom 8 · icon tile 28dp circle (`iconContainer`) with an 18dp icon (`iconContent`, filled style) · gap 10 · label `AppTextStyles.sectionHeader`, `onSurface`, never uppercased (Devanagari) · optional count `bodyMedium` `onSurfaceVariant`, gap 6 · optional trailing slot at the end.
- **Count is `null` on the Inbox.** ⛔ Standing decision "no debt counter" (`InboxScreen.kt:1700`). The mock showed counts; they are **dropped**. The open total stays in the header subtitle.
- **Inbox kind → icon / colours** (mapping lives in feature/home, colours from tokens):

| Kind | Icon | Container | Content |
|---|---|---|---|
| OVERDUE | `Icons.Filled.History` | `GistiSchedule.overdueContainer` | the matching `GistiSchedule` content colour (verify ≥ 3:1) |
| TODAY | `Icons.Filled.WbSunny` | `GistiColors.paletteTint(2)` (peach) | `GistiColors.paletteContent(2)` |
| UPCOMING | `Icons.Filled.Event` | `colorScheme.primaryContainer` | `colorScheme.primary` |
| ANYTIME | `Icons.Filled.AllInclusive` | `GistiColors.paletteTint(3)` (lilac) | `GistiColors.paletteContent(3)` |

- Measured icon-on-tile (non-text, 3:1): peach 4.66 / lilac 5.58 / primary 5.03 light; 6.65 / 7.06 / 5.36 dark.
- **Semantics:** `heading()`. The icon is decorative (`contentDescription = null`).

### 2.4 Project chips — what replaces `InboxPagerDots`
- **Today (`InboxScreen.kt:1199-1203`, `:1927-2012`):**
  - The pager pages are **`content.pages`**. Index 0 is always the system **Inbox**; the rest are **project checklists**, one page each, in pager order. The ViewModel guarantees this (`InboxScreenContract.kt:125-129`).
  - Dots: hidden when `pages.size <= 1`. `LazyRow`, centred when it fits, auto-centres the selected dot (`LaunchedEffect(selectedPage)`). Each dot has `Role.Tab` + `selected` + `contentDescription = page.title`, a 32dp visual inside a 48dp target, and tap → `pagerState.animateScrollToPage(index)`.
  - Swipe stays the primary way to change page; the title in the bar names the page.
- **B: `AppPageChipRow`.** New `DS/components/AppPageChipRow.kt`. **It replaces `InboxPagerDots` one-for-one and keeps every behaviour above.** Same signature plus the data a chip needs: `AppPageChipRow(pages: List<PageChip>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier)` with `data class PageChip(val title: String, val dotColor: Color?, val leadingIcon: ImageVector?)`. The feature maps Inbox → `leadingIcon = Icons.Outlined.Inbox`, project → `dotColor = GistiColors.avatarContent(checklistId)`.
- **Anatomy:**
  - `LazyRow`, `contentPadding` horizontal 16, `spacedBy(8)`, row height min 48.
  - Each chip: min height 36 visual inside a 48 target (`minimumInteractiveComponentSize`), shape `Pill`, padding start 10 / end 14. Leading: 10dp dot or an 18dp icon, gap 8. Label `labelLarge`, max 1 line, chip max width 180dp, Ellipsis.
  - Unselected: transparent, 1dp `outlineVariant` border, label `onSurface`.
  - **Selected:** fill `onSurface` (ink), label `surface`, icon `surface`, no border (14.0:1 light / 15.1:1 dark).
- **Behaviour (verbatim from the dots):** hidden when `pages.size <= 1`. Auto-scroll keeps the selected chip in view, centred, on every `selectedPage` change. No pinned chip. Tap → `animateScrollToPage`. Chip semantics `Role.Tab`, `selected`. Container `Modifier.selectableGroup()`.
- **All pages stay reachable:** swipe (unchanged `HorizontalPager`), chip tap, the Inbox nav tab tap → page 0 (`homeSignal`, `InboxScreen.kt:1153-1156`, unchanged), and the Projects tab.
- **Motion:** selection colour `animateColorAsState(AppMotion.effectsDefaultAs())`, no size change.

### 2.5 Ask pill in the bottom plate — what replaces the centre AI FAB
- **Today (`V2NavigationShell.kt:611-737`):**
  - 56dp circle overhanging the bar by 22dp, with a 3dp page-coloured ring. The 76dp gap is the hit area.
  - Tap → `onOpenChat`. **No long-press, no voice.**
  - `contentDescription = nav_chat_fab_content_description` ("Ask Gisti"); TalkBack reads it after the four tabs (`traversalIndex` 2f).
  - Hand-off to the chat dock: scale up + fade while `chatOpen`, out of the a11y tree while open.
  - Gone together with the bar while `captureOpen`. Compact only (rail and drawer keep their FAB / ExtendedFAB).
- **B: extend `AskGistiBar`** (`DS/components/gisti/AskGistiBar.kt:59`); do not write a twin:
  - `onMicClick: (() -> Unit)?` becomes nullable; null removes the mic button.
  - New `style: AskGistiBarStyle = Card` with a `ChromePill` variant: height min 48, shape `Pill`, container `AppSurface.bottomChromeRaised()`, 1dp `AppChatColors.controlOutline()`.
  - `SparkleTile` 36dp circle (gradient, §1.3).
  - Label `bodyLarge` `onSurfaceVariant`, max 1 line, Ellipsis.
- **Label text:** existing `nav_chat_fab_content_description` "Ask Gisti". The mock's "Ask Gisti or add a task" is dropped: capture lives in the list's "Add task" row, and promising it here would create a second capture door.
- **No mic.** The shell's chat dock has no "start in voice" entry (`V2ChatDockOverlay.kt:191` takes no voice parameter). A mic glyph would be a dead affordance. If wanted later, that is a feature (open the dock in recording mode), not part of this redesign.
- **Plate (`V2ShellCompactBar`, `:464`):**
  - One `Column` is the plate: `background(AppSurface.bottomChromeShoulder()).clip(AppShapeTokens.SheetTop).background(AppSurface.bottomChrome())`. Children: Ask pill (padding horizontal 12, top 12, bottom 4), then `V2SplitNavigationBar` with **no gap**.
  - Remove the `Spacer`, `gapAfter` and traversal shift at `:849-855`. Move the `background(shoulder).clip(SheetTop)` modifiers from the `NavigationBar` (`:844-846`) up to the plate.
  - `onSizeChanged` moves to the **plate**, so `barHeight` and the consumed inset cover pill + bar.
- **`V2ShellMetrics`:** `AiButtonSize`, `AiButtonOverhang`, `AiButtonRing`, `AiButtonGapWidth`, `AiButtonBarSpacer` are deleted. `FabBandPadding` becomes `AppDimens.SpacingSm` (no overhang). Every v2 tab that reads it (Inbox, Calendar, Projects, Overview) shrinks its bottom reserve. `V2ShellAiButtonTest` must be rewritten against the pill (the "near-miss swallowed by gap" case disappears).
- **Behaviour kept:**
  - Tap anywhere on the pill → `onOpenChat`.
  - Semantics: `Role.Button`, `contentDescription = nav_chat_fab_content_description`, `traversalIndex = -1f` (read **first**, before the tabs; it is now above them visually).
  - `chatOpen` hand-off: alpha 1→0 over `V2ChatMotion.ButtonHandoffMs` with `ExitEasing` (no scale, in a `graphicsLayer` lambda), `clearAndSetSemantics {}` while open, return on `ButtonReturnMs` with `EnterEasing`.
  - `captureOpen`: the whole plate leaves (pill + bar), exactly as `chromeVisible` gates it today (`:515`), and the system-nav strip branch (`:591-596`) stays.
  - `barVisible == false` (pushed route) → no plate.
- **States:** pressed = state layer on the pill · disabled = n/a.
- **Light/dark:** pill `#FFFDF9`-level raised (`surfaceContainerLowest`) on `#DCE2EC` / `#26282E`-level (`surfaceContainerHigh`, now `#2D2924`) on `#191D25`. Outline `outline` 3.64:1 on chrome light.

### 2.6 Checklist header card with progress ring — `AppChecklistHeaderCard`
- **Where:** new `DS/components/gisti/AppChecklistHeaderCard.kt` (primitives only, no domain types). It replaces both the in-bar title/subtitle (`ChecklistDetailScreen.kt:1124-1133`) and the bare progress line (`:1447-1474`) in v2. The v1 `ProgressHeader` (`:2476`) is untouched.
- **Extends:** `GistiAvatarTile` (tile) + `AppCardDefaults` (fill / hairline). New `AppProgressRing` wraps M3 `CircularProgressIndicator(progress = {…})` in `DS/components/AppProgressRing.kt`.
- **API:** `AppChecklistHeaderCard(seed: Long, title: String, subtitle: String?, checked: Int, total: Int, isFolder: Boolean, modifier)`.
- **Anatomy:** Surface, shape `AppShapeTokens.HeaderCard` (28), `AppSurface.card()`, 1dp `outlineVariant`, padding 16, Row gap 14, `Alignment.Top`:
  1. **Tile** 48dp `GistiAvatarTile(seed, label = title)`. Monogram, no new icon logic; `isFolder` → `Icons.Outlined.Folder` in the same tile. Corner 16.
  2. **Column** weight 1: title `headlineSmall` (22/28 Bold), `onSurface`, **max 3 lines**, Ellipsis. Subtitle `bodyMedium` Medium, `onSurfaceVariant`, max 2 lines, top 4.
  3. **`AppProgressRing`** 52dp, stroke 5dp round cap, track `AppSurface.recessed()`, progress `primary`, or `tertiary` when `checked == total > 0` (existing completion colour). Centre label `labelLarge` Bold `onSurface` = `folder_progress` ("%1$d/%2$d").
- **Label rule:** the ring label is shown only when `total in 1..99` and `fontScale < 1.3`. Otherwise the ring has no label (the numbers are in the subtitle). Complete → the label becomes a 22dp `Icons.Filled.Check` in `tertiary`.
- **Narrow rule (measured, §7):** inside `BoxWithConstraints`, when `maxWidth < 340.dp × fontScale`, the avatar tile is **hidden** (it is decorative) and the title gets the 62dp.
- **Empty level (`total == 0`):** ring hidden, subtitle hidden. The title stays.
- **Semantics:** the card is not clickable. Ring: `contentDescription = detail_progress_content_description(checked, total)` (existing). Title has `heading()`.
- **Light/dark:** as `AppSurface.card()`. Ring track `#EFE9DF` / `#2D2924`.

### 2.7 "Ask Gisti about this list" row — `AppAiInviteRow` (generalises `AppPlanNudge`)
- **Where:** new `DS/components/AppAiInviteRow.kt`. **`AppPlanNudge` (`AppPlanNudge.kt:103`) is rewritten to delegate to it** with its current title, subtitle and recessed style, so the nudge does not change and there is one anatomy. No twin.
- **API:** `AppAiInviteRow(title: String, onClick: () -> Unit, modifier, subtitle: String? = null, style: AiInviteStyle = AiInviteStyle.Recessed)` with `enum AiInviteStyle { Recessed, Outlined, GroupRow }`.
- **Anatomy:**
  - Surface(`onClick`), min height 48, shape `Pill` for `Outlined` and `AppShapes.medium` for `Recessed`, padding start 8 / end 16 / vertical 6, gap 10.
  - `SparkleTile` 32dp circle · title `titleSmall` `onSurface` (max 2 lines) · optional subtitle `bodySmall` `onSurfaceVariant` · trailing 20dp `KeyboardArrowRight` in `onSurfaceVariant`, decorative.
  - `Outlined` = transparent fill + 1dp **dashed** `outline` border (`drawBehind` + `PathEffect.dashPathEffect(floatArrayOf(6dp, 4dp))`, 4.15:1 on page).
  - `GroupRow` = no own container (it sits in a `groupSegment`).
- **Detail use:** `Outlined`, title `detail_ask_about_list` (new string). `onClick` = **the exact lambda the top-bar sparkle runs today** (`ChecklistDetailScreen.kt:1197-1206`: `onOpenChatSheet?.invoke(); onOpenChat()`). Rendered only when `onOpenChat != null`, like today.
- **Inbox use:** the plan nudge (`PlanNudgeRow`, `InboxScreen.kt:1765`) renders `AppPlanNudge` → `AppAiInviteRow(style = GroupRow)` as the **last segment of the ANYTIME group**. The swipe-to-snooze wrapper is unchanged, and the gating at `:1482-1486` is unchanged.
  - ⚠️ `onPlanDayClick` is null in production today ("review screen does not exist yet", `InboxScreen.kt:235-243`), so the nudge is **not drawn** until a host passes it. That is correct. The mock showed it only to demonstrate the style.

### 2.8 Completed collapsible section (detail) — extend `CompletedSectionHeader`
- **Where:** `ChecklistDetailScreen.kt:4398`. Restyle in v2 only, through a `grouped: Boolean` parameter.
- **Header (grouped):** `AppSectionHeader` with label `completed_count` ("Completed (%1$d)", existing), icon `Icons.Filled.CheckCircle` in a tile of `AppSurface.recessed()` / `onSurfaceVariant`, trailing 48dp toggle icon `ExpandLess`/`ExpandMore` (`collapse`/`expand` strings, existing).
  - The **whole header row** is the toggle (`Modifier.toggleable(value = expanded, role = Role.Button)`), min height 48.
- **Rows:** `ChecklistItemCard(container = Grouped)` in their own group (`groupSegment` by index among `completedItems`), same swipe and tap as today (`:1756-1786`).
- **State:** `completedExpanded` stays UI-local (`:1389`), but becomes `rememberSaveable` so rotation and process death keep it. `OnCompletedSectionToggle` analytics unchanged (`:1747-1750`).
- **Motion:** rows `animateItem()` (fade + placement, default specs). The chevron rotates 180° on `AppMotion.spatialDefault`.

### 2.9 Chat reply without label — extend `ChatMessageBubble`
- **Where:** `CHAT/components/ChatMessageBubble.kt:85`.
- **`showSenderLabel: Boolean` → `showAvatar: Boolean`** (rename and update all 3 callers: `ChatScreen.kt:470`, `App.kt:1605`, `App.kt:1648`).
- **Assistant layout:** `Row(verticalAlignment = Bottom)`: avatar 28dp (`SparkleTile` circle, gradient) + gap 8 + bubble Column. With `showAvatar = false`, a 36dp spacer keeps the column aligned.
  - **Avatar** only on the **last assistant message of a consecutive assistant run** (computed in the list: next-newer message is not assistant).
  - **`AiSenderLabel` (`:307`) is deleted.** Its other caller `AiChoiceResponse.kt:139` switches to the same avatar-beside layout.
- **Bubble:**
  - Assistant: corners 20/20/20/`ChatBubbleTail`(8) bottom-start. Fill `AppChatColors.raised()` + 1dp `contentOutline()` (unchanged roles). Max width 300dp (was 340: the avatar takes 36).
  - User: corners 20/20/8/20, `primaryContainer` / `onPrimaryContainer` (unchanged).
- **Action chips move INSIDE the assistant bubble:** Open checklist / Ask AI / Retry (`:240-278`) and the Become Pro CTA (`:284-294`) render in the bubble's Column under the text, top padding 10, `FlowRow` gap 8, start/end 16, bottom 12. Same `AiChoiceChip`s, same roles.
- **Feedback row (Copy · 👍 · 👎):**
  - Buttons grow to **44dp** (`MessageActionButton`, `:353-356`, was 32 → a11y), icon 18.
  - Shown **only when `isLatestAssistant || actionsRevealed`**. `isLatestAssistant` is computed in the list; `actionsRevealed` is a new param driven by a UI-local `revealedActionsFor: String?` in `ChatMessageList`.
  - Tapping an assistant bubble toggles `revealedActionsFor`. Use `Modifier.clickable(onClickLabel = chat_message_show_actions)` on the Surface. Long-press stays text selection (`SelectionContainer`), so there is no gesture conflict.
  - Start-aligned with the bubble text: start offset = avatar slot 36 − 6.
- **Cost badge** under user messages (`:192-194`): unchanged.
- **Typing indicator:** `ChatTypingIndicator` renders inside an assistant bubble shape with the avatar (list item `__typing`).

### 2.10 Credits chip inside the chat input — extend `ChatInputRow`
- **Where:** `CHAT/components/ChatInputRow.kt:127`.
- **New param:** `leading: ChatInputLeading = ChatInputLeading.Help`, with `sealed interface ChatInputLeading { data object Help; data class Credits(val balance: Int, val onClick: () -> Unit) }`.
- **Help** = today's "?" button (`:274-296`), byte-identical. The App.kt dock hosts (`:1703`, `:1797`) **keep `Help`** in this pass; they are outside the 3-screen scope.
- **Credits:**
  - Pill chip 32dp visual inside a 48dp target, container `primaryContainer`, `Icons.Outlined.AutoAwesome` 16dp in `primary`, value `labelLarge` Bold in `onPrimaryContainer` (15.0:1). Padding 6 / 10.
  - Same `AnimatedVisibility` as Help: hidden while `simpleSendOnly || isTranscribing`.
  - `contentDescription` = existing `chat_credits_label` ("%1$d credits left · ≈ 0–3") plus `onClickLabel` = existing `chat_pricing_caption_action` ("Why?").
- **ChatScreen wiring (`ChatScreen.kt:344-366`):** `leading = Credits(state.creditBalance, onClick = { onIntent(ChatScreenIntent.OnHelpClick) })`, which opens `AiChatPricingHelpSheet` (the same intent the pricing band's "Why?" fires today, `:307-309`).
  - `creditBalance == null` (not loaded) → fall back to `Help`. `onHelpClick` there = `OnFeaturesHelpClick`.
- **Pricing sheet** (`AiChatPricingHelpSheet.kt:62`) gains `onGetMoreClick: (() -> Unit)? = null`. When non-null it renders an `AppButtonSecondary` "Get more" (`chat_settings_get_more`, existing) above "Got it". ChatScreen passes `{ onIntent(OnHelpDismiss); onNavigateToPaywall?.invoke() }`. This keeps the header chip's one-hop paywall route at two hops, and Settings → Get more also stays.

### 2.11 Supporting primitives (new, DS)
- **`AppSoftIconButton(icon, contentDescription, onClick, modifier, tint = onSurface, filledIcon = false)`** — 48dp target, 40dp circle, `AppSurface.card()` + 1dp `outlineVariant`, icon 22dp. Wraps M3 `IconButton`. Used in the detail and chat top bars (B look). The reminder "scheduled" state passes `tint = primary` + the filled bell (`ChecklistDetailScreen.kt:1245-1257` logic unchanged).
- **`AppProgressRing`** — §2.6.
- **`AppRoundCheckbox`** — §2.2.

---

## 3. SCREENS

### 3.1 Inbox (v2) — `HOME/inbox/InboxScreen.kt`

```
Box (root, :407)
└ AppScaffold(title = null, subtitle = null, actions = {}, bottomBar = QuickCaptureDock…)   ← :408-442; top bar no longer rendered (AppScaffold draws none when title/back/nav are null, AppScaffold.kt:184)
   └ content
      ├ InboxHeader  (NEW private composable, fixed — not in the LazyColumn)
      │  ├ Row(padding start 16 / end 4, top 8)
      │  │  ├ Column(weight 1)
      │  │  │  ├ overline: inbox_header_date(weekday, monthAbbr, day) — bodyMedium Medium, onSurfaceVariant   (from content.nowMillis)
      │  │  │  ├ headline: currentPage.title ?: inbox_title — headlineLarge 28/36 Bold, maxLines 2, Ellipsis
      │  │  │  └ subtitle: inbox_task_count plural (open tasks of currentPage) — bodyMedium onSurfaceVariant   (same as :412-418)
      │  │  ├ CreditsToolbarAction(creditsSource)                      ← moved from :430-432, unchanged
      │  │  └ InboxToolbarActions(...)                                   ← moved from :433-440, unchanged (tune + project overflow)
      │  └ (Loading/Error: header still composed with title = inbox_title, actions = credits only — the paywall entry survives Loading/Error as today)
      ├ AppPageChipRow (replaces InboxPagerDots at :1199-1203)
      ├ AnimatedNotificationsOffBanner (unchanged, :1207-1214)
      └ HorizontalPager → InboxPageList (:1216-1246)
         └ LazyColumn (:1336) contentPadding top 4, bottom SpacingXl + contentBottomPadding
            ├ empty_state (unchanged, :1381-1426)
            ├ per section:
            │  ├ AppSectionHeader (via InboxSectionHeader, :1437)
            │  └ tasks i: TaskRow(container = Grouped).groupSegment(groupPositionOf(i, n))   ← :1441-1476; drop the HorizontalDivider at :1469-1474 (segment draws it), keep rowInset (16) as the group's outer margin <!-- docs-leak-scan: reviewed — code line anchors / RU sample title, not metrics -->
            │     └ SectionArrivalHighlight shape = segment shape (was medium/rect, :1671)
            │  └ ANYTIME only: plan nudge as the group's Last segment (GroupRow style) — the task before it becomes Middle
            ├ AddTaskRow (unchanged position :1511-1518, restyle: Pill shape, min height 48, label onSurfaceVariant, leading Add in a 28dp recessed circle)
            └ ai_source_row (unchanged, :1545-1578)
```

- **Header overline/headline at fontScale 1.5:** headline wraps to 2 lines. The header is not fixed-height, so the pager gets what is left.
- **`compact` display option:** rows inside the group use the compact anatomy (48). The `verticalArrangement` at `:1352-1356` becomes `spacedBy(0)` in **both** modes; groups are separated by the section header gap.
- **Skeleton (`InboxLoadingSkeleton`, `:1799-1834`):** one group (Single → First/Middle/Last segments, 5 rows), same widths, `AppSkeletonLine`.
- **Error and empty:** unchanged.
- **Insets / z-order:**
  - Unchanged; the capture dock stays in `bottomBar`.
  - `CaptureChromeScrim` (top tile) now dims the **header + chips** zone. The header lives in the content slot, so `contentTopPx` (`:591`) becomes the status-bar height and the content scrim covers header + chips + list. Verify the two tiles still meet with no seam (the top tile is now ~status-bar tall).
- **Shell (Compact):** §2.5. The plate is the bottom chrome on every v2 tab.

### 3.2 Checklist detail (v2, Standard view) — `HOME/detail/ChecklistDetailScreen.kt`

```
Box (root, :1119)
└ AppScaffold (:1120)
   ├ title = if (headerScrolledOff) state.currentFolderTitle ?: state.checklist.name else null   ← was always set (:1124-1128); subtitle = null (:1129-1133)
   │    headerScrolledOff = derivedStateOf { listState.firstVisibleItemIndex > 0 } (UI-local); title fades in via AnimatedContent(fade 150ms)
   ├ navigationIcon = AppSoftIconButton(ArrowBack, back) → same onBackButtonClick lambda (:1135-1141)
   ├ actions (non-edit): AppSoftIconButton(bell, filled/primary when scheduled) → OnReminderClick (:1244-1258); AppSoftIconButton(MoreHoriz) → OnOverflowMenuClick (:1259-1264)
   │    — the sparkle IconButton (:1197-1214) is REMOVED from the bar; its lambda moves to the Ask row below
   │    edit mode: TextButton "Done" (unchanged :1169-1177)
   ├ bottomBar = DetailCapturePlate (NEW, v2 + Standard + !isEditMode only) — see below
   └ content: Column (:1440)
      ├ (progress line :1447-1474 REMOVED in v2 — the ring replaces it)
      └ LazyColumn (:1475) padding horizontal 16, spacedBy(0) in v2 (was SpacingSm), contentPadding bottom = SpacingLg (plate is a scaffold slot now, no FAB band)
         ├ slot 0 (UNCONDITIONAL, keep — index math :1499-1502):
         │   Column(spacedBy 8)
         │   ├ AppChecklistHeaderCard(seed = checklist.id, title = currentFolderTitle ?: checklist.name,
         │   │     subtitle = detail_header_progress(open, checked, total) [hidden when total == 0],
         │   │     checked, total, isFolder = currentFolderTitle != null)   ← counts already computed :1067-1079
         │   ├ notificationsBanner() (:1509-1519)
         │   ├ CompletionBanner (AnimatedVisibility isComplete, :1525-1531)
         │   ├ ChecklistDetailEmptyState (isLevelEmpty, :1532-1540) — action = openPlate (see below)
         │   └ RecurringNudgeCard (:1549-1554)
         ├ ViewAllFillsCard (:1559-1567) — restyle as Single groupSegment row (padding 16, title + count + chevron)
         ├ open nodes (:1573-1652): Folder → FolderCard(container = Grouped) / Leaf → ChecklistItemCard(container = Grouped), groupSegment by index in localNodes; top gap 12
         ├ ask_about_list (NEW item, key "ask_about_list", only if onOpenChat != null && !isEditMode): AppAiInviteRow(Outlined), top 12
         ├ (inline_add_item :1665-1736 REMOVED in v2)
         ├ completed_header (:1739-1753) → CompletedSectionHeader(grouped = true), top 12
         └ completed rows (:1755-1787) → ChecklistItemCard(container = Grouped) + groupSegment
   (FAB :1800-1830 REMOVED in v2; DetailFabBand / fabShown become dead in v2 — delete their v2 branches)
```

**DetailCapturePlate** (new private composable in the detail file; v2, `viewMode == Standard`, `!isEditMode`). It lives in `AppScaffold.bottomBar`, so set `contentExtendsBehindNavBar = false` **in v2**: the glass dock is not shown in v2 (`chatDockContent == null`), and the slot then owns `ime ∪ navigationBars`, as on the Inbox.
- **Resting (`!state.itemCreateMode`):**
  - `Surface(color = AppSurface.bottomChrome(), shape = SheetTop)`, padding 12/16/16.
  - One `Row`, min height 48: a pill (`Pill`, `bottomChromeRaised()`, 1dp `controlOutline()`) showing `detail_inline_add_placeholder` "Add a task" in `onSurfaceVariant`, then a 48dp filled `primary` circle with `Icons.Filled.Add` (`onPrimary`).
  - The **whole row is ONE button**: `Role.Button`, label `detail_inline_add_placeholder`. The "+" is part of it, not a second node. This avoids two "Add task" nodes; the empty state's CTA keeps `detail_add_task_fab`.
  - Tap → `openPlate()` = `onIntent(OnDockItemCreateOpened(null))`.
  - No attach button in resting (attach lives in the active state).
- **Active (`state.itemCreateMode`):** the existing **`QuickCaptureDock`** (DS, already the bottom-chrome capture surface):
  - `text = state.pendingItemInput`, `onTextChange → OnItemInputChanged`, `onAdd → OnAddItemWithParse`, `placeholder = item_create_input_placeholder`.
  - `aboveInput = { ItemCreateChipsRow(state, onIntent); ItemCreateAttachmentStrip(...) }`, the same composables as `:1690-1704`, default 16 padding.
  - `trailingToggle =` 48dp attach `IconButton` → `OnItemCreateAttachClick` (`attachment_add_file_button`).
  - `highlightRange` = the same Smart-Add range the Inbox derives, if the detail draft exposes one; otherwise null.
  - The dock autofocuses on mount (its contract), stays open across adds (VM keeps `itemCreateMode`), and closes on BACK → `OnDockItemCreateClosed`, on a tap on the scrim, or on blur with an empty draft (the same rule as `:1726-1730`).
  - **Scrim:** reuse the Inbox tiles: `CaptureChromeScrim` (top) + a content-slot scrim with `detectTapGestures` (`InboxScreen.kt:626-635`), driven by `itemCreateMode`. **Not** the control arm's `scrimAlpha` dock scrim; that one is gated to the glass dock.
  - `PlatformBackHandler(enabled = itemCreateMode)` → `OnDockItemCreateClosed`.
- `openPlate` is also the empty-state CTA action (replacing `openInlineAdd`).
- **Delete in v2:** `inlineAddFocusRequester`, `inlineAddFocusSignal`, `inlineAddRowIndex()`. Grep every use before removing; the v1 branch must not reference them.
- **Weekly view:** no plate (same as the FAB today). The header card is not used: Weekly keeps today's AppScaffold title + subtitle. Tokens only.
- **Snackbar host (`:1146-1166`):** in v2 → plain `SnackbarHost`. The scaffold already lifts it above `bottomBar`.

### 3.3 AI chat (full screen) — `CHAT/ChatScreen.kt`

```
Scaffold (:144)
├ topBar = ChatHeader (CHAT/components/ChatHeader.kt:41), restyled:
│   [AppSoftIconButton back → OnBackClick | drawer menu if onMenuClick != null]
│   [avatar SparkleTile 36dp circle] gap 10 [Column: "Gisti" titleMedium Bold (literal brand name → put in strings as chat_header_title) / chat_header_subtitle bodySmall Medium onSurfaceVariant]  weight 1
│   [AppSoftIconButton settings → OnSettingsClick]
│   — creditBalance + onCreditsClick REMOVED from the header (:152, :155); height 64
└ ChatContent (:281)
   ├ ChatPricingRow (:307-309) REMOVED (ChatScreen host). Keep the composable file: no other caller found; delete it if grep confirms none.
   ├ ChatBody / ChatMessageList (:390): showAvatar = lastOfAssistantRun; isLatestAssistant; actionsRevealed = revealedActionsFor == message.id
   ├ ChatRecordingOverlay, ChatAttachmentChipStrip (unchanged)
   └ ChatInputRow(leading = Credits(...) | Help fallback) (:344-366)
Sheets: Pricing (+onGetMoreClick), Features (unchanged, now opened from Settings), Settings (+ row), Language, Feedback — unchanged hosts (:183-254)
```

- **Settings sheet** (`ChatSettingsSheet.kt:69`): new param `onFeaturesHelpClick: (() -> Unit)? = null`. It renders a row (same row component as "Response language") titled `chat_features_help_action` ("How to use AI Chat", existing), leading `Icons.AutoMirrored.Outlined.HelpOutline`, placed after Response language. ChatScreen passes `{ onIntent(OnSettingsDismiss); onIntent(OnFeaturesHelpClick) }`.
- **Page / bubbles:** page `surface` (now sand). The assistant bubble `raised()` resolves to the cream card. User bubble `primaryContainer`.
- **Insets:** unchanged (`imePadding` on the ChatContent root Column, `:301-304`).

---

## 4. STATE NEEDS (for @feature-expert)

| Need | Where the data already is | New? |
|---|---|---|
| Inbox overline date | `InboxScreenState.Content.nowMillis` + `weekday_*` / `month_abbr_*` strings | UI-only formatting helper. **No new state.** |
| Inbox chips data | `content.pages` (title, checklistId, isInbox), `content.selectedPage` | none |
| Section header icons | `InboxSectionKind` | none (mapping in UI) |
| Detail counts (left / done / total) | computed in screen `:1067-1079` (`checkedCount`, `totalCount`, `openTaskCount`) | none |
| Header card seed / title / folder | `state.checklist.id`, `state.checklist.name`, `state.currentFolderTitle` | none |
| Completed group expanded | UI-local `completedExpanded` (`:1389`) → `rememberSaveable` | UI-local (compose) |
| Header-scrolled-off → bar title | `listState` | UI-local (compose) |
| **Separate completed by default** | `Checklist.separateCompleted: Boolean = false` (rule `checklist-domain`) | **YES, decision for @feature-expert.** The brief's "completed interleaved" defect is only fixed when this is true. Spec: **change the model + Room column default to `true` for checklists created from now on.** Existing rows keep their stored value (the user may have set it); the Overflow toggle stays. A Room migration is needed only for the column DEFAULT, not for the data. If the owner wants existing lists to switch too, that is a separate data migration (not specified here). |
| Capture plate open/close | `state.itemCreateMode`, `OnDockItemCreateOpened(null)`, `OnDockItemCreateClosed`, `pendingItemInput`, `OnAddItemWithParse`, `OnItemCreateAttachClick`, chips intents | none. Verify `OnDockItemCreateClosed` is safe to fire from BACK with a non-empty draft (today blur keeps the mode when the draft is non-empty; BACK should keep the draft and close the mode: confirm the VM keeps `pendingItemInput`). |
| Smart-Add highlight range on detail | Inbox has `draft.smartAddHighlightRange()`; detail has `state.parsedToken` | optional. If detail has no offsets, pass null (the token preview chip in `ItemCreateChipsRow` still shows the parse). |
| Chat credits chip | `ChatScreenState.creditBalance`, `isPremium` | none |
| Latest assistant / reveal actions | `state.messages` | UI-local (compose) |
| Pricing sheet → paywall | `onNavigateToPaywall` already passed to `ChatScreen` | callback only. Paywall `source`: reuse whatever `ChatHeader.onCreditsClick` reported. If the source is assigned in App.kt, keep the same constant so the funnel series does not break. |
| Ask pill (shell) | `onOpenChat`, `chatOpen`, `captureOpen`, `barVisible` | none |

**Analytics to keep alive** (an event attached to a deleted control silently stops):
- `nav_create_fab_tapped(source = "inline_row")` on the Inbox add row: unchanged.
- The centre FAB's open-chat analytics (if App.kt emits one in `onOpenChat`): unchanged, same lambda.
- Detail FAB tap analytics (if any on `openInlineAdd`): move to `openPlate`.
- `OnCompletedSectionToggle`: unchanged.
- Chat header credits chip tap → add the same emit to the input chip tap, or document that the series ends.

---

## 5. FUNCTION PRESERVATION

| # | Screen · function today | Where it lives in B |
|---|---|---|
| I1 | Inbox · page title + open-task count in top bar (`:409-418`) | InboxHeader headline + subtitle (same strings) |
| I2 | Inbox · credits chip → paywall, survives Loading/Error (`:430-432`) | InboxHeader actions, composed in every state |
| I3 | Inbox · display options (tune) (`:961-967`) | InboxHeader actions |
| I4 | Inbox · project overflow: open project / rename / delete (`:969-1013`) | InboxHeader actions (project pages only) |
| I5 | Inbox · swipe between Inbox + projects (`HorizontalPager`) | unchanged |
| I6 | Inbox · dots: tap-to-jump, `Role.Tab` names, auto-centre, hidden on 1 page | `AppPageChipRow` (§2.4) |
| I7 | Inbox · Inbox nav tab → page 0 (`homeSignal`) | unchanged; the chip row follows `selectedPage` |
| I8 | Inbox · page anchor survives pushed routes (`:1119-1179`) | unchanged |
| I9 | Inbox · notifications-off banner (`:1207`) | unchanged, below the chips |
| I10 | Inbox · date sections + "group by date" option | `AppSectionHeader` + groups; option unchanged |
| I11 | Inbox · compact vs card layout option | compact = 48dp single-line rows in groups; default = 60dp two-line rows in groups |
| I12 | Inbox · task row: check (30%), details sheet (70%), long-press, due chip, alarm/repeat glyph, source icon, attachments, priority | `TaskRow(Grouped)`: same zones and meta; priority = amber ring + star (bar dropped) |
| I13 | Inbox · section-arrival highlight (`:1664`) | unchanged, segment shape |
| I14 | Inbox · plan nudge (tap, swipe-to-snooze, gated) | last segment of the ANYTIME group, same gating and swipe |
| I15 | Inbox · Add task row → capture dock; empty-state CTA | unchanged (restyled pill) |
| I16 | Inbox · AI source row on sparse Inbox | unchanged |
| I17 | Inbox · capture dock (due rail, Important, AI sources, Smart-Add tint, scrim tiles) | unchanged |
| I18 | Inbox · item sheet host (ItemDetailsSheet, reminder, repeat, note, move, attachments, delete, rename) | unchanged |
| I19 | Inbox · loading skeleton / error / empty | grouped skeleton / unchanged / unchanged |
| I20 | Shell · centre AI FAB: open chat, a11y name, hand-off motion, hidden with capture, Compact only | Ask pill in the plate (§2.5) |
| I21 | Shell · 4 tabs, debounced navigation, labels wrap ≤ 2 lines, measured height | unchanged (4 equal tabs, no gap) |
| I22 | Shell · rail / permanent drawer chat + create FABs (Medium/Expanded) | unchanged |
| D1 | Detail · back / exit edit mode | `AppSoftIconButton` back, same lambda |
| D2 | Detail · name in bar | header card title; bar title appears when the card scrolls off |
| D3 | Detail · open count subtitle ("2 tasks") | header card subtitle "2 left · 4 of 6 done" |
| D4 | Detail · progress line + a11y description | `AppProgressRing` (same description string) |
| D5 | Detail · Ask Gisti (sparkle; seeds chat with this checklist) | `AppAiInviteRow` "Ask Gisti about this list", same lambda |
| D6 | Detail · reminder bell (filled + primary when scheduled) | `AppSoftIconButton`, same states |
| D7 | Detail · overflow sheet (share, edit/rename, fills, separate completed, auto-delete, delete completed, folders on/off, delete checklist) | unchanged |
| D8 | Detail · edit mode: long-press → wiggle, drag reorder, Done | unchanged, in grouped rows |
| D9 | Detail · swipe-to-delete + undo snackbar | unchanged, slab clipped to the segment |
| D10 | Detail · item tap zones, highlight from Calendar, completion pop, note preview, URL chips, meta row | unchanged inside `ChecklistItemCard(Grouped)` |
| D11 | Detail · folders: open, long-press actions, drag, progress label, reminder glyph | `FolderCard(container = Grouped)`: folder icon tile 28, name, progress label, chevron; same callbacks |
| D12 | Detail · view all fills | Single-segment row |
| D13 | Detail · completion banner, recurring nudge, notifications banner | slot 0, under the header card |
| D14 | Detail · inline add row (Smart-Add, chips, attachments, multi-add) + FAB | capture plate: resting button → `QuickCaptureDock` with the same chips / strip / intents |
| D15 | Detail · empty level CTA | unchanged label, action = `openPlate` |
| D16 | Detail · completed section toggle + analytics | grouped completed header (§2.8) |
| D17 | Detail · Weekly view (per-day add, move-to-day) | unchanged |
| D18 | Detail · item details sheet and every sub-sheet / dialog | unchanged |
| D19 | Detail · glass chat dock (classic arm only) | untouched (v1) |
| C1 | Chat · back / drawer menu | header, `AppSoftIconButton` |
| C2 | Chat · credits chip → paywall | input credits chip → pricing sheet → "Get more" → paywall; also Settings → Get more |
| C3 | Chat · settings (deep thinking, response language, default list reset, clear chat, get more) | unchanged + new "How to use AI Chat" row |
| C4 | Chat · pricing band "≈0–3 credits · Why?" → pricing sheet | input credits chip → the same sheet |
| C5 | Chat · "?" features help | Settings row (ChatScreen). The dock hosts keep "?" this pass |
| C6 | Chat · markdown, selection copy | unchanged |
| C7 | Chat · "AI Assistant" label + avatar per reply | avatar beside the last bubble of a run, no label |
| C8 | Chat · Copy / 👍 / 👎 under every reply | latest reply always; older replies on bubble tap; 44dp targets |
| C9 | Chat · Open checklist / Ask AI / Retry / Become Pro | inside the bubble, same chips |
| C10 | Chat · cost badge under user messages | unchanged |
| C11 | Chat · pending choice (AiChoiceResponse, memory toggle, edit) | unchanged; label → avatar |
| C12 | Chat · typing indicator | bubble + avatar |
| C13 | Chat · attachments (source sheet, chips, thumbnails) | unchanged |
| C14 | Chat · hold-to-record, drag-cancel, transcribing state | unchanged (the leading chip hides while transcribing, as Help did) |
| C15 | Chat · day divider, feedback sheet, empty state with suggestions | unchanged |

---

## 6. STRINGS (new keys, EN; RU/HI added by the implementer)

All in `core/designsystem/src/commonMain/composeResources/values/strings.xml`. Write apostrophes literally, never `\'`.

| Key | EN | Note |
|---|---|---|
| `inbox_header_date` | `%1$s, %2$s %3$d` | weekday (`weekday_*`), month abbr (`month_abbr_*`), day. RU: `%1$s, %3$d %2$s`. HI: translator orders. |
| `detail_header_progress` | `%1$d left · %2$d of %3$d done` | open, checked, total. RU: `Осталось %1$d · готово %2$d из %3$d` |
| `detail_ask_about_list` | `Ask Gisti about this list` | |
| `chat_header_title` | `Gisti` | brand, still a resource |
| `chat_header_subtitle` | `Your checklist helper` | |
| `chat_message_show_actions` | `Show actions` | onClickLabel for the tap-to-reveal |

Reused, no change: `nav_chat_fab_content_description` (pill label), `inbox_title`, `inbox_task_count`, `inbox_section_*`, `completed_count`, `collapse`, `expand`, `folder_progress`, `detail_progress_content_description`, `detail_inline_add_placeholder`, `detail_add_task_fab`, `item_create_input_placeholder`, `attachment_add_file_button`, `plan_nudge_title`, `plan_nudge_subtitle`, `chat_credits_label`, `chat_pricing_caption_action`, `chat_settings_get_more`, `chat_features_help_action`, `back`.

Orphaned after B (delete only if grep shows no other user): `chat_assistant_label`, `chat_pricing_caption`, `detail_open_chat_action` (the Ask row uses the new key; keep it if the v1 arm still reads it).

---

## 7. EDGE CASES

- **RU long titles:** checked on the mock at 360 and 320 with text ×1.25 (§9).
  - Header card: "Еженедельные покупки продуктов" takes 3 lines at 360, and at 320 with the ring the subtitle wraps to 2 lines. → max 3 lines + Ellipsis on the title; the narrow rule (hide the tile below `340dp × fontScale`) frees 62dp. <!-- docs-leak-scan: reviewed — code line anchors / RU sample title, not metrics -->
  - Ask row RU wraps to 2 lines → `heightIn(min = 48)`, never `height`.
  - Inbox headline (project name) max 2 lines.
  - Chips: label max 1 line, chip max width 180 + Ellipsis (full title is in the headline).
- **HI (Devanagari):** every new text uses `heightIn`. Section headers are never uppercased. The chip label line box comes from `labelLarge` (lineHeight 20) inside a 36dp chip, so matras fit. The overline weekday in HI can be long; it may wrap (allowed).
- **fontScale 1.5:**
  - Bottom plate = pill ≥ 48 + bar with labels wrapping to 2 lines → measured by `onSizeChanged` (§2.5). Keep the measured-height consume.
  - Header card ring label hidden (≥ 1.3).
  - Task rows wrap per existing rules. Chips grow in height (`heightIn`).
  - Chat input: the credits chip keeps 32dp visual and grows with the text, so the input row may wrap to 2 lines (min 56, grows).
- **Big numbers:** ring label only for `total ≤ 99`. Credits chip `9999` → chip grows; field `weight(1f)` shrinks; the placeholder ellipsises (allowed).
- **Empty states:**
  - **Empty Inbox:** no chips when `pages.size == 1` (as the dots). `EmptyState` with the Add task action (unchanged). Header shows "Inbox" + "0 tasks" subtitle as today.
  - **Empty checklist / empty folder level:** header card with title only (no ring, no subtitle), the Ask row stays (useful: "what should be in this list"), `ChecklistDetailEmptyState` with its CTA → `openPlate`, resting plate at the bottom.
  - **Empty chat:** `ChatEmptyState` unchanged; credits chip in the input.
- **Many projects (12+):** chips scroll; the selected one auto-centres; no zero-width collapse (`LazyRow`, as the dots).
- **Tablet / web ≥ 600dp (rail) and ≥ 840dp (permanent drawer, `trip_web.png`):**
  - No bottom plate. The rail and drawer keep their chat + create FABs (`V2NavigationShell.kt:991-1015`, `:1107-1136`).
  - Inbox: the header, chips and list inherit `adaptiveContentWidth()` (720 cap). Apply it to the header and chip row too, so they line up with the list.
  - Detail: header card + groups capped at 720; the capture plate spans the pane, and `QuickCaptureDock` already caps its field (`QuickCaptureDock.kt:341-342`).
  - Two-pane Expanded (Inbox list + detail): each pane renders its own header; no shared chrome.
- **Dark theme:** warm dark page `#161412` under the cool chrome `#191D25` (ΔL\* +4.2, hue shift accepted in the approved mock). Check the plate shoulders on device; `bottomChromeShoulder()` = the page.
- **RTL:** not shipped, but use `start`/`end` everywhere (segment dividers, avatar side, chip padding).
- **Missed reminder / overdue rows:** `AppDueChip` states unchanged.

---

## 8. IMPLEMENTATION ORDER (sequential, screenshots after each step)

**(a) Tokens + DS components** — owner `@compose-expert`. Files: `theme/Color.kt`, `theme/GistiColors.kt`, `theme/Elevation.kt` (alpha), `theme/Shape.kt`, `theme/Density.kt`, plus new DS files: `AppGroupedSection.kt`, `AppRoundCheckbox.kt`, `AppSectionHeader.kt`, `AppPageChipRow.kt`, `AppProgressRing.kt`, `gisti/AppChecklistHeaderCard.kt`, `AppAiInviteRow.kt` (+ `AppPlanNudge` delegating), `AppSoftIconButton.kt`, and `AskGistiBar` extended.
- Goldens: one host test per new component: light/dark × fontScale 1.0/1.5 × EN/RU/HI, at 320dp and 412dp. Re-record the existing DS goldens (colours move everywhere) and **look at them**: any surface that now reads wrong on sand is a finding, not a re-record.
- Grabli: `RowMinHeightCompact` 44→48 moves every compact list. `tonalElevation` must stay 0 on new Surfaces (`Elevation.kt` rule).

**(b) Shell plate + Inbox** — `@compose-expert`; `@feature-expert` only if an analytics emit moves. Files: `V2NavigationShell.kt` (plate, metrics, traversal), `InboxScreen.kt` (header, chips, grouped list, skeleton), `TaskRow.kt` (Grouped), `AddTaskRow.kt` (restyle); `V2ShellAiButtonTest` rewritten.
- Snapshots: `inbox` 360×660 (compare with canvas B-Inbox), 320×640, 412×915, 840 rail, 1280 drawer; light/dark; fontScale 1.5; RU + HI; capture dock open; 13 pages; empty Inbox; compact layout option; overdue section present.

**(c) Detail** — `@compose-expert`, + `@feature-expert` for the `separateCompleted` default (§4). Files: `ChecklistDetailScreen.kt` (v2 branches only), `FolderCard.kt` (Grouped), domain model + Room default.
- Snapshots: `detail` 360×660 (compare with canvas B-Detail / B-Detail-Dark / B-Detail-RU), 320×640 RU long title, fontScale 1.5, 0-item list, folders level, edit mode (drag mid-flight), swipe mid-flight, active plate with chips + 2 staged attachments + keyboard inset, Weekly view untouched, v1 arm untouched (regression frame).

**(d) Chat** — `@compose-expert`. Files: `ChatScreen.kt`, `ChatHeader.kt`, `ChatMessageBubble.kt`, `ChatInputRow.kt`, `AiChoiceResponse.kt`, `ChatSettingsSheet.kt`, `AiChatPricingHelpSheet.kt`, plus `App.kt` call sites for the `showAvatar` rename.
- Snapshots: `chat` 360×660 (compare with canvas B-Chat), 320×640, dark, fontScale 1.5, RU/HI, transcribing state, pending choice, out-of-credits reply with CTA inside the bubble, 3 consecutive assistant messages (avatar only on the last), the dock host (App.kt) to confirm the "?" there is unchanged.

---

## 9. Проверка (picture check of this spec)

- **Owner-approved mock** (rendered and viewed before publishing):
  - `<session-scratchpad>/shots\sheet-B.png`
  - `...\shots\sheet-B2.png` (B with RU detail)
  - `...\shots\sheet-dark.png` (contains B-Detail-Dark)
- **Narrow check for this spec:** `<session-scratchpad>/shots320\sheet.png` — B Inbox, B Detail RU, B Chat and B Detail dark at 320dp, with task/title/bubble text ×1.25. Findings moved into the spec:
  - The header-card title + ring squeeze the RU title to 3 lines and wrap the subtitle → narrow rule + 3-line cap (§2.6, §7).
  - The Ask row RU wraps to 2 lines → `heightIn` (§2.7).
  - The Weekend chip is clipped at the edge (scroll hint, intended).
  - Chat holds at 320.
- **Contrast:** `scratchpad\contrast.js` and `scratchpad\contrast2.js` (every pair in §1 measured; the idle-label alpha fix came from this run).
- **Not re-rendered:** the deltas against the mock (no section counts, pill "Ask Gisti" without mic, resting detail plate without attach, ring-label and narrow rules). They remove elements rather than add them. A real fontScale 1.5 run (the mock uses px text) was not done → step (a)/(b) goldens are the first true fontScale check.

## 10. Эталон

- Raster frames for side-by-side comparison: `...\scratchpad\shots\B-Inbox.png`, `B-Detail.png`, `B-Chat.png`, `B-Detail-Dark.png`, `B-Detail-RU.png`.
- Before: `composeApp\build\store-listing\raw\{inbox,detail,chat}.png`.
- ⚠️ Deltas vs the eталон are listed in §9 and are intentional.

## 11. Grabli (top 3)

1. **One container across lazy items.** Draw the group per segment (§2.1). Never wrap rows in a `Column`/`Card` — reorder, swipe and `animateItem` break.
2. **Shell plate height is the measured plate, not the bar.** Move `onSizeChanged` and the `background(shoulder).clip(SheetTop)` pair to the plate together, or the consumed inset and the shoulder hole come back (`V2NavigationShell.kt:539-560`, `:815-846`).
3. **Two nodes with the same accessible name.** The resting capture plate is ONE button labelled "Add a task"; the empty-state CTA keeps "Add task". Do not give the plate's "+" its own semantics.

Out of scope, noticed: `chat_pricing_layer1_*` still says "Standard commands — free, processed on your device", but Layer 1 is disconnected (rule `ai-chat`). The pricing sheet copy is stale — product / copy owner.
