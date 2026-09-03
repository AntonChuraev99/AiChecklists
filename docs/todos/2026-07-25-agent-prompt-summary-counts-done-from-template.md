# Агентский промпт видит «0 выполнено» всегда — `buildChecklistsSummary` считает по шаблону

**Статус:** Deferred — pre-existing, найдено попутно
**Создан:** 2026-07-25
**Найдено:** L1-правило `progress-from-template-instead-of-fill` + подтверждение `@bug-pattern-reviewer`
в task-gate 2026-07-25 (задача про деиктический хинт — находка **вне** её scope)

## Симптом

`ChatViewModel.buildChecklistsSummary()` (~2256) формирует `checklists_summary` — payload,
который уходит в промпт Layer 3 / агента:

```kotlin
doneItems = checklist.items.count { it.type == ChecklistNodeType.ITEM && it.checked },
```

`Checklist.items` — это **шаблон**, у которого `checked` всегда `false`. Реальное состояние
живёт в default `ChecklistFill.items`. Значит модель в промпте систематически видит
**«0 из N выполнено»** для любого списка.

## Почему это важно

Это не UI-баг (прогресс-бар на экранах считается правильно), а **загрязнение входа модели**.
Прямо бьёт по запросам вида «что мне осталось», «подведи итог по этому списку», «что
добавить» — агент рассуждает о списке, который «никогда не начинали».

Пересекается с известным recurring-паттерном
`[[checklist-detail-optimistic-state-sync]]` (template vs fill) — тот же корень,
другой потребитель.

## Что сделать

1. Брать `doneItems` из default `ChecklistFill.items` (fallback на шаблон только для `totalItems`,
   когда fill ещё не создан).
2. Проверить остальные потребители `checklists_summary` (`chat_completion`, `chat_agent`).
3. Тест: чек-лист с 2 из 5 отмеченными → в payload `doneItems == 2`.

Не тронуто в задаче 2026-07-25 сознательно: другой слой, другой scope, нужен свой red-тест.
