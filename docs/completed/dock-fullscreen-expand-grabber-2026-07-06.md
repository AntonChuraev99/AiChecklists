# Dock Fullscreen Expand with Grabber Hit-Zone

**Статус:** Done
**Дата старта:** 2026-07-06
**Start SHA:** 9f0fa9b0
**Project:** checklists
**Тип:** feature
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** core/designsystem, feature/aichat/impl, composeApp

## Цель (продуктовая)

Chat dock (шторка) на MainScreen + ChecklistDetailScreen разворачивается in-place на весь экран (3-й якорь DockAnchor.Full) с полной scrollable историей чата (reuse ChatMessageList); зона захвата grabber'а увеличена до >=48dp (WCAG 2.5.5) БЕЗ изменения визуала pill'а и текущих Peek/Expanded состояний.

## Технический план

- Вынести ChatMessageList из ChatContent (feature/aichat) для reuse на Full-якоре.
- Расширить enum DockAnchor 2→3 (Peek/Expanded/Full) в GistiInlineChatPanel.kt; остаться на AnchoredDraggable (BottomSheetScaffold отвергнут — docs/solutions/chat-dock-inplace-morph-vs-bottomsheetscaffold-2026-06-26.md).
- Grabber: touch-target 48dp, видимый pill неизменён (report-small/touch-big через Modifier.layout).
- Motion: MaterialTheme.motionScheme spatial spec.

## Лог итераций

### Итерация 1 — 2026-07-06 — compose-feature-expert
**Что сделано:** Реализована полная поддержка 3-якорного dock с режимом Full-расширения. ChatMessageList вынесена для переиспользования; DockAnchor расширен до 3 состояний (Peek/Expanded/Full); grabber с 48dp touch-zone; AnchoredDraggable с fixed-height панелью; визуал pill'а неизменён.

**Почему так:** Остаться на AnchoredDraggable (BottomSheetScaffold отвергнут из solution-doc); fixed-height (panelMaxPx из constraints.maxHeight) убирает anchor-churn; chat режим с 3 якорями, item-create с 2 якорями (отдельные пути).

**Баги/проблемы:** 2 минорных бага пойманы при реализации: (1) item-create унаследовал бы 3-anchor конфиг (fix: fallback answerRevealPx + drop Full); (2) layer-A answer frame крал бы scroll/tap на Full (fix: gate composition off при targetValue==Full, minor alpha pop на crossfade — acceptable).

**Решение:** Компилируется Android + wasmJs; 2-anchor путь item-create нетронут. Ready для runtime-верификации и L2-ревью.

**Итог итерации:** Реализация завершена; ожидает ручной проверки grabber hit-testing (on-device/браузер :9090) + L2 ревью. До Done требуется.

**Изменённые файлы:**
- composeApp/.../App.kt (+37) — fullContent слот, ChatMessageList wiring (chat mode only)
- core/designsystem/.../GistiChatDock.kt (+69) — topCornerRadiusProvider, clip 28→0dp, TopRoundedShape
- core/designsystem/.../GistiInlineChatPanel.kt (+331/−180) — enum DockAnchor 2→3, fixed-height anchors, crossfade, grabber 48dp touch/24dp footprint
- feature/aichat/impl/.../ChatMessageList.kt (NEW) — reverseLayout LazyColumn extract
- feature/aichat/impl/.../ChatScreen.kt (−94) — ChatContent зовёт ChatMessageList
- feature/home MainScreen.kt (+5), ChecklistDetailScreen.kt (+6) — topCornerRadiusProvider lerp

## Выводы

**Статус:** Полностью завершено и верифицировано на устройстве пользователем.

Реализована полная поддержка 3-якорного dock-режима (Peek/Expanded/Full) с seamless переходом к полноэкранному чату. ChatMessageList вынесена для переиспользования; grabber hit-zone соответствует WCAG 2.5.5 (48dp touch-target); состояния Peek/Expanded сохранили исходное поведение.

**Ключевое находка (pattern discovery):**
При добавлении состояния к работающей gesture/layout-механике (измеренной content-constrained панели под imePadding) — **аддитивный слой поверх безопаснее и дешевле, чем рестрактор существующего якоря**. Первый подход (рестрактор 2→3-якорной механики в fixed-height screen-relative panel) сломал keyboard-up Expanded ДВАЖДЫ; решение (git reset + Full как отдельный аддитивный AnchoredDraggableState overlay) избежало регрессии по конструкции — untouched Expanded-механика не может сломаться.

**Файлы (5 изменён):** GistiFullChatOverlay.kt (новый), GistiInlineChatPanel.kt, App.kt, ChatScreen.kt, ChecklistDetailScreen.kt + MainScreen.kt (мелкие изменения).

**Verification:** ручная проверка на Pixel 9 + AndroidEmulator (grabber drag, keyboard up/down) PASS; wasmJs браузер :9090 PASS (drag-up, Back-collapse, ввод сохраняется).

## Предложения по улучшению агентов

### compose-feature-expert
- [ ] При добавлении состояния к работающей gesture/layout-механике, которая измеряет content под imePadding или constraints.maxHeight, по умолчанию предлагать **аддитивный слой поверх** (отдельный AnchoredDraggableState, overlay, другой composition-scope), а не рестрактор существующего якоря/anchor-set. Рестрактор работающей панели = класс keyboard-up/scroll-race регрессий. Прецедент: dock 3-anchor expansion (git reset после 2 попыток рестрактора → аддитивная Full overlay решила за 1 итерацию).
