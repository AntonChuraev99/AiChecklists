# Новая launcher-иконка v5

**Статус:** Done
**Дата старта:** 2026-11-13
**Start SHA:** 30678c3f
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** androidApp, composeApp

⚠ INIT пропущен — документ реконструирован на COMPLETE, лог итераций неполный.

## Цель (продуктовая)

Иконка на полке Play и на рабочем столе почти не отличалась от Any.do и Checklist.com
(синий квадрат, белый диск, синяя галочка). Владелец выбрал v5 из шести концептов
раунда 2026-11-13 (выбор — страница design-picker, проверка на Pixel 9 мокапом
рабочего стола в натуральную величину). Внедрить в приложение; тест иконки в листинге
Play — отдельно, ручными шагами владельца.

## Технический план

Adaptive icon: растровый background с глифом в безопасной зоне, прозрачный foreground,
векторный monochrome, legacy mipmap, Play 512. Web favicon и лендинг — вне задачи.

## Лог итераций

### Итерация 1 — 2026-11-13 — android-platform-expert: слои adaptive icon из арта v5, legacy mipmap, playstore png → `:androidApp:assembleDebug` зелёный, превью под тремя масками и монохром проверены картинкой

## Выводы

- Решение и пересборка — `docs/solutions/launcher-icon-v5-raster-adaptive-2026-11-13.md`.
- Splash Android 12+ при прозрачном foreground не пустой: система рисует adaptive icon
  целиком (вывод ревью по документации и AOSP). На устройстве не проверены splash и
  themed icon.
- Не сделано: web favicon (`composeApp/src/wasmJsMain/resources/favicon.svg`,
  `landing/favicon.svg`) остался старым; тест иконки в Play — ручной шаг владельца, не
  раньше 2026-11-19 (окно замера CTR скриншотов идёт до 2026-11-18).

## Предложения по улучшению агентов

- `@android-platform-expert` сам поднял риск системного splash при переносе арта в
  background — полезно закрепить в агенте как чек-пункт задач с adaptive icon.
