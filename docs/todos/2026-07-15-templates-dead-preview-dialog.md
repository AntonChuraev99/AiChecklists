---
title: TemplatesViewModel.createFromTemplate — мёртвый путь (остаток inline-preview диалога)
date: 2026-07-15
status: deferred
blocking_reason: dead-code-cleanup (не влияет на пользователя — код недостижим; удаление затрагивает TemplatesScreenState и требует проверки, что диалог не планируют вернуть)
resume_trigger: "User says «почисти мёртвый код в templates / убери preview-диалог / зачем selectedTemplate»; ИЛИ при следующем редизайне экрана Templates"
estimated_complexity: Trivial
keywords: [templates, dead-code, createFromTemplate, selectedTemplate, showPreviewDialog, analytics]
---

# TemplatesViewModel.createFromTemplate недостижим

## Что нашли

`TemplatesViewModel.createFromTemplate()` (`feature/create/.../templates/TemplatesViewModel.kt:~135`) **никогда не исполняется**:

- `TemplatesScreenState.selectedTemplate` нигде не **присваивается** — только сбрасывается в `null` (`:130`, `:154`) и читается (`:135`).
- Первая же строка `val template = _screenState.value.selectedTemplate ?: return` → всегда `return`.
- `OnTemplateClick` ведёт на `navigateToTemplatePreview(intent.template)` — отдельный экран `TemplatePreviewViewModel`, который и создаёт чек-лист (там эмит `source=template` добавлен).
- `showPreviewDialog` в `TemplatesScreen.kt` не используется.

Похоже на остаток inline-preview диалога, заменённого отдельным экраном.

## Как нашли

Ревью `@bug-pattern-reviewer` (2026-07-15) пометил `TemplatesViewModel:141` как «путь персистит чек-лист и молчит в аналитике» — по grep это выглядело как дырка инструментации. При попытке закрыть дырку тестом (`OnTemplateClick` → `OnCreateFromTemplate` → ожидаем `checklist_created`) тест вернул `got []`: событие не пришло, потому что **путь недостижим**. Grep нашёл вызов `addChecklist`, но не проверил достижимость.

⚠️ Эмит аналитики сюда добавлять НЕЛЬЗЯ — `ChecklistSource` контрактом запрещает значения без достижимого emit-site («an enum value with no emit site is a lie»). В коде оставлен KDoc-маркер.

## Что сделать

1. Удалить `createFromTemplate()`, `dismissPreview()`, интенты `OnCreateFromTemplate`/`OnDismissPreview` и поля `selectedTemplate`/`showPreviewDialog` из `TemplatesScreenState` — если inline-диалог не планируют вернуть.
2. Либо: восстановить диалог, если он был убран случайно (проверить git-историю `TemplatesScreen.kt`).
3. После удаления — прогнать `:feature:create:testAndroidHostTest` (тесты на состояние экрана могут ссылаться на поля).

## Почему отложено

Пользователю не вредит: код недостижим, ресурсов не ест. Удаление трогает публичный контракт экрана (`TemplatesScreenState`, `TemplatesScreenIntent`) и требует решения «диалог вернут или нет» — это продуктовый вопрос, не механическая чистка.
