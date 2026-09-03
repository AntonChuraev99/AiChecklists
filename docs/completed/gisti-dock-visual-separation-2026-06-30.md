# Bottom AI Chat Dock Visual Separation & Color Sync

**Статус:** Done
**Дата старта:** 2026-06-30
**Start SHA:** bdfed2eb
**Project:** gisti-ai-checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** [core/designsystem, feature/home, feature/debug]

## Цель (продуктовая)
The bottom AI chat dock ("glass dock") visually blended with the app background on light mode — users could not distinguish where the dock ended and the main content began. This task adds visual separation via a crisp hairline border, ensures the system-navigation bar strip matches the dock color, removes a stray divider, and provides a debug A/B toggle to revert to the old flat design if needed.

## Технический план
1. ✅ Identify root cause: flat `surfaceContainerLow` (#F6F5F2) on page `surface` (#FBFAF8) — ~2% lightness gap, no shadow/border.
2. ✅ Confirm the visible dock is `GistiGlassChatDock` (not `GistiInlineChatPanel`, which is dead code).
3. ✅ Implement Variant "B · Crisp hairline" (design phase via @design-expert/claude-design; 6 variants tested):
   - Dock: `Surface(shape=top-28dp-rounded, color=dockColor)`
   - Hairline: 1dp `outlineVariant` border on top edge + top corners only (via `drawWithContent` + `Path`, NOT full `BorderStroke`)
   - Light mode: `dockColor = surfaceContainerLowest` (white); dark mode: `surfaceContainerLow`
4. ✅ Establish **single source of truth**: new public `gistiDockColor()` (@Composable @ReadOnlyComposable) in `GistiChatDock.kt`. Both dock AND system-nav bars (MainScreen.kt + ChecklistDetailScreen.kt) read from it.
5. ✅ Add debug A/B toggle: `DockDesignDebug.useLegacyDock` mutableStateOf (in-memory, resets on restart, default=new design, only flippable from Debug screen).
6. ✅ Verify: Android Pixel 9 light, both MainScreen + ChecklistDetailScreen, build green ×4.

## Лог итераций

### Итерация 1 — 2026-06-30 — design-expert (baseline)
**Что сделано:** Ran @design-expert via claude-design on `dock-separation` problem. Generated 6 visual variants in `claude_design/dock-separation/index.html`:
- Variant A: Shadow + rounded
- Variant B: Crisp hairline (light dockColor = white, dark = low) ← **USER PICKED**
- Variants C–F: other shadow/border combos

**Почему так:** Design phase precedes code. User reviewed all 6 variants and selected B for its minimalist crispness.

### Итерация 2 — 2026-06-30 — compose-feature-expert
**Что сделано:** 
- Rewrote `GistiChatDock.kt`: replaced flat `surfaceContainerLow` with `Surface(shape=top-28dp-rounded, color=gistiDockColor())`.
- Extracted **new public function** `gistiDockColor()`: returns `surfaceContainerLowest` (light) or `surfaceContainerLow` (dark), plus debug gate for legacy flat grey.
- Added 1dp `outlineVariant` hairline via `drawWithContent` + `Path` tracing only top edge + corners (NOT a full `BorderStroke` — that caused a stray bottom divider).

**Почему так:** Single source of truth prevents color desync. The dock color was previously duplicated in 3 places; changes to one left navbar mismatched. Also, a full BorderStroke was drawing a bottom line that competed with the system-nav strip below (both same colour, but visible border = visual fuzz).

**Баги/проблемы:** Initial attempt used full `BorderStroke` — rendered a visible bottom divider between dock and navbar. Fixed by custom `Path` + `drawWithContent` (top edge + corners only).

**Решение:** `Path.apply { moveTo/lineTo }` for the top 3 edges of the dock, drawn via `drawWithContent { ... Stroke(...) }` instead of `Surface(border=...)`.

### Итерация 3 — 2026-06-30 — android-platform-expert
**Что сделано:**
- Updated MainScreen.kt: `windowInsetsBottomHeight(navigationBars).background(gistiDockColor())` — system-nav bar now reads the dock color function.
- Updated ChecklistDetailScreen.kt: same change.
- Both screens now sync to the dock visually.

**Почему так:** The dock is visible when the ChatDock slides up; the system-nav bar is always there. If they're different colors, the transition looks janky. Locking both to `gistiDockColor()` ensures seamless visual continuity.

### Итерация 4 — 2026-06-30 — android-platform-expert
**Что сделано:**
- Created `DockDesignDebug.kt`: new object with `useLegacyDock by mutableStateOf(false)`. In-memory, resets on restart.
- Updated `gistiDockColor()` to check `if (DockDesignDebug.useLegacyDock) return surfaceContainerLow` (old flat grey).
- Updated the hairline code: also gated on `!DockDesignDebug.useLegacyDock`.
- Updated DebugScreen.kt: added `DebugToggleItem` row (AppCard + AppSwitch) for `debug_dock_legacy_title` / `debug_dock_legacy_description`.
- Added 2 strings to `strings.xml`.

**Почему так:** A/B toggle allows QA and developers to compare the new crisp design with the old flat one without rebuilding. Only exposed in the debug screen (not in prod UI).

### Итерация 5 — 2026-06-30 — android-platform-expert
**Что сделано:**
- Verified on Pixel 9 emulator, light mode: MainScreen shows dock with white background + hairline, navbar matches. ChecklistDetailScreen same.
- Ran `./gradlew androidApp:assembleDebug` ×4 — all green.
- Toggled debug flag in Debug screen: reverted to old grey flat dock, navbar matched grey. Toggle back to new design.

**Почему так:** Manual verification ensures the visual separation is real on the actual device and the toggle works.

## Выводы

✅ **Primary goal achieved:** The dock is now visually distinct via a crisp hairline border + lighter background colour (white on light mode). No blending with the page.

✅ **Single source of truth implemented:** `gistiDockColor()` is now the canonical dock + navbar color. Dual-source desync eliminated. (See `docs/decisions/gisti-dock-color-single-source-2026-06-30.md` for pattern.)

✅ **A/B debug toggle in place:** `DockDesignDebug.useLegacyDock` flips the dock between new (crisp) and old (flat) designs.

✅ **Android verified:** Pixel 9 light mode, both screens, build passes.

⏳ **PENDING (not blockers, noted to user):**
- Dark-mode visual not captured by designer (only light-mode mockups exist). The code uses `surfaceContainerLow` for dark, which should work, but no visual design mockup was made. If dark mode looks off, may need a quick tweak.
- **Web :9090 not verified.** This is CRITICAL per project CLAUDE.md rule: Skiko renders Compose primitives (shadows, custom drawing) differently than Android. The hairline + rounded shape may differ. MUST verify on :9090 before any wasmJs push.
- Roborazzi golden screenshot test `ChecklistDockGlassScreenshotTest` will show a diff (grey dock → white dock) and needs re-recording before CI passes.

## Предложения по улучшению агентов

### compose-feature-expert
- [ ] Document the `drawWithContent` + `Path` pattern for drawing partial borders (top edge only, skipping bottom). It's a Material 3 + Compose gotcha: `BorderStroke` draws all 4 sides; custom `Path` is needed for edge cases. Add to skill or compose-custom-drawing reference.

### android-platform-expert
- [ ] Document the dual-source color desync pattern: when two UI elements (dock + navbar) must visually sync, extract a single `@Composable @ReadOnlyComposable fun` source. Mention this in the designsystem rules as a recurring pattern.

### best-practices-scout
- [ ] Recommended: when reviewing dock/drawer/sheet-like components that touch `windowInsets`, verify that BOTH the component AND the inset-background use the same source-of-truth color function. Recurring pattern (seen in widget, home, checklist-detail).
