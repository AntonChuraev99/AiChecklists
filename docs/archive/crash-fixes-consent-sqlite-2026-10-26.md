# Краш-фиксы vc90: ConsentManager ANR и libsqliteJni.so

**Статус:** Done
**Дата старта:** 2026-10-26
**Start SHA:** a1f45af15dee9cb02b4c684d190fc18ee4d1e035
**Project:** Checklists
**Тип:** bug-fix
**Затронутые модули:** composeApp, core/common/api

⚠ INIT пропущен — документ реконструирован на COMPLETE.

## Цель (продуктовая)

Убрать два дефекта, найденных разбором Crashlytics и Play vitals 2026-10-26: ANR при старте в
определении региона согласия и FATAL при открытии базы на установках без `libsqliteJni.so`.

## Технический план

1. `ConsentManager`: telephony с main thread на IO с кешем; fallback через пустые строки; red-тест.
2. `DatabaseBuilder.android.kt`: проба `sqliteJni`, fallback на `AndroidSQLiteDriver`; SQL-аудит на 3.9.

## Лог итераций

### Итерация 1 — 2026-10-26 — android-platform-expert: ConsentManager резолв на IO + EeaCountryResolution → red 3/6, green 11/11, debug-сборка ok (источник: отчёт специалиста)
### Итерация 2 — 2026-10-26 — android-platform-expert: SqliteDriverSelector + SQL-аудит (совместим с 3.9) → red 1/2, green 2/2, debug и release сборки ok (источник: отчёт специалиста)
### Итерация 3 — 2026-10-26 — ревью диффа (gate 2.3b): вечный кеш региона ломал диалог при смене страны, фоновый резолв стартовал до Koin, Koin в api-модуле → правки consent и колбэк onDriverFallback вместо Koin

## Выводы

- Оба фикса закрыты тестами на логику выбора; на устройстве fallback-пути не воспроизводились.
- Попутно найден дефект сильнее ANR: EEA-устройство без SIM получало consent GRANTED без диалога.
- Ревью гейта поймало регрессию первой версии: регион кешировался навсегда, и переезд в EEA не
  показывал диалог. Теперь резолв на каждом старте, кеш — только для синхронного решения.
- Остаётся: проверка fallback-путей на устройстве; iOS actual `getDatabaseBuilder` не компилировался
  (Windows).
- Постоянные документы: `docs/solutions/consent-eea-telephony-anr-and-empty-iso-2026-10-26.md`,
  `docs/solutions/bundled-sqlite-missing-so-fallback-2026-10-26.md`.

## Предложения по улучшению агентов

- android-platform-expert: оба раза сам сверил поведение SDK по исходнику/байткоду (`""` из
  `getSimCountryIso`, ленивый `<clinit>` в sqlite-bundled) вместо доверия брифу — паттерн рабочий,
  менять нечего.
