**Статус:** Partially Done

**Дата старта:** 2026-11-04

**Start SHA:** `7b6a1c47` (+ merge `feat/voice-call-agent` `5ffe1bca`)

**Project:** Gisti — AI Checklists

**Тип:** feature / redesign / refactor

**Затронутые модули:** `:feature:onboarding`, `:feature:user` (выбор варианта), `:feature:splash`, `:feature:debug`, `:core:designsystem`, `:composeApp` (`App.kt`, навигация).

---

## Цель (продуктовая)

Обновить онбординг `ai_welcome` там, где воронка теряет людей, и показать три способа работы с Gisti: чек-лист из любого вложения (готовый пример или свой файл), обсуждение дел с агентом в чате, голосовой звонок с агентом.

Метрика: доля Android-людей со стартом онбординга, у которых за 24 ч есть `onboarding_completed`; guardrails — первый AI-чек-лист и ответ чата за 24 ч. Замер pre/post по 4 недели, без A/B (малая выборка). Baseline, цель и все цифры воронки — в gitignored `docs/reports/onboarding-funnel-2026-11-04.md` (репозиторий публичный, цифры не публикуются). <!-- docs-leak-scan: reviewed — only an observation window, no figure -->


## Что показал замер (Amplitude, Android, только люди)

- Главная утечка — первый экран Welcome: заметная доля стартов уходит с него навсегда, ещё часть пропускает. Экраны Capture и Value почти не теряют, но и ничего не дают.
- На финальном экране большинство жмёт кнопку с пустым полем; AI-генерацию в онбординге получают единицы.
- Дверь «из вложения» после онбординга нажимают охотно, но анализ запускает меньшинство нажавших (нет файла под рукой?). Удержание у сделавших чек-лист из вложения выше базы (направленно, не доказано).
- Пропуск онбординга на D1 не хуже прохождения.

## Решения владельца (2026-11-04)

- Структуру шагов выбрать по исследованию лучших онбордингов и конкурентов.
- Вложение: на выбор — готовый пример или свой файл.
- Звонок показывать сразу: ветка строится поверх `feat/voice-call-agent`; вход в звонок за тем же RC-флагом `voice_call_enabled`, что и кнопка в Ask pill.
- Варианты `interactive` и `slides` удалить; fallback везде — `ai_welcome`.

## Ограничения

- Пэйвол в онбординг не возвращается, новых A/B нет (`docs/decisions/2026-09-17-lock-ab-winners-stop-experiments.md`).
- Онбординг обязан работать, когда AI недоступен (на 2026-11-04 предоплата Gemini исчерпана): демо-пример не ходит в AI и не тратит кредиты.
- Дизайн-язык — редизайн B «Soft & tactile» (`docs/decisions/2026-10-16-redesign-b-soft-tactile.md`); переиспользовать существующие компоненты (`SourceRowSection`, пузыри чата).
- Строки только из `strings.xml` (EN/RU/HI).

## Зависимость

PR онбординга несёт в `master` и голосовой звонок (`docs/active/voice-call-agent-2026-11-04.md`), который ждёт пополнения Gemini и проверки на устройстве. Merge — после проверки обоих на устройстве.

## Лог итераций

### Итерация 1 — 2026-11-04 — главный + скауты
Разведка: knowledge-scout (решения и ADR), Explore (карта кода, аналитика по шагам), product-expert (воронка Amplitude W0/W1). Решения владельца по четырём развилкам получены.

### Итерация 2 — 2026-11-04 — best-practices-scout
Исследование онбордингов (Chameleon, Android permissions, ScreensDesign, Duolingo, ChatGPT Voice) → подтверждены 2 экрана; образец вложения — настоящий чек-лист из встроенного образца без AI; микрофон только по тапу; без квиза персонализации. По конкретным конкурентам первоисточников мало (UNVERIFIED).

### Итерация 3 — 2026-11-04 — feature-expert
Удалены варианты `slides` и `interactive` (экраны, маршруты, методы `AppNavigator`, ветки Splash, debug-пункты, 72 мёртвых строки × 3 локали, мёртвые константы аналитики), `GetOnboardingVariantUseCase` → `AI_WELCOME` везде, кроме `none`; `named("isAndroid")` убран → onboarding 30/30, user 65/65, splash 15/15, navigation 18/18, `assembleDebug` и wasmJs зелёные; в `:feature:home` 2 падения `CaptureDockShoulderTest` — воспроизводятся на чистом `master` `7b6a1c47` (8 тестов, 2 падения), к задаче не относятся.

### Итерация 4 — 2026-11-04 — design-expert
DESIGN_SPEC `docs/designs/onboarding-refresh-2026-11-04/DESIGN_SPEC.md`: Showcase (3 плитки с мини-демо) → Try it (образец-записка, нарисованная кодом → список «This week» из 6 пунктов без AI; SourceRow; поле + чипы; карточка Chat/Call) → проверено HTML-прототипом на 22 кадрах. Главный принял рекомендации: один образец, без дат в списке, вход Google на вебе только если уже есть в один тап, в онбординге ничего не добавлять для звонка при упавшем AI; `GistiPresenceOrb` переезжает в `core/designsystem`, новый токен `GistiColors.aiHeadlineBrush`.

### Итерация 5 — 2026-11-04 — android-platform-expert
`feature/onboarding/build.gradle.kts`: убраны 6 неиспользуемых зависимостей (updatefeed, paywall, create, sharing, kotlinx-datetime, androidMain activity-compose) → компиляция Android/wasmJs, onboarding 30/30, `assembleDebug` зелёные.

### Итерация 6 — 2026-11-04 — feature-expert (SPEC)
`docs/designs/onboarding-refresh-2026-11-04/FEATURE_SPEC.md`: матрица состояний, `OnboardingContract.kt`, `ShellLaunch.kt` (липкий запрос оболочке открыть чат/звонок — у событий навигатора `replay = 0`), защёлки через SavedStateHandle (ViewModel в Nav3 без декоратора живёт на уровне Activity), 21 тест-кейс, UI_SKELETON → сборка зелёная, 30/30. Решения главного по §10: новые `seed` (`default`/`multimodal`/`inbox` → заканчиваются с релизом, добавлен `plain_list`), подсказки в Inbox при Skip/Chat/Call, без Google-входа в блоке ошибки, `onUserChecklistCreated` для образца не вызывать; онбординг не продвигает триггеры CSAT.

### Итерация 7 — 2026-11-04 — feature-expert (IMPLEMENT)
ViewModel по FEATURE_SPEC, `ShellLaunchRequestsImpl` + склейка в `App.kt`, `AnalyzeEventParams` (общий для Analyze и онбординга), `AiEntrySource.ONBOARDING`, `ChecklistSource.ONBOARDING_*`, CSAT пропускает события онбординга, 58 строк × 3 локали (источник: отчёт feature-expert); найден дефект: «свой файл» оставлял онбординг под Analyze — теперь сначала Inbox с очисткой стека → onboarding 53/53, analyze 101/101, splash 15/15, navigation 20/20, composeApp CSAT/Koin/Activation 67/67, wasmJs и `assembleDebug` зелёные. Тесты писались вместе с кодом (не RED-first) — мутационная проверка на gate. <!-- docs-leak-scan: reviewed — test counts only -->


### Итерация 8 — 2026-11-04 — compose-expert
Вёрстка Showcase / Try it / оверлей образца / AI-лоадер по DESIGN_SPEC; `GistiPresenceOrb` перенесён в `core/designsystem` (параметр `static`), токен `GistiColors.aiHeadlineBrush`; Roborazzi заведён в `feature/onboarding` по образцу voicecall; короткие паузы переписаны с `delay()` на часы кадров (`awaitFrameMillis`), иначе Robolectric не двигал их → 41 голден, voicecall 34 голдена без изменений, wasmJs и `assembleDebug` зелёные. Анимации и клавиатура снимком не проверены.

### Итерация 9 — 2026-11-04 — главный
В `values-ru` не было 37 ключей `onboarding_welcome_*` (чипы, подсказки в Inbox, ошибки) — русский пользователь видел английский; переведены, RU-голдены перезаписаны → verify зелёный.

### Итерация 10 — 2026-11-04 — gate 2.3b (Standards + Spec) + feature-expert REVIEW
Подтверждены главным по коду: F1 — в v2 на первом запуске выход Chat открывает док, а через кадр `LaunchedEffect(currentTopRoute)` (`App.kt:1411`) закрывает его из-за подмены корня Main → Inbox (`App.kt:1463`); CSAT-фильтр по `source=onboarding` глушил и анализ своего файла на экране Analyze; дубль пунктов образца; платформа сравнивалась строкой `"web"`; свои брейкпоинты вместо `AppWindowSizeClass`; `CLAUDE.md` описывал 4 варианта онбординга (исправлено главным). Фиксы — feature-expert (логика) и compose-expert (брейкпоинты). <!-- docs-leak-scan: reviewed — code line numbers only -->

### Итерация 11 — 2026-11-04 — feature-expert + compose-expert (фиксы ревью)
F1: гейт `isShellStableForLaunch` + маркер `v2DockLaunchRoute`; CSAT режет только `checklist_created` с онбординговым источником и `ai_analyze_completed{surface=onboarding}`; общий `SAMPLE_ITEMS`; `expect val isWebTarget` вместо строки `"web"`; typed-защёлка по тексту; исключение `analyzeData` → `ai_analyze_failed`; сбой образца из блока ошибки восстанавливает его; брейкпоинты из `AppWindowSizeClass` → VM 47/47, Rules 11/11, голдены 41/41 без перезаписи, `ShellLaunchGateTest` 5/5, CSAT 14/14. <!-- docs-leak-scan: reviewed — test counts only -->

## Выводы

- Онбординг `ai_welcome` переписан на 2 шага: Showcase (3 плитки — написать / прикрепить / поговорить) → Try it (записка-образец → настоящий чек-лист без AI и кредитов; свой файл → Analyze; поле + чипы; Chat / Call). Пэйвола нет. Варианты `slides` и `interactive` удалены, `ai_welcome` — на всех платформах, `none` по-прежнему пропускает онбординг.
- Звонок в онбординге — за тем же признаком, что кнопка в Ask pill (`voice_call_enabled` + поддержка платформы): в release он выключен, пока звонок не раскатят.
- Аналитика: `seed` на каждом пути (включая `skip`), `step_index`, `onboarding_mode_selected`, `onboarding_sample_used`, `ai_analyze_*` на typed-пути с `surface=onboarding`, `checklist_created` с `ChecklistSource.ONBOARDING_*`. Ряды `seed` `default` / `multimodal` / `inbox` заканчиваются с релизом.
- В RU не было 37 строк старого онбординга (источник: `comm` ключей `onboarding_welcome_*` values vs values-ru) (чипы, подсказки в Inbox) — переведены.
- Переиспользуемое знание — `docs/solutions/shell-launch-from-pre-shell-screen-2026-11-04.md`.
- Осталось: проверка на устройстве (Chat/Call из онбординга на v2 с первой установки, клавиатура на Try it, анимации, TalkBack), веб на :9090 (сейчас RC на вебе `none`, но fallback стал `ai_welcome`), HI-тексты — носителю. Merge — после задачи звонка (`docs/active/voice-call-agent-2026-11-04.md`): ветка несёт его код. Тесты VM писались вместе с кодом, не red-first.
- Пред-существующее, не чинилось: 2 падения `CaptureDockShoulderTest` на чистом `master`; instrumented `ScreenshotCatalogTest` / `EndToEndFlowTest` / `DiagnosticTest` описывают слайды (устарели с 07-28).
- Замер эффекта: pre/post по 4 недели от релиза, primary «старт → `onboarding_completed` за 24 ч», guardrails из «Цель»; baseline — `docs/reports/onboarding-funnel-2026-11-04.md` (gitignored). <!-- docs-leak-scan: reviewed — window lengths only, no figure -->

## Предложения по улучшению агентов

- `@feature-expert`: в брифе было «RED-first по ключевым веткам», тесты написаны вместе с кодом и красными не видены (агент сам это отметил). Стоит явно требовать в IMPLEMENT прогон хотя бы одного acceptance-теста на скелете до реализации, когда бриф просит RED-first.
- `@compose-expert`: в Robolectric-матрице `delay()` в композиции не двигается `mainClock` — пришлось переписывать на часы кадров (`awaitFrameMillis`). Паттерн стоит закрепить в агенте для любого UI с таймингами под Roborazzi.
