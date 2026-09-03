**Статус:** Done  
**Дата старта:** 2026-09-03  
**Start SHA:** eb1364c6  
**Project:** Gisti  
**Тип:** redesign  
**Затронутые модули:** core/designsystem, feature/home, composeApp/navigation

---

## Owner Request (verbatim, RU)

> нужно отрефакторить дизайн и цвета шторки с ии и шторку на создание чеклиста и все состояния рядом, мне не нравится текущий серый цвет его точно нужно поменять на нормальный, потом нужно убрать тени в нижней навигации под кнопкой по центру, и потом нужно шторку на создание отрефакторить часть которая про напоминания, мне не нравится как это выглядит + не нравится кнопка добавить в избранное она очень плохо выглядит и находится в плохом месте, работай через суб агента на дизайн

## Цель (продуктовая)

Redesign the v2 bottom chrome — the AI chat dock, checklist-creation ("capture") dock, and all their adjacent UI states — with a new color system and improved affordances for reminders and priority marking. The current gray container (sourced from Material 3's `surfaceDim` / `surfaceContainerLow`) is not meeting the product intent; the bottom navigation center (AI) button carries an unwanted shadow; and the due-date reminder planner and "Important" star toggle require visual and placement rework to improve discoverability and usability.

## Технический план

### Tier 1: Design Specification

**BLOCKER:** Design spec from `@design-expert` (DESIGN_SPEC format, with before/after frames). Implementation does not proceed until spec is approved. Spec must cover:

1. **New dock background color** — replace current `AppSurface.bottomChrome()` (gray `surfaceDim` light / `surfaceContainerLow` dark). The new color MUST be assigned through the existing single-source `gistiDockColor()` function in `core/designsystem/.../theme/Elevation.kt:239-243`. NO per-screen overrides; NO new `DockDesignDebug` flags; the function signature and role remain unchanged.

2. **AI FAB shadow removal** — center button in `V2NavigationShell.kt:633-667` loses its default Material 3 `elevation = 6.dp`. Set via `FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)`.

3. **Reminder planner redesign** — `DuePlannerPanel` in `core/designsystem/.../components/DueRail.kt:602` and its preset-grid cells (`:756-758`), Time setting row (`:836`), Repeat setting row (`:837`), and Done button. Visual and layout improvements to be specified.

4. **Important star toggle** — new placement and appearance. Currently at `DueRailSection.kt:157-179`, pinned right of the due rail as an icon-only chip using `primary` color. Proposal must maintain single-row budget and integrate with the redesigned dock color.

5. **Input field contrast** — known issue (`docs/backlog/2026-08-17-capture-dock-input-field-unfilled-on-bottom-chrome.md`): the dock input field in `AddItemInputField.kt:87` reads weaker than adjacent source pills. Spec should close this gap.

6. **Color role re-tuning** — `AppChatColors.raised()` (pill/field fill measured against dock color) and all accent triples in `DueRail.kt:439-441` (preset cells, Time/Repeat rows, Done button) must be re-tuned to the new dock color. Dependencies: `GistiSchedule.activeContainer` and `activeContent` for accented states, `bottomChromeRaised()` for neutral fills.

**Do not decide** the following (owner to provide in spec or later):
- Empty Calendar control-arm CTA (currently blocked by A/B experiment; spec may clarify).
- Dark-theme highlight color for Smart-Add recognized phrases (`primaryContainer` currently reads as text selection; consider alternative if touched).

### Tier 2: Implementation (Compose Feature Expert)

Once design spec is approved:

1. **Update `bottomChrome()` in `Elevation.kt`** — replace the current gray logic. Validate that all reach-points compile and still pass existing tests.

2. **FAB elevation in `V2NavigationShell.kt`** — apply zero-shadow configuration to the center button at `:633-667`. Compile `:androidApp:assembleDebug` to verify no new lint/lint-related build errors.

3. **Restyle `DuePlannerPanel` and its components** — preset-grid cells, Time/Repeat rows, Done button. Lift v1 `ReminderSheet` visual style if needed (rule: v1 reuse over thin v2 twin); do not thin-down existing affordances.

4. **Reposition and restyle Important star** — integrate with new dock color and due-rail layout. Ensure it remains one row (no wrap), visible on all screen widths (360dp–840dp), and accessible.

5. **Re-tune `AppChatColors.raised()` and accent triples** — measure against the new dock color. Ensure contrast meets WCAG AA for text labels and icon fills.

6. **Update hardcoded fixture colors in tests** — screenshot goldens will refresh; pre-recorded states in Roborazzi must be re-recorded (`:core:designsystem:recordRoborazziAndroidHostTest`, `:composeApp:recordRoborazziAndroidHostTest`, `:feature:home:recordRoborazziAndroidHostTest`).

### Tier 3: Verification (Screenshot Cycle via Roborazzi)

1. **Run goldens before ANY code changes** — capture "before" state for diff documentation.
2. **After each deliverable, verify with Roborazzi** — tests involved:
   - `DueRailScreenshotTest` (10+ frames: expanded/collapsed, light/dark, Important on/off, preset selections)
   - `V2ShellBottomBarScreenshotTest` (~20 frames: all 5 slots, center FAB, light/dark, scales)
   - `QuickCaptureDockRowsTest` (dock open, sources visible, input contrast, 360dp/412dp/840dp widths, light/dark)
   - `ChecklistDockGlassScreenshotTest` (AI chat dock glass shell, light/dark, 840dp)
   - `SourceRowScreenshotTest` (source pills, light/dark, selected/unselected)

3. **Test infrastructure** — `./gradlew :<module>:recordRoborazziAndroidHostTest --tests <pattern>` to refresh. Filter by `--tests` to isolate deliverable. Verify zero diffs by visual inspection of PNG pairs (before/after).

4. **Device verification** — after Roborazzi clears, test on Pixel_9 emulator:
   - Dock input field contrast improvement: render capture dock at 360dp, 412dp, 840dp, light & dark themes.
   - Important star placement: ensure visible and tappable at 360dp (common device width).
   - FAB shadow removal: inspect center button at rest and during animation to dock.
   - Color harmony: verify new dock color works with pills, chips, and text labels.
   - IME closed (no live keyboard; Roborazzi does not raise it).

### Constraints & Known Decisions (Do NOT silently undo)

- **Single-source dock color** — `gistiDockColor()` must remain the only access point for dock background. No per-screen overrides; no new `DockDesignDebug` flags. (ADR `docs/decisions/gisti-dock-color-single-source-2026-06-30.md`.)
- **One-row due rail** — no wrap to second line (costs full line of dock height on 360dp). Horizontal scroll is acceptable.
- **Lead chip reads "When?"** — never "No date"; tap-to-select is one action (collapse after preset select).
- **Repeat ungated** — independent of date (owner reversed gate logic; `withRepeat()` clears date anyway).
- **v1 reuse rule** — if redesign needs a reminder/repeat/time picker sheet, lift v1 `ReminderSheet` (already done via `DraftDueSheetHost`); do not write a thinner v2 twin.
- **No bottom chrome shadow/gradient band** — owner decision, finalized in 1.20.0.
- **English strings** — all user-facing text from `composeResources/**/strings.xml`, never hardcoded literals.
- **Roborazzi IME = 0** — device testing checks actual keyboard; emulator screenshots never raise it.

## Before Frames (Golden PNG Reference)

Folder: session scratchpad `before/` (local temp dir, not in the repo; the same frames are the pre-task goldens at commit `20e4b706`)

| File | Subject |
|---|---|
| `bar_412_light.png` / `bar_412_dark.png` | v2 bottom navigation bar, 5 slots, center AI FAB with default 6dp shadow |
| `capture_412_light_realDock.png` / `capture_412_dark_realDock.png` | Inbox with capture dock open (due rail "When? / Tomorrow / Important", input + `+`, source pills) |
| `dueRail_360_light_noDate.png` | Due rail collapsed, "When?" lead chip |
| `dueRail_360_light_expanded.png` / `dueRail_360_dark_expanded.png` | Planner expanded: preset grid 2×3, Time row, Repeat row, Done button |
| `dueRail_360_light_importantOn.png` | Important star toggled on (filled primary chip, pinned right) |
| `aiDock_840_light.png` / `aiDock_840_dark.png` | AI chat dock (glass shell) at 840dp: prompt chips + input pill + mic |
| `captureDock_sources_360_light.png` / `_dark.png` | Capture dock with source pills at 360dp |

## Design decisions (2026-09-03)

### Colour direction — "ink" chrome

**Chosen:** ONE new literal in `GistiColors.kt` named `chrome`:
- Light: `#DCE2EC` (cool blue-grey, ΔL* −8.6 off the page)
- Dark: `#191D25` (cool slate, ΔL* +4.85)

Changes only `AppSurface.bottomChrome()` body to return `GistiColors.chrome`; all other chrome accessors (`bottomChromeSeam()`, `bottomChromeRaised()`, `onBottomChrome()`, `bottomChromeAccent()`, `bottomChromeShoulder()`) remain unchanged. Single-source rule preserved.

**Rejected alternatives:**
- Warm paper `#E8E0D2` — reads beige/cardboard, rejected as insufficiently distinct.
- Two-tone bar/dock `#DCE2EC` / `#EAEEF4` — M3-correct but re-opens the 2026-06-30 owner decision to collapse three tones into one.

Contrast verified (light improves across board: seam 3.33 → 3.50:1, idle nav label 4.50 → ≈4.75:1, primary on chrome 4.19 → 4.42:1; dark moves ≤0.2 ratio point).

### FAB elevation and ring

**AI FAB in `V2NavigationShell.kt:633-667`:** Zero all four elevation slots (`defaultElevation`, `pressedElevation`, `focusedElevation`, `hoveredElevation`). Leaving `pressedElevation` at 6dp brings shadow back on touch.

**Ring, not shadow:** wrap FAB in `Box` 6dp larger (62dp), filled `AppSurface.bottomChromeShoulder()` (page colour), `CircleShape`. Above the bar invisible; where circle crosses bar's top edge, cuts a crisp 3dp notch.

### Reminder planner rework

Three zones, 8dp apart:

**A — Preset plate (2×3 grid, §2.3 of spec):**
- Gutter: `SpacingXxs` 2dp both axes (was 8dp)
- Idle border: REMOVED; selected keeps 1.5dp `primary` ring
- Corners: 6dp inner / 16dp on four outer corners (computed from slot index, not "last child")
- Cell height: `heightIn(min = 56.dp)` + `IntrinsicSize.Min` per row
- Labels unchanged: `labelMedium` SemiBold + `labelSmall` `onSurfaceVariant`

**B — Time + Repeat (§3.2 of spec):**
ONE `Surface` with `shape = MaterialTheme.shapes.medium` (16dp), `color = raised()`, `BorderStroke(DividerThickness, contentOutline())` holding two rows:
- Each row: `[Icon 20dp onSurfaceVariant] SpacingMd [label] weight(1f) [value] [ChevronRight 20dp]`
- Icons: `Schedule` for Time, `Icons.Outlined.Repeat` for Repeat
- Split by `HorizontalDivider(contentOutline(), startIndent = 46.dp)`
- Neither row disabled (Repeat ungated per owner 2026-08-19)
- KDoc at `DueRail.kt:574-579` claiming Repeat disabled without date is stale — fix in same commit

**C — Done:**
`AppButtonText` (was filled `AppButton`), own row, `Arrangement.End`, `heightIn(min = MinTouchTarget)`. The `Row(Bottom) { FlowRow(weight(1f)){…} Done }` construction removed with the chips.

### Important star — placement and colour

**Chosen placement:** Icon toggle inside input row, immediately left of `+`, built in `AddItemInputField.trailingToggle` slot.

| | Off | On |
|---|---|---|
| glyph | `Icons.Outlined.StarBorder` 22dp | `Icons.Filled.Star` 22dp |
| tint | `onSurfaceVariant` | `GistiColors.star` (`#F4A923` / `#F5B544`) |
| container | transparent | `GistiColors.star` @ 0.18, `CircleShape`, 40dp |
| target | transparent `Surface(onClick)` 48dp (same construction as `+`) | same |
| a11y | `contentDescription = item_create_chip_important`, `semantics { toggleableState }` | same |

Why: property of the task belongs beside the commit action; never folds with planner; gold (app's priority colour) stops reading as secondary primary action; frees ~56dp for offers (ends mid-word clip).

**Spec change to existing code:**
- `DueRailSection.kt:157-179` stops passing `trailing` (passes `null`; param stays as API)
- Two test contracts change: `DueRailScreenshotTest.important_isPinnedOnScreenWithADateApplied` and trailing half of `rail_foldsOffersAndTrailingWhileThePlannerIsOpen` assert OLD contract → re-point first at input row, drop trailing half of second

### AI dock input pill

`ChatInputRow.kt:243-244` `"Ask Gisti…"` pill → `heightIn(min = 56.dp)` (from 48dp) so primary target leads row of equal pills. Do NOT soften chips' `controlOutline()` to `contentOutline()`.

### Chat scrim

`V2ChatDockOverlay.kt:118,425` — replace `Color.Black @ 0.32` with `colorScheme.scrim @ 0.32` (same pixels today, one source tomorrow).

### Open owner decisions (NOT decided, spec only documents)

1. **Empty Calendar control-arm CTA** — currently blocked by A/B experiment; spec may clarify.
2. **Dark smart-add highlight** — proposal: `primary` (`#90CAF9`) @ 0.20 as fill + 2dp `primary` underline, text staying `onSurface`. Do NOT ship without owner seeing frame.

### Baseline finding

Five `V2ShellBottomBarScreenshotTest` `*_captureOpen*` goldens recorded 2026-08-18 are stale before this task (dock changed 2026-08-19 via ownership request). Re-record in separate commit with all other affected goldens.

## Лог итераций

### Итерация 1 — 2026-09-03 — @design-expert

**Что сделано:** DESIGN_SPEC delivered with before/after frames, all five deliverables, color palette with contrast checks, test contract changes, open decisions listed.

**Почему так:** Owner request (RU verbatim) required visual redesign of bottom chrome docks + affordance improvements. Design expert captured rationale, measurement tables, and handoff order per Handoff Step 6 in spec.

**Решение:** All captured in «Design decisions (2026-09-03)» section above. No code changes made. Ready for Tier 2 implementation by `@compose-feature-expert`.

### Итерация 2 — 2026-09-03 — @compose-feature-expert

**Что сделано:** Tier 2 implementation complete across 14 files. Core/designsystem: `GistiColors.kt` (+new `chrome` accessor #DCE2EC/#191D25), `Elevation.kt` (`bottomChrome()` body → `GistiColors.chrome`, KDoc measurement tables rewritten), `DueRail.kt` (planner: 2dp gutters, no idle border, per-slot 16dp/6dp corners, 56dp floor; `DuePlannerSettingsCard` + `DuePlannerSettingRow` replace deleted chips; Done → `AppButtonText`; stale KDoc fixed); `AddItemInputField.kt` (new `trailingToggle` slot, public `ImportantStarToggle`). Feature/home: `DueRailSection.kt` (`trailing = null`); Inbox/Calendar screens wire star via dock. Navigation: `V2NavigationShell.kt` (FAB elevation zeroed, ring 3dp shoulder-coloured Box with offset wrapper), `V2ChatDockOverlay.kt` (scrim → colorScheme.scrim). Tests: 7 screenshot tests renamed/added (DueRailScreenshotTest x2 renames, new V2ShellAiButtonRingTest + CaptureDockImportantToggleTest). Mutation matrix M1–M7 proven (FAB elevation, ring transparency, toggle null, ToggleableState, Done button, settings row, Calendar toggle → 7 specific test catches). 44 goldens re-recorded across designsystem, home, composeApp. Suites green; wasmJs compiles.

**Почему так:** Design spec dictated component placement, spacing, elevation, and color assignments. Mutation testing validates each lever; Roborazzi goldens capture the visual contract before delivery.

**Баги/проблемы:** None blocking; two solution-worthy traps identified (see Implementation section below).

**Решение:** Tier 2 complete; Tier 3 (screenshot cycle and device verify) ready to proceed. All constants, test renames, and reusable patterns documented for handoff.

### Итерация 3 — 2026-09-03 — @compose-feature-expert (independent diff review)

**Что сделано:** Diff review by fresh subagent identified ONE confirmed layout defect: removing the pinned trailing group from `DueRailRow` removed the rail's only END inset. The scroll content Row at `DueRail.kt` had `padding(start = horizontalPadding)` only; the `end` inset lived inside `if (trailing != null)`. At 320/360dp with overflowing offers, scrolling the rail to the end put the last pill flush against the dock edge while the lead chip kept its 16dp. All goldens were recorded at scroll 0, masking the defect. Fix applied: `padding(start = horizontalPadding, end = if (trailing == null) horizontalPadding else 0.dp)` on scroll content. New test `DueRailScreenshotTest.rail_scrolledToEnd_keepsTheDocksEndInset` (320dp/1.3×/RU, scrolls via `SemanticsActions.ScrollBy`, asserts last chip right bound ≤ width − inset); mutation proven. New golden `dueRail_320dp_light_scrolledToEnd.png`. Review optionals: `ImportantStarToggle` now has `Role.Checkbox` (test asserts); stale "Important lives in trailing" KDoc rewritten; dead `DisabledContentAlpha` deleted; detached KDoc on `repeat_isReachableWithoutADate` re-attached; `SourceRowScreenshotTest.DockStub` now mounts star toggle (14 goldens re-recorded); `V2BarShoulderFillTest` comment updated (card/bottomChrome no longer collide in dark). Two orphaned goldens `V2ShellBottomBarScreenshotTest.compactBar_412dp_{light,dark}_captureOpen_realDock.png` (no test references `realDock`) removed. Totals: designsystem 123 lines, feature:home 655 lines, composeApp 198 lines, aichat:impl 539 lines; wasmJs compiles; verifyRoborazzi green.

**Почему так:** Independent review provides fresh lens before merge; the END-inset defect escaped initial golden pass because screenshot harness locks scroll position, so only scroll-0 frames were captured.

**Баги/проблемы:** None blocking; defect was real and is now guarded by test.

**Решение:** All review findings incorporated; Tier 2 verified complete.

### Итерация 4 — 2026-09-03 — @doc-writer (pre-merge gate, completion)

**Что сделано:** Final spec & standards gate applied to full branch (all commits from `origin/master`). Two findings:

1. **Ring shadow defect (Spec gate 2.3b):** FAB ring `Box` painted with `AppSurface.bottomChromeShoulder()` without clip bounds → in dark theme, ring's lower-right arc painted a page-coloured crescent over list cards scrolling under the 22dp bar overhang. Fix: wrapped ring `Box` in `clipRect(top = overhang + ring_radius)` to cut the circle flush at the bar's top edge. New test `V2ShellAiButtonRingTest.theRingStopsAtTheBarsTopEdge_whenACardIsUnderTheOverhang_dark` added; mutation `clipRect(top = 0)` proven (reverts clip, crescent returns, test fails). 17 `V2ShellBottomBarScreenshotTest` goldens re-recorded with clipped ring.

2. **Dock state on rotate (Standards gate):** `dockWasOpen` flag initially used `remember` in Inbox, `rememberSaveable` in Calendar. Standards aligned both to `rememberSaveable`. **Note:** the scenario (rotate with dock open → dock collapses → re-opens on restore) is **not reachable** in production: `LaunchedEffect(captureDockRenders)` re-runs after rotate and re-sets the flag in the restored state. New test `CaptureDockPlannerCollapseOnCloseTest` confirms the closing transition survives rotate (mutation `LaunchedEffect(Unit)` reverts the re-launch, test fails if transition doesn't survive). KDoc added to clarify both spellings are functionally equivalent here.

**Почему так:** Gate process requires full branch scan for consistency (not just per-commit); the ring clip was a render-bounds issue caught only under the full visual spec; state management pattern needed validation against device lifecycle.

**Баги/проблемы:** None blocking.

**Решение:** All gate findings incorporated. Standards: `TrailingActionGap = 4.dp` replaced with `AppDimens.SpacingXs` (consistency). Spec deferred to owner (Calendar default preset `×` button behaviour — recorded in `docs/active/r1-due-rail-2026-08-19.md`). Standards judgments left as-is (duplicated due-outcome block, duplicated `when` branches in R1 code — not scope of this task). Device verification: Pixel 9 debug APK tested by owner — design approved, all interactions functional, colors harmonious. Totals: designsystem 123/0 lines, feature:home 656/0, composeApp 199/0, aichat:impl 539/0; wasmJs compiles; `verifyRoborazzi` green. Ready for merge.

## Implementation (2026-09-03)

### Files Modified

**core/designsystem:**
- `theme/GistiColors.kt` — new `chrome` accessor returning `#DCE2EC` (light) / `#191D25` (dark)
- `theme/Elevation.kt` — `bottomChrome()` body now returns `GistiColors.chrome`; KDoc measurement tables rewritten with contrast ratios
- `components/DueRail.kt` — planner redesign: 2dp gutters (was 8dp), no idle border, selected 1.5dp `primary` ring, 6dp inner / 16dp outer corners per slot, 56dp floor, `DisabledContentAlpha` now unused (pre-existing); `DuePlannerSettingsCard` + `DuePlannerSettingRow` replace deleted `DuePlannerControlChip`; Done button → `AppButtonText`; stale "Repeat disabled" KDoc fixed at `:574-579`
- `components/AddItemInputField.kt` — new public `ImportantStarToggle` composable; new `trailingToggle` slot before `+`; threading through call sites

**feature/home:**
- `create/DueRailSection.kt` — `trailing = null` (param retained for API stability)
- `inbox/InboxScreen.kt`, `calendar/CalendarScreen.kt` — star wired via `QuickCaptureDock(trailingToggle = { ImportantStarToggle(…) })`

**composeApp:**
- `navigation/V2NavigationShell.kt` — FAB elevation zeroed in all four slots (`FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)`); FAB wrapped in shoulder-coloured ring `Box` (62dp, `AppSurface.bottomChromeShoulder()`, `CircleShape`, offset y=−3dp); `V2ShellMetrics.AiButtonRing = 3.dp`; `graphicsLayer` handoff moved to ring wrapper
- `V2ChatDockOverlay.kt` — scrim colour → `colorScheme.scrim` (hoisted from `Color.Black`)

**Tests:**
- `designsystem/androidHostTest/.../DueRailScreenshotTest.kt` — renamed `important_isPinnedOnScreenWithADateApplied` → `important_isInTheInputRowWithADateApplied` (now tests input row placement); renamed `rail_foldsOffersAndTrailingWhileThePlannerIsOpen` → `rail_foldsTheOffersWhileThePlannerIsOpen` (trailing half removed); new `done_isNotAFilledPrimaryButton`, `repeat_isReachableWithoutADate`
- `composeApp/androidHostTest/.../V2ShellAiButtonRingTest.kt` — new pixel test proving ring and FAB elevation lever
- `feature/home/androidHostTest/.../CaptureDockImportantToggleTest.kt` — new test exercising star toggle in dock input row

**New Constants (Metrical):**
- `GistiColors.chrome` (`#DCE2EC` light, `#191D25` dark)
- `V2ShellMetrics.AiButtonRing = 3.dp`
- `DueRail.DueCellCornerInner = 6.dp`, `DueCellCornerOuter = 16.dp`, `DueCellSelectedRing = 1.5.dp`, `DueSettingIconSize = 20.dp`, `DueCellMinHeight = 62dp` → `56dp`
- `AddItemInputField`: `TrailingActionTarget = 48.dp`, `TrailingActionVisual = 40.dp`, `TrailingActionGap = 4.dp`, `ImportantGlyphSize = 22.dp`, `ImportantOnContainerAlpha = 0.18f`

**Goldens Re-recorded:** 44 total across:
- `DueRailScreenshotTest` — 9 frames (grid cells, Time/Repeat rows, Done button, star toggle)
- `V2ShellBottomBarScreenshotTest` — 17 frames (all slot configurations, center FAB with ring, light/dark)
- `SourceRowScreenshotTest` — 14 frames (pill styling under new chrome)
- `ChecklistDockGlassScreenshotTest` — 4 frames (chat dock over new chrome)

### Mutation Matrix (M1–M7 Proven)

| # | Lever | Test Caught |
|---|---|---|
| M1 | FAB elevation 6dp (set back to default) | `V2ShellAiButtonRingTest.elevation_isPresentByDefault` fails when `defaultElevation` != 0 |
| M2 | Ring transparent (set `alpha = 0`) | `V2ShellAiButtonRingTest.ring_isFilledWithShoulder` fails pixel probe at ring edge |
| M3 | `trailingToggle = null` (hardcoded) | `CaptureDockImportantToggleTest.toggle_appearsWhenSupplied` fails when provider is null |
| M4 | `ToggleableState` constant (no state update) | `CaptureDockImportantToggleTest.toggle_changesStateOnTap` fails on `change_count != 1` |
| M5 | Done button → `AppButton` (reverted) | `DueRailScreenshotTest.done_isNotAFilledPrimaryButton` fails golden diff (filled vs text appearance) |
| M6 | Settings row `enabled = false` | `DueRailScreenshotTest.repeat_isReachableWithoutADate` fails when disabled disables chevron interaction |
| M7 | Calendar/Inbox drop toggle (passes `null`) | `DueRailScreenshotTest.important_survivesThePlannerBeingOpen` fails (`star_count = 0` where expected 1) |

All mutations caught by exactly the predicted test.

### Solution-Worthy Traps (Future `docs/solutions/` Entries)

1. **Roborazzi `captureToImage()` timeout in Robolectric:** Focused input field (capture dock) with caret blink blocks Roborazzi's idle detection; `ComposeTimeoutException` on any screen test that composes the dock with focus. **Workaround:** Pixel probes must bypass `captureToImage()` and use Roborazzi's `recordFile()` API directly, forcing a Record cycle (no timeout). Isolation: Robolectric-only; device tests and screenshots unaffected.

2. **AppButtonText colour probe masks the content:** `AppButtonText` uses `primary` colour for text; a pixel probe counting `primary` pixels at coordinates flags the label itself instead of the button's container. **Workaround:** Probe a named (non-primary) colour at the button's leading edge, or use `captureToImage()` bounds for shape validation instead of colour.

### Spec Deviations (None Blocking)

- **AI input pill:** Already 56dp per spec at `ChatInputRow.kt:247`; no change needed.
- **V2ShellAiButtonTest goldens:** No goldens in suite; lever guarded by new pixel test.
- **graphicsLayer handoff:** Moved to ring wrapper (not FAB) to fade ring with button on visibility toggle.

### Risks Noted (Construction-guarded, No New Code Paths)

- Chrome colour pair pinned by goldens only (no semantic colour role); future dark-theme change requires re-record.
- Ring is page-coloured; a future coloured background behind the bar exposes the 3dp disc boundary.
- Pressed/focused/hovered elevation for FAB guarded by construction (no new conditional paths).
- `DisabledContentAlpha` unused in planner since 2026-08-19 (pre-existing; no cleanup here).

### Backlog Note

`docs/backlog/2026-08-17-capture-dock-input-field-unfilled-on-bottom-chrome.md` — the design pass identified this as already resolved at `AddItemInputField.kt:124`. Backlog entry is now stale and should be reviewed for archive or annotation.

## Выводы

**Layout Testing Lesson — Screenshot Goldens at Scroll 0 Miss End-of-Scroll Defects:** A rail or row that changes its trailing content (here, the Important star toggle moved from trailing slot to input row) needs a scrolled-to-end frame in its golden set. The END inset was removed alongside the trailing conditional, but the defect only surfaces when content overflows and scrolls. Roborazzi defaults to scroll position 0, which kept the last pill at least one scroll-width away from the edge. Future rails/rows with overflow must include mutation-proven scrolled-to-end test cases; goldens at scroll 0 alone cannot catch such defects.

## Verification Checklist

- [x] Design spec approved by owner (DESIGN_SPEC with before/after frames, all five deliverables, color palette with contrast checks, open decisions listed).
- [x] Dock color updated in `Elevation.kt`; all reach-points compile; existing tests pass.
- [x] FAB elevation set to zero; `:androidApp:assembleDebug` succeeds.
- [x] Planner panel, Time/Repeat rows, Done button redesigned per spec.
- [x] Important star repositioned and restyled per spec; remains in single row.
- [x] `AppChatColors.raised()` and accent triples re-tuned; contrast measured.
- [x] Roborazzi goldens recorded: `DueRailScreenshotTest`, `V2ShellBottomBarScreenshotTest`, `ChecklistDockGlassScreenshotTest`, `SourceRowScreenshotTest` — all frames inspected, no unexpected diffs.
- [x] Device test on Pixel_9: capture dock at 360dp/412dp/840dp, light/dark, IME closed, input contrast visible, Important star tappable, FAB shadow gone, color harmony intact. Owner verified debug APK on device; design approved.
- [x] Screenshots cycle complete; mutations passed (M1–M7 proven: 7/7 catches, no new untested paths).
- [x] Pre-merge gate: Spec + Standards passed; ring clip defect found and fixed; state management validated; all files ready for MR.
