---
status: deferred
date: 2026-06-16
blocking_reason: FillDetailScreen has no ItemDetailsSheet; FillItemCard is AppCard(onClick=toggle) so an inline clickable link would conflict with the checkbox toggle
resume_trigger: User says «открытие ссылок в дополнительных заполнениях / open link in FillDetailScreen»
keywords: [link, openUri, FillDetailScreen, FillItemCard, clickable link, LinkAnnotation]
---

# Open link action on the multi-fill screen (FillDetailScreen)

## Что отложено

В рамках фичи «красивые/кликабельные ссылки в пунктах» (2026-06-16) **открытие** ссылки
реализовано только на главном пути — `ChecklistDetailScreen` → `ItemDetailsSheet` →
строка «Open link». На экране **дополнительных заполнений** `FillDetailScreen`
(`FillItemCard`) ссылки показываются **красиво и read-only** (компактный тег «🔗 домен»
вместо длинного URL), но кликнуть и открыть их там нельзя.

## Контекст

- `FillItemCard` обёрнут в `AppCard(onClick = { toggle checkbox })` — весь тап карточки
  тогглит чекбокс. Inline-кликабельная ссылка (`withLink(LinkAnnotation.Url)`) под этим
  onClick **может** перехватываться или конфликтовать.
- В отличие от `ChecklistDetailScreen`, у `FillDetailScreen` **нет** `ItemDetailsSheet` —
  note редактируется через `IconButton` → `NoteDialog`, открыть ссылку негде.
- Default fill (основной путь пользователя) идёт через `ChecklistDetailScreen` — там
  открытие работает. `FillDetailScreen` показывается только для **не-default** заполнений
  (когда у чек-листа создано несколько fill-сессий) — менее частый путь.
- Read-only визуал (главная жалоба со скриншота — простыня URL) на этом экране уже решён.

## Шаги возобновления

Готовый util `core/designsystem/.../util/LinkText.kt` (`extractUrls`, `displayDomain`,
`String.asWholeUrl`) переиспользуется — детект писать не нужно. Варианты:

- **A (consistent):** добавить `ItemDetailsSheet` (или мини-аналог) в `FillDetailScreen`
  и в нём строку «Open link» (тот же `ItemDetailsSheetRow` + `uriHandler.openUri` из
  синхронного onClick). Канонично, как на главном экране.
- **B (lightweight):** сделать note в `FillItemCard` inline-кликабельной через
  `buildAnnotatedString { withLink(LinkAnnotation.Url(url)) { append(displayDomain(url)) } }`
  — но СНАЧАЛА проверить на устройстве, что тап по ссылке открывает браузер, а тап вне
  ссылки по-прежнему тогглит чекбокс (`AppCard(onClick)`). Если конфликт — путь A.

Строка ресурса `detail_item_sheet_action_open_link` (EN+RU) уже есть.
