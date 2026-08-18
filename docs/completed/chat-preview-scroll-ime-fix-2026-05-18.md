# AI Chat Preview-Card Scroll-Into-View Fix on Keyboard Close

**Статус:** Done
**Дата старта:** 2026-05-18
**Start SHA:** 53f959e60bedc1a0388757d31e58a9d0ba5777a4
**Project:** checklists
**Тип:** bug-fix
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/aichat/impl (ChatScreen composable)

⚠ INIT phase was skipped — minimal active doc reconstructed during COMPLETE. Counters могут быть неточными.

## Цель (продуктовая)
После закрытия клавиатуры при предпросмотре команды (Add-item с редактируемым текстом), карточка предпросмотра должна оставаться полностью видна в LazyColumn, без смещения вверх и без обрезания кнопок Apply/Cancel.

## Технический план
1. Диагностировать причину смещения карточки при resize viewport (keyboard open/close)
2. Исправить LaunchedEffect ключи для переоценки scroll-anchor при IME-событиях
3. Валидировать на Pixel_9: открыть чат → ввести команду → закрыть клавиатуру → verify card visible

## Лог итераций

### Итерация 1 — 2026-05-18 — main-agent (diagnostic)
**Что сделано:** Попытка добавить scrollOffset hack в animateScrollToItem(0) с отрицательным значением.
**Почему так:** Предположение, что items[0] нужно более "высокий" scroll для полной видимости в reverseLayout.
**Баги/проблемы:** ❌ User reported: карточка всё ещё обрезана. Скролл вверх был избыточным.
**Решение:** Reverted hack.

### Итерация 2 — 2026-05-18 — main-agent (diagnostic)
**Что сделано:** Заменил LaunchedEffect(keys) на LaunchedEffect(Unit) с независимым CoroutineScope, добавил snapshotFlow.
**Почему так:** Предположение, что scope+снимки быстрее переоценят scroll при IME close.
**Баги/проблемы:** ❌ User reported: initial scroll сломался — чат открыва́ется в верхушке листа, не внизу. Стал неюзабилен.
**Решение:** Reverted.

### Итерация 3 — 2026-05-18 — main-agent (success)
**Что сделано:** Вернул оригинальный LaunchedEffect с keys, добавил `WindowInsets.ime.getBottom(LocalDensity.current)` в список ключей. Добавил временные [ChatDiag] логи для трассировки viewport-размеров (1584px) и card-height (686px).
**Почему так:** Root cause найдена: reverseLayout=true в LazyColumn НЕ переанкоривает bottom-pinned item при resize viewport. IME open/close меняет viewport, старый anchor теряется. Добавление IME-bottom в keys принуждает re-run LaunchedEffect и повторный scrollToItem(0) на каждый IME-event.
**Баги/проблемы:** ✅ User confirmed: "все заработало" после закрытия клавиатуры.
**Решение:** Оставил fix, удалил временные диагностические логи перед commit.

## Выводы

**Root Cause:** `LazyColumn(reverseLayout=true)` с `animateScrollToItem(0)` в `LaunchedEffect` теряет scroll-anchor при viewport-resize (keyboard open/close). Нужен explicit re-trigger на IME-change.

**Решение:** Добавить `WindowInsets.ime.getBottom(LocalDensity.current)` в список ключей `LaunchedEffect`. Это гарантирует, что при каждом IME-событии (keyboard close → viewport grow) перезапускается scroll-to-item, переанкоривая card в правильное место.

**Применение:** Паттерн обобщаемый — любой reverseLayout LazyColumn с bottom-pinned элементом и scroll-into-view логикой должен включать IME-bottom в LaunchedEffect keys.

**Валидация:**
- assembleDebug: 15s PASS
- :feature:aichat:impl:testAndroidHostTest: 114 active PASS
- Pixel_9 APK install via Wi-Fi ADB: PASS
- User repro: PASS

## Предложения по улучшению агентов

### android-expert
- [ ] Добавить в раздел «LazyColumn patterns»: reverseLayout + IME-resize = viewport anchor loss. Компенсация: include `WindowInsets.ime.getBottom()` в LaunchedEffect keys for scroll-trigger logic.

### mobile-design-expert
- [ ] Добавить в раздел «Keyboard handling»: reverseLayout layouts потребуют явного переoтвязывания scroll на IME events.
