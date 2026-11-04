**Статус:** In Progress

**Дата старта:** 2026-11-04

**Start SHA:** `c7e81dd8`

**Project:** Gisti — AI Checklists

**Тип:** feature / architecture

**Затронутые модули:** новые `:core:voice:api`, `:core:voice:impl`; `:feature:aichat:impl`; `:composeApp`; `firebase-functions` (Python).

---

## Цель (продуктовая)

Голосовой звонок с AI-ассистентом Gisti на Android: пользователь говорит, Gisti отвечает голосом и умеет то же, что текстовый чат (создаёт задачи и чек-листы через инструменты `chat_agent`). Вход — кнопка звонка в пилюле «Ask Gisti» (ветка `feat/chat-pill-attach-call`).

## Решения владельца (2026-11-04)

- Логика — движок звонка из соседнего проекта AiTeacher (on-device распознавание → серверный ход → системный TTS, перебивание, аудиофокус, пауза при сворачивании). UI — свой.
- Прошлая версия Gisti (коммит `017e45ae`, ветка `archive/voice-call-2026-08-09`) за основу не берётся.
- Сервер — существующий `chat_agent` с голосовым режимом (короткий ответ без markdown, те же инструменты).
- Тариф — минуты, считает сервер по heartbeat. Ход в живой голосовой сессии не списывает чат-кредиты. Лимиты из Remote Config: free 5 мин на всё время, premium 20 мин/сутки, один звонок до 10 мин.
- Только Android в первой версии.
- Деплой функций и живую проверку владелец разрешил сделать сразу после готовности голоса.

## Технический план

1. `:core:voice:api` — контракты и модели движка (порт `domain/voice/VoiceContracts.kt`, `VoiceModels.kt`, `VoiceCallEngine.kt` из AiTeacher без команд урока).
2. `:core:voice:impl` — commonMain: движок, VAD, разбивка текста, учёт фокуса распознавателя; androidMain: SpeechRecognizer, TextToSpeech, детектор перебивания с AEC, аудиофокус, маршрут звука; Koin `factory` на звонок.
3. Сервер: CF `voice_session` (start / heartbeat / end, минуты в Firestore), голосовой режим `chat_agent`; пункт о звонке в каталоге фич ассистента — после раскатки.
4. Фича: шлюзы хода (цикл `chat_agent` + `ToolCallDispatcher`) и сессии, ViewModel звонка, оверлей вне Nav3, кнопка в пилюле.
5. UI по DESIGN_SPEC `design-expert`, скриншот-цикл.

## Пауза (2026-11-04)

Остановлено по внешней блокировке: у проекта Gemini API кончилась предоплата (`402 RESOURCE_EXHAUSTED: prepayment credits are depleted`), на карте владельца нет средств для пополнения. С 05:47 UTC AI-чат в проде не отвечает никому — это не регрессия ветки.

Триггер возобновления: `chat_agent` на текстовый ход отвечает успехом (smoke — текстовый ход одноразовой учётки; голосовой — `voice_session` start → `chat_agent` с `voice.session_id` → heartbeat → end).

Что уже в проде: `voice_session` (`voice-session-00001-duw`, smoke PASS), `chat_agent` с голосовым режимом (`chat-agent-00028-quc`). Debug APK с кнопкой звонка стоит на Pixel 9.

Шаги после пополнения:
1. Smoke текстового и голосового хода `chat_agent`.
2. Живой звонок на Pixel 9: кнопка звонка → разрешение микрофона → «создай чек-лист …» → карточка действия → тап открывает чек-лист; `adb logcat` без `NoDefinitionFoundException`.
3. `/task-gate` full, PR в `master`.
4. Решение владельца: удаление голосом без карточки подтверждения.

## Лог итераций

### Итерация 1 — 2026-11-04 — design-expert
DESIGN_SPEC экрана звонка (шар Gisti, статус, субтитры, карточка действия агента, док «Микрофон / Завершить / Субтитры», экраны ошибок, итога и лимита; EN/RU, 24 кадра в матрице) → проверена HTML-прототипом на 18 кадрах. Решения раздела 0 приняты по умолчанию: тап по карточке завершает звонок и открывает объект; Back завершает без диалога; субтитры включены; остаток минут — с сервера; тариф минутами. Добавить HI.

### Итерация 2 — 2026-11-04 — android-platform-expert
Модули `:core:voice:api` / `:core:voice:impl`: контракты AiTeacher урезаны под Gisti (выброшены урок, репетитор, лента курса; добавлены `VoiceAgentAction`, `VoiceCallError`, `isCallSupported`), Android-реализации SpeechRecognizer / TTS / barge-in с AEC / аудиофокус с legacy-веткой для minSdk 24, заглушки wasmJs/iOS, Koin `voiceModule`, манифест (`queries`, `uses-feature microphone required=false`) → api + impl тесты 20/20, `assembleDebug` зелёный; Koin-привязки в рантайме ещё не резолвились.

### Итерация 3 — 2026-11-04 — feature-expert (SPEC)
Новый модуль `:feature:voicecall` (не `feature/aichat/impl`: звонку нужны только интерфейсы из `feature/aichat/api`, `ChatViewModel` > 4000 строк): матрица состояние × событие, `VoiceCallUiState` / `VoiceCallAction` / `VoiceCallEffect`, UI_SKELETON, 36 фикстур, строки EN/RU/HI → сборка Android + wasmJs, 4/4 теста. Решения главного по открытым вопросам: конец без действий показывает итог; «Premium» на баннере завершает звонок и открывает пэйвол; `OutOfCredits` → ошибка сервера с логом; deep-link звонок не прерывает.

### Итерация 10 — 2026-11-04 — главный (деплой и smoke)
`master` (пилюля, PR #70) влит в ветку, `onStartVoiceCall = startVoiceCall` подключён в `App.kt`. Деплой: `voice_session` → ревизия `voice-session-00001-duw`, smoke PASS (start / heartbeat по серверным часам / чужая сессия — отказ / end идемпотентен); `chat_agent` → `chat-agent-00028-quc` (память и таймаут — как в `deploy.sh`, оба секрета). Голосовой и текстовый ход `chat_agent` падают с `Agent step failed`: корень — Gemini API `RESOURCE_EXHAUSTED: prepayment credits are depleted` (баланс предоплаты AI Studio). Ошибка началась утром на прошлой ревизии `00027`, до деплоя — не регрессия ветки. Переключить Cloud Run обратно на `00027` нельзя: `Container import failed`. Ждёт пополнения баланса владельцем.

### Итерация 9 — 2026-11-04 — feature-expert (IMPLEMENT)
`VoiceCallViewModel`, маппер `CallEngineState` → `VoiceCallUiState`, `VoiceSessionGatewayImpl` (Ktor к `voice_session`), `ChatAgentVoiceTurnGateway` в `feature/aichat/impl` (≤ 5 раундов, идемпотентность по `clientTurnId`, `DispatchOutcome` → `VoiceAgentAction`), общий `AgentChecklistsSummary`, RC `voice_call_enabled` (debug — вкл, release — выкл), события аналитики `voice_call_*`, оверлей в `App.kt` → voicecall 110 тестов, aichat 627, `VoiceCallKoinGraphTest` 3/3, `assembleDebug` зелёный. В голосе `delete_item` / `clear_completed_items` выполняются без карточки подтверждения — голосовая директива сервера требует подтвердить голосом.

### Итерация 8 — 2026-11-04 — compose-expert
Экран звонка по DESIGN_SPEC в `feature/voicecall` (`VoiceCallInCall`, `VoiceCallFullState`, `GistiPresenceOrb`, `CallControls`, `CallTimerPill`, `VoiceCallBanner`, `VoiceTranscript`, бюджет высоты `VoiceCallLayoutMath`); Roborazzi заведён в модуле (`android preview render` не видит KMP-модуль без app-потребителя) → 34/34 кадра, `VoiceCallLayoutMathTest` 12/12. Кадры сняты с reduced motion; живая анимация шара и TalkBack — только на устройстве.

### Итерация 7 — 2026-11-04 — core-expert + android-platform-expert + главный
`CallPhase.Thinking.tool` из `VoiceTurnEvent.Status.toolName` (49/49); `VoiceNetworkMonitor` (Android `registerDefaultNetworkCallback`, горячий поток, `ACCESS_NETWORK_STATE`). Главный: онлайн = только `NET_CAPABILITY_INTERNET` без `VALIDATED` (ложное «нет сети» за captive portal/VPN блокирует звонок, ложное «есть» безопасно — ход упадёт в Reconnecting); фабрика движка по умолчанию берёт монитор, а не `flowOf(true)`. 5 падений `test_main.py` подтверждены на чистом master (`c7e81dd8`).

### Итерация 6 — 2026-11-04 — core-expert
`DefaultVoiceCallEngine` перенесён в `core/voice/impl` commonMain (internal, часы и id инжектятся), `factory<VoiceCallEngine.Factory>` с шлюзами через `parametersOf` → `DefaultVoiceCallEngineTest` 48/48, модуль 63/63; удалено 9 урок-специфичных кейсов, добавлено 4 (actions из `Done`, `OutOfCredits`, ошибка `end`). Источника сети в проекте нет — `isOnline` по умолчанию `flowOf(true)`, монитор заказан отдельно.

### Итерация 5 — 2026-11-04 — android-platform-expert (заход 2)
Expect/actual-точка `VoiceCallPlatform` в `:feature:voicecall` (`platform/VoiceCallPlatform.kt`): запрос `RECORD_AUDIO` с `canAskAgain` по `shouldShowRequestPermissionRationale`, настройки приложения и TTS (`false` — экран не открылся, хост обязан сообщить), `isChangingConfigurations` для ON_STOP; web/iOS — «не поддерживается» → сборка Android + wasmJs зелёная, на устройстве не проверено.

### Итерация 4 — 2026-11-04 — general-purpose (Python, сервер)
CF `voice_session` (start / heartbeat / end; списание по серверным часам ≤ 20 с за промежуток; ленивое закрытие через 45 с; квоты из `remote_config/current`: `voice_call_enabled`, `voice_free_lifetime_seconds=300`, `voice_premium_daily_seconds=1200`, `voice_max_call_seconds=600`) в `firebase-functions/voice_logic.py` + `main.py`; голосовой режим `chat_agent` (поле `voice.session_id`, без резерва кредита на первом раунде, лимит 20 ходов/мин и 1.5 с между ходами, `_voice_directive`, `supports_options=false`) → `tests/test_voice_session.py` 41/41; в `test_main.py` 5 падений, которые агент считает старыми (проверить на master). Не задеплоено.

Пункт о звонке для каталога фич ассистента написан, но в приватный промпт не внесён: фича за RC-флагом попадает в каталог только после раскатки (`.claude/rules/ai-chat.md`). Текст хранится вне репозитория — приватный промпт в публичных доках не цитируется.

## Выводы

## Предложения по улучшению агентов
