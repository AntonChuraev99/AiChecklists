# Adaptive UI — Trade-off Log

Decisions made during the adaptive UI migration (commits `9fe64d23`, `aa7a99a3`). Each entry documents the chosen option, the rejected alternatives, and the impact.

---

## 1. Reorderable drag-and-drop — Compact only

**Decision:** Enable `sh.calvin.reorderable` only when the screen layout is `LazyColumn` (Compact, <600dp). Disabled on `LazyVerticalGrid` (Medium/Expanded).

**Why:** `sh.calvin.reorderable 3.1.0` does not provide a `LazyGridReorderableState` equivalent. The library's public API covers `LazyList` and `LazyColumn` only. Implementing reorder on Grid requires a custom touch-tracking solution outside the library's scope.

**Rejected alternative:** Implement a custom grid drag-and-drop using `Modifier.pointerInput` + hit-testing. Rejected due to complexity and testing cost; the feature is lower priority on tablets.

**Impact on users:** Tablet and desktop users (Medium/Expanded) cannot reorder checklists by drag. A snackbar hint is displayed when a long-press is detected on those form factors: "Reordering is available on phone layout."

**Resume trigger:** If `sh.calvin.reorderable` ships `LazyGridReorderableState` in a future release, enable grid reorder and remove the hint snackbar.

---

## 2. ChatScreen and PaywallScreen scaffold exception

**Decision:** `ChatScreen` and `PaywallScreen` do NOT use `AppScaffold`. They manage their own `Scaffold` instances.

**ChatScreen — why:**
- Has a pinned input row at the bottom that must stay above the software keyboard.
- Requires `imePadding()` applied _inside_ the Scaffold `contentWindowInsets`, not at the shell level.
- Is a top-level drawer destination (not a detail screen), so it receives navigation-shell chrome but owns its content area completely.
- Applying `AppScaffold` would double-apply insets and break the keyboard-avoiding layout.

**PaywallScreen — why:**
- Full-bleed hero image that must extend behind the status bar.
- Custom gradient overlay and action button pinned at the bottom.
- Does not fit the standard top-bar + scrollable-content model that `AppScaffold` provides.
- Is a leaf screen (no sub-navigation), so it receives no shell chrome at all.

**Rule derived:** Any screen that (a) pins UI at the bottom against the IME, or (b) requires a full-bleed visual layout, should use its own `Scaffold` and handle insets manually via `statusBarsPadding()` / `navigationBarsPadding()` / `imePadding()`.

---

## 3. Playwright baselines committed to the repository

**Decision:** Playwright visual regression baseline screenshots are committed to `git` under `playwright/baselines/`.

**Viewports covered:** 360px (phone), 800px (tablet portrait), 1280px (desktop), 1440px (wide desktop).

**Why committed:**
- Baselines must be deterministic and shared across CI runs. Without committing them, each CI runner generates its own baseline and pixel-level differences cause false failures.
- The screenshots are generated from the wasmJs production build (`wasmJsBrowserDistribution`), which is deterministic for the same source code.
- File size is acceptable (~40 KB per PNG, ~600 KB total for the initial set).

**Rejected alternative:** Store baselines in an external artifact registry (S3, GCS). Rejected due to additional infra complexity for a small team; committed PNGs are simpler to review in PRs.

**Maintenance rule:** After any intentional visual change to a screen that has a baseline, regenerate the baseline with `npx playwright test --update-snapshots` and commit the updated PNG alongside the code change.

---

## 4. ListDetailSceneStrategy pair selection

**Decision:** Four screen pairs were registered with `listPane` / `detailPane` metadata. All other routes are single-pane.

| List pane | Detail pane | Rationale |
|---|---|---|
| `Main` | `ChecklistDetail` | Primary user flow; tablet users benefit most from seeing the list while editing a checklist |
| `Templates` | `TemplatePreview` | Browse-and-preview pattern maps naturally to two-pane |
| `Today` | `ChecklistDetail` | Same detail screen; today view is a filtered list |
| `Calendar` | `ChecklistDetail` | Calendar taps open a checklist; two-pane avoids push-pop navigation |

**Routes intentionally kept single-pane:**

| Route | Reason |
|---|---|
| `Splash`, `Onboarding` | Full-screen flows; detail pane would be empty and confusing |
| `CreateChecklist` | Linear wizard; no list to show alongside it |
| `Analyze`, `AnalyzeResultPreview` | Sequential; result replaces analyze screen |
| `Paywall`, `SubscriptionStatus` | Leaf screens with no associated list |
| `ShareChecklist` | Short modal-like action; no browsing context |
| `Debug`, `StoreScreenshot` | Internal tools; not user-facing |

---

## 5. adaptiveContentWidth default — 720dp

**Decision:** `Modifier.adaptiveContentWidth(maxWidthDp = 720)` centers single-column content on screens wider than 720dp.

**Why 720dp:**
- Material Design 3 guidance recommends a maximum content width of ~840dp for single-column reading layouts. 720dp leaves a comfortable margin on 1280dp+ displays while keeping content dense enough to feel intentional rather than sparse.
- Matches the Medium breakpoint upper boundary (839dp), so Medium-width screens are never clamped — only Expanded screens are affected.

**Rejected alternatives:**
- 600dp: Too narrow on 1280dp displays; content feels like a phone layout on a desktop.
- No cap: Content stretches to fill the full viewport, producing very long text lines (>120 chars) that harm readability.

**Applied to:** all screens using `LazyColumn` or `Column` that do not switch to a grid layout on wider widths (i.e., all screens except `MainScreen`, `TemplatesScreen`, `AnalyzeScreen`).
