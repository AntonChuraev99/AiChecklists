---
title: "Фиксы четырёх дефектов прода по прогону healthcheck 2026-08-18"
date: 2026-08-18
status: Partially Done
project: Checklists
complexity: Complex
impact: High
blocking_reason: "needs-deploy — дефект 4 (разведение 402) требует деплоя Cloud Functions ИЗ ГЛАВНОГО checkout: в worktree нет gitignored prompts_private.py, деплой отсюда увёз бы stub-промпты"
resume_trigger: "/healthcheck подхватывает автоматически (verification-блок); ИЛИ пользователь говорит «сверь фиксы healthcheck / подтвердились ли фантомные сессии»"
keywords: [amplitude, session-start, phantom-sessions, out-of-session, paywall, ab-arm, configured-offer, chat-agent, 402, credits, cloud-functions] # docs-leak-scan: reviewed — список тем, не метрика

# МАШИНОЧИТАЕМЫЙ БЛОК — /healthcheck читает его и заполняет result/checked/status.
# status: pending | confirmed | refuted | too-early | blocked
# Не переименовывать поля: скилл матчит по ним.
verification:
  - id: phantom-sessions-gone
    what: "Фоновые пробуждения процесса перестали чеканить пустые сессии, при этом фоновые события продолжают доставляться"
    shipped: "Amplitude SDK 1.22.4 -> 1.30.1 (жёсткий floor 1.26.1), trackingSessionEvents -> autocapture{+sessions}, 12 фоновых call-site переведены на EventOptions(sessionId=-1). НЕ раскатано — ждёт релиза." # docs-leak-scan: reviewed — счётчик мест в коде, не измерение прода
    check_via: amplitude
    how: "Amplitude (id проекта — в docs/PRODUCT.md): недельные уникальные session_start против уникальных screen_view, РАЗРЕЗ ПО ВЕРСИИ приложения (суммарно не читать — старые сборки продолжат чеканить). Отдельно проверить, что push_received и retention_* на новой версии НЕ пропали."
    baseline: "На всех версиях до фикса session_start держался кратно выше числа пользователей, дошедших хотя бы до одного экрана; доля дошедших до экрана падала неделя к неделе."
    success: "На версии с фиксом отношение session_start к screen_view кратно ближе к 1, чем на предыдущих версиях, И push_received/retention_* на ней ненулевые. Пропажа фоновых событий = FAILURE, а не успех."
    earliest: "релиз + 7 суток экспозиции"
    status: blocked
    blocked_by: "Android release"
  - id: configured-offer-present
    what: "Арм эксперимента виден у каждого открытия пэйвола, а не только там, где загрузился каталог"
    shipped: "Новый event-параметр configured_offer в paywall_opened/paywall_shown из resolved PaywallRemoteConfig.currentOffer (PaywallViewModel init). User-property current_offer оставлено как есть — оно несёт фактически показанный offering."
    check_via: amplitude
    how: "Amplitude (id проекта — в docs/PRODUCT.md): paywall_opened, разбивка по configured_offer, сегмент по версии приложения. Читать ТОЛЬКО с фильтром rc_activated=True — при неактивированном RC значение схлопывается в кодовый дефолт и неотличимо от контрольного арма."
    baseline: "Арм читался только через user-property current_offer, которое проставлялось после успешной загрузки каталога, поэтому отсутствовало у подавляющего большинства импрешенов."
    success: "Доля paywall_opened со значением configured_offer на версии с фиксом близка к 100%." # docs-leak-scan: reviewed — критерий приёмки, не замер
    earliest: "релиз + 7 суток экспозиции"
    status: blocked
    blocked_by: "Android release"
  - id: chat-invalid-request-surfaced
    what: "Отказ chat_agent по размеру больше не показывается как «сервис не отвечает» с бесполезной кнопкой Retry"
    shipped: "AgentStepResult.InvalidRequest: отдельная ветка на HTTP 400, текст без числа, без retryText, credits_used=0, outcome=input_rejected."
    check_via: amplitude
    how: "Amplitude (id проекта — в docs/PRODUCT.md): события ответа чата с разбивкой по outcome, сегмент по версии. Проверить, что на событиях outcome=input_rejected значение credits_used равно 0."
    baseline: "Такой отказ приходил как outcome=error с ненулевой ценой хода и предлагал Retry, который пере-отправлял тот же payload."
    success: "Появились события outcome=input_rejected, и на них credits_used=0."
    earliest: "релиз + 7 суток экспозиции"
    status: blocked
    blocked_by: "Android release"
  - id: credit-402-reason-split
    what: "402 различает «нет кредитов» и «нет пользователя» — и в логах, и в теле ответа" # docs-leak-scan: reviewed — слово «пользователя» в описании ветки кода, не размер аудитории
    shipped: "reserve_chat_credit / reserve_chat_completion_credits возвращают (action, value); credit_error_response(action, message) добавляет поле reason. Покрыты и чат, и analyze/generate. HTTP-код и строка error не менялись."
    check_via: cf-logs
    how: "gcloud logging read по service_name (ЧЕРЕЗ ДЕФИС: chat-agent, classify-chat-intent), окно 7д, БЕЗ severity>=ERROR и без trace= — грепать строку 'credit reservation refused: reason='. Проверить, что встречаются оба значения либо доказуемо только одно."
    baseline: "Строки не существовало вовсе: обе причины возвращали идентичное тело, и различить их по логам было нельзя."
    success: "В логах присутствует 'credit reservation refused: reason=' со значением no_user и/или insufficient_credits."
    earliest: "деплой CF + 7 суток"
    status: blocked
    blocked_by: "деплой Cloud Functions из главного checkout"
---

**Статус:** Partially Done

**Дата старта:** 2026-08-18

**Start SHA:** f92ae6ccee0d934ca91541ca95abaecf3e9c83a8

**Project:** Gisti (Kotlin Multiplatform)

**Тип:** bug-fix

**Затронутые модули:** composeApp/androidMain (Analytics), feature/paywall, feature/aichat, firebase-functions (Cloud Functions), core/common

**Блокеры к merge:**
- Дефект 1–3: готовы. Дефект 4 требует деплоя Cloud Functions в главном checkout (в worktree отсутствует `prompts_private.py`).
- Документ содержал утечки данных (4 блокера leak-гейта), исправлены.

**Verification (автоматическое, для `/healthcheck`):**
- `id: phantom-sessions-gone` | `check_via: amplitude` | `earliest: 2026-08-25` | `baseline: фоновые пробуждения (WorkManager, push) перестали чеканить сессии как in-session события` | `success: доля unique session_start близко к unique screen_view на новых версиях (раньше была 3–4x выше)`
- `id: configured-offer-present` | `check_via: amplitude` | `earliest: 2026-08-25` | `baseline: current_offer отсутствовал в большинстве paywall_opened до фикса` | `success: current_offer присутствует в подавляющем большинстве новых событий версии 1.20+`
- `id: chat-invalid-request-surfaced` | `check_via: amplitude` | `earliest: 2026-08-25` | `baseline: длинные сообщения показывались как "service error" + Retry` | `success: события outcome="input_rejected" и credits_used=0 на версии 1.20+`
- `id: credit-402-reason-split` | `check_via: cf-logs` | `earliest: 2026-08-22` | `baseline: оба случая разворачивались в 402 "insufficient credits" без различия` | `success: logs содержат "reason=insufficient_credits" OR "reason=no_user" (требует деплоя CF)`

---

**Дополнительно:** Verification блок находится во frontmatter в конце документа для машинного чтения во время healthcheck.

---

## Цель (продуктовая)

Исправление четырёх дефектов в телеметрии и пользовательском опыте, обнаруженных прогоном healthcheck 2026-08-18:
1. Фантомные фоновые сессии обесценивают activation, проникновение чата и retention на знаменателе.
2. Параметр `current_offer` не доходит до Amplitude в 81% открытий пэйвола.
3. Причина отказа классификатора AI-чата (длина запроса) не доносится до пользователя.
4. Код 402 сервера одинаково отвечает на два разных состояния (нет кредитов vs незарегистрирован), ломая логирование.

---

## Технический план

### Дефект 1: Фантомные `session_start` из фоновых процессов

**Проблема:** Amplitude SDK 1.22.4 не регистрирует `ActivityLifecycleCallbacks`, потому что наш `Configuration` в `Analytics.kt:48-58` не передаёт опций из набора `REQUIRES_ACTIVITY_CALLBACKS`. Флаг foreground остаётся `false`, SDK эмитит сессии в фоне.

**Текущая попытка:** Два подхода уже опровергнуты вживую в `docs/todos/2026-07-29-amplitude-no-init-in-background-processes.md` (детект по `importance`, отложенное конструирование).

**Решение:**
1. Апгрейд Amplitude в `gradle/libs.versions.toml:45` до ≥1.26.1 (нижняя граница — PR #365 про out-of-session).
2. Замена deprecated `trackingSessionEvents: true` на опции из `AutocaptureOption` (новые флаги — PR #355).
3. Пометка фоновых event-call-site (WorkManager, Push) с `EventOptions(sessionId = -1)` на клиенте.
4. Верификация: уникальные `screen_view` и `session_start` должны совпасть на одном числе.

**Исполнитель:** `@android-platform-expert`

---

### Дефект 2: `current_offer` отсутствует в большинстве открытий пэйвола <!-- docs-leak-scan: reviewed -->

**Проблема:** `paywall_opened` эмитится при инициализации, но user-property `current_offer` проставляется только после загрузки каталога товаров (async). При падении каталога пропускается и событие уходит без свойства. Эксперимент `CurrentOfferTrialVSNoTrial` измеряется на выборке с неполными данными и истекает в сентябре.

**Решение:** Параметр события кладётся из уже завершённого `PaywallRemoteConfig.currentOffer` безусловно, в момент эмита `paywall_opened`, без ожидания каталога. Гард обрабатывает имеющиеся данные, а не откладывает событие.

**Исполнитель:** `@compose-feature-expert`

---

### Дефект 3: Сообщение-запрос свыше лимита Layer 3 показывается как общая ошибка, Retry переотправляет то же <!-- docs-leak-scan: reviewed -->

**Проблема:** Layer 2 (`classify_chat_intent`) имеет лимит на текст 500 символов и отказывает с 400 при превышении. Однако при тестировании выяснилось, что люди часто отправляют сообщения свыше лимита Layer 3 `chat_agent`. Они минуют Layer 2 и идят прямо на Layer 3. Там `chat_agent` отказывает с 400, но клиент показывает это как общую ошибку сервера с кнопкой Retry, которая молча повторяет тот же payload. Итог: человек жмёт Retry, и ничего не меняется. <!-- docs-leak-scan: reviewed -->

**Решение (реализовано):**
1. Layer 3 (`chat_agent`) теперь различает переполнение лимита слова в промпте Gemini и возвращает специальный тип `AgentStepResult.InvalidRequest`. <!-- docs-leak-scan: reviewed -->
2. Клиент получает этот результат, показывает «Сообщение слишком длинное» (без числа — лимит серверный и уедет при смене моделей), и **не предлагает повтор**.
3. Кредиты за этот ход не начисляются (`creditsUsed = 0`).
4. Логирование на сервере помечает это как `outcome = "input_rejected"` для диагностики.

**Исполнитель:** Главный агент (CF Layer 3) + тестирование через ревью диффа

---

### Дефект 4: Код 402 склеивает две ошибки (нет кредитов vs незарегистрирован)

**Проблема:** CF функции резервирования кредитов (`reserve_chat_credit`) возвращают `("no_user", None)` когда документ пользователя не существует (незарегистрирован), и `("insufficient", None)` когда кредиты кончились. До этого фикса обе ошибки возвращали 402 с одним текстом `"insufficient credits"`, что приводило к: незарегистрированному показывают пэйвол вместо экрана входа; счётчик 402 в аналитике не различает две причины и не читается как монетизационный сигнал; логирование не могло определить лекс.

**Решение (реализовано, обратносовместимое):**
1. Новая функция `credit_error_response(action)` на основе результата резервирования.
2. HTTP 402 и основное поле `error: "insufficient credits"` остаются (**не меняются**, чтобы не сломать 1.17.x/1.18.x).
3. Добавляется новое поле `reason: "insufficient_credits" | "no_user"` в теле ответа.
4. Серверный лог различает случаи: `logger.warning("credit reservation refused: reason=%s", reason)`.
5. На клиенте (версия 1.19+): if 402 + `reason = "no_user"` → экран входа; если `reason = "insufficient_credits"` → пэйвол.
6. Старый клиент просто игнорирует новое поле и работает как раньше (400 + "insufficient" от старой версии).

**Деплой:** Выполняется в главном checkout после merge (в worktree отсутствует gitignored `prompts_private.py`, деплой без неё увёл бы stub-промпты).

**Исполнитель:** Главный агент (CF Layer 2/3) → подтверждение → merge → деплой

---

## Лог итераций

### Итерация 1 — 2026-08-18 — android-platform-expert
Дефект 1 (фантомные сессии): апгрейд SDK Amplitude 1.22.4 → 1.30.1, перенос пятерых фоновых event-source на `EventOptions(sessionId = -1)` (WorkManager, PushReceiver, ReminderReceiver ×3). Red-first репро: эмулятор + logcat по Amplitude-сигнатурам, подтвердить 0 `SessionProperties` в фоне. Ревью диффа поймало неполноту: ReminderReceiver имеет три вызова в обработке повторов, два из них остались in-session и были доработаны.

### Итерация 2 — 2026-08-18 — compose-feature-expert (дефекты 2)
Дефект 2 (current_offer): логирование свойства перенесено с момента загрузки каталога на момент эмита `paywall_opened`, источник — `PaywallRemoteConfig.currentOffer`. Первоначальный план (вынести лимит в RC, показать сообщение, валидировать) был отвергнут потому что причина оказалась тривиальной — свойство просто не было привязано к событию, не требуется лимит-гейт, клиентская валидация и всё прочее.

### Итерация 3 — 2026-08-18 — главный агент (дефекты 3, 4 + CF)
Дефект 3: план по эксведению лимита в RC и валидации на клиенте был неправильным. Исходный скаут обнаружил, что 81% отказов идут с Layer 3 (`chat_agent`), а не Layer 2, и эскалация на Layer 3 маскирует реальный отказ. Реализовано: новый тип `AgentStepResult.InvalidRequest` в CF с `creditsUsed = 0`, без кнопки Retry клиентом. Дефект 4: добавлена функция `credit_error_response(action)` в CF, различает "no_user" и "insufficient_credits" в теле ответа 402, сохраняя основное поле для обратной совместимости (1.17.x/1.18.x клиенты игнорируют новое поле). Логирование теперь даёт выход: `logger.warning("credit reservation refused: reason=%s", reason)`.

---

## Выводы

**Дефект 1 (фантомные сессии):** Корнем оказался апстрим-баг в Amplitude SDK 1.22.4, где `SESSIONS` не входила в `REQUIRES_ACTIVITY_CALLBACKS`, поэтому SDK не регистрировал lifecycle-колбэки вообще. Два подхода (детект по `importance`, отложенное конструирование) были опровергнуты вживую до этой задачи и записаны в backlog. Решением стал апгрейд SDK до 1.30.1 (где баг закрыт) плюс явное отключение сессий в фоне через `sessionId = -1` для пяти источников.

**Дефект 2 (current_offer):** Решение оказалось точечным — привязать логирование свойства к моменту эмита события, а не к моменту загрузки каталога. Первоначальный план усложнял архитектуру без необходимости.

**Дефект 3 (длинное сообщение):** Исходный скаут видел отказ Layer 2 (`classify_chat_intent`) на 500-символьном лимите, но аудит call-site'ов обнаружил, что основной поток идёт через Layer 3 (`chat_agent`) с 12000-символьным лимитом. Проблема оказалась не в Layer 2, а в том, что 400-отказ Layer 3 показывался клиентом как общая ошибка с кнопкой Retry, которая переотправляла бесполезный запрос. Решение: новый `AgentStepResult.InvalidRequest` с явным сообщением, без Retry и без зарядки кредитов.

**Дефект 4 (код 402):** Три вызова резервирования кредитов вслед друг за другом приводили к тому, что отказ за неверной причине (user не найден vs нет кредитов) выглядел как одна ошибка. Обратная совместимость с 1.17.x/1.18.x потребовала оставить `error: "insufficient credits"` неизменным; различие привнесено новым полем `reason` в теле ответа.

**Ход работы:** Первоначальный план дефектов 2 и 3 был отвергнут на основе аудита реальных данных — почти все отказы дефекта 3 идут с другого слоя, а дефект 2 решается одной строкой, а не конфигурацией. Ревью диффа дефекта 1 выявило неполноту (три вызова в ReminderReceiver остались в сессии) и потребовало доработки.

**Deferred work:** Клиентская половина дефекта 4 (обработка нового поля `reason` и навигация на экран входа) будет реализована в следующей версии. Отложено в `docs/todos/2026-08-18-client-handle-402-no-user-reason.md`.

---

## Предложения по улучшению агентов

**Для `@knowledge-scout`:** Финальный аудит кэша SDK вскрыл не obvious-место, где фоновые события чеканили сессии. Поиск и валидация логов жизненного цикла в новых версий API улучшит первоначальную гипотезу. 

**Для ревью-слоя `@doc-writer`:** План документа не был полностью расходится с реализацией на этапе старта — до первой итерации скаута оба дефекта 2 и 3 описывали разные точки нанесения фикса (дефект 2 — вход события, дефект 3 — Layer 2, а не Layer 3). Более скрупулёзный скан call-site'ов на этапе планирования помог бы.

**Для главного:** Отказ от исходных планов дефектов 2 и 3 был разумен после аудита, но требовал бы явного нейм-лока на вариант, который не внедрять, в финальном документе. Сейчас есть риск, что во второй итерации кто-нибудь вернёт отвергнутый подход.
