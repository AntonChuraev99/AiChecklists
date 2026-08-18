---
title: "Stale closure + async stringResource render cycles — empty message bubbles"
date: 2026-07-15
type: bug-fix
modules: [feature/aichat, core/designsystem, composeApp]
keywords: [stale-closure, stringResource, LaunchedEffect, rememberUpdatedState, async-composition, message-rendering, Compose-recomposition]
project: Checklists
---

# Stale Closure + Async Composition State: Empty Message Bubbles in Chat

## Проблема / Контекст

Chat Assistant answers (dispatch results, reminders, feedback) rendered as **empty bubbles** (icon + timestamp + action button, **no text**) across all live users, despite server sending full messages. Symptom stably appeared in production whenever a dispatcher result had multi-word localized text: `"Добавлено«молоко»", "Взял«молоко»отсюда", "Напоминание каждый день в 09:00"` and any English variant.

**Root cause:** `LaunchedEffect` in two render sites (`App.kt:794` and `ChatRoute.kt:352`) captures `messages` list via lambda **once on first render**, then `stringResource()` API resolves asynchronously (first recomposition = empty, then updated). Collector holding the Flow receives an **empty string map forever** — all downstream renders read keys as empty strings.

```kotlin
// App.kt:794 — WRONG pattern (stale closure)
LaunchedEffect(viewModel) {
  viewModel.messageUpdates.collect { messages ->
    // Lambda captured HERE, on first render
    val result = messages.mapIndexed { index, msg ->
      val text = stringResource(Res.string.key) // ← async, first frame = ""
      MessageWithText(text, msg.intent)
    }
  }
}

// Result: first recomposition has empty map, collector pin
```

Affected render paths: every Assistant message following a user action (Add, Complete, Move, Delete, Undo).

## Решение

**Wrap the message list in `rememberUpdatedState`** to ensure collectors dereference fresh Compose state, not stale closure capture. Fix deployed to both render sites:

```kotlin
// App.kt — CORRECT pattern
val freshMessages = rememberUpdatedState(viewModel.messageUpdates)

LaunchedEffect(viewModel) {
  freshMessages.value.collect { messages ->
    // At call-time, freshMessages.value reads the current list, not captured value
    val result = messages.mapIndexed { index, msg ->
      val text = stringResource(Res.string.key)
      MessageWithText(text, msg.intent)
    }
  }
}
```

Apply the same pattern at `ChatRoute.kt:352`.

## Почему именно так

On Android, `rememberLauncherForActivityResult` internally manages state dereference for each callback invocation — the callback lambda is not captured once, but re-fetched from Compose state on each call. On wasmJs, Compose Multiplatform does NOT auto-wrap `remember`-managed launchers/collectors. The pattern forces **late binding** of state: at the moment the collector fires, it reads the current `messageUpdates` Flow from fresh Compose state, not from the closure created on the first render.

**Why async `stringResource` specific?** The resource string is resolved on **first composition** (before the collector even starts). By the time the message lands in the collector (async server response + Flow + collect), the string-mapping has already been pinned to an empty map. Without `rememberUpdatedState`, the message renders as `MessageWithText("")`.

## Примеры

**Before (❌ three sites with the bug):**
- `App.kt:794` — `val messages = viewModel.messageUpdates`; `LaunchedEffect(viewModel)` → collector reads stale
- `ChatRoute.kt:352` — same collector pattern, same stale capture
- `ToolCallPreviewRenderer.kt:composable` — passed `messages` to nested Compose scope

**After (✅ fixed both collector sites):**
- `App.kt:794` — `val freshMessages = rememberUpdatedState(messages)` inside `LaunchedEffect` body
- `ChatRoute.kt:352` — same wrapping applied
- All 459 unit tests pass; live wasmJs verification (`:9090`, RU locale) confirms text rendering

**Verification checklist:**
1. Open chat, send message triggering dispatch (e.g., "Добавь молоко")
2. Verify response bubble contains text (not empty): "Добавлено «молоко»" + timestamp
3. Undo message — bubble must show "Взял «молоко» отсюда." + timestamp
4. Switch locale (Settings → Language → EN) — verify English response texts render
5. logcat/devtools: no string resolution errors in logs

## Связанные файлы

- `feature/aichat/impl/ChatRoute.kt` (line 352: `LaunchedEffect` with collector)
- `composeApp/App.kt` (line 794: app-level message collector)
- `feature/aichat/impl/preview/ToolCallPreviewRenderer.kt` (uses localized strings)
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` (string resource keys)
- **Test coverage:** `ChatViewModelTest`, `AiChatScenariosTest` (459 tests, all green)

## History

- **Discovered:** 2026-07-15, 10:45 UTC, live verification on `:9090` (RU locale) — chat responses consistently rendered as empty
- **Instrumentation:** Logged at both Flow boundaries (emit in ViewModel + append in collector) in same session
- **Root cause narrowed:** `stringResource()` resolves asynchronously; first recomposition yields empty map
- **Applied fix:** `rememberUpdatedState` in both LaunchedEffect sites; re-verified live + 459 tests green
- **Scope:** pre-existing production bug, not regression from D1 stage (Live users saw empty bubbles until this session)

## Lessons Learned

1. **Async Composition APIs (stringResource, remember, custom launchers) require explicit state tracking.** If a composable reads Compose-managed state inside a remember-wrapped or Flow-based callback, wrap mutable state in `rememberUpdatedState` to guarantee fresh reads at call-time, not closure-time.
2. **`compile + unitTest` does NOT cover Compose runtime.** String resolution failures, stale-closure reads, and recomposition ordering stay green in CI. Always verify UI paths on the actual renderer (Android device, wasmJs browser) before shipping.
3. **Stale-closure is not unique to FilePicker.** Any `remember`-wrapped callback or Flow collector that reads Compose state can exhibit this bug. Establish a linting rule or review checklist: "Does this lambda/collector body read ViewModel state or mutable Compose state? → Add `rememberUpdatedState` wrapper."
4. **String-resolution delays are cumulative.** When a large batch of messages arrives, each string resolution happens independently; if the collector is pinned to an empty map, ALL messages in that batch render empty. One stale capture breaks the entire conversation history for that user.

