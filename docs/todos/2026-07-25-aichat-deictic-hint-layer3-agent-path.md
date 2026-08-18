# AI Chat — деиктический checklistHint не обрабатывается на пути Layer 3 (agent)

**Статус:** Deferred — осознанный пробел, не блокер
**Создан:** 2026-07-25
**Связано:** `docs/solutions/ai-chat-deictic-hint-bad-answer-2026-07-25.md` (фикс L2-пути)

## Что закрыто и что нет

Фикс 2026-07-25 снимает местоимение-хинт («этот чеклист» / "this checklist") в
`ChatViewModel.biasToolCallToContext()` — через него проходят **все три** call-site
L1/L2-пути превью (`ChatViewModel.kt` ~972, ~1038, ~2044).

**Не закрыт путь Layer 3.** Агентский цикл диспатчит замапленные вызовы напрямую:

```
runAgentTurn → AgentToolCallMapper.map(call) → toolCallDispatcher.dispatch(toolCall)
```
(`ChatViewModel.kt` ~2397 read-only, ~2476 mutating) — мимо `biasToolCallToContext`.
Если агент вернёт `checklist_hint` местоимением, диспатчер по-прежнему упрётся в
`chat_dispatch_no_checklist_match`.

## Почему отложено, а не сделано

- **Экспозиция кратно меньше.** Агенту контекстный чек-лист передаётся **по имени**
  (`resolveContextChecklistName()`, ~2294), поэтому в норме он и отвечает настоящим именем,
  а не местоимением. У L2 такого входа нет вовсе — отсюда и баг.
- **Нет наблюдения.** Ни одного thumbs-down с этим симптомом на L3-пути в Amplitude не найдено
  (прогон `/ai-chat-feedback-fixer` 2026-07-25 по обоим проектам, 14 дней).
- Правило проекта: не патчить невоспроизведённое.

## Что сделать, когда возьмут

1. Проверить наблюдением: поднять `logger.warning` в диспатчере на `no_checklist_match`
   и посмотреть, приходят ли местоимения с L3.
2. Если приходят — прогнать `AgentToolCallMapper.map()` результат через
   `clearDeicticChecklistHint` **с той же оговоркой**, что и на L2-пути: снимать хинт только
   при наличии `resolveContextChecklist()`, иначе `CompleteItem`/`DeleteItem` уедут в первый
   список молча (у delete нет undo-handle).
3. Red-тест на agent-пути перед фиксом.

Найдено `@bug-pattern-reviewer` (L2) в task-gate 2026-07-25, помечено как «молча отгруженный
пробел» → зафиксировано здесь, чтобы gate закрылся честно.
