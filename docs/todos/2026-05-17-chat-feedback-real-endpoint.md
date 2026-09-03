---
title: Chat Feedback — real Cloud Function endpoint
date: 2026-05-17
status: cancelled
cancellation_date: 2026-06-17
parent_task: feature/aichat feedback MVP (today's session)
blocking_reason: cancelled — goal superseded by Amplitude `ai_chat_feedback`
cancellation_note: "CANCELLED 2026-06-17. The goal (collect thumbs-down to build a knowledge base) is already met by the Amplitude `ai_chat_feedback` event (emitted from ChatViewModel.handleFeedbackSubmit via AnalyticsEvents.Chat.FEEDBACK) + the /ai-chat-feedback-fixer skill that mines it. A dedicated Firestore submit_chat_feedback CF is redundant. Pending-anchor removed from code; logcat+Amplitude logging kept."
resume_trigger: "N/A — cancelled. Reopen only if a Firestore-native feedback store (independent of Amplitude) is genuinely needed."
estimated_complexity: Trivial
keywords: [chat, feedback, cloud-function, analytics, knowledge-base]
---

# Chat Feedback — real Cloud Function endpoint

## Что отложено

В `ChatViewModel.handleFeedbackSubmit()` вместо HTTP POST к Cloud Function стоит:
- `logger.info(TAG, "FEEDBACK: question='...' answer='...' feedback='...'")`
- `_sideEffect.emit(ShowSnackbar("chat_feedback_submitted"))`

Real Cloud Function (`submit_chat_feedback`) для сбора базы знаний — не создана.

## Контекст

MVP scaffolding feedback системы реализован полностью: UI (ChatFeedbackSheet), state (feedbackTarget/feedbackText/isSubmittingFeedback), intents (OnFeedbackOpen/OnFeedbackTextChange/OnFeedbackSubmit/OnFeedbackDismiss), icon на assistant-bubble (RateReview, 20dp). Пользователь явно сказал: «пока для MVP просто логируем» и «потом уберём, доведи чтобы я смог набить базу знаний». Логирование в logcat позволяет вручную собирать feedback на этапе dog-fooding.

## Что нужно сделать при возобновлении

- [ ] Создать Cloud Function `submit_chat_feedback` (Python, Firestore collection `chat_feedback`)
  - Принимает: `{ question: str, answer: str, feedback: str, userId: str, timestamp: int }`
  - Пишет в Firestore `chat_feedback/{auto_id}` + логирует
- [ ] Добавить API-интерфейс `ChatFeedbackApiService` в `feature/aichat/api/`
- [ ] Добавить Ktor-реализацию в `feature/aichat/impl/data/ChatFeedbackApiServiceImpl.kt`
- [ ] Заменить `logger.info(...)` в `ChatViewModel.handleFeedbackSubmit()` на реальный вызов сервиса
  - Паттерн: `runCatching { feedbackApiService.submit(...) }.onFailure { logger.error(...) }` — ошибка не должна ломать UX (feedback best-effort)
- [ ] Удалить `// Pending: docs/todos/2026-05-17-chat-feedback-real-endpoint.md` комментарий из ChatViewModel

## Как возобновить (для cold-start агента)

Файл: `feature/aichat/impl/.../presentation/ChatViewModel.kt`, метод `handleFeedbackSubmit()`.
Сейчас там `logger.info(TAG, "FEEDBACK: ...")`. Нужно добавить Cloud Function submit (best-effort, не блокирующий UX). Аналогичный паттерн вызова CF — `ChatCompletionApiServiceImpl.kt` и `ChatClassifierApiServiceImpl.kt` в том же модуле.
