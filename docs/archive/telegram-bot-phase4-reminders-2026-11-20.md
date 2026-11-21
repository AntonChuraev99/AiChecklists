# Telegram bot — phase 4 (reminders as Telegram messages)

**Статус:** Done
**Дата старта:** 2026-11-20
**Start SHA:** 83f437ce
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** firebase-functions, telegram-bot, composeApp (telegram screen, notification), feature/checklist (sync), core/designsystem (strings), firestore.rules

## Цель (продуктовая)

Reminders arrive as Telegram messages with «Done» / «In 1 hour» / «Open list», including for web-only
users who have no reminders today. Owner decisions (2026-11-20): channel per user push / Telegram /
both; after linking it stays push and the bot offers to switch; changeable in the bot (`/reminders`)
and on the app's Telegram screen. Phase 1/3 design: `docs/archive/telegram-bot-phase1-2026-11-19.md`;
idea and constraints: `docs/backlog/2026-08-03-telegram-bot-reminders.md` (не проверено — файл есть только в основном checkout, git его не отслеживает), approach A.

## Технический план

Today reminders are scheduled on the device only (AlarmManager); the server knows no due times and no
time zone. Plan:

1. **Profile fields** `users/{google_uid}`: `timezone` (IANA; the app writes the device zone, the bot's <!-- docs-leak-scan: reviewed — list number, not a metric -->
   `/timezone` is the fallback) and `reminder_channel` (`push` | `telegram` | `both`, absent = push).
2. **Due index** `telegram_reminders/{rid}` (server-only), recomputed by Firestore triggers on
   `users/{uid}/checklists/{id}` and on `users/{uid}` (only when `timezone` / `reminder_channel`
   changed) and on link / unlink. Occurrences are computed on the server from rule + time of day +
   zone (`zoneinfo` + `tzdata` dependency), not by replaying the device's `repeatNextAt`.
3. **Sending**: the worker's cron (every minute) calls `claim_due_reminders` (claim before send =
   never twice) and delivers through the chat's Durable Object with inline buttons.
4. **App**: writes the zone; channel choice on the Telegram screen; Android does not post the
   notification when the channel is `telegram` but keeps all bookkeeping; checklist-level reminder
   edits get marked for sync (today they never reach the cloud).

The worker part lands after Phase 2 is merged (both edit `telegram-bot/src/chat.ts`).

## Лог итераций

### Итерация 1 — 2026-11-20 — design-expert: выбор канала на экране Telegram → `docs/designs/telegram-screen-2026-11-19/reminders-addendum.md`
Радиогруппа в стиле «Настройки → Тема»: 3 строки на Android (телефон / Telegram / оба), 2 на вебе («Присылать в Telegram» сохраняет `both`, а не `telegram`, — настройка одна на пользователя, `telegram` молча заглушил бы его телефон). Требования к серверу, переданные Python-исполнителю: отвязка возвращает канал в `push`; `set_reminder_channel` принимает часовой пояс устройства в том же запросе.

### Итерация 2 — 2026-11-20 — general-purpose (Python): индекс `telegram_reminders`, 2 Firestore-триггера, `claim_due_reminders` / `snooze_reminder`, `set_reminder_channel`, отвязка → push → после правок main: Python 213 passed, `test_main.py` те же 5 падений
Отступления от исходного плана: claim не удаляет запись, а ставит `next_fire_at = None` («надгробие») — иначе веб, который не чистит `reminderAt`, и любая правка чек-листа в течение 5 минут воскресили бы отправленное напоминание; `carry_at` держит наступившее, но не забранное срабатывание, когда телефон уже сдвинул повтор; claim перечитывает связку и канал. Главный откатил добавленное поле `offer_reminders` в ответе `consume_app_token` вместе с правкой двух существующих assert'ов `test_telegram_link.py`: оффер после `/start` воркер решает сам по `users/{google_uid}.reminder_channel`.
### Итерация 3 — 2026-11-20 — feature-expert: канал на экране Telegram (состояние, web-маппинг, last-tap-wins, откат), кэш канала в DataStore, зона устройства в профиль (Android), фикс синхронизации напоминаний чек-листа (red: `expected:<1> but was:<0>`) → host-тесты зелёные
Веб-часть записи зоны → NEEDS_DELEGATION wasmjs-expert (`__firestoreMergeDoc` + зона из `Intl`; на wasm kotlinx-datetime отдаёт `SYSTEM`). Найдено при приёме: `touchForSync` в фоновых путях (`advanceRepeatSchedule`, очистка сработавшего разового) — путь потери данных: строка `PENDING_UPLOAD` пропускает удалённую версию при слиянии (`SyncRepositoryImpl.kt:457-472`) и затирает правку из веба или бота; возвращено feature-expert.
### Итерация 4 — 2026-11-20 — feature-expert: фоновые записи напоминаний без `touchForSync` → `ChecklistRepositoryImplTest` 18/18, `RecoverRecurringRemindersUseCaseTest` 8/8, `:composeApp:compileKotlinWasmJs` и `:androidApp:assembleDebug` ok
`advanceRepeatSchedule` больше не помечает строку; новые `clearFiredReminder` и `clearFinishedRepeatSchedule` — только локально (у `clearRepeatSchedule` нашлись фоновые вызовы: `ReminderReceiver.kt:139`, `RecoverRecurringRemindersUseCase.kt:53`). Вызовы в `ReminderReceiver.kt:73` и `:139` переключает android-platform-expert. Тот же класс, существовавший до задачи: `resetDefaultFillChecks` из будильника (`ReminderReceiver.kt:131`) пишет заполнение и помечает родителя — не тронут, нужен продуктовый выбор (сброс на сервере?).
### Итерация 5 — 2026-11-20 — compose-expert: секция «Напоминания» на экране Telegram по аддендуму → wasm/Android сборки ok, Roborazzi 21 новый кадр, старые эталоны экрана побайтно те же
Паттерн радио-строк из «Настройки → Тема» / `ActivationReminderSheet`, без нового компонента. Снэкбар с действием — `actionOnNewLine = true` (при 320 × 2.0 RU текст ломался посреди слова — источник: Roborazzi-кадры `RemError_ru_320_fs2.0_light.png`; это меняет и снэкбар ошибки подключения). Не проверено: TalkBack на `liveRegion` слота состояния.
### Итерация 6 — 2026-11-20 — android-platform-expert: канал `telegram` глушит уведомление в `ReminderReceiver`, служебная часть без изменений → `ReminderChannelGateTest` 5/5, `FullScreenIntentGateTest` 4/4, Gradle-задача `:androidApp:assembleDebug` ok (источник: вывод Gradle в этой сессии)
Решение — `shouldPostReminderNotification(channel)`; канал читается после очистки, сдвига и перевзвода. Фоновые вызовы переключены на `clearFiredReminder` / `clearFinishedRepeatSchedule`. Тот же класс, существовал до задачи: обработчики пункта пишут через `updateFill` (`PENDING_UPLOAD` + `touchForSync`) — в backlog вместе с `resetDefaultFillChecks`.
### Итерация 7 — 2026-11-20 — wasmjs-expert: зона на вебе — `__firestoreMergeDoc` (`setDoc` с `merge: true`) + `BrowserDeviceTimeZoneProvider` из `Intl` → `compileKotlinWasmJs` ok, на :9090 мост есть, зона IANA, без входа запись отвечает отказом прав
`Etc/Unknown` (так Chromium отвечает, когда не знает зону) отсекается — сервер ответил бы 400. Реальная запись под Google-аккаунтом не проверена.
### Итерация 8 — 2026-11-20 — general-purpose (worker): cron раз в минуту → `claim_due_reminders` → доставка через DO, кнопки `rd:`/`rs:`/`ro:`, `/reminders`, оффер после `/start` → telegram-bot 214/214, mcp-server 82/82, dry-run ok
Кнопки `/reminders` несут суффикс `:c` (`rc:t:c`), а точные `rc:t/b/p` — нажатие на оффер (Python шлёт их для обоих источников), чтобы аналитика различала источник. Одна попытка `sendMessage` на напоминание: claim уже продвинул запись.
### Итерация 9 — 2026-11-20 — task-gate full: ревью Standards + Spec → 3 подтверждённых дефекта + каталог чата
Standards: в каталоге функций AI-чата (`firebase-functions/prompts_private.py` — не проверено guard'ом: файл gitignored) не было напоминаний в Telegram — главный дописал пункт 26 RU/EN в копии worktree (перед деплоем перенести в основной checkout). Spec: (1) кэш канала на телефоне устаревал после `/unlink` или «телефон» в боте — напоминания терялись в обоих каналах → data-push FCM устройствам при смене `reminder_channel`; (2) перенос (`carry`) слал напоминание по уже отмеченному или удалённому пункту; (3) бухгалтерия будильника пункта через `updateFill` ставила строку в выгрузку и при открытии приложения откатывала «Готово», нажатое в Telegram → локальная запись. Попутно `snooze_reminder` при выключенном канале → отказ, а не молчаливо выброшенная запись.

### Итерация 10 — 2026-11-20 — general-purpose ×2, feature-expert, android-platform-expert: фиксы ревью → бот 216, mcp-server 82, Python 223, Kotlin host-тесты + wasm + `:androidApp:assembleDebug` ok (источник: вывод Gradle в этой сессии)
FCM data `{"type":"reminder_channel","channel":…}` при смене канала (триггер профиля) → `ReminderChannelPush.kt` пишет кэш; `carry` только для живого неотмеченного пункта с будильником; `snooze_reminder` при выключенном канале → 409 `channel_off`; бухгалтерия пункта — `updateFillLocalOnlyFromAlarm`.
### Итерация 11 — 2026-11-21 — главный: деплой бэкенда (`firestore.rules`, 2 триггера, `telegram_account`, `telegram_bot_internal`, `chat_completion`, `chat_agent`) + general-purpose: флаг в `deploy.sh`
Ловушка деплоя: `on_user_settings_written_tg` (выложен ещё 11-20) падал на каждом событии с `TypeError: on_user_settings_written_tg() takes 1 positional argument but 2 were given` (источник: `gcloud functions logs read`). Причина: `gcloud functions deploy` не ставит `FUNCTION_SIGNATURE_TYPE=cloudevent` для триггеров на `firestore_fn`, а Firebase CLI ставит (у `on_rc_event_created` переменная есть), и без неё functions_framework вызывает функцию в legacy-сигнатуре `(data, context)`. Оба триггера выложены заново с `--update-env-vars=FUNCTION_SIGNATURE_TYPE=cloudevent`, после этого вызовы проходят без исключений. В `deploy.sh` флаг добавлен всем трём Firestore-триггерам. Смоук `on_checklist_written_tg`: служебный документ `users/zz-smoke-tg-phase4/checklists/smoke1` создан и удалён, триггер ответил 200 (источник: журнал запросов Cloud Run). <!-- docs-leak-scan: reviewed — HTTP status codes, not metrics -->

### Итерация 12 — 2026-11-21 — главный: воркер `gisti-telegram` (cron `* * * * *`) + `setup.mjs` → первый запуск cron через ~9 мин после деплоя, `claim_due_reminders` отвечает 200 каждую минуту
Расписание создано в 06:51:24 (источник: Cloudflare API `workers/scripts/gisti-telegram/schedules`), но первый вызов `scheduled` случился только в 07:00:38. Это задержка распространения cron в Cloudflare, а не дефект. Пустой tail в первые минуты после деплоя ничего не доказывает. `setup.mjs`: webhook, меню команд EN/RU/HI и описания — все вызовы `ok`.

## Выводы

- **Сделано:** напоминания приходят сообщением в Telegram с «Готово» / «Через час» / «Открыть список». Сервер считает срабатывания сам в IANA-зоне пользователя (индекс `telegram_reminders`, два Firestore-триггера, claim до отправки), cron воркера раз в минуту. Канал «телефон / Telegram / оба» выбирается на экране Telegram и в `/reminders`, после привязки бот предлагает переключить. Android при канале Telegram молчит, но ведёт бухгалтерию будильника. Зона пишется в профиль с Android и веба.
- **Выяснили:** запись из будильника, помеченная к выгрузке, затирает правки из веба и бота — при слиянии строка `PENDING_UPLOAD` пропускает удалённую версию; поэтому вся фоновая бухгалтерия напоминаний теперь только локальная. Кэш канала на устройстве нужно обновлять с сервера (data-push), иначе смена канала в боте глушит напоминания везде.
- **Выложено 2026-11-21:** `firestore.rules`, 6 функций, воркер с cron, `setup.mjs`. Пункт 26 каталога перенесён в `prompts_private.py` основного checkout (не проверено — файл gitignored). Ловушка выкладки — `FUNCTION_SIGNATURE_TYPE=cloudevent` для триггеров через gcloud (итерация 11).
- **Осталось:** Android-релиз и сверка Data Safety — `docs/todos/2026-11-19-telegram-android-release-gates.md` (пункт 4); «сбросить отметки» повтора — `docs/backlog/2026-11-20-alarm-writes-mark-rows-dirty.md`.
