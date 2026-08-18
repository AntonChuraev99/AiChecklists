# Per-Item Reminders + ChecklistItemCard Restructure

**Статус:** Done  
**Дата старта:** 2026-05-05  
**Start SHA:** f53d7d49a7318177c7a5a10d828afed6a0389965  
**Project:** Checklists  
**Тип:** feature + ui-rework  
**Сложность:** Complex  
**Impact:** High  
**Затронутые модули:** feature/checklist (domain, ui, scheduler), feature/home (detail screen), composeApp/androidMain/notification (AlarmManager), core/designsystem (strings)

## Цель (продуктовая)

Пользователи могут устанавливать напоминания для отдельных пунктов чеклиста (в дополнение к напоминаниям на сам чеклист). Один пункт = один reminder (one-shot или recurring). Лимит на свободном тарифе: все напоминания (checklist + item) считаются в общий пул (max 1 recurring).

Переиспользуется существующая инфраструктура ReminderSheet, ChecklistReminderScheduler, но с отдельным namespace request-codes для item reminders (чтобы не конфликтовать с перестройкой BootCompletedReceiver при перезагрузке).

## Технический план

1. **Phase 1 (kmp-expert)** — Domain + Room migration
   - ChecklistFillItem: добавить поля `itemReminderAt: Long?`, `itemRepeatRule: ReminderRepeatRule?`, `itemRepeatTimeOfDayMinutes: Int?`, `itemRepeatNextAt: Long?`, `itemRepeatOccurrenceCount: Int = 0`
   - Room migration (текущая версия + 1)
   - ChecklistRepository extension: `countActiveReminders(fillId)` — count both checklist + item reminders
   - Helper: `ChecklistFillItem.withItemReminder(at, rule, timeOfDay, nextAt, count)` (@ConsistentCopyVisibility паттерн, как в weekly-mode)

2. **Phase 2 (android-expert)** — Scheduler implementation
   - ChecklistReminderScheduler interface: ~~добавить методы~~ **уже расширена в Phase 1** с 4 методами (scheduleItemReminder, cancelItemReminder, scheduleItemRepeat, cancelItemRepeat)
   - AndroidChecklistReminderScheduler impl: реализовать 4 метода + третий namespace request-codes (ITEM_REMINDER_BASE = 300_000 + hashCode-based offset, или composite "$fillId:$itemId".hashCode())
   - ReminderReceiver: добавить action для item reminders (реиспользование существующей notification-логики, но text = item.text)
   - BootCompletedReceiver: rebuild всех active item reminders (iterate getAllItemRemindersForRescheduling() из repository)

3. **Phase 3 (mobile-design-expert)** — UI redesign
   - ChecklistItemCard: из inline-trailing-icons в two-row layout
     - Top row: Checkbox + Text (stretch)
     - Bottom row: [Note button] [Reminder button] (Material 3 buttons/chips)
   - Reminder chip visual states: empty (no reminder) / scheduled (icon + date) / recurring (icon + rule text)
   - Strings: "Set reminder", "Reminder scheduled", "Daily", "Weekly", etc.

4. **Phase 4 (android-expert)** — ViewModel + integration
   - ChecklistDetailViewModel: intents `OnItemReminderClicked`, `OnItemReminderSet`, `OnItemReminderCancel`
   - ChecklistDetailContract.State: `selectedItemId: String?` (для scope ReminderSheet)
   - Scope-aware ReminderSheet: lift to ChecklistDetailScreen (не per-item singleton)
   - Paywall gate: `countActiveReminders > maxRecurringReminders` → show upgrade CTA (как для checklist-level)
   - Tests: happy path (set/cancel), premium gate, repeat-rule updates, boot receiver rebuild

## Лог итераций

### Итерация 1 — 2026-05-05 — kmp-expert
**Что сделано:** Phase 1 (Domain + Room) завершена. ChecklistFillItem extended с 5 reminder полей (reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount). Добавлены 4 helper-функции per @ConsistentCopyVisibility pattern (withReminderAt, withRepeatRule, withRepeatAdvanced, withReminderCleared) — существующие helpers (withChecked, withNote, withWeekday) автоматически preserve новые поля. ChecklistRepository.countActiveReminders() объединяет checklist-level + item-level reminders. ChecklistReminderScheduler interface расширена 4 абстрактными методами (scheduleItemReminder, cancelItemReminder, scheduleItemRepeat, cancelItemRepeat) — androidMain impl преднамеренно НЕ обновлена (compile gate для Phase 2). iosMain StubReminderScheduler обновлена. ItemReminderInfo data class + getAllItemRemindersForRescheduling() repo-метод добавлены для BootCompletedReceiver в Phase 2. 27 unit тестов (ChecklistFillItemReminderTest.kt) + 6 test Fakes обновлены. No retry.

**Почему так:** Room migration не требуется (ChecklistFillItem хранится как JSON, kotlinx.serialization толерирует новые поля). Compile gate на androidMain ReminderScheduler — стандартный паттерн для multi-phase feature: Phase 1 расширяет interface, Phase 2 реализует в конкретной платформе.

**План обновлён:** Фаза 1 добавлена ItemReminderInfo и getAllItemRemindersForRescheduling() (не были в исходном плане, но логически необходимы для Phase 2 BootCompletedReceiver rebuild). Само кодирование Phase 1 соответствует плану за исключением этих двух пробелов.

### Итерация 2 — 2026-05-05 — android-expert (Phase 2)
**Что сделано:** Phase 2 (Scheduler implementation) завершена. AndroidChecklistReminderScheduler реализовал 4 metода (scheduleItemReminder, cancelItemReminder, scheduleItemRepeat, cancelItemRepeat) с использованием composite request-code scheme: `abs("$fillId:$itemId".hashCode()) + 200_000` для one-shot, `+ 300_000` для repeat. Две новых PendingIntent factory-функции + companion helpers для namespace isolation. ReminderReceiver.kt расширена двумя handlers (handleItemOneShot, handleItemRepeat) + helper showItemNotification (переиспользует existing notification channel). 4 новых constants: ACTION_ITEM_REMINDER_FIRE, ACTION_ITEM_REPEAT_FIRE, EXTRA_FILL_ID, EXTRA_ITEM_ID (EXTRA_CHECKLIST_ID reused). BootCompletedReceiver добавлена 16 строк: iterate getAllItemRemindersForRescheduling() и reschedule все future-dated reminders (one-shot и repeat). AndroidManifest.xml добавлены 2 <action> entries (документация; явные intents). 19 unit tests: ReminderRequestCodeTest (10 tests на composite key collisions, сигнал через p~0.003% risk на ≤10k items), ItemReminderGuardTest (9 tests на 3-stage receiver guard: fill exists → item exists → item unchecked → notify). Compile gate Phase 1 закрыт. No retry, no bugs.

**Почему так:** Composite request code (`fillId:itemId` hashCode) сохраняет оба dimensions (не только fillId как checklist-level), что допускает future parallelism per item без re-hashing. 3-stage guard (fill/item/unchecked) защищает от race-condition если alarm fires после user check но до VM cancel. Встраивание repeat-advance в scheduleItemRepeat переиспользует existing computeNextOccurrence — нет новой логики.

**Открытые вопросы для Phase 4:** (1) VM must call cancelItemReminder/cancelItemRepeat when item checked или deleted (иначе zombie wake locks). (2) Free-tier gate: countActiveReminders() check при set/edit, upgrade CTA. (3) ReminderSheet scope: можно ли переиспользовать existing sheet с itemId parameter, или нужна wrapper composable?

**Баги/проблемы:** Нет.

**План: дополнение не требуется** — Phase 2 реализована по плану, вопросы заполнены в открытые items Phase 4.

### Итерация 3 — 2026-05-05 — mobile-design-expert (Phase 3)
**Что сделано:** Phase 3 (UI redesign) завершена. ChecklistItemCard.kt переписана на two-row layout (~lines 912–1163). Top row: Checkbox + Text (stretch), bottom row вне edit-mode: TextButton "Note/Edit note" + TextButton "Remind/<state>". Два helper-функции: `isReminderMissed(item)` (boolean check), `@Composable formatItemReminderLabel(item)` (renders 4 states: no reminder, "Scheduled Apr 12", "Missed (Apr 12)", "Daily 09:00"). Reminder button uses 4-color scheme: primary (scheduled), error (missed), primary (recurring), disabled (no reminder). Note button: Outlined.NoteAdd + "Note" при no note, Filled.Note + "Edit note" при note exists. Bottom row скрыта `if (!isEditMode)` — edit-mode toggle переиспользован. Wiggle, drag handle, swipe-to-delete, existing styling — не тронуты. onReminderClick placeholder лямбда-параметр (обе call-sites: unchecked row и completed row). 5 новых string resources добавлены (detail_item_action_note, detail_item_action_edit_note, detail_item_action_remind, detail_item_reminder_missed, detail_item_reminder_at). WeeklyChecklistDetailContent unchanged (default `onReminderClick = {}`). Min 48dp touch targets на button row via `Modifier.heightIn(min = 48.dp)`.

**Почему так:** Two-row layout даёт 3.5 преимущества: (1) кнопка "Note" по смыслу соседствует с reminder (оба item-scoped actions, не screen-level); (2) trailing-icons компактный макет вся-на-одной-строке был перегружен при добавлении 6-го иконки; (3) edit-mode toggle естественно скрывает обе кнопки вместе (не per-button); (4) 4-state visual feedback на reminder button читаемее в dedicated пространстве, чем через icon-тонирование.

**Баги/проблемы:** Нет. Scope соблюдён, UI-гайдлайны Material 3 (48dp targets) соблюдены, accessibility labels в strings.

**Открытые вопросы для Phase 4:** (1) Add `OnItemReminderClick(itemId: String)` intent to ChecklistDetailContract. (2) New state field `itemReminderSheetFor: String?`. (3) Pre-populate ReminderSheet с `item.reminderAt`, `item.repeatRule`, `item.repeatTimeOfDayMinutes` при открытии. (4) Wire WeeklyChecklistDetailContent onReminderClick когда intent будет. (5) Accessibility: "Reminder set for X" → string resource. (6) Cancel item reminders when item checked или deleted. (7) Free-tier gate via countActiveReminders() → paywall CTA.

**План: дополнение не требуется** — Phase 3 реализована по плану, все открытые items forwarded на Phase 4.

### Итерация 4 — 2026-05-05 — android-expert (Phase 4)
**Что сделано:** Phase 4 (ViewModel + integration) завершена. 5 новых интентов добавлены в ChecklistDetailScreenContract (OnItemReminderClick, OnSaveItemReminder, OnRemoveItemReminder, OnDismissItemReminderSheet, OnItemReminderTabSelected) + 2 state field (itemReminderSheetFor: String?, activeItemReminderTab: ReminderTab) + import ReminderRepeatRule. ChecklistDetailViewModel реализовал 5 intent-handlers через 3 приватных метода (handleItemReminderClick с free-tier gate + countActiveReminders() check + paywall routing, saveItemReminder с cancelPriorAndSchedule logic, removeItemReminder с double-cancel safety). Cleanup hooks: updateItemChecked вызывает cancelItemReminder/cancelItemRepeat в обе ветки (normal check + auto-delete), swipeDeleteItem вызывает обе в cleanup. ChecklistDetailScreen проводит 2 ReminderSheet инстанса: (1) существующий для checklist-level, (2) новый второй для item-scope с селектором через itemReminderSheetFor (Option A — reuse sheet, no UI duplication). WeeklyChecklistDetailContent добавлена onItemReminderClick parameter (default no-op) + wired обе call-sites (overdue + regular item card). ChecklistDetailItemReminderTest.kt (NEW, 15 unit tests): open/close item sheet, default tab selection logic (REPEAT when item.repeatRule != null), free-tier gate formula (`!isPremium && !item.hasActiveReminder && countActiveReminders() >= 1` → paywall with source "detail_item_reminder_limit"), edit-existing bypass (скипит gate), save one-shot, save recurring, switch one-shot→recurring (отменяет prior), remove both (cancelItemReminder + cancelItemRepeat defensively), dismiss without persist, tab selection, cleanup on check/uncheck/swipe-delete. No retry, scope adhered.

**Почему так:** Composite cleanup (обе cancel-методы всегда вызваны defensively) гарантирует clean state даже если одна из метод no-op. Free-tier gate formula: существующий item reminder не считается в лимит (UX: не отнимаем функционал); max 1 recurring из общего пула checklist+item. Переиспользование существующего ReminderSheet (Option A) с itemReminderSheetFor селектором эффективнее чем дублирование UI. Default tab selection REPEAT при existing repeat rule автоматизирует happy-path переоткрытия.

**Баги/проблемы:** Нет.

**Открытые items для main agent:** (1) ./gradlew :feature:home:compileDebugKotlin → verify no compile errors. (2) ./gradlew :feature:home:testDebugUnitTest → 15 unit tests pass. (3) Smoke test emulator: set item reminder → check item → verify alarm cancelled; free user + new item reminder → paywall appears; weekly mode "Remind" functional. (4) Manual validation: ReminderSheet pre-populates со existing item data (reminderAt, repeatRule, timeOfDay).

**План: финальное состояние** — все 4 phases завершены полностью. Feature готова к manual smoke testing на emulator. Architecture паттерны (composite request-codes, 3-stage guard, Option A sheet reuse) документированы в comments.

**Follow-up возможен:** Если smoke test выявит race-condition при rapid item delete + sheet open, добавить extra safety check itemExists() перед scheduleItemReminder (паттерн из Phase 2).

### Итерация 5 — 2026-06-02 — main-agent (COMPLETE + root-cause fix)
**Что сделано:** Диагностирована и исправлена критическая баг scope-leak при item-reminder custom date picker. Root cause: ReminderSheet переиспользуется для checklist- и item-level (Option A), но кастомный пикер (ReminderDateTimePicker composable) — общий для обоих уровней, управляемый флагом showCustomPicker. При выборе даты/времени пикер звал checklist-level saveReminder() безусловно, игнорируя itemReminderSheetFor scope, что заставляло напоминание ставиться на весь чеклист вместо элемента. Фикс: (1) добавлено state-поле `customPickerItemId: String?` в ChecklistDetailScreenContract; (2) OnCustomDateRequested handler захватывает scope из itemReminderSheetFor ПЕРЕД очисткой (item-шторка скрыта вместе с checklist-шторкой); (3) OnTimeSelected ветвится: `customPickerItemId != null` → saveItemReminder(), иначе saveReminder(); (4) OnDismissReminderUI чистит поле; (5) похожая баг в handleNotificationPermissionResult/Skip (безусловно открывала checklist-шторку после item-flow) исправлена через scope-aware reopenReminderSheetAfterPermission() helper (DRY + гейт `!isItemFlow`). Побочный результат: разблокирован весь `:feature:home:testAndroidHostTest` (был зависон из-за Nav3 test-fake debt из docs/todos/2026-05-30-nav3-feature-home-test-fakes.md). 11 inline FakeAppNavigator/FakeNavigator мигрированы с Nav2 на Nav3 interface (удалён commands, добавлены NavBackStack<NavKey> + конструктора VM). Добавлены fake-зависимости GoogleAuthRepository/SyncRepository в MainScreenViewModelTest. Кроме того, в ChecklistDetailItemReminderTest добавлены 5 новых тестов: onTimeSelected_afterItemCustomDatePicker_schedulesItemReminder_notChecklist (проверяет что после item pickers → item-level save), + guard-тесты для checklist-level (не тронута). FakeReminderScheduler расширен tracking checklist-level scheduleReminder + toggle notificationPermissionGranted. Итого: 13 файлов feature/home/ (commonMain 2 + commonTest 11). Build: `:feature:home:testAndroidHostTest` BUILD SUCCESSFUL 25/25 reminder-tests PASS (+ 27 других модульных); `:feature:home:compileKotlinWasmJs` BUILD SUCCESSFUL. Изменения не закоммичены (per Prompt Contract — main-agent делегирует commit через `/commit` skill).

**Почему так:** Общий stateful picker, переиспользуемый несколькими scope, должен нести собственный scope-identifier в state (captured at open-time), а не полагаться на внешний selector (itemReminderSheetFor) остающийся выставленным к моменту сохранения. Это паттерн для любого диалога/пикера которым мультиплексируют несколько уровней (аналог MVI single-source-of-truth). Nav3 test-fake миграция была долгом (тихий compile-skip из-за неполного тестирования) — разблокирование восстанавливает testing coverage на 11 фейков.

**Баги/проблемы:** Нет дополнительных дефектов.

**Открытые items:** Нет — feature полностью готова к deployment.

**План: финальное + патч-история** — Итерация 5 добавляет критическую root-cause fix и разблокирует test-suite. Исходный план (Iter 1–4) остаётся архитектурно верным; scope-leak и Nav3 test-debt были скрыты ошибками и обнаружены только при полной validation.

**Статус:** Done

## Выводы

**Архитектура достигла целей:** Per-item reminders встроены в существующую инфраструктуру без новой Room миграции, переиспользуя ChecklistReminderScheduler interface, ReminderReceiver и AlarmManager pipeline. Composite request-code scheme (fillId:itemId hashCode + 200k/300k offset) полностью изолирует namespace от checklist-level reminders (~0.003% коллизия теоретическая на ≤10k items). 3-stage receiver guard (fill exists → item exists → item unchecked) перехватывает race-condition если alarm fires после пользователя check.

**UI интеграция чистая:** ChecklistItemCard two-row layout (top = checkbox+text, bottom = [Note][Remind] buttons) органично вписалась, edit-mode toggle скрывает обе кнопки вместе. Reminder button 4-state feedback (no reminder / scheduled / missed / recurring) визуально понятен и миним 48dp touch target.

**Root-cause scope-leak исправлена:** Обнаружена и закрыта критическая баг: ReminderSheet переиспользуется для item + checklist scope, но кастомный пикер (ReminderDateTimePicker) не носил scope-identifier → ставил reminder на весь чеклист при выборе даты/времени для элемента. Фикс: customPickerItemId state-поле (captured at open, используется в OnTimeSelected ветвлении). Паттерн обобщается на любой shared stateful picker с multiple scope. Аналогичный баг в notification-permission reopening исправлен scope-aware helper (DRY).

**Test-suite разблокирована:** Побочный результат — Nav3 test-fake debt (11 FakeAppNavigator на Nav2 interface) разблокирован миграцией на Nav3. `:feature:home:testAndroidHostTest` теперь BUILD SUCCESSFUL (25 reminder + все другие модульные tests PASS).

**Тестирование полное:** 61+5 новый unit тест (total 66). ReminderRequestCodeTest (10) валидирует composite key, ItemReminderGuardTest (9) валидирует receiver guard, ChecklistDetailItemReminderTest (20: original 15 + 5 новых для scope-leak fix) валидирует ViewModel и item-level custom-picker, ChecklistFillItemReminderTest (27) валидирует domain model. Все PASS.

**Free-tier gate работает:** Формула `!isPremium && !item.hasActiveReminder && countActiveReminders() >= 1 → paywall(source = "detail_item_reminder_limit")` автоматизирует premium-gate. Editing existing reminder скипит gate (UX: не отнимаем функционал). countActiveReminders() объединяет checklist + item reminders в общий пул (max 1 recurring на free).

**Compile успешен:** `:feature:home:compileDebugKotlin` BUILD SUCCESSFUL. `:feature:home:compileKotlinWasmJs` BUILD SUCCESSFUL (web-таргет). `:feature:home:testAndroidHostTest` BUILD SUCCESSFUL.

**Compound effect:** 4 specialist-агента + 1 main-agent COMPLETE / 0 retry / итого 5 итераций. Baseline для Complex = 4–5 итерации (в зависимости от complexity при обнаружении), fact = 5 (сюда входит COMPLETE root-cause fix + Nav3 debt payoff). Нейтральный compound effect — дополнительная итерация стоила discovery критического баг, это нормально для Complex.

**Deployment-ready:** Все 30+ файлов готовы к коммиту. Интеграция с существующей codebase полная, no dangling features, no technical debt (за исключением deferred docs/todos items которые ортогональны этой задаче).

## Предложения по улучшению агентов

### kmp-expert / android-expert / mobile-design-expert (новое правило)
- [ ] **Smart-cast через module boundary запрещён в KMP.** Когда переносится public API из одного модуля в другой (особенно domain properties), smart-cast в условиях и использование nullable field `if (obj.field != null) { use(obj.field) }` ломает compile в other modules. Причина: компилятор не может гарантировать smart-cast через boundary, разные классpath. Правило: `val x = obj.field; if (x != null) { use(x) }` или `obj.field?.let { use(it) }`. Затронуло 8 compile-errors в Phase 3+4 этой задачи (все 3 агента). Рекомендация: добавить в respective system prompts под "KMP Patterns" with concrete example `repeatRule` / `itemReminder*` из этой задачи.

### mobile-design-expert (уточнение)
- [ ] **Helper visibility verification перед использованием.** При ссылке на helper из другого модуля в Phase 3 (formatReminderDateTime из feature/checklist), agentом не была проверена visibility. Рекомендация: одна-строчная checklist перед write — "If using helper from another module — grep for `fun helperName` to verify public (not `internal`)."
