# Analyze: надёжность и анализ в фоне

**Статус:** Done
**Дата старта:** 2026-11-20
**Start SHA:** 83f437cea09bbb695ef6780d0335023956a97cec
**Project:** Checklists
**Тип:** bug-fix
**Затронутые модули:** feature/analyze, composeApp (sync, navigation), core, firebase-functions

## Цель (продуктовая)

Analyze (AI превращает фото, PDF, текст, ссылку, голос или .txt в чек-лист) стабильно отдаёт результат по содержимому входа, а результат не теряется: пользователь может уйти с экрана или из приложения и получить готовый чек-лист позже. Требование владельца 2026-11-20: «анализ должен уметь идти и в фоне, чтобы пользователь мог уйти с экрана и потом получить результат».

## Технический план

Найдено расследованием прод-логов и прогоном матрицы входов на эмуляторе (2026-11-20):

1. PDF не читается: `AnalyzeRepositoryImpl.convertInputData` отправляет строку `"PDF content from: ${inputData.fileName}"` вместо содержимого — AI сочиняет список по имени файла.
2. ViewModel экрана живёт столько же, сколько Activity (у `NavDisplay` в `App.kt` нет ViewModel-store декоратора, `koinViewModel` с ключом) → повторный вход той же кнопкой висит на лоадере; «Назад» не отменяет запрос, и потом `navigateToChecklistDetail(clearBackStack = true)` перебрасывает пользователя.
3. Уход из приложения во время ожидания: сеть фонового приложения обрывается, результат теряется («Connection problem»), ошибка нигде не логируется.
4. Новая установка: слушатель `UserCreditsSync` получает `PERMISSION_DENIED` в первой сессии → баланс 0, кнопка Analyze выключена до перезапуска.
5. `AnalyzeScreenContract.aiActionCost` по умолчанию 30 и нигде не обновляется, сервер списывает 20 → кнопка гаснет на остатке 20.
6. Фото уходит без уменьшения (снимок 4000×3000 на 5,2 МБ шёл ~92 с на эмуляторе; источник: прогон mobile-tester, итерация 1).
7. Сервер `generate_checklist`: нет повтора и запасной модели на Gemini `503 UNAVAILABLE`; пустой или мусорный ввод списывается и даёт пустой чек-лист.
8. Нет валидации URL (`analyze_error_invalid_url` не используется).

Попутно уточнено окно простоя Gemini prepay (402): ≈2026-10-24 → 2026-11-11 по логам Cloud Run.

План по фазам: сервер (PDF, 503, пустой результат) и кредиты новичка — параллельно; затем SPEC фонового анализа (исполнитель уровня приложения, Android-воркер, доставка результата) и дизайн доставки; реализация клиента; повторный прогон на эмуляторе.

## Лог итераций

### Итерация 1 — 2026-11-20 — mobile-tester: матрица входов P/D/T/L/F/V + сценарии S1–S5 на эмуляторе → дефекты 1–8 подтверждены, ключевые сверены с кодом

### Итерация 2 — 2026-11-20 — core-expert: «0 кредитов у новичка» → корень не в слушателе, а в CompleteOnboardingUseCase

**Почему так:** гипотеза из логов (`UserCreditsSync: credit listener error: PERMISSION_DENIED`) оказалась ложным следом — для анонимной установки правила Firestore требуют `request.auth != null`, и ветка ошибки слушателя только пишет warning, кэш не трогает.
**Баг:** `CompleteOnboardingUseCase` вызывал `repository.update(UserData(isOnboardingPassed = true))`; `update()` пишет все поля снимка, поэтому кредиты и premium из `register_user` перезаписывались дефолтами (0 / false) до холодного рестарта.
**Решение:** `UserDataRepository.setOnboardingPassed()` пишет один ключ; RED `UserDataRepositoryImplTest.completeOnboardingKeepsRegisteredCreditsAndPremium` → GREEN. Остаток риска: `update(snapshot.copy(...))` в `AnalyzeRepositoryImpl` и `ChatViewModel` — перезапись из устаревшего снимка.

### Итерация 3 — 2026-11-20 — feature-expert (SPEC): матрица фонового анализа + контракт → `docs/active/analyze-reliability-background-2026-11-20-FEATURE_SPEC.md`, контрактные файлы в `feature/analyze/.../domain/job/` компилируются (Android + wasmJs)

**Почему так:** анализ уходит из ViewModel в планировщик уровня приложения (id задания = серверный `request_id`, копия входа в хранилище приложения); результат доставляет ровно одна сторона (экран / snackbar / уведомление) — это закрывает и вечный лоадер, и «перехват» после «Назад». Хуки активации — в момент доставки, не в воркере (одноразовый flow без replay). Новый дефект: нечитаемый файл уходил текстом `"Failed to read image: <path>"` и списывался.

### Итерация 4 — 2026-11-20 — general-purpose (сервер): `pdf_base64` → Gemini `application/pdf`, проверка входа до резерва (`unsupported_input_type` / `invalid_input` / `input_too_large`), повторы SDK + один вызов запасной модели на 503/429, `nothing_found` (422, возврат кредитов) → новые тесты `tests/test_analyze_reliability.py` RED → GREEN, не задеплоено <!-- docs-leak-scan: reviewed — HTTP codes and credit refund mechanics, no metric -->

**Почему так:** в `google-genai` повторы выключены, пока не задан `retry_options` — 503 сразу становился 500. Настройки повторов передаются в вызов analyze/generate, а не в общий клиент: иначе изменились бы таймауты `classify_chat_intent` и `transcribe_audio`. Сервер выкатывается раньше клиента, который начнёт слать `pdf_base64`.

### Итерация 5 — 2026-11-20 — design-expert: DESIGN_SPEC фаз Waiting / Waiting(network) / Delivering / Failed, строка «N in progress», ready-snackbar, уведомление → проверено на кадрах прототипа (RU/HI, fontScale 1.5–2.0, 840 dp, dark); пробелы контракта G1–G3 переданы feature-expert

**Почему так:** на кадрах snackbar с именем при 320 dp / 1.5 / RU занимал 5 строк (источник: кадры прототипа design-expert) → имя обрезается до 24 символов, `actionOnNewLine` по длине; диск `errorContainer` в тёмной теме тревожный → `AppSurface.recessed()` + иконка error; три snackbar подряд перекрывают низ ~30 с → сводка при ≥ 2 готовых чек-листах.

### Итерация 6 — 2026-11-20 — android-platform-expert: `AnalysisWorker` (expedited, `getForegroundInfo`, KEEP по id задания), `WorkManagerAnalysisJobLauncher` с фолбэком на корутинный запуск, `FileAnalysisPayloadStore`, `AndroidImageDownscaler` (EXIF), уведомления + тап (cold/warm) в `MainActivity`, запрос `POST_NOTIFICATIONS` без перехода в «Настройки» → `:androidApp:assembleDebug` OK, 14 новых тестов зелёные; DI и уведомления на устройстве не проверены

### Итерация 7 — 2026-11-20 — wasmjs-expert: `WasmImageDownscaler` (`createImageBitmap` с `imageOrientation: from-image`, JPEG через OffscreenCanvas), `VisibilityForegroundMonitor`, `beforeunload`-охрана на время незавершённых заданий → compile wasmJs OK; тест `JpegHeaderTest` не запущен (блокер — `vararg Result<…>` в тестовых дублях feature-expert, запрещён на Kotlin/Wasm); живой прогон на :9090 — после склейки commonMain

### Итерация 8 — 2026-11-20 — feature-expert (IMPLEMENT): `AnalysisJobManager` / `AnalysisJobProcessorImpl` / `AnalysisReadySignalImpl` / `resolveDelivery`, очередь заданий в DataStore (`analysis_jobs_v1`), PDF как `PDF_BASE64`, цена из `get_credits_info`, `normalizeAnalyzeUrl`, `nothing_found` / нечитаемый файл без запроса, `setAiCredits` одним ключом, ready-snackbar в `App.kt` → 7 red-репро RED → GREEN, `:feature:analyze:testAndroidHostTest` 164/164; тесты onboarding/user/composeApp не прогнаны (процесс убит из-за нехватки памяти)

**Почему так:** проверка «пустой результат» перенесена в репозиторий (существующий тест VM ждал чек-лист из пустого фейка) — побочно вложения в чате и онбординг тоже перестают создавать пустой чек-лист.

### Итерация 9 — 2026-11-20 — compose-expert: фазы Waiting / Waiting(network) / Delivering / Failed, строка «N in progress», ряд стоимости при неизвестной цене, ошибка ссылки, запрос разрешения на уведомления → 26 кадров Roborazzi (`AnalyzeJobStatesScreenshotTest`), `:feature:analyze:testAndroidHostTest` 190/190; добавлены недостающие RU-строки `analyze_error_network`, `analyze_error_user_not_ready`

### Итерация 10 — 2026-11-20 — главный: текст отказа в Android-уведомлении берётся из `userFacingMessage` (локализованная причина) вместо `serverMessage` → `AndroidAnalysisReadyNotifier.kt`

### Итерация 11 — 2026-11-20 — feature-expert: отказ 4-го параллельного анализа — подсказка без заголовка «Ошибка» (`hintError` / `errorIsHint`) → `:feature:analyze:testAndroidHostTest` 191/191

### Итерация 12 — 2026-11-20 — главный: `/task-gate full` запущен (L1 static 0; ревьюеры Standards + Spec спавнены по пакету `review-package-83f437cea0.md`); мок `test_premium_user_higher_usage_limit` → непустой результат (одобрено владельцем), тест зелёный; сборка `:androidApp:assembleDebug` запущена

### Итерация 13 — 2026-11-20 — главный: ревью диффа (Standards + Spec) → 7 подтверждённых находок; EXIF-фолбэк получил лог; остальные 6 отданы feature-expert пачкой; impact-scan Telegram-бота и MCP чист (MCP шлёт в analyze только `Exclude<CfInputType, "none">`)

### Итерация 14 — 2026-11-20 — главный: деплой `generate_checklist` + `analyze_and_fill_checklist` (подтверждён владельцем) → smoke: PDF 500 (`httpx.ReadTimeout`)

**Почему так:** политика «3 попытки × 15 с» подобрана по задержкам текста и фото; PDF-документ Gemini читает дольше, три короткие попытки исчерпали бюджет.
**Решение:** для `pdf_base64` / `audio_base64` — одна попытка 45 с (`ANALYZE_DOCUMENT_HTTP_OPTIONS`), тест `test_documents_get_one_long_attempt_not_three_short_ones`; повторный деплой → ревизии `generate-checklist-00046-fav`, `analyze-and-fill-checklist-00041-zos` (весь трафик; источник: вывод `gcloud functions deploy`), smoke: PDF 200 за 10,4 с с 8 шагами из файла, неизвестный тип → 400 `unsupported_input_type`. Откат — ревизии `generate-checklist-00044-fol`, `analyze-and-fill-checklist-00039-zod`. <!-- docs-leak-scan: reviewed — Cloud Run revision traffic and latency of a smoke test, no metric -->

**Итог итерации (до продолжения):** сессия остановлена лимитом.

### Итерация 15 — 2026-11-20 — feature-expert: находки ревью F1–F6 (текст исключений не уходит пользователю, `TARGET_MISSING`; упавшее задание из snackbar/уведомления открывает Analyze в фазе Failed с вводом через `AnalysisJobHandoff`; вопрос про уведомления один раз навсегда — `analysis_notification_asked_v1`; `NothingFound` в онбординге и вложениях чата; `USER_NOT_READY` и сбой записи в Room — повтор с тем же id без второго списания; `INVALID_INPUT`) → RED → GREEN, `:feature:analyze` 201/201, onboarding 179/179; попутно: `onOpen` на холодном старте читал задание из ещё не загруженного потока — теперь из хранилища

### Итерация 16 — 2026-11-20 — mobile-tester: повторный прогон на эмуляторе → заблокирован: сначала emulator-5554 параллельно вела другая сессия (AiTeacher), затем процесс эмулятора завершился (вероятно, нехватка памяти); второй AVD система остановила по памяти. Пройдено: N1 (новичок видит 100 кредитов в первой сессии), строка цены «costs 20». Остальная матрица не прогнана.

**Итог итерации:** код, сервер (задеплоен, smoke пройден) и тесты готовы; нужен прогон на устройстве, когда освободится память / эмулятор. Осталось: принять ревью (2.3b) и починить подтверждённое; impact-проверка вызывающих `generate_checklist` вне клиента — `telegram-bot/src/backend.ts`, `mcp-server/src/cf.ts` (новые 400/413/422 и `error_code`, отказ `input_type: "none"` в analyze); деплой `generate_checklist` + `analyze_and_fill_checklist` (владелец подтвердил) до проверки PDF; эмулятор (владелец разрешил) — повторная матрица + новые сценарии; COMPLETE doc-task, отчёт gate, MR.

### Итерация 17 — 2026-11-21 — mobile-tester: прогон на emulator-5554 (Pixel_9, API 36) → PASS: PDF по содержимому (8 пунктов из файла), Back → snackbar «… is ready» + Open, HOME → уведомление → тап открывает чек-лист, повторный вход — свежая форма, невалидная ссылка отсечена без запроса, ссылка без схемы принята; остальное упёрлось в кредиты тестового uid

### Итерация 18 — 2026-11-21 — главный + mobile-tester: тестовому uid выставлено `ai_credits=200` в Firestore (с согласия владельца) → PASS: офлайн → Cancel (форма с вводом, задание отменено), офлайн → сеть (анализ стартовал сам), `am kill` в ожидании сети (JobScheduler поднял процесс, один результат, одно списание, уведомление), `.txt` (все строки файла; источник: отчёт mobile-tester, кадр `s7_1.png`), `....` и тишина в голосе → `nothing_found` без списания; находка: `asdf qwer zxcv` → чек-лист из трёх слов со списанием — не регрессия, владелец отложил → `docs/backlog/2026-11-21-analyze-gibberish-text-becomes-checklist.md`

## Выводы

- Анализ живёт в задании уровня приложения (id = серверный `request_id`), а не во ViewModel. Результат доставляет ровно один получатель: экран, snackbar или уведомление. Это закрыло дефекты 2 и 3 плана: вечный лоадер, выдёргивание после «Назад», потерю результата при уходе из приложения. Проверено на эмуляторе, включая убийство процесса и офлайн.
- Сервер в проде с 2026-11-20: `generate-checklist-00046-fav`, `analyze-and-fill-checklist-00041-zos` — PDF по содержимому, проверка входа до резерва, повторы и запасная модель на 503/429, `nothing_found` с возвратом кредитов, длинная попытка для PDF и аудио. Откат: `generate-checklist-00044-fol`, `analyze-and-fill-checklist-00039-zod`. <!-- docs-leak-scan: reviewed — refund mechanics, no metric -->
- «0 кредитов у новичка» вызывал не слушатель Firestore, а `CompleteOnboardingUseCase`: запись всего снимка `UserData`. Риск того же класса остаётся у `update(snapshot.copy(...))` в `AnalyzeRepositoryImpl` и `ChatViewModel`.
- Решения владельца, принятые по умолчанию: до 3 анализов параллельно; «Продолжить в фоне» + snackbar без автоперехода; разрешение на уведомления запрашивается один раз; на вебе перезагрузка помечает файловое задание как прерванное, `beforeunload` предупреждает и о заданиях в очереди.
- Не проверено на устройстве: первый запрос `POST_NOTIFICATIONS` (разрешение уже было выдано), голос с речью (микрофон эмулятора отдаёт тишину), убийство процесса посреди сетевого запроса, живой прогон веба на :9090. В logcat нет событий задания (размер фото после сжатия, итоговый URL). У Compose-узлов нет `testTag` / `testTagsAsResourceId`, поэтому device-flow скрипты по id не пишутся. <!-- docs-leak-scan: reviewed — logcat events, no metric -->
- Вне задачи замечено: `classify_chat_intent` / `chat_agent` / `chat_completion` / `transcribe_audio` тоже не повторяют запрос на 503; повторный `request_id` при гонке двух повторов может вернуть кредиты дважды (паттерн возврата был до задачи); `credit_error_response` игнорирует свой `message`.
- Постоянный документ: `docs/solutions/analyze-background-job-and-reliability-2026-11-21.md`.
