---
title: "Clickable / pretty links in checklist items"
date: 2026-06-16
type: feature
modules: [core/designsystem, feature/home]
keywords: [link-detection, url-regex, linkification, itemdetailssheet, read-only-chips, wasmjs-popup]
project: checklists
---

# Clickable / Pretty Links in Checklist Items

## Problem / Context

Checklists store URLs in item `text` (name) and `note` (description). When a user pastes a long URL (e.g., LinkedIn profile), the raw string:
- Takes 3+ lines on screen (poor UX)
- Renders as plain text (no affordance to open)
- On web, has no way to open (no picker, no copy affordance)

Goal: detect URLs, render them compactly (read-only chip "🔗 domain"), and provide an action to open in a browser/external app.

## Solution

### 1. KMP URL Detection & Linkification (commonMain)

**File:** `core/designsystem/src/commonMain/kotlin/.../util/LinkText.kt`

Pure Kotlin utilities (no Android-only deps):

```kotlin
fun extractUrls(text: String): List<String>
  // Regex https?://\S+ with trailing punctuation trimming (no "http://example.com.")

fun String.asWholeUrl(): Boolean
  // True if entire string is a URL (used for chip vs inline-linkify decision)

fun displayDomain(url: String): String
  // Extract domain from https://example.com/path → "example.com"

@Composable
fun rememberLinkifiedText(raw: String, linkColor: Color): AnnotatedString
  // Returns AnnotatedString with URL ranges tagged; caller renders with Tag.Link for styling
```

**Why no `java.net.URI` / `android.util.Patterns`:**
- `java.net.*` not available in commonMain (JVM-only).
- `android.util.Patterns` is Android-only; wasmJs has no equivalent.
- Regex `https?://\S+` + manual trim covers 95%+ of cases; full URL validation not needed (LocalUriHandler handles invalid URLs gracefully).

**Testing:** 15 test cases in `LinkTextTest` (commonMain JVM):
- Whole URL, inline URL, multiple URLs, edge cases (trailing punctuation, query params, fragments, emoji-adjacent).
- All pass on `:testAndroidHostTest`.

### 2. Rendering on Cards (read-only)

**ChecklistDetailScreen.kt**
- If `item.text.asWholeUrl()` → render chip `AppItemMetaChip(Icons.Filled.Link, displayDomain(item.text))` (no onClick).
- If URL inside text → `Text(rememberLinkifiedText(...))` with link-colored ranges.
- Same for `item.note`.

**FillDetailScreen.kt** (read-only fills)
- Same chip + linkified text; no interaction (fills are immutable).

**Why read-only on cards:**
- Rule `ui-card-patterns`: cards use 30/70 hit-zone overlay (combinedClickable) → tappable content underneath gets blocked.
- Affordance: overlay acts as "view details" trigger; all actions live in sheet.

### 3. Opening Links (ItemDetailsSheet)

**ChecklistDetailScreen.kt ItemDetailsSheet**

New row for each unique URL found in text + note:
```kotlin
ItemDetailsSheetRow(
  icon = Icons.Filled.Link,
  title = stringResource(Res.string.detail_item_sheet_action_open_link), // "Open link"
  subtitle = displayDomain(url),
  onClick = {
    try {
      LocalUriHandler.current.openUri(url)
    } catch (e: Exception) {
      koinInject<AppLogger>().warning("ItemDetailsSheet", "Failed to open URL: ${e.message}")
    }
  }
)
```

**Why sync onClick (not coroutine):**
- On wasmJs, `LocalUriHandler.openUri()` → `window.open()`.
- Browser popup policy: can only open from **synchronous** user-gesture handlers.
- If called from `LaunchedEffect` / `ViewModel.onIntent` coroutine → popup blocked.
- Solution: embed URL-open logic directly in onClick lambda; no ViewModel intermediate.

**Error handling:** `AppLogger.warning()` (not snackbar) — user may have web-redirect handler; error is logged for debugging, not surfaced (silent graceful degrade).

## Why Exactly This

1. **commonMain regex (not `Patterns`)** — avoids Android-only coupling; KMP-first design allows web/iOS to use same utility.
2. **Read-only on card** — respects `ui-card-patterns` hit-zone rule; card is preview/summary, sheet is interaction.
3. **Sync onClick for web** — browser popup policy is strict; LaunchedEffect coroutine violates it → popup blocked. Verified: sync onClick = popup works.
4. **DI-injected AppLogger** — consistent error logging; no hardcoded Crashlytics or silent catch.
5. **FillDetailScreen read-only only** — no ItemDetailsSheet for fills (immutable data); can revisit when fill editing lands.

## Examples

**Input:** Checklist item with text = "Read this article: https://example.com/long/path/article?id=123&v=2"

**Output (Android):**
- Card displays: "Read this article: 🔗 example.com"
- Sheet row: icon Link, "Open link", subtitle "example.com"
- Tap row → Intent to browser

**Output (Web):**
- Card displays: same (CSS-rendered chip)
- Sheet row: same UI
- Tap row → `window.open("https://example.com/...")` (sync, popup succeeds)

## Related Files

- `core/designsystem/src/commonMain/kotlin/.../util/LinkText.kt` (new)
- `core/designsystem/src/androidHostTest/kotlin/.../util/LinkTextTest.kt` (new, 15 test cases)
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` (+1: `detail_item_sheet_action_open_link`)
- `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` (+1)
- `feature/home/presentation/ChecklistDetailScreen.kt` (+108 lines)
- `feature/home/presentation/FillDetailScreen.kt` (+75 lines)

## Deferred

**Opening links in FillDetailScreen** — FillDetailScreen has no ItemDetailsSheet (fills are read-only); opening flow requires new sheet or alternate affordance. Logged in `docs/todos/2026-06-16-open-link-in-filldetailscreen.md`.
