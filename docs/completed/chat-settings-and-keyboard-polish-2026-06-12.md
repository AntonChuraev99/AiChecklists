# Chat Settings Premium Flag + Keyboard Hide Polish

**Статус:** Done
**Дата старта:** 2026-06-12
**Start SHA:** 40f82e2a (fix(checklist): pin detail toolbar on scroll)
**Project:** gisti-ai-checklists
**Тип:** feature
**Сложность:** Trivial
**Impact:** Low
**Затронутые модули:** feature/aichat/impl, feature/home

## Цель (продуктовая)
Wire the isPremium flag into the chat settings PRO badge so it actually reflects subscription status; hide keyboard on successful checklist item addition to match Create flow UX.

## Технический план
1. ✅ Chat settings: add isPremium to ChatScreenState; mirror UserData.isPremium in ChatViewModel getUserDataFlow().collect (no second RevenueCat subscription — reuse existing flow).
2. ✅ Keyboard hide on detail add: keyboardController?.hide() after successful onAddItem(); detect success via synchronously-cleared input text (no ViewModel side-effect).
3. ✅ Weekly add same pattern.

## Лог итераций

### Итерация 1 — 2026-06-12 — main-agent
**Что сделано:** 
- Chat settings isPremium wired: `ChatScreenState.isPremium` + `ChatViewModel` mirrors it from getUserDataFlow().collect block (same block that already feeds creditBalance). No second RevenueCat subscription.
- Keyboard hide: `ChecklistDetailScreen` + `WeeklyChecklistDetailContent` — added `keyboardController?.hide()` after `onAddItem()` success. Used text-cleared-synchronously logic to detect success (rejected Smart Add preserves text + keeps keyboard).
- UX refinement: `InlineAddItemInput` now has `submitAttempted` flag + `LaunchedEffect(text, submitAttempted)` to collapse the row instantly on success (no flash waiting for IME-dismiss effect). Separate `onItemCommitted` callback (does not fire `quick_add_cancelled` analytics).

**Почему так:** 
- isPremium: UserData already flowed creditBalance in ChatViewModel, adding isPremium to the same collect block costs one line, avoids over-coupling.
- Keyboard: detection via cleared text is reliable because `addItemWithParse()` clears `pendingItemInput` synchronously and only on success (reject branches early-return with text preserved).
- UX flash: the IME-dismiss effect (waits for keyboard animation) was slow; detecting success via text state allows instant collapse + keyboard hide in one frame.

**Баги/проблемы:** 
- Initial hide-keyboard approach too brutal: unconditional `keyboardController?.hide()` fired even on rejected Smart Add (e.g., "tomorrow 7am" trigger-only phrase → hint shown, input preserved — user should keep typing). Fixed by detecting success via text.

**Решение:** 
- Text-state-driven success detection avoids ViewModel side-effect coupling (ChecklistDetailViewModel has `Nothing` side-effects anyway).
- `submitAttempted` flag + LaunchedEffect(text, submitAttempted) handles the state flow without race conditions.

## Выводы

**Задачи завершены:**
1. **Chat settings isPremium** — one-line addition to ChatViewModel collect block. Validation `:feature:aichat:impl:testAndroidHostTest` 302 PASS.
2. **Keyboard hide + UX polish** — wired consistently across detail/weekly, with proper success-detection. Validation `:feature:home:testAndroidHostTest` PASS. APK installed Pixel_9.

**Ключевые паттерны:**
- Reuse existing flow dependencies to avoid over-coupling (isPremium via userData, not separate RevenueCat subscription).
- Text-state-driven success detection when ViewModel has no side-effect channel.
- LaunchedEffect on reactive state (text, flag combo) resolves UI-local state without explicit callbacks.

## Предложения по улучшению агентов

### android-expert
- [ ] Document text-state-driven success detection pattern for UX polish when ViewModel constraints preclude side-effects.

### Other agents
- (no proposals)

---

⚠️ **INIT phase was skipped — minimal active doc reconstructed during COMPLETE.** Counters may be approximate.
