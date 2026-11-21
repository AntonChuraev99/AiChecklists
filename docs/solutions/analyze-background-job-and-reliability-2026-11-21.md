---
title: "Analyze: анализ как задание уровня приложения, доставка одной стороной, повторы Gemini на сервере"
summary: "Анализ жил во ViewModel и терялся при уходе с экрана; вынесен в задание с id = request_id (WorkManager / корутина), результат доставляет ровно один из трёх получателей."
date: 2026-11-21
type: architecture
modules: [feature/analyze, feature/user, composeApp, firebase-functions]
keywords: [network, retry, notification, analyze, WorkManager, AnalysisJobManager, resolveDelivery, request_id, pdf_base64, nothing_found, gemini-503, android, wasmjs]
project: Checklists
---

# Analyze: фоновое задание, доставка одной стороной, повторы на сервере

**Суть:** долгий AI-запрос не держим во ViewModel: его ставит `AnalysisJobManager` с id = серверный `request_id`, поэтому повтор не списывается дважды.
Получателя результата выбирает `resolveDelivery`: экран → snackbar → уведомление, ровно один, после `claimDelivery`.

## Проблема / Контекст

ViewModel Analyze жила столько же, сколько Activity: повторный вход висел на старом лоадере, «Назад» не отменял запрос, а результат выдёргивал пользователя с экрана, куда тот ушёл. Уход из приложения обрывал сеть и терял результат. PDF уходил строкой с именем файла, цена была зашита как 30 (сервер берёт 20), у новичка после онбординга обнулялись кредиты.

## Решение

- Домен — `domain/job/` (`AnalysisJob`, `ResolveDelivery`, `AnalysisJobHandoff`), исполнение — `data/job/` (`AnalysisJobManager`, `AnalysisJobProcessorImpl`). Очередь хранится в DataStore, копия входа — в хранилище приложения.
- Android: `AnalysisWorker` (expedited, KEEP по id задания) через `WorkManagerAnalysisJobLauncher`, запасной путь — `CoroutineAnalysisJobLauncher`. Уведомление — `AndroidAnalysisReadyNotifier`. Web: `BeforeUnloadJobGuard`, `VisibilityForegroundMonitor`.
- Хуки активации — в момент доставки: `reminderOptInRequests` не повторяет события, и эмиссия из воркера сожгла бы одноразовый лист.
- Сервер: `call_gemini_with_fallback` — повторы плюс `ANALYZE_FALLBACK_MODEL` на 503/429. Для PDF и аудио — одна попытка `ANALYZE_DOCUMENT_HTTP_OPTIONS`. Пустой результат → 422 `nothing_found` с возвратом кредитов.
- Кредиты: `CompleteOnboardingUseCase` писал `update(UserData(...))` и затирал кредиты. Теперь `setOnboardingPassed()` пишет один ключ.

## Почему именно так

- id = `request_id`: сервер уже дедуплицирует резерв по нему, отдельный протокол не нужен.
- Повторы задаются в вызове, а не в клиенте `genai`. Иначе меняются таймауты `classify_chat_intent` и `transcribe_audio`.
- Три попытки по 15 с исчерпали бюджет на PDF (smoke: 500 `httpx.ReadTimeout`).
- Запись целого снимка затирает чужие поля. Тот же риск остаётся у `update(snapshot.copy(...))` в `AnalyzeRepositoryImpl` и `ChatViewModel`.

## Связанные файлы

- `feature/analyze/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/analyze/domain/job/ResolveDelivery.kt`
- `feature/analyze/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/analyze/data/job/AnalysisJobManager.kt`
- `feature/analyze/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/feature/analyze/platform/AnalysisWorker.kt`
- `feature/user/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/user/domain/usecase/CompleteOnboardingUseCase.kt`
- `firebase-functions/main.py`, `firebase-functions/tests/test_analyze_reliability.py`
- Матрица состояний: [FEATURE_SPEC](../archive/analyze-reliability-background-2026-11-20-FEATURE_SPEC.md)
