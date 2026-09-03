# Roborazzi Screenshot Cycle — Four Traps in UI Testing

## Суть

Pixel-perfect UI testing under Roborazzi reveals four systematic traps that escape initial golden passes. These surface in screenshot-driven development workflows for input fields, state transitions, overflow content, and layered elements.

| Trap | Surface | Solution |
|---|---|---|
| Robolectric idle timeout | Focused input fields + caret blink | Use forced `recordFile()` instead of `captureToImage()` |
| Colour probe miscounting | `AppButtonText` uses semantic `primary` for text | Probe a non-primary colour at the button edge, or use bounds |
| Overflow not visible | Rails/rows scroll to position 0 only | Test includes scrolled-to-end frame with mutation proof |
| Layered paint order | Coloured ring/frame over scrollable content | Clip the shape to prevent paint outside bounds |

---

## Проблема

### 1. Robolectric `captureToImage()` Timeout on Focused Input Fields

**When:** Any Roborazzi screenshot test that composes a focused text input field (capture dock, chat prompt row).

**Why:** A focused `TextField` renders a blinking caret via `LaunchedEffect` on every frame. Roborazzi's `captureToImage()` waits for Compose idle (no pending frames). The caret animation never truly idles — it re-launches on every render pass. After ~30 seconds, `ComposeTimeoutException` is raised; the test fails without a screenshot. <!-- docs-leak-scan: reviewed — LaunchedEffect is a Compose API, not an install metric -->

**Evidence:** Git commit history contains multiple test disables due to timeout (`feature/home` integration tests for the dock, Compose 1.8.x era). Issue resurfaces whenever a new snapshot test touches focused input.

**Scope:** Robolectric-only (JVM environment). Device/emulator tests and Compose preview rendering are unaffected; caret animation does not block `captureToImage()` on real Skiko.

**Code example (failing):**
```kotlin
@Test fun dock_withFocusedInput() {
    composeTestRule.setContent {
        QuickCaptureDock(onCapture = {}) // composable contains focused TextField
    }
    // ❌ waits 30s, times out
    composeTestRule.onRoot().captureToImage()
}
```

---

### 2. AppButtonText Colour Probe Masks Content Colour

**When:** Pixel-counting tests that probe a `AppButtonText` button's colour to validate appearance.

**Why:** `AppButtonText` renders text in `primary` colour (per design system). A pixel probe that counts `primary` pixels to validate "the button contains primary" will count the text label itself, not the button's container or background. This conflates two separate UI concerns: the label colour and the button appearance. The mutation (changing label colour) passes the test undetected because the pixel count stays the same.

**Evidence:** The button's visual state (filled vs outlined vs text-only) cannot be validated by counting `primary` pixels; the test is checking the wrong layer.

**Code example (failing):**
```kotlin
@Test fun done_button_appearance() {
    val image = composeTestRule.onRoot().captureToImage()
    // ❌ counts primary in the text, not the button's container
    val primaryPixelCount = image.countColour(colorScheme.primary)
    assertTrue(primaryPixelCount > 100) // passes even if button is not visible
}
```

---

### 3. Goldens at Scroll 0 Miss End-of-Scroll Layout Defects

**When:** A rail or row (like `DueRailRow` with planner chips) has overflow content and changes its trailing slot (e.g., Important star moved from pinned trailing to input row).

**Why:** Roborazzi screenshot tests compose at `ScrollState(0)` by default. When a trailing element is removed, insets that guarded it may disappear too. The defect only surfaces when the content overflows and the user scrolls to the end — at scroll position 0, the trailing content is off-screen. Goldens recorded at position 0 cannot catch this defect.

**Evidence:** During the bottom-chrome redesign, removing the trailing group from `DueRailRow` removed the row's END padding. The defect surfaced only during visual proof (scrolling the rail to show offers at the end). Goldens recorded at scroll 0 passed undetected; the last offer pill sat flush against the dock edge.

**Code example (defect):**
```kotlin
Row(modifier = Modifier.padding(start = horizontalPadding)) { // ❌ end padding missing
    // scroll content
    LazyRow(…) { … }
}
// Before fix: the row had padding(end = ...) only if trailing != null
// Removing trailing removed the end inset — defect invisible at scroll 0
```

---

### 4. Layered Elements Must Clip to Container Bounds

**When:** A coloured ring, frame, or decorative shape is drawn around or behind a widget that overlaps a scrollable area below.

**Why:** Compose's `Box` and `Surface` paint content in the order it appears in the composable tree. A ring drawn in a parent `Box` paints *above* children, even if those children scroll under it. Without explicit clipping, the ring paints into the space occupied by the card/item scrolling under the bar. In dark theme, a page-coloured ring becomes a visible crescent arc over list items.

**Evidence:** During the bottom-chrome redesign, the AI FAB ring was drawn without clip bounds. In dark theme, the lower-right arc of the ring painted over cards scrolling under the 22dp bar overhang, creating a visible page-coloured crescent.

**Code example (defect):**
```kotlin
Box(modifier = Modifier.clip(CircleShape)) { // ❌ clips to full circle, not bar bounds
    FloatingActionButton(…)
}
// Ring paints inside the circle, even below the bar's top edge
// Children scrolling under the overhang get a coloured arc painted on top
```

**Clip fix:**
```kotlin
clipRect(top = barTopEdge + ringRadius) { // ✅ clip to bar bounds
    Box(modifier = Modifier.clip(CircleShape)) {
        FloatingActionButton(…)
    }
}
```

---

## Решение

### Trap 1: Robolectric Timeout — Use Forced Record

**For pixel probes that must work in Robolectric, bypass `captureToImage()` and use Roborazzi's `recordFile()` API directly:**

```kotlin
@Test fun dock_withFocusedInput() {
    composeTestRule.setContent {
        QuickCaptureDock(onCapture = {})
    }
    // ✅ recordFile forces a single-frame capture without waiting for idle
    composeTestRule.onRoot().apply {
        captureRoboImage(filename = "capture_dock_focused")
    }
    // Or use the lower-level recordFile:
    // composeTestRule.recordFile("capture_dock_focused.png")
}
```

**Alternative:** Blur or disable the caret in Robolectric tests only (set `cursorBrush = SolidColor(Color.Transparent)` when `isInRobolectricTest`).

**When to use:** Any test that exercises a focused text field (capture dock, chat input row, note editor). Ignore timeout warnings; Roborazzi's `recordFile()` does not wait for idle.

---

### Trap 2: AppButtonText — Probe a Non-Primary Colour or Bounds

**Option A — Probe a named colour at the button's edge (not the label):**

```kotlin
@Test fun done_button_appearsAsText() {
    composeTestRule.setContent {
        DueRailDoneButton(onClick = {})
    }
    val image = composeTestRule.onRoot().captureToImage()
    // ✅ Probe the button's leading edge for a non-primary colour
    // (e.g., the surface or outline colour that bounds the button)
    val edgePixel = image.getPixel(x = 10, y = image.height / 2)
    assertTrue(edgePixel == colorScheme.surface || edgePixel == colorScheme.outline)
}
```

**Option B — Validate button bounds and shape instead of colour:**

```kotlin
@Test fun done_button_existsAndIsTappable() {
    // Use semantic assertions instead of pixel colour
    composeTestRule.onNode(hasText("Done")).apply {
        assertIsDisplayed()
        assertIsEnabled()
        // No pixel colour check — visual state is validated by the golden PNG
    }
}
```

**When to use:** Tests that validate button appearance (filled vs text vs outlined). Avoid counting semantic colours (`primary`, `secondary`) in pixel probes; probe structural colours instead (`surface`, `outline`, `scrim`).

---

### Trap 3: Overflow Content — Include Scrolled-to-End Frame with Mutation Test

**Step 1: Record a second golden at scroll position to the end:**

```kotlin
@Test fun rail_scrolledToEnd_keepsTheDocksEndInset() {
    composeTestRule.setContent {
        DueRailRow(…) // rail with overflow content (e.g., many offer chips)
    }
    // Scroll to end
    composeTestRule.onNode(hasTestTag("due_rail_scroll")).performScrollToEnd()
    // ✅ Record the scrolled state
    composeTestRule.onRoot().captureRoboImage("dueRail_scrolledToEnd_light")
}
```

**Step 2: Prove the inset with mutation:**

```kotlin
// In the source code, the END inset is set conditionally:
Row(modifier = Modifier.padding(
    start = horizontalPadding,
    end = if (trailing == null) horizontalPadding else 0.dp // ← mutation here
)) { … }

// Mutate to:
//   end = 0.dp  (always zero)
// Expected: test fails because the last pill sits flush against the edge
//
// Mutate to:
//   end = horizontalPadding  (always present)
// Expected: test passes; the golden matches the inset

// ✅ The test now guards both scroll positions (0 and end)
```

**When to use:** Any row or rail with `LazyRow`, `LazyColumn`, or horizontal `Row(Modifier.horizontalScroll())` that has trailing elements, trailing padding, or trailing slots. Golden set must include both scroll 0 and scrolled-to-end frames; mutations must prove both.

---

### Trap 4: Layered Paint Order — Clip Decorative Shapes to Container

**For a ring or frame drawn around a FAB (or any widget) that sits above a scrollable area:**

```kotlin
Box(modifier = Modifier.clipRect(top = barTopEdge + ringRadius)) { // ✅ clip first
    Box(
        modifier = Modifier
            .size(62.dp)
            .align(Alignment.Center)
            .clip(CircleShape)
            .background(AppSurface.bottomChromeShoulder())
    )
}

// Or use a custom Shape that clips itself:
class BarBoundedRingShape(val barTop: Dp, val ringRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = …
}
```

**Proof mutation:**

```kotlin
// Mutate the clip to:
//   top = 0.dp  (clip to full screen, not bar bounds)
// Expected: in dark theme, a page-coloured crescent appears over list items
//
// ✅ Test `V2ShellAiButtonRingTest.theRingStopsAtTheBarsTopEdge_whenACardIsUnderTheOverhang_dark`
//    catches the missing clip and fails
```

**When to use:** Any decorative element (ring, shadow, gradient band, blur) drawn in a parent container that overlaps a scrollable area. Clip the shape to the visible bounds of the parent, not the full compose tree. Test includes a case where content scrolls under the element; mutation proves the clip is necessary.

---

## Почему именно так

1. **Robolectric timeout** — Focused input fields with caret animation block Roborazzi's idle detection indefinitely. Forced `recordFile()` avoids idle waiting and captures a single frame. This is a limitation of JVM event dispatch, not a Compose bug.

2. **Colour probe** — Semantic colours are overloaded across multiple UI layers (text, background, borders). Probing a semantic colour conflates layers; a structured probe (bounds, shape, non-semantic colour) validates one layer at a time.

3. **Overflow not visible** — Screenshot harnesses compose at a canonical scroll position (0) for reproducibility. Overflow defects only surface at non-canonical positions (end, middle). Mutation testing proves that the fix is necessary at both positions, not just the visible one.

4. **Layered paint order** — Compose draws in document order; explicit clipping is required to prevent paint outside logical bounds. A clip is cheaper than re-ordering the composable tree; it also works with animations (ring fades, card slides under).

---

## Применение

**Before any new screenshot test for:**
- Focused text input fields → use `recordFile()` or blur the caret
- Button appearance validation → probe structural colours, not semantic ones
- Rows/rails with overflow → record scroll-to-end + mutation-prove the inset
- Decorative shapes over scrollable content → clip to visible bounds + mutation-prove

**Documentation for teammates:**
- Add a comment above the test explaining the trap and why the workaround is used.
- Link this document in the comment.
- Ensure mutations are documented in the test; mark with `// Mutation: <description>`.

---

## Precedents

- Bottom-chrome redesign (2026-09-03): All four traps surfaced during Tier 3 (screenshot cycle) and Tier 4 (pre-merge gate). Overflow defect (`DueRailRow` END inset) caught by independent reviewer scan; ring clip defect caught by Spec gate. Robolectric timeout prevented initial test for focused dock composition; fixed via `recordFile()`. Colour probe issue identified but not blocking (test used semantic assertions instead).
- Earlier: `feature/home` dock tests (Compose 1.8.x) had timeout issues; tests were disabled for years, then re-enabled with `recordFile()`.

---

## Дополнительно

**Related documents:**
- `docs/active/bottom-chrome-redesign-2026-09-03.md` — design task that surfaced these traps (Iterations 2–4).
- `docs/solutions/test-infrastructure/` — other Roborazzi patterns (fixture management, device scaling, theme variants).

**Future work:**
- Lint rule or lint plugin to detect overflow rows without scrolled-to-end tests (false negatives hard to catch in review).
- Roborazzi plugin extension for `recordFileWithoutIdle()` to make the API more explicit.
