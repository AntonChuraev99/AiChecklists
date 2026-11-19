---
title: Скрывать клавиатуру при создании пункта чеклиста
date: 2026-06-07
status: resolved
resolved_date: 2026-06-12
parent_task: "chat-dock prompt-chips removal session (2026-06-07)"
blocking_reason: RESOLVED — hide-always wired across Create, Detail, Weekly
resume_trigger: "n/a (done)"
estimated_complexity: Trivial/Low
keywords: [keyboard, ime, AddItemInputField, focusManager, keyboardController, clearFocus, checklist-item, add-item, ux]
---

# Скрывать клавиатуру при создании пункта чеклиста — Deferred

## ✅ RESOLVED 2026-06-12 — hide-always chosen by user, wired on all surfaces

User picked "hide always (like Create)" via AskUserQuestion. Implemented:
- **Checklist detail** inline add (`ChecklistDetailScreen.kt`): added `keyboardController = LocalSoftwareKeyboardController.current`; `keyboardController?.hide()` after `onAddItem()` in both the IME `onDone` and the check-button `onClick`. The existing IME-tracking effect (lines ~2609-2616) then closes the add-input — desired "done adding" behaviour.
- **Weekly** per-day add (`WeeklyChecklistDetailContent.kt` → `WeeklyAddItemRow`): added the controller + `keyboardController?.hide()` after `onSubmit()` in IME `onDone`. Header "+" left as-is (it's a focus-affordance, not a submit).
- **Create** flow already hid (`CreateChecklistScreen.kt:266/274`) — unchanged.

Serial-input tradeoff accepted by the user (no longer keeps keyboard open between items on detail). Validation: `:feature:home:testAndroidHostTest` BUILD SUCCESSFUL. The pre-fix analysis below is kept for history.

### Follow-up refinement 2026-06-12 — kill the empty-input "flash"

First pass had a UX glitch: after a successful add on **detail**, the inline input briefly re-showed empty for ~1s, then collapsed — because the collapse was delegated to the slow IME-dismiss `LaunchedEffect` (waits for the keyboard animation to reach `imeBottom==0`). Also a latent bug: the unconditional `keyboardController?.hide()` fired even on **rejected** adds (trigger-only phrase like "tomorrow 7am" → Smart Add hint, input preserved) — wrong, the user must keep typing.

Fix (UI-local, no ViewModel/contract change — `ChecklistDetailViewModel` has `Nothing` side-effects):
- `addItemWithParse()` clears `pendingItemInput` **synchronously and only on success** (line ~702); reject branches early-return with the text preserved. So the next recomposition's `text` reveals the outcome.
- `InlineAddItemInput` now sets a `submitAttempted` flag on the add gesture and a `LaunchedEffect(text, submitAttempted)` resolves it: `text` blank ⇒ success ⇒ hide IME + new `onItemCommitted()` callback collapses the row **instantly** (no flash); `text` non-blank ⇒ rejected ⇒ do nothing (keep keyboard + text).
- `onItemCommitted` is separate from `onClose` so a successful add does **not** fire the `quick_add_cancelled` analytics event.

Net: single add → row collapses + keyboard down in one frame; rejected add → keyboard stays, hint shows, text preserved. `:feature:home:testAndroidHostTest` PASS; APK installed on Pixel_9.

## 🔍 Code re-check 2026-06-12 — (pre-fix snapshot) PARTIALLY DONE (inconsistent across screens)

Verified against current code — the behaviour exists on **one** of the two add-item surfaces:

- ✅ **Create flow** (`feature/create/.../CreateChecklistScreen.kt:266` + `:274`): both the IME `onDone` and the check-button `onClick` now call `onConfirm()` then `keyboard?.hide()` (`keyboard = LocalSoftwareKeyboardController.current`). Keyboard hides after confirming.
- ❌ **Checklist detail flow** (`feature/home/.../detail/ChecklistDetailScreen.kt:2657-2676`, the inline add-item field — the todo's *primary* target): `onDone`/check-button only call `onAddItem()`; keyboard stays open (serial input preserved). The `focusManager.clearFocus()` at line 2612 fires only when the keyboard is dismissed *externally* (system back), to close the input — not a hide-after-add.
- ❌ **Shared `AddItemInputField`** (`core/designsystem`): still no IME hide.

**De-facto resolution of the original UX question** ("always hide" vs "serial input"): the codebase landed on *both* — Create hides (you confirm once), Detail keeps the keyboard for rapid serial entry. That's actually a defensible split. If the user wants Detail to also hide, it's a small change at `ChecklistDetailScreen.kt:2659/2666`. Kept **deferred** pending the user confirming whether Detail should match Create or stay serial.

> ⚠️ Note: commit `4d40080b "fix(home): lift checklist detail above keyboard"` is **not** this feature — it adds `Modifier.imePadding()` to lift content above the IME, unrelated to hiding it. Easy to conflate.

## Контекст

Session 2026-06-07: основная задача — убрать prompt chips из открытого чат-дока. Попутно user попросил «запиши в todo скрывать клаву когда создаёшь пункт чеклиста».

Сейчас при добавлении пункта (Done на клавиатуре ИЛИ тап по кнопке «+») клавиатура **остаётся открытой** — `AddItemInputField` нигде не вызывает `keyboardController.hide()` / `focusManager.clearFocus()`. User хочет, чтобы после создания пункта клавиатура скрывалась.

## Где менять

Главный компонент — `core/designsystem/.../components/AddItemInputField.kt`:
- `keyboardActions = KeyboardActions(onDone = { if (isTextNotBlank) onAdd() })` (строка ~87) — submit с клавиатуры.
- `Surface(onClick = onAdd, …)` (строка ~92) — кнопка «+».

Оба пути зовут только `onAdd()` и НЕ трогают IME. Чтобы скрывать клаву, добавить в компонент:
```kotlin
val keyboardController = LocalSoftwareKeyboardController.current
// или: val focusManager = LocalFocusManager.current
```
и вызвать `keyboardController?.hide()` (или `focusManager.clearFocus()`) внутри обёртки над `onAdd()` в обоих местах (Done + кнопка «+»).

## ⚠️ UX-вопрос ПЕРЕД реализацией (обсудить с user)

Скрывать клаву после **каждого** добавления конфликтует с серийным вводом — типичный паттерн чеклистов: «добавил пункт → клава осталась → сразу пишу следующий». Если клава будет закрываться, придётся каждый раз заново тапать поле. Варианты:
1. **Скрывать всегда** (буквально по запросу) — простой, но ломает быстрый серийный ввод.
2. **Скрывать только по кнопке «+», оставлять по Done** (или наоборот) — компромисс: явный «готово» жест прячет, Enter продолжает серию.
3. **Скрывать только когда поле очищается/теряет смысл** — например после последнего пункта.

Уточнить у user через `AskUserQuestion`, какой сценарий имелся в виду, прежде чем писать код.

## Контекст файлов (все call-sites AddItemInputField)

- `core/designsystem/.../components/AddItemInputField.kt` — сам компонент (источник изменения).
- `feature/home/.../detail/ChecklistDetailScreen.kt` — основной экран чеклиста.
- `feature/home/.../detail/weekly/WeeklyChecklistDetailContent.kt` — weekly-вариант.
- `feature/create/.../create/CreateChecklistScreen.kt` — создание чеклиста.
- `feature/create/.../preview/TemplatePreviewScreen.kt` — превью шаблона.
- `feature/analyze/.../preview/AnalyzeResultPreviewScreen.kt` — превью AI-результата.

Менять в одном компоненте — поведение применится ко всем экранам. Решить, нужно ли это везде или только на ChecklistDetailScreen (тогда вынести флаг-параметр `hideKeyboardOnAdd: Boolean = false`).

## Как возобновить

1. Прочитать этот файл.
2. Задать user UX-вопрос (см. секцию выше) — скрывать всегда / по «+» / по Done.
3. Добавить `LocalSoftwareKeyboardController` / `LocalFocusManager` в `AddItemInputField`, обернуть `onAdd()`.
4. Проверить отсутствие double-hide jank с `leadingPreview` (Smart Add chip) — chip анимируется по IME, hide не должен дёргать layout.
5. Билд: `:androidApp:compileDebugKotlin` + ручная проверка на Pixel_9 (добавить 2-3 пункта подряд, оценить серийный ввод).
