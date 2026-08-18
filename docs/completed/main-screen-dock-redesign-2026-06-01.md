---
Статус: Done
Дата: 2026-06-01
Start SHA: a9b83a96
Тип: feature / redesign
Сложность: Complex
Impact: High
Затронутые модули: core/designsystem, feature/home, composeApp
---

# Редизайн главного экрана: баннер, чат-док, prompt-chips, создание чеклиста

## Цель

Семь связанных UX-правок флагманского главного экрана (MainScreen), поверх незакоммиченных фич
«redesign variant D» + «inline chat dock» (`GistiInlineChatPanel`):

1. **Баннер вместо строки.** Заменить тихую строку `CalmUpgradeHint` («4 of 4 free lists used / Go
   Premium», последний item списка) на яркий градиентный `PremiumBanner(isActive=false)` для
   free-юзеров — на том же месте, внизу списка.
2. **Фикс обрезки prompt-chips.** `GistiPromptChips` (📷 Photo→list / ➕ Add tasks / 🔔 Remind)
   обрезается по padding-границе родителя — последний чип обрубается, скролл неочевиден. Сделать
   edge-to-edge горизонтальный скролл с внутренним `contentPadding`.
3–4. **Новый UX чат-дока.** Тап «Ask Gisti» → поле раскрывается в активный инпут с фокусом;
   над инпутом — ОДНО поле фиксированной высоты с последним ходом ассистента (текст ответа ИЛИ
   `ChatPreviewCard` ИЛИ `AgentPlanCard`). Убрать scrim-затемнение фона. Полная история — по
   кнопке «на весь экран». Попутно закрывает блокер: confirm/preview-карточки теперь рендерятся в доке.
5. **Создание чеклиста наверх.** Иконка «+» в топбаре справа (рядом с кредитами) И первый чип
   «➕ Новый список» над инпутом. Оба ведут в выбор из templates (`navigateToTemplatesScreen()`).
6. **Убрать видимость док-панели при переходе внутрь чеклиста.** Закрывать чат-док при изменении
   top-route, чтобы панель не «переезжала» поверх DetailScreen.
7. **Чистый белый цвет в доке.** Убрать M3 tonal-overlay (tonalElevation=0), заменить на border,
   обновить все Surface-токены с поддержкой dark-theme.

## Технический план

- **Правка 1** (главный, сам): `feature/home/.../MainScreenContent.kt` — своп `CalmUpgradeHint` →
  `PremiumBanner` в обоих путях (LazyColumn Compact + LazyVerticalGrid Medium/Expanded). Условие
  `limits != null && !limits.isPremium` сохранить. `onActionClick`/`onUpgradeClick` →
  `onPremiumBannerClick`.
- **Правка 2** (@mobile-design-expert): `GistiPromptChips.kt` — внутренний `contentPadding` через
  `LazyRow` (или внутренний horizontal padding после scroll). Снять `padding(horizontal)` с ряда
  чипов в `MainScreen.bottomBar` и `App.kt emptyStateContent`.
- **Правки 3-4** (@mobile-design-expert layout → @android-expert logic): `GistiInlineChatPanel.kt` —
  убрать scrim Box, заменить mini-chat `LazyColumn` на слот `lastAnswerContent` фикс. высоты;
  `FocusRequester` проброс в `ChatInputRow`. `App.kt` — mapping последнего хода (assistant message /
  `pendingPreview` / `pendingAgentPlan`) в слот, авто-фокус при `chatSheetOpen`, рендер
  `ChatPreviewCard`/`AgentPlanCard`.
- **Правка 5** (@mobile-design-expert визуал → @android-expert навигация): topbar action «+» в
  `MainScreen.actions`, первый chip в `gistiDefaultPromptChips`/`GistiPromptChips` с отдельным
  callback. Навигация → `OnAddChecklistFromTemplatesClick` → `navigator.navigateToTemplatesScreen()`.

## Решения по развилкам (от пользователя, 2026-06-01)
- Баннер: градиентный `PremiumBanner`, на месте строки (внизу списка).
- Обрезка: `GistiPromptChips` (ряд «Photo→list / Add / Remind»), не карточки списка.
- Чат-док: инпут + одно поле последнего ответа ИИ, без scrim.
- Создание: и в топбар (иконка), и первым чипом — оба в Templates.

## Лог итераций

### Итерация 1 — 2026-06-01 — @mobile-design-expert

**Что сделано:**
- Правка 2: `GistiPromptChips.kt` — переход на `LazyRow` + `contentPadding(horizontal=ScreenPaddingHorizontal)` для edge-to-edge скролла и фикса обрезки чипов. Добавлены параметры `onNewListClick`, `newListLabel`, `contentPadding` и ведущий чип «New list».
- Правка 5: `MainScreen.kt` — иконка Add (topbar справа) и новый param `onCreateFromTemplatesClick`. Убран padding-родителя для bottomBar, паддинг перенесён на детей (AskGistiBar/AppButtonSecondary) чтобы чипы инсетили сами.
- Правки 3–4 layout: `GistiInlineChatPanel.kt` — убран scrim (затемнение фона), mini-chat `LazyColumn` заменена слотом `lastAnswerContent` фиксированной высоты (96–160dp), убрано `messages/messageContent/listState`. Добавлен param `focusRequester: FocusRequester?` в `ChatInputRow`.
- Strings: добавлены `main_create_checklist_action`, `main_prompt_new_list` (EN + RU).

**Почему так:**
- `LazyRow` + `contentPadding` — MD3-канонический паттерн для edge-to-edge скролла (заменяет старый подход Row(horizontalScroll) внутри padding-родителя).
- Слот `lastAnswerContent` вместо mini-chat рендер — позволяет @android-expert заполнить его `ChatPreviewCard`/`AgentPlanCard`/последним bubble, что одновременно закрывает известный блокер (confirm-карточки раньше не отображались в доке).
- Две точки входа в Templates (topbar иконка + ведущий чип) — паттерн для важной фичи, убирает шаг.

**Статус правок:**
- Правка 1 (баннер): DONE главным в INIT.
- Правки 2, 5 (визуал): код готов к компиляции.
- Правки 3–4 (layout): макет готов; логика слота (наполнение `lastAnswerContent` и авто-фокус) за @android-expert.
- App.kt: намеренно не скомпилирован (старая сигнатура вызова панели) — будет перестроена.

**Key findings:**
1. Edge-to-edge chips: LazyRow с `contentPadding` — стандартный MD3-подход.
2. Slot pattern закрывает блокер: confirm/preview-карточки теперь могут рендериться в контексте inline-панели.
3. Мёртвая строка `chat_panel_scrim_description` осталась в strings (scrim удалён) — безвредна, cleanup возможна позже.

**Правка плана:** Технический план остаётся верен; добавлен контекст что слот «lastAnswerContent» не просто UI-контейнер, но архитектурное решение для разблокирования confirm-карточек (они рендерятся не в отдельной layer, а в штатном потоке App.kt).

### Итерация 2 — 2026-06-01 — @android-expert

**Что сделано:**
- Правки 3–4 (логика + навигация): `GistiInlineChatPanel.kt` — обновлена сигнатура слота `lastAnswerContent`, теперь принимает `chatViewModel.screenState.chatContent`; рендер идентичен ChatScreen: `agentPlan` → `AgentPlanCard(onApply, onCancel)`, `pendingPreview` → `ChatPreviewCard(onApply/Cancel/Reject, showReject с условием `toolCall !is CreateChecklistFromAttachment`)`, `isProcessing` → `ChatTypingIndicator`, иначе последний assistant message `ChatMessageBubble`. Обёрнуто в `Column(verticalScroll).padding` для фиксированного кадра без дёргания. Авто-фокус: `FocusRequester` проброшен в `ChatInputRow`, триггер в `App.kt` — `LaunchedEffect(chatSheetOpen) { delay(150); requestFocus() }`.
- `App.kt`: перестроен вызов `GistiInlineChatPanel` — новая сигнатура принимает `hasLastAnswer: Boolean` и `lastAnswerContent: @Composable () → Unit`. `lastAnswerContent` = выбор описанный выше (when-приоритет идентичен ChatContent). `hasLastAnswer` вычисляется как наличие последнего assistant message, preview или plan. Навигация: `onCreateFromTemplatesClick = { navigator.navigateToTemplatesScreen() }` в entry<Main>.
- `feature/home/.../MainScreen.kt`: убрана нижняя `AppButtonSecondary` из `bottomBar` (дублирование с новым чипом), ниже остались только `GistiPromptChips` и `AskGistiBar`. Чип «New list» → `onCreateFromTemplatesClick`. Убраны osiрочевшие импорты.
- **УДАЛЕНЫ**: `composeApp/.../ChatPanelMappings.kt` + `commonTest/ChatPanelMappingsTest.kt` (функция `mapChatStateToPanelMessages` больше не нужна; логика мигрирована в App.kt).

**Почему так:**
- Одна точка авто-фокуса (слот + LaunchedEffect в App.kt) вместо распыления по компонентам — упрощает управление и избегает race-condition между открытием панели и запросом фокуса.
- Delay(150ms) подобран под анимацию въезда панели (~300ms duration), фокус приходит после стабилизации UI.
- Удаление ChatPanelMappings упрощает codebase (функция была утилитой, теперь один when-выбор в App.kt).

**Баги/проблемы:**
- Нет (зелёные компиляции).

**Статус:**
- Правка 1: DONE (в INIT).
- Правки 2, 5: DONE (итерация 1, visual layout).
- Правки 3–4: **DONE** (итерация 2, logic + nav).
- Блокер (inline-док не рендерит confirm-карточки): **ЗАКРЫТ** — confirm/preview/agent-plan карточки рендерятся в доке через slot с идентичным when-приоритетом как в ChatScreen.

**Key findings:**
1. AGP 9 после split: корректный compile-таск для composeApp (KMP library) = `:composeApp:compileAndroidMain`, НЕ `:composeApp:compileDebugKotlin`. Важно для будущих брифов и CI.
2. Архитектурный паттерн: slot pattern в inline-компоненте + заполнение из родительского слоя (App.kt) с условной логикой — решает оригинальный блокер (confirm-карточки теперь видны).
3. Dead code (безвредный, cleanup позже): `ChatMessageUi.kt` в `core/designsystem/.../gisti/` (потребитель был ChatPanelMappings, теперь удалён), `chat_panel_scrim_description` в strings (scrim в панели удалён).

**Валидация:**
- `:composeApp:compileAndroidMain` ✓ SUCCESS
- `:composeApp:compileKotlinWasmJs` ✓ SUCCESS
- `:feature:aichat:impl:testAndroidHostTest` ✓ SUCCESS (все тесты green)

### Итерация 3 — 2026-06-01 — главный (правка 6) + @mobile-design-expert (правка 7)

**Что сделано:**

**Правка 6 (главный):**
- `composeApp/.../App.kt` — LaunchedEffect(currentTopRoute): убрано условие `isDockRoute(Main||ChecklistDetail)`, теперь панель закрывается при **ЛЮБОЙ** смене top-route. Обновлена логика: `if (chatSheetOpen) chatSheetOpen = false` триггерится на каждое изменение `currentTopRoute`, не только вход в Detail. Побочный плюс: фиксит «док открыт при перезагрузке приложения» из `rememberSaveable` — начальный проход эффекта сворачивает панель перед первым рендером.

**Правка 7 (@mobile-design-expert):**
- `core/designsystem/.../gisti/GistiInlineChatPanel.kt` — убран tonal-overlay:
  - `tonalElevation: 2.dp → 0.dp` (ключевой фикс: M3 поверх surface накладывает primary-tint, создавая серо-голубой оттенок даже при `background(surface)`);
  - `shadowElevation = if(isDark) 0.dp else 8.dp` (глубина через тень на светлом, без shadow на тёмном);
  - `border = if(isDark) Stroke(1.dp, outlineVariant) else null` (чёткое разделение на тёмном).
- `feature/aichat/impl/.../components/ChatInputRow.kt` — `Surface(surfaceContainerHigh)` → `Surface(surfaceContainerLowest) { ... Border(outlineVariant) }` (паттерн AskGistiBar). Убран tonal, добавлен контур.
- `feature/aichat/impl/.../components/ChatMessageBubble.kt` — assistant-bubble `surfaceContainerHigh` → `surfaceContainerLowest + Border(outlineVariant)` (чистый фон); user-bubble `primaryContainer` сохранён (акцент).
- `feature/aichat/impl/.../components/ChatTypingIndicator.kt` — `surfaceContainerHigh` → `surfaceContainerLowest + Border(outlineVariant)`.
- **Не трогались** (уже чистые, используют AppCard/правильные токены): `ChatPreviewCard`, `AgentPlanCard`, `GistiPromptChips`, content в App.kt.

**Почему так:**

1. **Корень проблемы M3 tonal-overlay:** `Surface(tonalElevation)` поверх любого фона (`surface`, `background`) добавляет overlay основного цвета темы (primary) с прозрачностью. На белом фоне белый Surface + primary-tint = серо-голубой оттенок даже при явном `background(White)`. Решение: `tonalElevation = 0` + глубина через `elevation` (shadow/border), что используется в дизайн-системе (AppCard, AskGistiBar).

2. **surfaceContainerHigh → surfaceContainerLowest:** В M3 иерархия Surface токенов: `surface < container < containerLow < containerLowest < containerHigh`. Для «Minimal & Clean white» системы `containerHigh` (более тёмный) выглядит грязно-серым, а `containerLowest` (ближайший к background) остаётся чистым белым. Паттерн: главные компоненты доки (input, bubbles, typing) используют `Lowest`, а вот приватные детали (ripple, background) через токены M3.

3. **Dark theme сохранён через токены:** всё через `surfaceContainerLowest` + `outlineVariant`, не через хардкод `Color.White`, поэтому на тёмной теме всё работает (слегка серый фон + светлый контур, правильная читаемость).

**Статус правок:**
- Правка 1: DONE.
- Правки 2, 5: DONE.
- Правки 3–4: DONE.
- Правка 6: **DONE** (код готов к компиляции).
- Правка 7: **DONE** (5 файлов обновлено).

**Key findings:**
1. M3 `tonalElevation` — скрытая ловушка на белых фонах: первичный цвет темы наложится через прозрачность, убирая чистоту. Стандартный паттерн фиксина: `tonalElevation=0 + shadow/border`.
2. `surfaceContainerHigh` неуместна в доке для чистого белого дизайна → `Lowest` + border — стандартный паттерн (AskGistiBar, AppCard это подтверждают).
3. Все Surface-компоненты в доке обновились в один проход без рассинхронизации (ChatInputRow, ChatMessageBubble, ChatTypingIndicator) — единое визуальное состояние.

**Финальный статус:**
- Все 7 правок код-готовы.
- Сборка (assembleDebug) + установка на Pixel 9 — в процессе у главного.

## Выводы

## Предложения по улучшению агентов


## Status reconciliation (2026-06-12)

Verified against code: GistiChatDock.kt live in core/designsystem, wired into MainScreen.kt and App.kt. Status corrected In Progress→Done.
