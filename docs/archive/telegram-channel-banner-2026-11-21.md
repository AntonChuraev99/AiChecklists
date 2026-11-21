# Telegram: канал в шторке напоминания + промо-баннер на Inbox

**Статус:** Done
**Дата:** 2026-11-21
**Ветка:** telegram-channel-banner
**Затронутые модули:** core/datastore, core/common (analytics), feature/checklist (ReminderSheet), feature/home (Inbox, InlineNudgeCard), feature/create, composeApp (App.kt, telegram), core/designsystem (strings)

## Запрос

Владелец: выбор «присылать в Telegram через бота» в настройке напоминаний + баннер про Telegram на главном экране, который листается вместе с контентом и иногда показывается.

## Решения владельца (2026-11-21)

- Выбор канала — в шторке напоминания (`ReminderSheet`), меняет общий канал пользователя; экран Telegram остаётся.
- Баннер — только Inbox, в слоте баннера «уведомления выключены» (один за раз); видят и гости; × → снова через 14 дней, максимум 3 цикла; авто-закрытие цикла после 7 дней показов; после привязки — исчезает навсегда. <!-- docs-leak-scan: reviewed — product rule, not a metric -->
- Текст баннера упоминает AI-чат бота; Stars, оплата, премиум, цены — нигде.

## Технический план

DESIGN_SPEC: `docs/designs/telegram-channel-banner-2026-11-21/spec.md` (§5 контракт, §6 аналитика, §8 матрица скриншотов, §10 handoff).
Порядок: `@core-expert` (общий кэш `TelegramLinkKnowledge` + `setReminderChannel` для фич + `retired`) → `@feature-expert` (`shouldShowTelegramPromo`, Inbox VM, счётчики в `HintsRepository`, `ReminderSheetState.delivery`, контроллер канала, wiring хостов, навигация, аналитика) → `@compose-expert` (`InlineNudgeCard`, слот баннера, `ReminderDeliveryRow`, превью, скриншоты).

## Выводы

- Фичи не видят `composeApp`, поэтому статус привязки Telegram для них — отдельный персистентный снимок в core (`TelegramReminderLink`, `TelegramLinkCacheRepository`) с единственным писателем `TelegramLinkCacheWriter`; data-only FCM смены канала тоже идёт через него.
- Счётчики «дней показа» нельзя вести по оценке во ViewModel: VM живёт в бэкстеке и в фоне, часы тикают. День засчитывается только по сигналу UI `OnTelegramPromoSlotOnScreen` (шапка Inbox в RESUMED) — поймано ревью гейта.
- Не проверено на устройстве: шторка → «Подключить» → Back (по коду `ModalBottomSheet` уходит вместе с хостом, на Expanded и web — тоже), живое окно шторки и wasm canvas.
- Вне задачи замечено compose-expert: «Никогд/а» ломает слово в табе «Повтор» при 320×2.0 RU; «Add to Google Calendar» не переведено в RU-шторке.

## Лог итераций

### Итерация 1 — 2026-11-21 — design-expert: DESIGN_SPEC обеих поверхностей + прототип 320/1024, светлая/тёмная → спека + 5 кадров в `frames/`; открытые вопросы Q1–Q4 закрыты владельцем
### Итерация 2 — 2026-11-21 — core-expert: `TelegramReminderLink` (core/common/api) + `TelegramLinkCacheRepository` (DataStore, снимок uid+linked+channel), писатель `TelegramLinkCacheWriter`, `retired` в `HintsRepository`, сохранение канала в application scope → 81 тест зелёный, assembleDebug + wasm ok (источник: вывод Gradle-задачи :androidApp:assembleDebug у субагента). Отступление от спеки: `ReminderChannelSaveResult` (Saved/Failed(reason)) вместо `Result`; SignedOut вычисляется из auth, не пишется
### Итерация 3 — 2026-11-21 — core-expert: data-only FCM смены канала обновляет и снимок (`TelegramLinkCacheWriter.onChannelPushed`) → red→green `channelPushed_updatesLinkedSnapshotOfSameAccount_only`, 50/50; без восстановленной сессии снимок догоняет `status` на foreground
### Итерация 4 — 2026-11-21 — feature-expert: `shouldShowTelegramPromo` + `TelegramPromoPrefsRepository`, Inbox VM/интенты, `ReminderDeliveryRowUi` + общий `ReminderDeliveryChannelController` через `LocalReminderDeliveryHost` (6 хостов), `AppNavRoute.Telegram(source)`, аналитика §6, строки §9 → 11/11 + 21/21 зелёные, assembleDebug + wasm ok (источник: вывод Gradle у субагента); полный прогон модулей прерван OOM. Отступления: счётчики промо в отдельном репозитории (не `HintsRepository`), колбэки группой `ReminderDeliveryCallbacks`, правило 9 срабатывает на следующий день
### Итерация 5 — 2026-11-21 — compose-expert: `ReminderDeliveryRow` + `ReminderSheetContent`, `InlineNudgeCard` (+onClick, actionStyle, hideBodyAtLargeFont, dismissTestTag), `TelegramPromoBanner` в `listHeader` Inbox → 50 Roborazzi-кадров просмотрены, голдены InlineNudge/NotificationsOff без изменений; `testAndroidHostTest` 6 модулей 1799/0, assembleDebug + wasm ok (источник: вывод Gradle-задачи :androidApp:assembleDebug у субагента)
### Итерация 6 — 2026-11-21 — feature-expert: фиксы ревью гейта — день показа/авто-закрытие только при `OnTelegramPromoSlotOnScreen` (LifecycleResumeEffect шапки Inbox, чистая `telegramPromoBookkeeping`), лог в `HintsRepositoryImpl`, `tg_reminder_channel_set_failed` шлёт `sending`, тап по выбранному радио снимает ошибку, нет повтора при ином ответе сервера; surface шторки создания пункта оставлен `draft` → RED 2/25 и 3/14 → green; home 804, checklist 345, composeApp 559, datastore 15 — 0 падений; assembleDebug + wasm ok (источник: вывод Gradle-задачи :androidApp:assembleDebug у субагента)
