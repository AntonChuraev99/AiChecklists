# Агент layout-debug — перенос в master

**Статус:** Done
**Дата старта:** 2026-11-16
**Start SHA:** cdb3285bafcd106e6eda662838764d46463542bc
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** composeApp, androidApp

⚠ INIT пропущен — документ реконструирован на COMPLETE.

## Цель (продуктовая)

Запускать приложение из `master` с инструментом layout-debug (живое Compose-дерево, сдвиги элементов на устройстве) без отдельной устаревшей ветки.

## Технический план

1. Cherry-pick `84409a0d` из `feat/layout-debug-agent` на ветку от `master`.
2. Привязать сокет агента к loopback.
3. Debug-сборка → Pixel 9 → проверить `/health`, `/tree`.

## Лог итераций

### Итерация 1 — 2026-11-16 — главный: cherry-pick без конфликтов + loopback в `LayoutDebugAgent.kt` → debug-сборка `:androidApp` зелёная, `/tree` на Pixel 9 отвечает

### Итерация 2 — 2026-11-16 — android-platform-expert: 6 находок ревью (CORS, сдвиг по identity, устаревшая база modifier, гонка, ложный ok, тихие сбои) → приёмка на эмуляторе зелёная

## Выводы

Агент перенесён, работает на текущем `master`. Найдена и закрыта дыра: сервер слушал все интерфейсы. Механизм и обоснование — `docs/solutions/layout-debug-agent-android-2026-11-16.md`. Старая ветка `feat/layout-debug-agent` и её worktree остаются — удалить после merge по решению владельца.

## Предложения по улучшению агентов
