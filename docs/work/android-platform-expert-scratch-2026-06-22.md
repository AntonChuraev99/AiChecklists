# Glance widget trampoline IAE fix — scratch (2026-06-22)

## Crash
`IllegalArgumentException: List adapter activity trampoline invoked without specifying target intent`
at `launchTrampolineAction(ActionTrampoline.kt:93)` via `ActionTrampolineActivity.onCreate`.

## КРИТИЧЕСКОЕ открытие (источник: исходники Glance 1.1.1 в Gradle cache)
Бриф предлагает `actionSendBroadcast(Intent)` как обход trampoline. **ЭТО НЕ ЛЕЧИТ.**

В `ApplyAction.kt`, ветка `isLazyCollectionDescendant == true` (т.е. клик ВНУТРИ LazyColumn):
- ВСЕ action-типы (включая `SendBroadcastAction`) → `getFillInIntentForAction` → `applyTrampolineIntent(type = BROADCAST)`
- `applyTrampolineIntent` оборачивает в `InvisibleActionTrampolineActivity` (non-ACTIVITY) ИЛИ `ActionTrampolineActivity` (ACTIVITY, только API<29)
- `setOnClickFillInIntent(targetId, fillInIntent)`
- При клике fillInIntent мерджится с template PendingIntent. Потеря fillInIntent → `launchTrampolineAction` → `requireNotNull(getParcelableExtra(ActionIntentKey))` → ТОТ ЖЕ IAE.

Вывод: broadcast внутри LazyColumn идёт через `InvisibleActionTrampolineActivity` с идентичным `requireNotNull`/строкой ошибки. Trampoline-путь НЕ устранён.

`ActionTrampolineActivity` (из трейса) = только API < 29 (Android 9-). API 29+ = `InvisibleActionTrampolineActivity`. Значит текущий краш бьёт по Android < 10.

## РЕШЕНИЕ (выбрано): убрать lazy-коллекцию для кликабельных строк
`isLazyCollectionDescendant` true ТОЛЬКО внутри Glance `LazyColumn`. Если рендерить элементы обычным `Column` + (опц.) `verticalScroll`, то `isLazyCollectionDescendant == false` → ветка `getPendingIntentForAction` → `setOnClickPendingIntent` напрямую, БЕЗ trampoline-activity, БЕЗ fillInIntent. IAE невозможен в принципе.

Trade-off: Glance Column не виртуализирует; RemoteViews лимит ~элементов. Виджет показывает один чеклист (обычно мало пунктов). Приемлемо. Можно cap items.

actionRunCallback при этом работает напрямую через PendingIntent.getBroadcast (ActionCallbackBroadcastReceiver) — НЕ trampoline. Значит можно ОСТАВИТЬ ToggleItemAction (ActionCallback) как есть, только сменить LazyColumn→Column. Меньше изменений, сохраняет glanceId в onAction (точечный update вместо updateAll).

## План применения
1. ChecklistWidgetContent.kt: `LazyColumn{itemsIndexed}` → `Column(verticalScroll(rememberScrollState))` + forEach с индексом. Cap items (защита от RemoteViews limit). Header уже вне списка (OK).
2. ToggleItemAction.kt — оставить (теперь PendingIntent напрямую, trampoline не вызывается). OpenChecklistAction тоже (header вне lazy → уже direct PendingIntent... но в ТЕКУЩЕМ коде header вне LazyColumn → уже direct → откуда краш ActionTrampolineActivity? = клики ПУНКТОВ на API<29; header не lazy).
3. Manifest — receiver НЕ нужен (остаёмся на ActionCallback). ОТКАТ пункта manifest из брифа.

## Статус
- Анализ: DONE. Бриф-подход (broadcast) отклонён с доказательством из исходников.
- Применение: PENDING
- Сборка/устройство: PENDING
