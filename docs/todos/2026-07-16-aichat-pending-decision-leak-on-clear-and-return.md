---
title: "AI Chat — _pendingAgentDecision виснет при Clear chat / новом ходе с открытым agent-batch"
date: 2026-07-16
type: todo
status: deferred
severity: low-medium
modules: [feature/aichat/impl]
keywords: [ai-chat, runAgentTurn, _pendingAgentDecision, CompletableDeferred, OnClearChat, coroutine-leak]
project: gisti-ai-checklists
blocking_reason: none — не блокер; ограниченная утечка, снимается отменой viewModelScope; чинить ВМЕСТЕ с общей live-verify (нужен поведенческий прогон, не тест)
resume_trigger: «почини pending-decision leak» / в общей live-verify этапа 3
---

# `_pendingAgentDecision` виснет на двух edge-путях

Найдено `@bug-pattern-reviewer` 2026-07-16 при L2-ревью диффа этапа 3. **Не регрессия этого диффа** —
edge существовал и раньше; персист транскрипта + углубление роли агента чуть повышают вероятность
попасть в него. Confidence low-med, needs_runtime_verify.

## Механика

`_pendingAgentDecision: CompletableDeferred<Boolean>?` штатно резолвится на **обоих** путях
agent-batch: ExecuteAll → `complete(true)` (`ChatViewModel.kt:1623`), Dismiss → `complete(false)`
(`:1859`). Висяка на happy/cancel **нет**. Но две утечки:

1. **`OnClearChat` (`:398-408`)** чистит history + transcript + state, но **не** комплитит висящий
   deferred. Если agent-batch открыт (`runAgentTurn` suspended на `_pendingAgentDecision.await()`) и
   юзер жмёт «Clear chat» → корутина `runAgentTurn` виснет на `await()`, экран остаётся в
   `isProcessing`.
2. **Повторный `runAgentTurn` (`:2268`)** ставит `_pendingAgentDecision = null`, осиротив старый
   `decision` (локальный `val` в предыдущем вызове продолжает `await` и не завершится).

Обе — **ограниченные** утечки: снимаются отменой `viewModelScope` при закрытии экрана, прикрыты
UI-гейтингом `isProcessing`/`pendingChoice`. Не вечный хэнг приложения. Но UX-симптом (зависший
`isProcessing`) нарушает правило «обратная связь на каждый user action».

## Готовый патч (применить при live-verify, тут же проверить поведением)

`complete(false)` идемпотентен — на уже-завершённом `CompletableDeferred` это no-op (вернёт `false`),
так что guard безопасен в любом состоянии.

**Место 1 — `OnClearChat`, `ChatViewModel.kt:398`:**
```
// old:
            is ChatScreenIntent.OnClearChat -> {
                viewModelScope.launch {
                    chatHistoryRepository.clear()
// new:
            is ChatScreenIntent.OnClearChat -> {
                // Release a suspended agent loop before wiping its state, else runAgentTurn hangs
                // on await() and the screen stays in isProcessing forever.
                _pendingAgentDecision?.complete(false)
                _pendingAgentDecision = null
                viewModelScope.launch {
                    chatHistoryRepository.clear()
```

**Место 2 — вход в новый ход, `ChatViewModel.kt:2268`** (сейчас `_pendingAgentDecision = null` без
завершения старого):
```
// old:
        _pendingAgentDecision = null
// new:
        // Complete (not just orphan) any decision a prior aborted turn left suspended.
        _pendingAgentDecision?.complete(false)
        _pendingAgentDecision = null
```
⚠️ Номера строк поплывут после правок диффа — якориться на `is ChatScreenIntent.OnClearChat ->` и на
комментарий `// Clear any stale deferred from a previous turn.` (он прямо над строкой места 2).

## Verify (поведением, не тестом — потому и в live-verify)

Открыть agent-batch (запрос вроде «добавь молоко, хлеб и сыр» → карточка «Do it all / Dismiss») →
**не** нажимая ни одну кнопку: (а) нажать «Clear chat» → чат не подвисает, `isProcessing` снят,
можно писать дальше; (б) отдельно — послать новое сообщение → предыдущий ход не оставляет висящей
корутины. Обе поверхности: полный `ChatScreen` и dock. Android + wasmJs.

Опционально закрыть тестом: открыть batch, послать `OnClearChat`, проверить, что
`_pendingAgentDecision` завершён (`isCompleted`) — тогда часть уйдёт из live-verify в unit.
