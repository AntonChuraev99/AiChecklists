---
title: "Имя системного Inbox — из ресурсов при показе, а не из БД"
summary: "Inbox показывал «Входящие» на английском UI: имя записывалось в БД на языке создания. Теперь для isInbox имя берётся из ресурсов при отрисовке."
date: 2026-10-26
type: bug-fix
modules: [feature/checklist, feature/home]
keywords: [localization, i18n, inbox, isInbox, checklistDisplayName, stringResource, getString, compose-resources, sync, Firestore]
project: Checklists
---

# Имя системного Inbox показывалось на языке, на котором его создали

**Суть:** имя Inbox в БД — служебная метка; показывать через `checklistDisplayName(storedName, isInbox)`
из `feature.checklist.ui.format`. Любой экран, печатающий имя чек-листа, идёт через этот хелпер.

## Проблема / Контекст

На английском UI таб Inbox показывал «Входящие». Имя Inbox задаёт
`ensureInbox(getString(Res.string.inbox_checklist_name))` один раз, при создании; для
существующего Inbox `ensureInbox` имя не обновляет, а экраны печатали `name` как есть.
`getString()`, записанный в БД, становится литералом на одном языке — правило о литералах
этого не ловит.

## Решение

- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/domain/model/ChecklistDisplayName.kt` —
  чистое правило `checklistDisplayName(storedName, isInbox, inboxName)`: для Inbox `inboxName`,
  иначе сохранённое имя. Compose Resources не трогает.
- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/ui/format/ChecklistDisplayNameText.kt` —
  `@Composable` обёртка поверх `stringResource(Res.string.inbox_checklist_name)`.
- `TodayReminderInfo` получил `isInbox` (дефолт `false`), репозиторий проставляет его из
  родительского чек-листа, чтобы Today и Calendar могли перевести имя проекта у задачи.
- Через хелпер идут: заголовок и чипы `InboxScreen`, карточка v1 списка, детали чек-листа,
  weekly-вид, список заполнений, строки Today и Calendar.

## Почему именно так

- **Не переименовываем в БД.** Имя синкается через Firestore: устройства с разными языками
  перезаписывали бы его друг у друга по кругу.
- **Перевод в Composable, а не в ViewModel.** Язык переключается внутри приложения
  (`AppLocale.kt`); строка, полученная `getString` и закешированная в VM, остаётся на старом
  языке, а `stringResource` перечитывается.
- **Одна точка, а не `if (isInbox)` по экранам** — копия условия дрейфует: один экран
  починили, остальные остались.
- Безопасно, потому что Inbox нельзя переименовать с таба Inbox (`decideRename`).

## Вне скоупа (имя Inbox всё ещё из БД)

Уведомления (`ReminderReceiver`), шаринг (`feature/sharing`), тексты результата AI-чата в
`ToolCallDispatcherImpl`, контекст для агента в `ChatViewModel`, календарное событие,
экран редактирования (`CreateChecklistViewModel` — переименование из v1 запишется, но не
покажется), `mcp-server/`.

## Связанные файлы

- `feature/checklist/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/domain/model/ChecklistDisplayNameTest.kt`
- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/repository/ChecklistRepositoryImpl.kt`
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/inbox/InboxScreen.kt`
