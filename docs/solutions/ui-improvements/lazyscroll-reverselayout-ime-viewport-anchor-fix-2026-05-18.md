---
title: "LazyColumn reverseLayout + IME Resize → Scroll Anchor Loss"
date: 2026-05-18
type: bug-fix
modules: [feature/aichat/impl, core/designsystem]
keywords: [LazyColumn, reverseLayout, IME, WindowInsets, viewport-resize, scroll-anchor, keyboard-handling, ChatScreen, animateScrollToItem]
project: checklists
---

# LazyColumn reverseLayout + IME Resize → Scroll Anchor Loss

## Проблема / Контекст

In `ChatScreen`, a `LazyColumn(reverseLayout=true)` with reverse-ordered chat messages is anchored to show the latest item (at position 0 in logical order, at the bottom of the viewport visually). When a tall preview-card is displayed and the user closes the keyboard, the viewport grows (IME bottom → 0), but the LazyColumn does not automatically re-anchor the bottom-pinned item. Result: the item drifts up, and the bottom of the card (Apply/Cancel buttons) becomes clipped.

**Symptom:** After typing a command and closing the keyboard, the preview-card scrolls up, leaving action buttons hidden.

**Root cause:** `animateScrollToItem(0)` in `LaunchedEffect` is triggered only on specific keys (item count, preview state). When viewport resizes due to keyboard open/close, those keys don't change, so the scroll anchor is not re-evaluated. The item stays visually pinned to its old scroll offset, which is no longer correct for the new viewport size.

## Решение

Include `WindowInsets.ime.getBottom()` (converted to Dp via `LocalDensity.current`) as an additional key in the `LaunchedEffect` that triggers scroll-to-item logic.

```kotlin
val imeBottomDp = with(LocalDensity.current) {
    WindowInsets.ime.getBottom(this).toDp()
}

LaunchedEffect(
    chatMessages.size,           // re-scroll on message count change
    previewState,                // re-scroll on preview state change
    imeBottomDp                  // ← ADD THIS: re-scroll on keyboard open/close
) {
    animateScrollToItem(0)       // re-anchor to latest message
}
```

**Why this works:**
- When keyboard closes, `WindowInsets.ime.getBottom()` changes from ~600dp (keyboard height) to 0dp.
- This change in key triggers `LaunchedEffect` re-run.
- `animateScrollToItem(0)` executes again, re-computing scroll offset for the new viewport size.
- Bottom-pinned item re-anchors correctly.

## Почему именно так

**Alternative 1: Remove reverseLayout**
- Breaks the visual design (messages appear oldest-to-newest instead of newest-to-oldest).
- Requires refactoring 3+ composables.
- ❌ Not acceptable.

**Alternative 2: Use rememberUpdatedState + snapshotFlow**
- Tested (Итерация 2). Breaks initial scroll: chat opens at the top of the list instead of bottom.
- ❌ Worse UX.

**Alternative 3: Add a dummy scroll-trigger variable**
- Every time keyboard closes, set `triggerScroll = !triggerScroll` and include it in keys.
- Verbose and pollutes ViewModel state.
- ❌ Less idiomatic than using IME-bottom.

**Why WindowInsets.ime is the right choice:**
- It directly mirrors the viewport-change event (keyboard open/close).
- No additional ViewModel state needed.
- Declarative: the key itself explains the dependency.
- Generalizable: any reverseLayout scroll logic can use this pattern.

## Примеры

**Before (broken):**
```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
    reverseLayout = true
) {
    items(chatMessages.size) { index ->
        ChatMessageRow(...)
    }
    if (previewState != null) {
        item {
            ChatCommandPreviewCard(...)  // tall card, scrolls up on keyboard close
        }
    }
}

LaunchedEffect(chatMessages.size, previewState) {
    animateScrollToItem(0)  // ❌ not re-triggered on keyboard close
}
```

**After (fixed):**
```kotlin
val imeBottomDp = with(LocalDensity.current) {
    WindowInsets.ime.getBottom(this).toDp()
}

LazyColumn(
    state = listState,
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
    reverseLayout = true
) {
    items(chatMessages.size) { index ->
        ChatMessageRow(...)
    }
    if (previewState != null) {
        item {
            ChatCommandPreviewCard(...)  // ✅ stays visible after keyboard close
        }
    }
}

LaunchedEffect(chatMessages.size, previewState, imeBottomDp) {
    animateScrollToItem(0)  // ✅ re-triggered on IME change
}
```

**Imports:**
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
```

## Связанные файлы

- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/ChatScreen.kt` — Applied fix (Iter 3, commit 53f959e6)
- `docs/active/chat-preview-scroll-ime-fix-2026-05-18.md` — Diagnostic trace (this session)

## Диагностика

If the issue recurs or happens in a different LazyColumn:
1. Trace viewport height and item bounds via `[ChatDiag]` logs:
   ```kotlin
   // Temporary debugging (remove after diagnosis):
   LaunchedEffect(...) {
       println("[ChatDiag] viewport=${listState.layoutInfo.viewportEndOffset}px, card=${itemHeights[0]}px")
       animateScrollToItem(0)
   }
   ```
2. Confirm viewport height changes when keyboard opens/closes (`onDispose { Log(...) }`).
3. Verify scroll offset matches viewport size after re-anchor.
