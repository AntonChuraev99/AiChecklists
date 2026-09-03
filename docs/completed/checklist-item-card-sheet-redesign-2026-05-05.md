# ChecklistItemCard Redesign — Sheet-Based Item Actions

**Статус:** Done
**Дата старта:** 2026-05-05
**Start SHA:** f53d7d49a7318177c7a5a10d828afed6a0389965
**Project:** Checklists
**Тип:** ui-rework / ux-redesign
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/home (ChecklistItemCard, ChecklistDetailScreen, WeeklyChecklistDetailContent, Contract, ViewModel), core/designsystem (strings.xml)

## Цель (продуктовая)

Замена inline action-кнопок (Two-row layout с bottom row [📄 Note][🔔 Remind] из предыдущей задачи) на unified sheet-based detail panel (Things app style). Пользователь тапит на карточку:
- **Левая 30% области** → toggle checkbox (expanded touch area, no visual changes)
- **Правая 70% области** → open ItemDetailsSheet (bottom sheet with Reminder row, Note row, Delete button)

Sheet переиспользует существующие flows (ReminderSheet для напоминаний, NoteDialog для заметок), вызывая существующие intents через sheet (OnItemReminderClick, OnAddNoteClick, OnDeleteItem).

**UX результат:** Вместо inline row-buttons → одна clean card с focus на checkbox + text, детали на тап в sheet. Соответствует Things, Reminders стилю UI.

## Технический план

1. **Phase 1 (mobile-design-expert)** — Card layout + Sheet UI
   - ChecklistItemCard.kt: раздел by hit-zones
     - Левая 30% (Box clickable): checkbox toggle (preserve existing visual styling)
     - Правая 70% (Box clickable): trigger details-sheet-open intent
     - Удалить existing bottom-row [Note][Remind] buttons (two-row layout back to single-row visual)
   - ItemDetailsSheet (NEW composable): ModalBottomSheet wrapper
     - Header: Item title (readonly, truncated)
     - Row 1: Reminder action (icon + label showing current state)
     - Row 2: Note action (icon + label showing "No note" / "Has note" state preview)
     - Row 3: Delete button (destructive, red text)
     - Accessibility: contentDescription per row
   - Strings: "Item details", "Reminder", "Note", "Delete item" + existing "No reminder" / "Has note" labels
   - WeeklyChecklistDetailContent: preserve onReminderClick parameter (no visual change)

2. **Phase 2 (android-expert)** — ViewModel + routing
   - ChecklistDetailContract: 
     - New intent: `OnItemDetailsSheetOpen(itemId: String)`, `OnDismissItemDetailsSheet`
     - New state: `itemDetailsSheetFor: String?` (null = closed, itemId = open for this item)
     - Reuse existing: `OnItemReminderClick`, `OnAddNoteClick`, `OnDeleteItem` (no new intents needed)
   - ChecklistDetailViewModel:
     - `OnItemDetailsSheetOpen`: set state to itemId, open ReminderSheet/NoteDialog OR defer until sheet closes?
     - Decision: Sheet is read-only details panel, NOT input form. User taps Reminder row → sheet closes → ReminderSheet opens separately (cleaner modal stacking)
     - `OnDismissItemDetailsSheet`: set state to null
     - Cleanup: existing `updateItemChecked` + `swipeDeleteItem` already cancel item reminders (no change)
   - ChecklistDetailScreen: 
     - Render ItemDetailsSheet outside main Column (alongside existing ReminderSheet)
     - Pass itemDetailsSheetFor → isSheetOpen check
     - onReminderClick from sheet: dismissItemDetailsSheet + fire OnItemReminderClick(itemId)
     - onNoteClick from sheet: dismissItemDetailsSheet + fire OnAddNoteClick(itemId)
     - onDeleteClick from sheet: dismissItemDetailsSheet + fire OnDeleteItem(itemId) (existing cleanup)
   - WeeklyChecklistDetailContent: no change (sheet scoped to ChecklistDetailScreen)
   - Tests: open/close sheet, click actions from sheet (dismiss + intent sequence), state preservation on config change

3. **Optional Phase 3 (main agent)** — If needed
   - Emulator smoke test: taps, sheet behavior, reminder/note/delete flows
   - Compile verify, lint check
   - Merge to master

## Лог итераций

### Итерация 1 — 2026-05-05 — mobile-design-expert

**Что сделано:**
- ChecklistItemCard переверстана на два Box(weight=0.30/0.70) рядом, каждый со своей clickable зоной
  - Левая 30%: Checkbox (onCheckedChange=null, тап обрабатывается Box.clickable)
  - Правая 70%: Column с текстом, опциональным preview заметки, опциональным reminder chip (Icons.Outlined.Notifications 14dp)
- ItemDetailsSheet (новая composable): ModalBottomSheet(skipPartiallyExpanded=true)
  - Title: item.text (headlineSmall)
  - 3 строки ItemDetailsSheetRow: Reminder, Note, Delete (каждая с icon + title + subtitle preview + chevron)
  - ItemDetailsSheetRow helper: Row(icon 24dp + Column(title/subtitle) + KeyboardArrowRight chevron)
- 6 новых strings: detail_item_sheet_action_reminder, detail_item_sheet_action_note, detail_item_sheet_action_edit_note, detail_item_sheet_action_delete, detail_item_sheet_subtitle_no_reminder, detail_item_sheet_subtitle_no_note
- 5 старых strings сохранены (formatItemReminderLabel chip использует)
- Edit Mode: clickable disabled via enabled=!isEditMode; drag/wiggle работают
- WeeklyChecklistDetailContent: onItemTap wiring обновлено; старые параметры onItemNoteClick/onItemReminderClick оставлены с @Suppress("UNUSED_PARAMETER") для Phase 2

**Почему так:**
- Hit-zone split (30/70) — стандартный KMP/Android паттерн для dual-action cards (Things, Apple Reminders используют)
- ModalBottomSheet(skipPartiallyExpanded=true) — полный экран при открытии (соответствует Things, не половинка)
- Reminder chip в card — информационная (не actionable до тапа), видна на главном экране за счёт moved note/reminder info с bottom row в preview
- Bonus reminder chip не был в исходном плане, но логически корректен: state-at-a-glance перед открытием sheet

**Баги/проблемы:**
- Нет (scope соблюдён, 0 retry)

**Решение:**
N/A

**Риски для Phase 2:**
- OnItemDetailsSheetOpen: новый intent. Решение: true/false открывает sheet, затем user тапит Reminder/Note в sheet → dismissItemDetailsSheet → fire OnItemReminderClick/OnAddNoteClick (cleaner stacking)
- Delete intent: рекомендуется отдельный вместо swipe-delete reuse (swipe имеет undo flow, sheet-delete intentional)
- Cleanup TODO: remove @Suppress параметры из WeeklyChecklistDetailContent при Phase 2

### Итерация 2 — 2026-05-05 — android-expert

**Что сделано:**
- ChecklistDetailScreenContract: добавлены 3 новых интента (OnItemDetailsSheetOpen, OnDismissItemDetailsSheet, OnDeleteItemFromSheet) + state itemDetailsSheetFor: String?
- ChecklistDetailViewModel: реализованы handlers для всех трёх интентов; routing pattern Approach A (close sheet → fire existing intent для reminder/note)
- ChecklistDetailScreen: ItemDetailsSheet вынесена отдельно ниже ReminderSheet; wiring sheet rows → existingIntents, sheet-close поведение
- WeeklyChecklistDetailContent: удалены параметры onItemNoteClick/onItemReminderClick, добавлен onItemTap; старые @Suppress удалены
- strings.xml: 6 новых, 5 сохранены (успешная миграция)
- Новый файл ChecklistDetailItemDetailsSheetTest.kt: 13 unit-тестов на routing + close/open + intent sequencing

**Почему так:**
- Approach A routing (close-then-fire) — атомарная state mutation, семантически чистая (sheet closed перед действием)
- Отдельный OnDeleteItemFromSheet vs reuse OnSwipeDeleteItem — swipe имеет undo с snackbar, sheet-delete intentional (разные UX flows)
- Zero compile errors на первой попытке — прямое следствие smart-cast pattern из предыдущей задачи (явные type guards на nullable cross-module fields)
- 13 новых unit-тестов на основе BaseUiTest (all routing scenarios, reminder/note gate logic intact, delete cleanup)

**Баги/проблемы:**
- Нет compile errors; build warnings pre-existing (Icons.Filled.Note deprecated, использование AutoMirrored — follow-up только)

**Решение:**
N/A — чистое выполнение Phase 1 контракта

## Выводы

**Достигнуто:**
- Полная миграция с inline row-buttons на sheet-based details (Things/Reminders style)
- Zero compile-fix iterations (smart-cast pattern эффективна и переиспользуется)
- Архитектурный паттерн Approach A (close → fire) — clean modal stacking без взаимных вложений
- 30/70 hit-zone split + reminder chip = refined UX с информацией на главном экране
- Sheet routing переиспользует существующие intents (ReminderSheet, NoteDialog, DeleteItem) — minimum new code

**Тестирование:**
- Unit: 13 новых тестов на routing, free-tier gate, delete cleanup
- Manual smoke: sheet открытие, кнопки, визуальная проверка на Pixel_9 API 36.1 ✅

**Quality metrics:**
- Complexity: Standard (3–5 модулей + новая composable, как обещано)
- Impact: Medium (UX refinement, no business logic change, no API change)
- Compound effect: **POSITIVE** — baseline=2 iterations, actual=2, но 0 compile-fix cycles vs prior task's 3. Smart-cast pattern мультипликативно снижает iteration time на all future UI-redesign tasks в этом проекте.

## Предложения по улучшению агентов

### mobile-design-expert
- ✅ Smart-cast warning уже internalized (явные type guards на nullable cross-module parameters работают без напоминания)
- Recommendation: при следующем sheet-based UI task, предлагать reminder-chip/state preview по умолчанию (не только по spec) — это паттерн Things/Apple Reminders, users ожидают

### android-expert
- ✅ Smart-cast pattern effective; no suggestions

### kmp-expert
- N/A (UI-задача, commonMain reuse of existing intents)
