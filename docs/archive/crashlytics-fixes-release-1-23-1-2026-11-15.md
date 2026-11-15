# Краши 1.23.0 и патч-релиз 1.23.1

⚠ INIT пропущен — документ реконструирован на COMPLETE

**Статус:** Done
**Дата старта:** 2026-11-15
**Start SHA:** 648dd569
**Project:** Checklists
**Тип:** bug-fix
**Затронутые модули:** feature/checklist, composeApp (androidMain), androidApp

## Цель (продуктовая)

Убрать FATAL на старте в 1.23.0, снять шум non-fatal в Crashlytics, выпустить 1.23.1 (vc92).

## Технический план

1. Red-репро дубля ключа → фикс в `ChecklistRepositoryImpl.buildRemindersInRange`.
2. `InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED` — в ожидаемые коды `AppUpdateController`.
3. `CancellationException` после `registerPushToken` — пробрасывать, не логировать.
4. versionCode 92 / 1.23.1, без поста в Update Feed (прецедент 1.22.1, 1.22.2).

## Лог итераций

### Итерация 1 — 2026-11-15 — test-expert: два red-теста на дубль → падают 2 вместо 1
### Итерация 2 — 2026-11-15 — android-platform-expert: -6 в ожидаемые, rethrow отмены, bump vc92 → compile + host-тесты зелёные
### Итерация 3 — 2026-11-15 — feature-expert: дедуп в buildRemindersInRange, побеждает повтор → 16/16 зелёные

## Выводы

- Корень FATAL: две независимые ветки (одноразовое и развёртка повтора) эмитят один момент; решение — [solution](../solutions/reminder-oneshot-recurring-duplicate-lazy-key-2026-11-15.md).
- `libsqliteJni.so` FATAL не трогали — device-side, последняя версия 1.21.4, фикс-fallback уже в 1.22.2.
- Сетевые non-fatal (DNS, socket abort, timeout) — шум на стороне сети пользователя, не правились.
- Проверка после релиза: в issue Crashlytics `159e2535…` (дубль ключа), `3fb7e2d3…` (app-update -6), `e3b37a52…` (Job was cancelled) нет событий на 1.23.1.

## Предложения по улучшению агентов
