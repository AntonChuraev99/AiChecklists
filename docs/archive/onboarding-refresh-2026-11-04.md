**Статус:** Done

**Дата старта:** 2026-11-04

**Start SHA:** `7b6a1c47` (+ merge `feat/voice-call-agent` `5ffe1bca`; 2026-11-17 влит `origin/master` `a162999e` → `52663d60`)

**Ветка:** `worktree-onboarding-refresh` (worktree `.claude/worktrees/onboarding-refresh`), не запушена, PR нет.

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
- Звонок показывать сразу: вход в звонок за тем же признаком, что и кнопка в Ask pill (`voice_call_enabled` + поддержка платформы). Ветка изначально строилась поверх `feat/voice-call-agent`; звонок с тех пор влит в `master` отдельно (#72).
- Варианты `interactive` и `slides` удалить; fallback везде — `ai_welcome`.

## Ограничения

- Пэйвол в онбординг не возвращается, новых A/B нет (`docs/decisions/2026-09-17-lock-ab-winners-stop-experiments.md`).
- Онбординг обязан работать, когда AI недоступен (поводом стала исчерпанная 2026-11-04 предоплата Gemini): демо-пример не ходит в AI и не тратит кредиты.
- Дизайн-язык — редизайн B «Soft & tactile» (`docs/decisions/2026-10-16-redesign-b-soft-tactile.md`); переиспользовать существующие компоненты (`SourceRowSection`, пузыри чата).
- Строки только из `strings.xml` (EN/RU/HI).

## Зависимость

Снята 2026-11-17. Звонок влит в `master` отдельным PR (#72, документ `docs/archive/voice-call-agent-2026-11-04.md`), `master` влит в ветку — PR онбординга несёт только онбординг и перенос `GistiPresenceOrb` в `core/designsystem`.

## Лог итераций

### Итерация 1 — 2026-11-04 — главный + скауты
Разведка: knowledge-scout (решения и ADR), Explore (карта кода, аналитика по шагам), product-expert (воронка Amplitude W0/W1). Решения владельца по четырём развилкам получены.

### Итерация 2 — 2026-11-04 — best-practices-scout
Исследование онбордингов (Chameleon, Android permissions, ScreensDesign, Duolingo, ChatGPT Voice) → подтверждены 2 экрана; образец вложения — настоящий чек-лист из встроенного образца без AI; микрофон только по тапу; без квиза персонализации. По конкретным конкурентам первоисточников мало (UNVERIFIED).

### Итерация 3 — 2026-11-04 — feature-expert
Удалены варианты `slides` и `interactive` (экраны, маршруты, методы `AppNavigator`, ветки Splash, debug-пункты, 72 мёртвых строки × 3 локали, мёртвые константы аналитики), `GetOnboardingVariantUseCase` → `AI_WELCOME` везде, кроме `none`; `named("isAndroid")` убран → onboarding 30/30, user 65/65, splash 15/15, navigation 18/18, debug-сборка и wasmJs зелёные; в `:feature:home` 2 падения `CaptureDockShoulderTest` — воспроизводятся на чистом `master` `7b6a1c47` (8 тестов, 2 падения), к задаче не относятся.

### Итерация 4 — 2026-11-04 — design-expert
DESIGN_SPEC `docs/designs/onboarding-refresh-2026-11-04/DESIGN_SPEC.md`: Showcase (3 плитки с мини-демо) → Try it (образец-записка, нарисованная кодом → список «This week» из 6 пунктов без AI; SourceRow; поле + чипы; карточка Chat/Call) → проверено HTML-прототипом на 22 кадрах. Главный принял рекомендации: один образец, без дат в списке, вход Google на вебе только если уже есть в один тап, в онбординге ничего не добавлять для звонка при упавшем AI; `GistiPresenceOrb` переезжает в `core/designsystem`, новый токен `GistiColors.aiHeadlineBrush`.

### Итерация 5 — 2026-11-04 — android-platform-expert
`feature/onboarding/build.gradle.kts`: убраны 6 неиспользуемых зависимостей (updatefeed, paywall, create, sharing, kotlinx-datetime, androidMain activity-compose) → компиляция Android/wasmJs, onboarding 30/30, debug-сборка зелёные.

### Итерация 6 — 2026-11-04 — feature-expert (SPEC)
`docs/designs/onboarding-refresh-2026-11-04/FEATURE_SPEC.md`: матрица состояний, `OnboardingContract.kt`, `ShellLaunch.kt` (липкий запрос оболочке открыть чат/звонок — у событий навигатора `replay = 0`), защёлки через SavedStateHandle (ViewModel в Nav3 без декоратора живёт на уровне Activity), 21 тест-кейс, скелет UI → сборка зелёная, 30/30. Решения главного по §10: новые `seed` (`default`/`multimodal`/`inbox` → заканчиваются с релизом, добавлен `plain_list`), подсказки в Inbox при Skip/Chat/Call, без Google-входа в блоке ошибки, `onUserChecklistCreated` для образца не вызывать; онбординг не продвигает триггеры CSAT.

### Итерация 7 — 2026-11-04 — feature-expert (IMPLEMENT)
ViewModel по FEATURE_SPEC, `ShellLaunchRequestsImpl` + склейка в `App.kt`, `AnalyzeEventParams` (общий для Analyze и онбординга), `AiEntrySource.ONBOARDING`, `ChecklistSource` (значения ONBOARDING_…), CSAT пропускает события онбординга, 58 строк × 3 локали (источник: отчёт feature-expert); найден дефект: «свой файл» оставлял онбординг под Analyze — теперь сначала Inbox с очисткой стека → onboarding 53/53, analyze 101/101, splash 15/15, navigation 20/20, composeApp CSAT/Koin/Activation 67/67, wasmJs и debug-сборка зелёные. Тесты писались вместе с кодом (не RED-first) — мутационная проверка на gate. <!-- docs-leak-scan: reviewed — test counts only -->


### Итерация 8 — 2026-11-04 — compose-expert
Вёрстка Showcase / Try it / оверлей образца / AI-лоадер по DESIGN_SPEC; `GistiPresenceOrb` перенесён в `core/designsystem` (параметр `static`), токен `GistiColors.aiHeadlineBrush`; Roborazzi заведён в `feature/onboarding` по образцу voicecall; короткие паузы переписаны с `delay()` на часы кадров (`awaitFrameMillis`), иначе Robolectric не двигал их → 41 голден, voicecall 34 голдена без изменений, wasmJs и debug-сборка зелёные. Анимации и клавиатура снимком не проверены.

### Итерация 9 — 2026-11-04 — главный
В `values-ru` не было 37 ключей ключей onboarding_welcome_… (чипы, подсказки в Inbox, ошибки) — русский пользователь видел английский; переведены, RU-голдены перезаписаны → verify зелёный.

### Итерация 10 — 2026-11-04 — gate 2.3b (Standards + Spec) + feature-expert REVIEW
Подтверждены главным по коду: F1 — в v2 на первом запуске выход Chat открывает док, а через кадр `LaunchedEffect(currentTopRoute)` (`App.kt:1411`) закрывает его из-за подмены корня Main → Inbox (`App.kt:1463`); CSAT-фильтр по `source=onboarding` глушил и анализ своего файла на экране Analyze; дубль пунктов образца; платформа сравнивалась строкой `"web"`; свои брейкпоинты вместо `AppWindowSizeClass`; `CLAUDE.md` описывал 4 варианта онбординга (исправлено главным). Фиксы — feature-expert (логика) и compose-expert (брейкпоинты). <!-- docs-leak-scan: reviewed — code line numbers only -->

### Итерация 11 — 2026-11-04 — feature-expert + compose-expert (фиксы ревью)
F1: гейт `isShellStableForLaunch` + маркер `v2DockLaunchRoute`; CSAT режет только `checklist_created` с онбординговым источником и `ai_analyze_completed{surface=onboarding}`; общий `SAMPLE_ITEMS`; `expect val isWebTarget` вместо строки `"web"`; typed-защёлка по тексту; исключение `analyzeData` → `ai_analyze_failed`; сбой образца из блока ошибки восстанавливает его; брейкпоинты из `AppWindowSizeClass` → VM 47/47, Rules 11/11, голдены 41/41 без перезаписи, `ShellLaunchGateTest` 5/5, CSAT 14/14. <!-- docs-leak-scan: reviewed — test counts only -->

### Итерация 12 — 2026-11-17 — главный: влит `origin/master`
6 коммитов master (звонок #72, релиз 1.23.0, иконка v5, дедуп напоминаний, layout-debug #76/#77). Конфликты add/add в 21 файле звонка (squash-merge #72 против исходных коммитов ветки) → взята сторона master, поверх возвращены импорты `GistiPresenceOrb` / `OrbMode` / `GistiOrbHaloExtent` из `core/designsystem`; повторно добавленный master'ом `feature/voicecall/.../GistiPresenceOrb.kt` удалён (дубль перенесённого; (не проверено — путь удалён этим merge намеренно)). `App.kt`: одна проверка `voiceCallAvailable` на месте из master (её читают Ask pill, кнопка звонка в чате и онбординг), наши дубли убраны. Устаревшая `docs/active/voice-call-agent-2026-11-04.md` удалена — у master она в `docs/archive/` со статусом Done. 21 голден Try it перезаписан: master переделал `SourceRow.kt`, сдвинулось сглаживание обводки таблеток (сверено картинкой сравнения). → onboarding, voicecall, voice, aichat, analyze, splash, user, navigation, весь `composeApp` host-test, wasmJs и debug-сборка зелёные; debug APK стоит на Pixel 9.

### Итерация 13 — 2026-11-17 — главный (codex-imagegen) + design-expert
Обратная связь владельца по шагу 2: один экран на все три выбора, записка обрезает текст, 4 таблетки «свой файл» устарели, поле ввода сливается с фоном и без голоса, карточка «Обсудите с Gisti» дублирует поле. Фото записки сгенерировано через Codex (EN/RU/HI, 1024×1536, текст = `onboarding_sample_note_title` и `onboarding_sample_note_1`…`_6`; HI — вычитать носителю). DESIGN_SPEC v2 `docs/designs/onboarding-steps-2026-11-17/DESIGN_SPEC.md`: варианты A (три экрана Write / Attach / Talk, «Try it now» → текстовая «Show me an example»), B (один экран, герой по выбору), C (композер внизу везде); рекомендован A; 37 кадров прототипа; `ChatInputRow` переезжает в designsystem, `SourceRow` расширяется карточками. Открыты вопросы владельцу Q1–Q5 (§ спеки).

### Итерация 14 — 2026-11-17 — владелец + feature-expert (SPEC + IMPLEMENT)
Владелец: вариант A (picker вернул B; после названного риска «Write ≈ Talk» — A), голосовое = диктовка в поле через `TranscribeAudioApiService` (1 кредит за вызов, возврат при сбое Gemini — `main.py` `transcribe_audio`), Chat/Talk с текстом отправляют сразу (`ShellLaunch.OpenChat(autoSend)` → штатный `handleSend` чата), «Show me an example» на Showcase, ключи ключи onboarding_sample_note_… остаются. FEATURE_SPEC v2 `docs/designs/onboarding-steps-2026-11-17/FEATURE_SPEC.md`; `OnboardingStep` = Showcase / Write / Attach / Talk, фазы `Recording` / `Transcribing`; `VoicePressTerminalGate` вынесен из чата в `core/filepicker/api` → onboarding 122, aichat 642, composeApp 397, navigation 20, splash 15, user 65 — зелёные, wasmJs и debug-сборка собираются; вёрстка — скелет. Удалены 2 теста снятого владельцем поведения (`pendingFocus`, CTA «Try it now»), тест «Chat не отправляет» переписан на autoSend по решению владельца. Главный: на вебе без Google микрофон скрыт (кредитов 0 — диктовка всегда упрётся в отказ). <!-- docs-leak-scan: reviewed — test counts only -->

### Итерация 15 — 2026-11-17 — feature-expert (`isVoiceAvailable`) + compose-expert
`isVoiceAvailable = voiceAvailable(isWeb, user.isGoogleLinked)`, следит за привязкой; 3 мёртвые строки голос→список удалены. Вёрстка A: Showcase с «Show me an example» (без дока CTA), Write — карточка-композер с микрофоном⇄отправкой и состояниями записи/распознавания, Attach — фото-карточка (`SamplePhoto`, кроп долями через `BitmapPainter`) + `SourceCardGrid` (расширение `SourceRow.kt`), Talk — приветствие с орбом, стартеры, `ChatInputRow` закреплён снизу; оверлей на фото с уголками и счётчиком. `ChatInputRow` перенесён в `core/designsystem` (`git mv`), жест микрофона вынесен в `GistiMicSendButton`; WebP 768×1152 на EN/RU/HI в `drawable{,-ru,-hi}`; удалены `TryItBlocks.kt`, `SampleNote.kt` и 9 строк × 3 локали (источник: отчёт compose-expert, ключи `onboarding_try_*`) → onboarding 153 (67 кадров матрицы), aichat 642 и designsystem 193 — голдены без изменений, wasmJs и debug-сборка собираются. Не доказано снимком: анимации, жест удержания, клавиатура, выбор `drawable-ru` в wasm. <!-- docs-leak-scan: reviewed — test counts only -->

### Итерация 16 — 2026-11-17 — gate 2.3b (Standards + Spec) + compose-expert
Standards — 0 находок; Spec — 3 Low. F1 подтверждена (после отказа в микрофоне поле теряло фокус) → `rememberVoiceRefocus` в `WelcomeOnboardingScreen.kt` возвращает фокус после записи/распознавания на Write и Talk, голдены без изменений. F2 (Enter на вебе в Talk не отправляет) — поведение самого `ChatInputRow` в чате, не чинилось. F3 (пустой чат с Talk недостижим) — следствие утверждённых кадров A, строка решения в DESIGN_SPEC исправлена.

### Итерация 17 — 2026-11-17 — владелец + compose-expert: вес голденов и фото
Голдены онбординга весили как все голдены `master` вместе — из-за кадров с фото (PNG почти не сжимает фотографию). Решение владельца: урезать и сжать. WebP перекодированы из PNG-оригиналов в 640×960, `cwebp -q 55` (EN 36.2 / RU 49.7 / HI 46.2 KB; источник: `Get-ChildItem` в сессии); 576 px уже размывает рукопись. Тестовый шов `LocalSampleNoteImage` в `SamplePhoto.kt`: в кадрах без реального фото — плоская заглушка 200×300 (`SampleNotePlaceholder.kt`) через тот же кроп `BitmapPainter`; реальное фото — в Showcase, Attach 35/36/39, оверлее 58 и sampleReady; кадры 35/36/39/58 записаны с `resizeScale = 0.5` (verify его учитывает — по байткоду roborazzi-core 1.77.0, отчёт compose-expert). → 67 кадров, 5.05 МБ, самый большой 241 KB (источник: `Get-ChildItem` в сессии); verify, host-тесты onboarding и wasmJs зелёные.

### Итерация 18 — 2026-11-17 — владелец (устройство) + compose-expert: зона навигации на Talk
Владелец на Pixel 9: на Talk системная навигация другого цвета, чем композер. Корень — корневой `Column` в `OnboardingChrome` (`WelcomeOnboardingScreen.kt`) делал `navigationBarsPadding()` для всех шагов, и фон `AppSurface.bottomChrome()` плашки `TalkComposerPlate` кончался над навигацией. Фикс: корень берёт только боковой инсет навигации, каждый шаг кроме Talk — нижний; плашка Talk сама делает `navigationBarsPadding()` внутри своего фона. Robolectric по умолчанию без инсетов, поэтому 67 кадров матрицы дефект не видели — новый `OnboardingNavBarInsetScreenshotTest` (6 кадров с поддельным инсетом навигации и клавиатуры): красный кадр до фикса, после — одна поверхность в светлой и тёмной теме, кадры с клавиатурой и Write не изменились. Боковая навигация в landscape по-прежнему цвета страницы — не трогалось.

### Итерация 19 — 2026-11-17 — владелец + general-purpose: размер списка из идеи
Владелец: пример «собраться на море» дал чек-лист из 43 пунктов. Корень — промпт `generate_checklist` («comprehensive», без цели по размеру); онбординг и создание из текста зовут одну функцию. Решение владельца: сервер, везде, норма 8–12. Промпт исправлен (идея → 8–12 плоским списком; источник с пунктами → все пункты), задеплоена только `generate_checklist` (`00042-civ` → `00043-ruk`). Живой тест `firebase-functions/tests/test_generate_checklist_size.py`: до — 33 / 38 пунктов (EN / RU), после — 10 / 11, список из 20 — 20 (источник: прогоны теста в сессии). Решение — `docs/decisions/2026-11-17-ai-checklist-size-from-idea.md`.

### Итерация 20 — 2026-11-17 — владелец + design-expert + compose-expert: лоадер AI без орба
Владелец: при создании из идеи «висит синий круг — страшно». design-expert — §L2 в `docs/designs/onboarding-steps-2026-11-17/DESIGN_SPEC.md`: заголовок, цитата, карточка из 4 шагов на компонентах строки чек-листа; шаги отмечаются по таймеру, 4-й крутится до результата; медленные подписи без цикла; компактный режим (только активный шаг) — по замеру высоты, не по `fontScale`. Досрочно отмечать оставшиеся шаги перед переходом не стали — контракт VM не менялся. compose-expert: `AiGeneratingLoader` переписан, таймер — `AiLoaderTimeline` на часах кадров (`withFrameMillis`); `waitForIdle` в Robolectric прокручивает такой таймер до конца, поэтому кадры матрицы фиксируют время через `LocalAiLoaderElapsedOverride`, а отдельный тест с ручными часами снимает настоящий спиннер. Кадры 65/66 перезаписаны, 68–78 добавлены, остальные байт-в-байт; verify, host-тесты и wasmJs зелёные.

## Выводы

- Онбординг `ai_welcome` — 2 шага: Showcase (3 плитки + текстовая «Show me an example», которая сразу запускает образец) → свой экран на каждый выбор: **Write** (карточка-композер, микрофон⇄отправка → AI-список, идеи, блок ошибки AI), **Attach** (фото записки → чек-лист без AI и кредитов; 4 карточки своих файлов → Analyze), **Talk** (приветствие Gisti, стартеры, композер чата со звонком). Текст поля общий у Write и Talk. Пэйвола нет. Варианты `slides` и `interactive` удалены, `ai_welcome` — на всех платформах, `none` по-прежнему пропускает онбординг. Первая версия шага 2 (один экран «Try it» на все выборы) отвергнута владельцем 2026-11-17.
- Записка-образец — фото, сгенерированное Codex, по файлу на язык (`drawable{,-ru,-hi}/onboarding_sample_note.webp`); текст фото = ключи ключи onboarding_sample_note_… (они же описание для TalkBack). Рисованная записка (`SampleNote.kt`) удалена. В голденах реальное фото — только в ключевых кадрах, остальные рисуют заглушку через `LocalSampleNoteImage`: новый кадр с крупным фото по умолчанию идёт с заглушкой, иначе голдены снова раздуются.
- Голосовое сообщение = диктовка в поле через `TranscribeAudioApiService` чата, 1 кредит за вызов (источник: feature-expert по `firebase-functions/main.py` `transcribe_audio`). На вебе без Google микрофон скрыт (`isVoiceAvailable`). Talk-отправка, стартеры и Chat с текстом уходят в чат сразу — `ShellLaunch.OpenChat(autoSend)`, решение в `docs/solutions/shell-launch-from-pre-shell-screen-2026-11-04.md`.
- Лоадер создания из идеи — карточка из 4 шагов без орба (§L2 спеки); шаги идут по таймеру на часах кадров, 4-й не закрывается до результата. Размер списка из идеи — 8–12 пунктов правилом серверного промпта `generate_checklist` (`docs/decisions/2026-11-17-ai-checklist-size-from-idea.md`), задеплоено отдельно от релиза.
- На Talk плашка композера продолжается под системной навигацией: отступ навигации держит сама плашка, у остальных шагов — обёртка шага.
- `ChatInputRow` переехал из `feature/aichat/impl` в `core/designsystem` (жест микрофона — `GistiMicSendButton`), `VoicePressTerminalGate` — в `core/filepicker/api`; голдены чата не изменились. Выбор своего файла — `SourceCardGrid` в `SourceRow.kt`.
- Звонок в онбординге — за тем же признаком, что кнопка в Ask pill (`voice_call_enabled` + поддержка платформы). Звонок раскатан в vc91 / 1.23.0 с включённым флагом (источник: project memory `release-vc91-2026-11-11`, RC не перепроверял) — значит, Call в онбординге будет виден и в release-сборке на Android; на вебе и iOS его нет.
- Аналитика: `seed` на каждом пути (включая `skip`), `step_index`, `onboarding_mode_selected`, `onboarding_sample_used`, `ai_analyze_*` на typed-пути с `surface=onboarding`, `checklist_created` с `ChecklistSource` (значения ONBOARDING_…); новые события `onboarding_starter_tapped`, `onboarding_voice_recorded`. Ряды `seed` `default` / `multimodal` / `inbox` заканчиваются с релизом.
- В RU не было 37 строк старого онбординга (источник: `comm` ключей ключей onboarding_welcome_… values vs values-ru) (чипы, подсказки в Inbox) — переведены.
- Переиспользуемое знание — `docs/solutions/shell-launch-from-pre-shell-screen-2026-11-04.md`.
- Тесты VM писались вместе с кодом, не red-first. HI-тексты и рукопись на HI-фото стоит показать носителю.

## Следующая сессия

Не доказано снимками, проверить на устройстве (UI водит владелец; можно снимать `adb logcat`):
- Talk / Write → удержать микрофон → отпустить: «Recognizing speech…», текст в поле; свайп вверх отменяет; отказ в разрешении — снекбар, поле редактируемо;
- Talk → Send / стартер: Inbox, док чата открыт и не закрывается, сообщение уже отправлено (F1 + autoSend);
- Attach → «Попробовать на записке» → чек-лист «This week» из 6 пунктов; Photo → Analyze → «назад» → Inbox;
- клавиатура: на Write видны поле и кнопка; на Talk 320×640 стартеры над полем;
- **Call** → звонок поверх Inbox (`voice_call_*` с `surface=onboarding`).
- Talk: зона навигации песочная, как плашка, в светлой и тёмной теме; Write → Send: шаги лоадера отмечаются, 4-й крутится до перехода; TalkBack читает заголовок один раз; цена спиннера на wasm.
- `generate_checklist` после правки промпта не проверен живым тестом на фото, PDF, ссылке, голосе и явной просьбе «подробный список».
- Веб на :9090: RC на вебе `none`, но fallback — `ai_welcome`; прогнать хотя бы раз и проверить выбор `drawable-ru` в wasm-сборке. ⚠️ merge в `master` автоматически деплоит веб в прод.
- Пред-существующее, не чинилось: 2 падения `CaptureDockShoulderTest` на чистом `master`; instrumented `ScreenshotCatalogTest` / `EndToEndFlowTest` / `DiagnosticTest` описывают слайды (устарели с 07-28).
- Замер эффекта: pre/post по 4 недели от релиза, primary «старт → `onboarding_completed` за 24 ч», guardrails из «Цель»; baseline — `docs/reports/onboarding-funnel-2026-11-04.md` (gitignored). <!-- docs-leak-scan: reviewed — window lengths only, no figure -->

## Предложения по улучшению агентов

- `@feature-expert`: в брифе было «RED-first по ключевым веткам», тесты написаны вместе с кодом и красными не видены (агент сам это отметил). Стоит явно требовать в IMPLEMENT прогон хотя бы одного acceptance-теста на скелете до реализации, когда бриф просит RED-first.
- `@compose-expert`: в Robolectric-матрице `delay()` в композиции не двигается `mainClock` — пришлось переписывать на часы кадров (`awaitFrameMillis`). Паттерн стоит закрепить в агенте для любого UI с таймингами под Roborazzi.
