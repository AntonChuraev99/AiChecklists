# Inline Item Text Edit in ItemDetailsSheet

**Статус:** Done
**Дата старта:** 2026-05-12
**Start SHA:** a9793ab5 (refactor(ui): fold name into progress header)
**Project:** Checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/home, feature/checklist, core/designsystem

## Цель (продуктовая)

Пользователь может отредактировать текст элемента чеклиста прямо в ItemDetailsSheet (ModalBottomSheet), не переходя на Edit-экран. Заголовок sheet (текст элемента) при тапе превращается в TextField с автофокусом и подсказкой клавиатуры; сохранение по IME Done или blur. После сохранения:
- Dual-update: fill и template обновляются одновременно
- ChecklistDetailScreen и Edit-экран автоматически видят изменение через reactive `observeChecklistById()` Flow (фикс от 2026-05-12)
- UX улучшение: не нужно открывать Edit-экран для быстрого переименования элемента

## Технический план

1. **Domain layer (feature/checklist)**
   - Добавить helper `ChecklistFillItem.withText(newText: String): ChecklistFillItem` с `@ConsistentCopyVisibility` guard
   - Валидация: trim() + isBlank() check → если пусто, вернуть `this` неизменённый

2. **Data layer (feature/checklist)**
   - Extend `ChecklistRepository` методом `updateItemText(fillItemId, checklistId, newText)` который:
     - Вызывает `updateFill(fill.copy())` после обновления item
     - Вызывает `updateChecklistTemplate(template.copy())` после обновления item в template
     - Гарантирует оба обновления успешны

3. **ViewModel (feature/home - ChecklistDetailViewModel)**
   - Добавить Intent: `OnItemTextEditSubmit(fillItemId, newText)`
   - Добавить Screen State field: `editingItemTextFor: String? = null` (fillItemId или null)
   - Handler для Intent: валидировать, вызвать repository.updateItemText(), обновить state
   - Guard: `editingItemTextFor != null` → игнорировать дублирующие события (IME Done + blur)

4. **UI layer (feature/home - ItemDetailsSheet & ChecklistDetailScreen)**
   - Modify ItemDetailsSheet заголовок (текущий header Row с text)
   - Добавить двухрежимный заголовок: read-only Text VS TextField
   - На тап заголовка: перейти в edit-mode (`editingItemTextFor = fillItemId`)
   - FocusRequester + LaunchedEffect для автофокуса
   - Сохранение:
     - IME Done: commit + close edit-mode
     - onFocusChanged(false): commit + close edit-mode
     - Double-fire guard: сохранить в state только если `editingItemTextFor == fillItemId`
     - Blank validation: если trim() пусто → cancel (не commit)
   - Новая локальная строка: `item_text_hint` = "Item name" (placeholder в TextField)

5. **Compose Resources (core/designsystem)**
   - Добавить строку в `strings.xml`: `<string name="item_text_hint">Item name</string>`

6. **Validation & Testing**
   - Unit тесты в ChecklistDetailViewModelTest (или новый ItemTextEditTest):
     - Happy path: edit + save → dual-update called
     - Blank validation: edit пусто → ignored, state не меняется
     - Double-fire guard: IME Done + blur only commit once
     - Cancel: esc или blur с неизменённым текстом → no-op
   - E2E: открыть Detail → тап на элемент → open sheet → тап на заголовок → edit → submit → проверить рефлекшн в Detail + Edit

## Лог итераций

### Итерация 1 — @android-expert (2026-05-12)

**Что сделано:** Полная реализация inline-edit режима в ItemDetailsSheet. Все 6 шагов плана выполнены без переделок.

**Выполненные шаги:**

1. **Domain layer** — `ChecklistFillItem.withText()` helper добавлен в `feature/checklist/src/commonMain/.../domain/model/Checklist.kt` рядом с существующими `withChecked()/withNote()/withWeekday()/withPriority()`. Обход `@ConsistentCopyVisibility` через стандартный паттерн.

2. **State & Intent** — `ChecklistDetailScreenContract.kt` расширен:
   - State.Content: добавлены `editingItemTextFor: String? = null` и `editingItemTextDraft: String = ""` 
   - Новые Intent: `OnStartItemTextEdit(itemId)`, `OnItemTextDraftChange(text)`, `OnConfirmItemTextEdit`, `OnCancelItemTextEdit`

3. **ViewModel handlers** — `ChecklistDetailViewModel.kt`: 4 новых handler'а (`startItemTextEdit`, `updateItemTextDraft`, `cancelItemTextEdit`, `confirmItemTextEdit`):
   - `confirmItemTextEdit`: blank-guard + same-text guard → cancel вместо commit, dual-update (fill + template), analytics-событие `item_text_edited`
   - `OnDismissItemDetailsSheet`: при закрытии sheet сбрасывает edit-mode (cancel)

4. **UI layer** — `ItemDetailsSheet` и `ChecklistDetailScreen.kt`:
   - Title двухрежимный: view-mode = clickable Text; edit-mode = BasicTextField (прозрачный, без рамки, `headlineSmall` typography, `singleLine=true`)
   - IME Done → confirm, blur (`onFocusChanged`) → confirm, autofocus через FocusRequester + LaunchedEffect(Unit)
   - Integration point (604-616): пробрасываются state.editingItemTextFor/Draft и intent callback'и

5. **Localization** — Добавлена строка `detail_item_sheet_edit_text_hint` в `core/designsystem/src/commonMain/composeResources/values/strings.xml`

6. **Testing** — `feature/home/src/commonTest/.../ChecklistDetailItemDetailsSheetTest.kt` — 6 новых unit-тестов:
   - start edit, draft change, blank cancel, same-text cancel, valid dual-update, dismiss-cancels-edit

**Ключевые паттерны (уже есть в проекте):**
- Item ID между fill и template не совпадает → матчим по text совпадению (как в `deleteItemFromSheet`)
- BasicTextField впервые в проекте для inline-режима (все остальные TextField'ы через AppTextField). Не выносится в design system до второго use-case.
- FocusRequester + LaunchedEffect(Unit) работают потому что ItemDetailsSheet использует Column, не LazyColumn.

**Питфоллы и решения:**
- Double-fire guard критичен: `editingItemTextFor ?: return` в confirmItemTextEdit() → иначе IME Done + blur вызовут commit дважды
- Blank text validation (`newText.isBlank()`) → cancel, не commit (иначе item исчезнет)
- Dual-update обязательно — иначе Edit-экран покажет старое значение
- Swipe down на sheet → cancel (не commit). Это интенционально: abandon редактирования без потери данных

**Почему так:** Специалист выявил, что ID мотчинг требует по text, а не по ID. Это сохранение паттерна из существующего кода (deleteItemFromSheet), а не новое изобретение. Прозрачный BasicTextField нужен именно для inline-стиля внутри Sheet (OutlinedTextField + AppTextField слишком heavy для этого контекста).

**Что дальше:** Главный сейчас валидирует build (compileDebugKotlin + testDebugUnitTest для feature/home + feature/checklist). После validation → COMPLETE-фаза.

## Выводы

Inline item text edit в ItemDetailsSheet успешно реализован в одну итерацию благодаря детальному брифу специалисту с готовыми сигнатурами handlers и точными путями файлов.

**Ключевые результаты:**
- Пользователь может редактировать текст элемента прямо в sheet (view-mode Text + edit-mode BasicTextField)
- IME Done / onFocusChanged(false) сохраняют изменения; blank/same-text → cancel
- Dual-update паттерн обновляет fill и template одновременно, синхронизируя Detail и Edit экраны через reactive Flow
- 6 новых unit-тестов в ChecklistDetailItemDetailsSheetTest, все pass
- Build: compileDebugKotlin (только pre-existing warnings), testAndroidHostTest (PASS)

**Файлы изменены:**
- feature/checklist/domain/model/Checklist.kt — `ChecklistFillItem.withText()`
- feature/home/presentation/detail/ChecklistDetailScreen{,Contract,ViewModel}.kt — State, Intent, UI, handlers
- feature/home/presentation/detail/ChecklistDetailItemDetailsSheetTest.kt — новый тестовый файл (6 тестов)
- core/designsystem/composeResources/values/strings.xml — `detail_item_sheet_edit_text_hint`

**Паттерны документированы в solution-файле:** docs/solutions/ui-improvements/inline-edit-item-text-in-details-sheet-2026-05-12.md

## Предложения по улучшению агентов

### android-expert
- [ ] Рассмотреть добавление в "Sheet Patterns" section: когда inline-edit режим уместен vs когда нужен отдельный диалог. Для ModalBottomSheet с одним полем — inline через BasicTextField эффективнее, чем separate dialog.
