**Статус:** Done

**Дата старта:** 2026-11-04

**Start SHA:** `c7e81dd8`

**Project:** Gisti — AI Checklists

**Тип:** UI / redesign

⚠ INIT пропущен — документ собран на COMPLETE из диффа.

**Затронутые модули:** `:core:designsystem`, `:composeApp`.

---

## Цель (продуктовая)

Владелец попросил освежить вход в чат на главных экранах по референсу: слева «+» для вложений, справа микрофон и кнопка голосового звонка, высота — один ряд, общая плита с нижней навигацией остаётся.

## Технический план

- `AskGistiBar` стиль `ChromePill` (`core/designsystem/.../gisti/AskGistiBar.kt`): «+» слева (круг 36dp в цели 48dp), плейсхолдер `maxLines = 1` + многоточие, микрофон нейтральным тоном, кнопка звонка — круг 40dp `primary` с `Icons.Filled.GraphicEq`.
- Новые параметры `onAttachClick` / `onCallClick` со значением `null` по умолчанию: `null` — кнопки нет. При `onAttachClick == null` слева остаётся прежний `SparkleTile`.
- `V2NavigationShell`: параметры `onOpenChatAttach`, `onStartVoiceCall`.
- `App.kt`: `openChatWithAttach` — открыть док чата и поднять `chatSheetAttachmentSheet`; аналитика `CHAT_FAB_TAPPED` с `source = pill_attach`.
- `onStartVoiceCall` в `App.kt` не передаётся: звонок делает отдельная ветка `feat/voice-call-agent`.

## Лог итераций

### Итерация 1 — 2026-11-04 — compose-expert
Пилюля, проброс, строки EN/RU/HI, 3 новых теста в `V2ShellAiButtonTest`, 4 новых кадра со звонком в `V2ShellBottomBarScreenshotTest` (например `compactBar_360dp_light_withCall.png`), витрина `SoftTactileComponentsScreenshotTest` → сборка Android и тесты шелла зелёные, голдены перезаписаны намеренно.

### Итерация 2 — 2026-11-04 — главный (фикс находки ревью)
Ревью диффа: при висящем вопросе (`pendingChoice`, не post-action) поле ввода дока скрыто, и файл, выбранный через «+», ложился в `pendingAttachments` невидимо. `openChatWithAttach` теперь при вопросе только открывает док на его чипах и пишет лог `ChatAttach` — то же правило, что у микрофона пилюли (`VoiceEntryDecision.OpenOnly`).

## Выводы

- Лист выбора вложения — `ModalBottomSheet` в своём окне, поэтому он встаёт поверх дока, который открывается тем же нажатием. Выбор идёт через `ChatScreenIntent.OnPickAttachment` — тот же путь, что у скрепки в открытом чате.
- Одна строка плейсхолдера на 320dp × fontScale 1.3 RU со звонком режет текст до «Спросите Gi…». Прежний KDoc ставил две строки именно из-за этой обрезки; одну строку попросил владелец.
- Микрофон в пилюле стал нейтральным (`onSurfaceVariant`) вместо `primary`, как в референсе.

## Предложения по улучшению агентов

Нет.
