---
title: "AppButton Icon + Text Centering Layout Fix"
date: 2026-05-06
type: bug-fix
modules: [core/designsystem]
keywords: [appbutton, leading-icon, material3, button-defaults, icon-spacing, centering]
project: gisti-checklists
---

# AppButton Icon + Text Centering Layout Fix

## Problem

When `AppButton` was rendered with the `icon` parameter set, the label drifted left of center. Visually it looked like the text was left-aligned even though Material3 `Button` is supposed to center its content.

## Root Cause

Earlier code attempted to "balance" a leading icon by adding a **trailing `Spacer`** after the text:

```kotlin
// ❌ WRONG — trailing spacer was a fake-symmetry attempt
if (icon != null) {
    Icon(...)
    Spacer(width = 8.dp)
}
Text(...)
if (icon != null) {
    Spacer(width = 18.dp + 8.dp)   // ← shoves the text leftward
}
```

The reasoning was: "If I add a Spacer on the right with the same width as the icon + leading spacer, the text will look centered." But Material3 `Button` already lays out its content with `horizontalArrangement = Arrangement.Center` ([Material3 source](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Button.kt)). The whole `[Icon][Spacer][Text]` group is treated as a **single block** that gets centered as one. Adding a trailing `Spacer` makes the block wider on the right side, so the trio shifts left to compensate.

## Anti-pattern: `Box(fillMaxWidth) + Icon(align CenterStart)`

A first-pass fix tried to wrap the content in a `Box(fillMaxWidth, contentAlignment = Center)` and pin the icon to `Alignment.CenterStart`. This **does** center the text mathematically — but it pins the icon to the **left edge of the button**, leaving a visible gap between the icon and the text. Looks fine for an FAB-style button or a destructive "delete" with an outlier icon, **wrong** for a standard leading-icon button where the icon should be **adjacent** to the label.

```
[🔒                                Become Pro to Unlock More                                ]
 ↑ icon glued to edge          ↑ text orphaned in the middle, no relation to icon
```

This is the pattern that "looks centered in code" but feels broken in production. Avoid.

## Correct Solution — stock Material3 leading-icon pattern

Just remove the trailing spacer and use the official constants:

```kotlin
Button(
    onClick = onClick,
    modifier = modifier.height(AppDimens.ButtonHeight),
    enabled = enabled,
    shape = shape,
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)         // 18.dp — official
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing)) // 8.dp — official
    }
    Text(text = text, ...)
}
```

Material3 `Button` will:
1. Measure the `[Icon][Spacer 8dp][Text]` group as a single unit.
2. Centre that whole unit horizontally inside the button's content area (because the underlying `Row` uses `Arrangement.Center`).

The icon ends up **directly to the left of the label**, with the proper 8dp gap, and the visual block is centered — exactly what the Material guidelines describe for a "Button with leading icon".

## Why use `ButtonDefaults.IconSize` / `IconSpacing`

These are the official Material3 tokens:
- `ButtonDefaults.IconSize = 18.dp`
- `ButtonDefaults.IconSpacing = 8.dp`

Using them keeps the button consistent with Material defaults if Google ever tweaks the spec, and signals intent to future readers ("this is a stock leading-icon button, not a custom layout").

## Decision tree — which layout for which button?

| Layout you want | Code to use |
|---|---|
| **Leading icon adjacent to centered label** (standard) | `Row { Icon; Spacer; Text }` — let Material3's `Arrangement.Center` do its job |
| **Icon pinned to the left edge, text in the middle** (rare, e.g., asymmetric chips) | `Box(fillMaxWidth) { Icon(align=CenterStart); Text(align=Center) }` |
| **Trailing icon** (uncommon for `Button`, common for `IconToggleButton`) | `Row { Text; Spacer; Icon }` |
| **Icon-only button** | `IconButton` — purpose-built |

Default to the first row unless you have a specific reason to use the others.

## Related precedent

`docs/solutions/ui-improvements/paywall-truthful-copy-and-layout-2026-04-28.md` — same family of bugs ("text doesn't center the way you expect"). The lesson there was about `Text(..., textAlign = Center)` needing `fillMaxWidth()` to actually centre. Here the lesson is the inverse direction: **don't fight Material3's built-in centering with extra Spacers or wrappers** — it already does the right thing for the standard case.

## Files touched

- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/AppButton.kt` (`AppButton` + `AppButtonSecondary`)

Affected consumers (no change needed in callers, the layout fix is local to AppButton):
- `feature/updatefeed/.../components/FeatureItem.kt` — locked weekly CTA
- `feature/create/.../templates/TemplatesScreen.kt` — locked "Create Manually" CTA
- `feature/create/.../create/CreateChecklistScreen.kt` — locked Save CTA
