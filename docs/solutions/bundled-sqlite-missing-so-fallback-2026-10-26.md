---
title: "libsqliteJni.so не найден: fallback на системный SQLite"
summary: "BundledSQLiteDriver грузит .so лениво в <clinit> на первом open() и роняет процесс; библиотека пробуется заранее, при неудаче — AndroidSQLiteDriver + non-fatal."
date: 2026-10-26
type: bug-fix
modules: [core/common/api]
keywords: [crash, database, UnsatisfiedLinkError, libsqliteJni, BundledSQLiteDriver, AndroidSQLiteDriver, room3, SqliteDriverSelector, split-apk, sqlite]
project: Checklists
---

# libsqliteJni.so не найден: fallback на системный SQLite

**Суть:** драйвер Room на Android выбирает `SqliteDriverSelector.select()`: проба
`System.loadLibrary("sqliteJni")`, удалась — `BundledSQLiteDriver`, нет — `AndroidSQLiteDriver` и
non-fatal с тегом `Database`. Новый SQL обязан работать на SQLite 3.9 (minSdk 24).

## Проблема / Контекст

Crashlytics `9775ecb7`, FATAL
`UnsatisfiedLinkError: dlopen failed: library "libsqliteJni.so" not found` из
`BundledSQLiteDriver$NativeLibraryObject.<clinit>` на первом `open()` в Room, в фоне. Единичные
устройства на всех версиях с 1.15.0. `.so` приходит ABI-split'ом; установка без него (sideload,
клон-окружение) библиотеку не находит (не проверено — вывод по признакам устройства, не репро).

## Решение

`DatabaseBuilder.android.kt` → `.setDriver(SqliteDriverSelector.select())`. Селектор:
`runCatching(loadBundledLibrary)` (ловит и `Error`) → `onFallback(cause)` + `AndroidSQLiteDriver()`.
Лог — колбэк `onDriverFallback` в `getDatabaseBuilder`; `ChecklistDataModule` пробрасывает его в
`AppLogger.error` (тег `Database`). Зависимость `sqlite3-framework` в каталоге. Тест: `SqliteDriverSelectorTest`.

## Почему именно так

- `try/catch` вокруг `setDriver(...)` не видит ошибку: `.so` грузится в `<clinit>` при `open()`.
- Ловить на `open()` нельзя: упавший static initializer ломает класс до конца процесса; проба делает
  тот же `loadLibrary` сама, на здоровом устройстве повтор — no-op.
- `AndroidSQLiteDriver` — дефолт Room 3 на Android (источник: байткод room3-runtime-android 3.0.3).
- Отвергнуты ABI-split off и legacy packaging: не лечат все причины. Логгер колбэком, а не Koin в
  api-модуле (первая версия тянула `koin-core` в `core/common/api` — убрано по ревью).

## Связанные файлы

- `core/common/api/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/core/common/api/SqliteDriverSelector.kt`
- `core/common/api/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/core/common/api/DatabaseBuilder.android.kt`
