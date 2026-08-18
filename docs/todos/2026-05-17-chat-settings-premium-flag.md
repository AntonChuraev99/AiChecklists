---
title: Wire isPremium flag into ChatSettingsSheet
date: 2026-05-17
status: resolved
resolved_date: 2026-06-12
parent_task: docs/solutions/features/ (chat-settings-deep-thinking task)
blocking_reason: RESOLVED — wired via UserData.isPremium flow
resume_trigger: n/a (done)
estimated_complexity: Trivial
keywords: [chat-settings, premium, user-data, credits-chip]
---

# Wire isPremium flag into ChatSettingsSheet

## ✅ RESOLVED 2026-06-12

Wired through the existing `UserDataRepository` flow (no second RevenueCat subscription — the very over-coupling that caused the original deferral):
- `ChatScreenState.isPremium: Boolean = false` added (`ChatScreenContract.kt:51`).
- `ChatViewModel` mirrors it in the same `getUserDataFlow().collect` block that already feeds `creditBalance` (`copy(creditBalance = userData.aiCredits, isPremium = userData.isPremium)`).
- `ChatScreen.kt:213` now passes `isPremium = state.isPremium` (Pending comment removed).

`AppCreditsChip` inside `ChatSettingsSheet` now shows the PRO badge / suppresses "Get More" for premium users. Validation: `:feature:aichat:impl:testAndroidHostTest` BUILD SUCCESSFUL (302 tests green).

## Что отложено

`ChatSettingsSheet` получает `isPremium = false` hardcoded в `ChatScreen.kt` (line ~222).
Флаг должен приходить из `UserDataRepository` / `UserData` model.

## Контекст

При реализации ChatSettingsSheet (2026-05-17) `UserData` не содержит явного `isPremium: Boolean`
поля — premium-статус живёт в `PaywallRepository` (RevenueCat). Подключение второго репозитория
в ChatViewModel ради одного флага было признано избыточным для этой итерации. `AppCreditsChip`
при `isPremium = false` корректно показывает "Get More" CTA и числовой баланс — пока приемлемо.

## Что нужно сделать при возобновлении

- [ ] Убедиться, что `UserData` (или отдельный `UserStatusRepository`) содержит `isPremium: Boolean`
- [ ] Добавить зависимость в `ChatViewModel` (или прочитать из `userDataRepository.getUserDataFlow()`)
- [ ] Обновить `ChatScreenState.isPremium: Boolean = false`
- [ ] Передавать `state.isPremium` в `ChatSettingsSheet` в `ChatScreen.kt`
- [ ] Убрать `// Pending:` комментарий из `ChatScreen.kt` line ~222

## Как возобновить (для cold-start агента)

Файл: `feature/aichat/impl/.../presentation/ChatScreen.kt` — поиск `isPremium = false`.
Там `// Pending: docs/todos/2026-05-17-chat-settings-premium-flag.md`.
ViewModel: `ChatViewModel` — добавить collect из PaywallRepository или UserData.
Тривиальная правка, 2-3 файла.
