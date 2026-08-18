# Inline Item Edit Mode

**Статус:** Done
**Дата старта:** 2026-04-18
**Start SHA:** 1df707c4e5aeddf09ca4883516ad263cea8fc2f7
**Project:** Checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/create, feature/checklist/domain, core/designsystem

## Цель (продуктовая)
Добавить inline-редактирование текста элемента чеклиста в режиме edit mode экрана `CreateChecklistScreen` (доступен через toolbar pencil в `ChecklistDetail` → `OnEditChecklistClick` → `CreateChecklistRoute.CreateChecklist(editChecklistId = X)` с `isEditMode = true`). У каждого item карточки появляются две inline-кнопки: удалить (крестик) и редактировать текст (карандаш). Тап на карандаш превращает текст в OutlinedTextField с автофокусом и открытой клавиатурой. IME Done / фокус на непустом тексте — автосохранение (commit), фокус на пустом — revert. Все изменения сохраняются одной командой `updateChecklist(...)` при нажатии Save (существующий flow).

## Технический план
1. ✅ **Расширить CreateChecklistScreenContract.kt** — добавить `editingItemId: String?`, `editingItemText: String` в State; `OnStartItemEdit(itemId)`, `OnItemEditTextChange(text)`, `OnConfirmItemEdit`, `OnCancelItemEdit` в Intent
2. ✅ **Реализовать handlers в CreateChecklistViewModel.kt** — `onIntent(exhaustive)` с guards, `startEdit()` calls `commitPendingEdit()` первым (критично), `commitPendingEdit()` trigger on IME Done + blur, blank-revert / non-blank-commit
3. ✅ **Написать unit tests (TDD)** — 9 тестов: 3 state-verify, 6 VM behavior (start→change→confirm, cancel, switch, double-save, error paths); StandardTestDispatcher + TestScope + synchronous onIntent (не sendIntent)
4. ✅ **InlineItemEditCard composable** — `OutlinedTextField` embedded inline, `FocusRequester + LaunchedEffect(Unit) { requestFocus(); keyboard?.show() }`, `onKeyboardEvent(ImeAction.Done) → onConfirm()`, `onFocusLost → if (text.blank()) revert else commit`
5. ✅ **IconButton Edit в item card** — `Icons.Outlined.Edit` рядом с Close в трейлинг-группе; тап → `OnStartItemEdit(itemId)`
6. ✅ **ChecklistItem.withText() helper** — сохраняет id + checked состояние, аналог заполнения через map/copy
7. ✅ **Strings в core/designsystem** — `create_edit_item`, `create_confirm_item_edit`
8. ✅ **Validation** — `compileDebugKotlin` Android: SUCCESS; `testDebugUnitTest` feature/create: 9/9 PASS; Build: green

## Лог итераций

### Итерация 1 — 2026-04-18 — main-agent
**Что сделано:** Реализована полная функция inline-edit в `CreateChecklistScreen`: UI (Edit icon + InlineItemEditCard с FocusRequester), Contract (State + Intent), ViewModel handlers (startEdit/commitPendingEdit/confirmEdit/cancelEdit с guard от double-fire), strings, новый helper `ChecklistItem.withText()`, 9 unit тестов (TDD паттерн).

**Почему так:** Inline-edit требует синхронизации нескольких слоёв (UI focus/keyboard ↔ State ↔ VM handlers), поэтому TDD (красный → зелёный) дал корректное поведение. `FocusRequester + LaunchedEffect(Unit)` — стандартный KMP Compose паттерн для autofocus. `commitPendingEdit()` вызывается ДО смены `editingItemId` (при переходе между items) и в `onSaveClick()` (финальная commit перед записью в Room) — предотвращает потерю in-flight текста. Синхронный `onIntent()` вместо `sendIntent()` нужен для unit-тестов (scheduler mismatch в runTest с SharedFlow).

**Баги/проблемы:** 
- Первый запуск тестов упал: 5 из 9 с IndexOutOfBoundsException. Причина — использовал `runTest(testDispatcher)` + async `sendIntent()` (SharedFlow schedule mismatch). 
- Обнаружен legacy-bug: `OnboardingViewModelTest` и `InteractiveOnboardingViewModelTest` сломаны (их Fakes не реализуют `events: SharedFlow<AppNavEvent>` и `showWidgetInstruction()`, добавленные позже в AppNavigator).

**Решение:** Переписал тесты на паттерн проекта — `TestScope(testDispatcher).runTest { vm.onIntent(...) }` (синхронно). Для Onboarding-фикса создал TODO в cross-session памяти.

### Итерация 2 — 2026-04-18 — обнаружена ошибка в плане
**План был неверен:** Исходный план указывал целевой экран как `ChecklistDetailScreen` (feature/home), но пользователь уточнил что это `CreateChecklistScreen` (feature/create) в режиме `editChecklistId`. Контекстная ошибка в INIT-фазе — мой промах при анализе исходного запроса.

## Выводы

1. **KMP inline-edit паттерн стандартизирован**: `FocusRequester + LaunchedEffect(Unit) { requestFocus(); keyboard?.show() }` + `OutlinedTextField(..., onFocusChanged { if (!it.isFocused) commit })` + IME Done callback. Blur автосохраняет непустой текст / отменяет пустой — минимум жестов, iOS-style. Паттерн переиспользуется на всех платформах (Android/iOS/wasmJs).

2. **Double-fire guard через editingItemId**: При IME Done → `onConfirm() + keyboard.hide()` → `onFocusChanged(isFocused=false)` поступает второй вызов commit. Но после первого `editingItemId = null`, второй — no-op. Безопасен без rate-limit. Инвариант: `editingItemId ?: return` в начале всех handlers.

3. **Auto-commit on switch pattern критичен**: `startEdit(newId)` обязан вызвать `commitPendingEdit()` ДО установки `editingItemId = newId`. Иначе при тапе карандаша другого item in-flight текст первого потеряется молча. Unit-test `onStartItemEdit_whenPreviousEditOpen_commitsPrevious_andStartsNew` на страже этого.

4. **VM-тесты KMP feature: StandardTestDispatcher + TestScope + synchronous onIntent**: Асинхронный `sendIntent()` через SharedFlow требует `advanceUntilIdle()` и нестабилен при rapid emits в одном runTest блоке. Canonical паттерн — `vm.onIntent(...)` синхронно, `advanceUntilIdle()` только для `repository.*` path. Пример: `CreateChecklistViewModelTest`.

5. **Legacy Fakes-debt в onboarding-тестах**: `OnboardingViewModelTest`, `InteractiveOnboardingViewModelTest` имеют невалидные Fakes (не реализуют `events: SharedFlow<AppNavEvent>` + `showWidgetInstruction()`). Тесты не компилируются. Кандидат на отдельный cleanup-PR.

6. **Context accuracy matters**: Ошибка в INIT-фазе (неправильный целевой экран) привела к переделкам в плане и документе. Поиск по "edit mode", "edit screen", "pencil icon" проясняет контекст лучше чем буквальная трактовка.

## Предложения по улучшению агентов

### material-3-skill
- [ ] Добавить пример "Inline editing in list items" с полным кодом (FocusRequester + LaunchedEffect + onFocusChanged-commit паттерн, keyboard show/hide). Сейчас пробел в skill'е.

### @mobile-design-expert
- [ ] Добавить явное напоминание в prompt: "Contract и VM handlers — домен @android-expert; я описываю спецификацию UI+focus+keyboard, не реализую Intent/onIntent/State."
