---
paths:
  - "**/core/designsystem/**"
  - "**/*Screen.kt"
  - "**/*Sheet*.kt"
  - "**/*Dialog*.kt"
---

# Design System — Minimal & Clean (white bg, blue accents)

Located in `core/designsystem/`. Always use design system components instead of raw Material3 when available (e.g. `AppSwitch` not `Switch`).

**Colors (`theme/Color.kt`):** Primary Blue `#2196F3`; Background/Surface White `#FFFFFF`; Text Primary Gray900 `#212121`; Text Secondary Gray600 `#757575`; Outline Gray300 `#E0E0E0`.

**Spacing (`theme/Dimens.kt`):** `AppDimens` — `SpacingXs` 4dp, `SpacingSm` 8dp, `SpacingMd` 12dp, `SpacingLg` 16dp, `SpacingXl` 24dp, `SpacingXxl` 32dp.

**Spacing density — keep stacked layouts compact (UI-review check).** When stacking title → subtitle → options/chips → action inside a sheet / dialog / form, default to **tight** inter-element gaps: `SpacingSm`–`SpacingMd` between adjacent elements. Reserve `SpacingXl`/`SpacingXxl` for genuine **section** breaks, not routine element-to-element gaps — over-spacing reads sparse/unpolished. Chips (and any `minimumInteractiveComponentSize` control) already carry ~16dp phantom vertical air per 48dp touch target, so `FlowRow` `verticalArrangement` = `0`–`SpacingXs`, never `SpacingSm+`. **When building or reviewing such a layout, flag any adjacent (non-section) gap ≥ `SpacingXl` as too loose and tighten it.** Precedent: post-cancel reason-picker (`PostCancelReasonSheet`, 2026-07-01) shipped with 24/16/32dp gaps → felt airy; tightened to 12/8/16 + 0 row-gap.

**Component naming:**
- Wrapping a single Material3 widget → `App` + widget name in `components/` (`AppButton`, `AppSwitch`, `AppTextField`).
- Layout container → `App` + name in `containers/` (`AppScaffold`).
- Composing multiple widgets into a reusable pattern → descriptive name in `components/` (`EmptyState`, `AddItemInputField`).
- Feature illustration → descriptive name in `illustrations/` (`CreateViaAiIllustration`).

**Components:** `AppButton`/`AppButtonSecondary`/`AppButtonText`/`AppButtonDestructive`; `AppCard` (12dp corners, 2dp elevation); `AppSwitch` (visible unchecked track = outlineVariant); `AppTextField` (outlined + keyboard options); `EmptyState`; `AddItemInputField`; `AppScaffold` (auto-handles system insets).

**System insets (edge-to-edge) — MANDATORY.** Screens **with** `AppScaffold`: automatic via `WindowInsets`, nothing to do. Screens **without** `AppScaffold` (`OnboardingScreen`, `PaywallScreen`, `SplashScreen`) MUST add:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
)
```

Per-item actions go in `ItemDetailsSheet`, not on the card — see `.claude/rules/ui-card-patterns.md`. Adaptive top bars (`AppScaffold` Compact vs Medium) — see `.claude/rules/adaptive-navigation.md`.

## Haze backdrop blur (glass chat dock) — ⛔ pin 1.7.2, NOT 2.0-alpha

The `GistiGlassChatDock` (`components/gisti/GistiChatDock.kt`) is the shared frosted-glass overlay (MainScreen + ChecklistDetailScreen). Blur via **`haze = "1.7.2"`** (`gradle/libs.versions.toml`, single `dev.chrisbanes.haze:haze` module).

- **NEVER bump haze to `2.0.0-alpha*`.** That alpha (separate `haze-blur` module, `blurEffect{}` DSL, `HazeColorEffect`) **does not render the backdrop** — only a flat tint draws. This cost a whole session (2026-06-07): symptom looked like a layout bug but the root was the alpha version. Web search will suggest the 2.0 `blurEffect{}` shape — ignore it.
- Working API (proven in prod swapfaceandroid): `Modifier.clip(shape).hazeEffect(state, style = HazeStyle(blurRadius = 16.dp, backgroundColor = surface, tint = HazeTint(color.copy(alpha = 0.6f))))`. `hazeSource` on the scrolling content, `hazeEffect` on the dock — **siblings in one Box**, one shared `rememberHazeState()`. Never wrap the effect node in `AnimatedVisibility`/`Surface` (kills sampling). No top hairline (reads dark on white).
- Bottom dock that must blur the navbar zone: use `AppScaffold(contentExtendsBehindNavBar = true)` so content goes edge-to-edge and the dock's blur covers the navbar (else: double navbar inset + an un-blurred strip). The dock owns its `navigationBarsPadding()`; pass `bottomPadding` for the gap above it (8dp on MainScreen).
- Verify blur actually renders with the Roborazzi high-contrast detector (`androidHostTest/.../ChecklistDockGlassScreenshotTest.kt`) — stripes smeared = works. Full write-up: `docs/solutions/features/blur-under-chat-dock-haze-1.7.2-downgrade-2026-06-07.md`.

## Scrim / dimming overlay z-order — the nav-bar strip stays dock-white

A full-screen scrim/dim over a dock screen (e.g. item-create mode dimming everything except the create dock) MUST **not** dim the bottom system-nav strip. That strip is the `windowInsetsBottomHeight(WindowInsets.navigationBars).background(gistiDockColor())` sibling Box painted at the screen bottom — it is **visually part of the dock** (same `gistiDockColor()`), so the system-nav zone must read as one continuous surface with the dock, bright, never dimmed.

**Draw order inside the anchor `Box` (later = on top):**

```
[hazeSource list content]  →  [content scrim]  →  [nav-bar strip]  →  [GistiGlassChatDock]
```

- The scrim goes AFTER the list (so the list dims) but BEFORE the nav-bar strip AND the dock (so both draw on top and stay bright).
- Same rule for the top-bar scrim: it is a sibling ABOVE `AppScaffold` in a root Box (dims the status bar + pinned app bar); both scrims share ONE `scrimAlpha` (`animateFloatAsState`) so they fade in lockstep with no seam.
- Dock keeps touch priority automatically (drawn last). The scrim's tap-to-dismiss (`animateTo(Peek)`) sits below it.

**Check when adding/reviewing a scrim:** if the bottom gesture/nav zone goes grey/black while the dock is bright, the nav-bar strip is drawn BEFORE the scrim — move it after. Precedent: the item-create scrim (2026-07-06) first shipped with the strip drawn before the scrim → the bottom system nav dimmed dark; user reported "нижняя системная навигация должна быть белой". Fixed by reordering strip after the content scrim.
