---
title: "Emoji Rendering on wasmJs + Touch-Zone Ripple Clipping"
date: 2026-06-04
type: bug-fix
modules: [designsystem, composeApp]
keywords: [emoji, wasmJs, Skiko, fontFamily, LocalEmojiFont, touch-zone, ripple-clipping, Surface-onClick, minimumInteractiveComponentSize]
project: Checklists
---

# Emoji Rendering on wasmJs + Touch-Zone Ripple Clipping

## Problem / Context

1. **Emoji rendering failure on web:** Compose Multiplatform wasmJs backend (Skiko canvas renderer) lacks system emoji fallback — emoji characters render as tofu (□ U+FFFD) even when the system has emoji fonts available. Android and iOS systems handle emoji through system font fallback; wasmJs canvas has no such mechanism.

2. **Ripple zone overflow:** `clickable()`/`combinedClickable()` modifiers applied outside `Surface` or `Card` cause ripple effects to extend beyond the component's visual bounds (e.g., rectangular ripple overflow rounded-corner cards, half-screen splash on chips).

3. **Touch target compliance:** Some interactive components fell below 48dp minimum touch target on web due to sizing.

## Solution

### Part A: Emoji Font Infrastructure

Pattern: **expect/actual FontFamily + LocalCompositionLocal + per-Text application**.

#### 1. Expect/Actual `rememberEmojiFont()` (core/designsystem)

**commonMain:**
```kotlin
expect fun rememberEmojiFont(): FontFamily
```

**androidMain / iosMain:**
```kotlin
actual fun rememberEmojiFont() = FontFamily.Default
```
Rationale: Android and iOS handle emoji via system fallback; no explicit font needed.

**wasmJsMain:**
```kotlin
actual fun rememberEmojiFont(): FontFamily {
    return rememberUpdatedState(FontFamily(Font(Res.font.noto_color_emoji))).value
}
```
Explicitly loads Noto Color Emoji (Twemoji, Mozilla COLR v1, 1.47MB).

#### 2. LocalEmojiFont CompositionLocal (core/designsystem)

```kotlin
val LocalEmojiFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
```

Avoid prop-drilling; provides font globally to any composable via `LocalEmojiFont.current`.

#### 3. Provide via App Root (composeApp/App.kt)

```kotlin
CompositionLocalProvider(LocalEmojiFont provides rememberEmojiFont()) {
    AppTheme {
        // Rest of app
    }
}
```

#### 4. Font Placement

- **wasmJsMain only:** `composeApp/src/wasmJsMain/composeResources/font/noto_color_emoji.ttf`
- Android and iOS do NOT include the font (1.47MB saved per platform build).

#### 5. EmojiText Helper (core/designsystem)

Reusable utility for mixed-emoji + regular-text:

```kotlin
data class EmojiAwareText(
    val text: AnnotatedString,
    val fontFamily: FontFamily
)

fun buildEmojiAwareText(
    text: String,
    emojiFont: FontFamily
): EmojiAwareText {
    val builder = AnnotatedString.Builder()
    // Parse text for emoji codepoints (U+1F30x etc.) and mark ranges
    // emoji ranges → SpanStyle(fontFamily = emojiFont)
    // non-emoji → SpanStyle(fontFamily = FontFamily.Default)
    return EmojiAwareText(builder.toAnnotatedString(), emojiFont)
}

@Composable
fun rememberEmojiAwareText(text: String): EmojiAwareText {
    val emojiFont = LocalEmojiFont.current
    return remember(text, emojiFont) { buildEmojiAwareText(text, emojiFont) }
}
```

**Usage:**
```kotlin
val emojiText = rememberEmojiAwareText("Photo 📷")
Text(text = emojiText.text, fontFamily = emojiText.fontFamily)
```

### Part B: Touch-Zone Architecture

#### 1. Surface(onClick) + Internal combinedClickable

Correct pattern for ripple clipping:

```kotlin
Surface(
    onClick = { /* handle click */ },
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
) {
    Row(
        modifier = Modifier
            .padding(16.dp)
            .combinedClickable(
                onClick = { /* redundant but harmless */ },
                onLongClick = { /* long-press */ }
            )
    ) {
        // content
    }
}
```

**Why:** `Surface(onClick)` applies the click handler at the shaped layer, so ripple is clipped by the shape. Internal `combinedClickable` is optional (can be omitted if only single-tap needed).

#### 2. Drag/Wiggle Gesture Stays Outside Card

```kotlin
Card(...) {
    // cardContent with combinedClickable
}
.pointerInput(Unit) {
    detectDragGesturesAfterLongPress { change, dragAmount in
        // drag/wiggle handler
    }
}
```

Gesture detector wraps Card (outside its bounds), allows drag zone to exceed card dimensions without affecting ripple.

#### 3. Minimum 48dp Touch Target

```kotlin
Surface(
    onClick = { ... },
    modifier = Modifier
        .minimumInteractiveComponentSize()  // Enforces 48.dp square
        .height(56.dp)  // or explicit sizing
)
```

### Part C: Non-Emoji Symbol Replacement

Twemoji does not cover all Unicode symbols (e.g., U+2192 RIGHTWARDS ARROW `→`). Replace with emoji presentation variants:

| Old | New | Unicode |
|---|---|---|
| `→` | `➡️` | U+27A1 + FE0F (emoji presentation) |
| `✓` | `✔️` | U+2714 + FE0F (emoji presentation) |

These codepoints are in Noto Color Emoji and render correctly through `LocalEmojiFont.current`.

## Why This Approach

1. **Cross-platform:** expect/actual allows Android/iOS to use system emoji (no overhead), wasmJs gets explicit font.
2. **Composable-friendly:** LocalCompositionLocal + `rememberEmojiFont()` avoids parameterization of every Text.
3. **Tested pattern:** Ported from swapfaceandroid (production Q2 2026); proven to solve emoji.ttf fallback on Skiko.
4. **Ripple clipping:** Placing onClick at Surface boundary automatically clips ripple by shape.
5. **Touch compliance:** `minimumInteractiveComponentSize()` ensures 48dp target per Material guidelines.

## Limitations

- **Roborazzi host-tests:** Android-host JVM uses system emoji fallback, not LocalEmojiFont. Host tests do NOT verify wasmJs emoji rendering — requires browser smoke-test on `wasmJsBrowserDistribution`.
- **Non-COLR glyphs:** Noto Color Emoji is COLR v1 (bitmap-based); TrueType/OTF outlines may not match pixel-for-pixel. For perfect glyph matching, compare against Figma export or Android native render.
- **fontFamilyResolver.preload() ineffective:** Skiko's internal preload (called via Compose's `fontFamilyResolver`) does not act as fallback for missing glyphs. Only explicit per-Text `fontFamily` works.

## Examples

### GistiPromptChips with Emoji Label

```kotlin
@Composable
fun GistiPromptChip(
    label: String,
    icon: String = "📷",
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Gray200,
        modifier = Modifier
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val emojiText = rememberEmojiAwareText(icon)
            Text(
                text = emojiText.text,
                fontFamily = emojiText.fontFamily
            )
            Text(label)
        }
    }
}
```

### ChecklistListCard with Ripple Clipping

```kotlin
@Composable
fun ChecklistListCard(
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress { change, _ in
                    // wiggle/reorder
                }
            }
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(16.dp)
        ) {
            val emojiText = rememberEmojiAwareText("🎉 $title")
            Text(
                text = emojiText.text,
                fontFamily = emojiText.fontFamily
            )
        }
    }
}
```

## Verification Checklist

- [ ] `rememberEmojiFont()` implemented for all three platforms
- [ ] `LocalEmojiFont` provided in App.kt CompositionLocalProvider
- [ ] All user-facing emoji Text apply `fontFamily = LocalEmojiFont.current`
- [ ] Non-emoji glyphs replaced with emoji variants (→➡️, ✓✔️)
- [ ] Surface(onClick) used for all clickable cards/chips; ripple visually contained
- [ ] minimumInteractiveComponentSize() present on interactive Surfaces
- [ ] `:composeApp:compileKotlinWasmJs` compiles without error
- [ ] `:androidApp:assembleDebug` builds successfully
- [ ] Roborazzi golden screenshots updated and verified
- [ ] Manual browser test on wasmJsBrowserDistribution confirms emoji render (not tofu)

## Related Files

- `core/designsystem/src/commonMain/kotlin/.../emoji/{LocalEmojiFont,EmojiFont,EmojiText}.kt`
- `composeApp/src/wasmJsMain/composeResources/font/noto_color_emoji.ttf`
- `composeApp/src/commonMain/App.kt` (CompositionLocalProvider)
- `core/designsystem/src/commonMain/kotlin/.../components/{GistiPromptChips,ChecklistListCard}.kt`
