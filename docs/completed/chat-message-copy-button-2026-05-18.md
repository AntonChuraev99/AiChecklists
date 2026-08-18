# Chat Message Copy Button

**Статус:** Done
**Дата старта:** 2026-05-18
**Start SHA:** 920486294c21455e2af379857fe6d5b857019b16
**Project:** checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/aichat/impl, core/designsystem

## Цель (продуктовая)
Добавить кнопку копирования текста для каждого сообщения в чате (user + assistant). Пользователь нажимает ContentCopy icon в meta-row сообщения → текст копируется в clipboard → появляется Snackbar с подтверждением "Скопировано" / "Copied" (в зависимости от локали).

## Технический план
1. Добавить строки в `core/designsystem/src/commonMain/composeResources/values/strings.xml` (EN: "Copied", RU: "Скопировано") и `values-ru/strings.xml`
2. Добавить intent в `ChatScreenContract.kt`: `CopyMessageToClipboard(messageId)` → handler в ViewModel
3. В ViewModel (`ChatViewModel.kt`) реализовать `onIntent(CopyMessageToClipboard)`: (1) найти сообщение по ID, (2) вызвать ClipboardManager.setText(message.content), (3) emitSideEffect(ShowSnackbar(messageKey))
4. Изменить `ChatMessageBubble.kt`: добавить icon button (ContentCopy) в существующую meta-row → `sendIntent(CopyMessageToClipboard(messageId))`
5. Validation: compileDebugKotlin + locales validation (EN/RU strings present)

## Лог итераций
<!-- Будет заполняться в фазе UPDATE -->

## Выводы
<!-- Будет заполняться в фазе COMPLETE -->

## Предложения по улучшению агентов
<!-- Будет заполняться в фазе COMPLETE -->


## Status reconciliation (2026-06-12)

Verified against code: shipped via SelectionContainer in ChatMessageBubble.kt — pivoted from a dedicated copy IconButton to native long-press selection (decision logged 2026-05-18). Goal (copyable chat text) met. Status corrected In Progress→Done.
