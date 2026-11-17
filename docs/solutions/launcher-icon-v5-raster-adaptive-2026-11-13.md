---
title: "Launcher-иконка v5: растровый background, прозрачный foreground, векторный monochrome"
summary: "Полуобъёмный арт иконки вектором не повторить — весь арт лежит растром в background adaptive icon, foreground прозрачный, themed icon — отдельный вектор-силуэт."
date: 2026-11-13
type: pattern
modules: [androidApp, composeApp]
keywords: [image, launcher-icon, adaptive-icon, ic_launcher, ic_launcher_background, ic_launcher_monochrome, themed-icon, splash, mipmap]
project: Checklists
---

# Launcher-иконка v5 — как устроена и как пересобрать

**Суть:** весь растровый арт — в `ic_launcher_background` (webp по плотностям, глиф в
круге 66 dp), foreground прозрачный, themed icon — `ic_launcher_monochrome.xml`.
Новый арт — пересобрать скриптами (раздел «Пересборка»), не руками.

## Проблема / Контекст

Владелец выбрал вариант v5 из раунда иконок 2026-11-13 (сгенерирован через Codex,
gpt-image-2): полуобъёмная белая плитка-чекбокс, повёрнутая примерно на −9°, золотая
галочка, градиент `#2196F3→#5C6BC0`. Старая иконка была векторной (фон + foreground
с кольцом, диском и галочкой), и monochrome ссылался на тот же foreground.

## Решение

- `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml`:
  background `@drawable/ic_launcher_background`, foreground `@drawable/ic_launcher_foreground`,
  monochrome `@drawable/ic_launcher_monochrome`.
- `composeApp/src/androidMain/res/drawable-*dpi/ic_launcher_background.webp` — растр слоя
  108 dp (108/162/216/324/432 px). Векторный `drawable/ic_launcher_background.xml` удалён:
  с ним имя ресурса конфликтовало бы с растром.
- `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml` — прозрачный
  прямоугольник 108 dp. Слой оставлен непустым.
- `composeApp/src/androidMain/res/drawable/ic_launcher_monochrome.xml` — силуэт: повёрнутая
  плитка, галочка вырезом с зазором, всё внутри круга 33 dp.
- `androidApp/src/main/res/mipmap-*/ic_launcher.webp` и `ic_launcher_round.webp` — legacy
  для API ниже 26: 48/72/96/144/192 px.
- `composeApp/src/androidMain/ic_launcher-playstore.png` — 512×512 PNG32 v5.

Глиф ужат так, что самая дальняя его точка лежит на 31.6 dp от центра при радиусе
безопасной зоны 33 dp (источник: отчёт `@android-platform-expert`, расчёт в `build.py`).
Подложка вне ужатого арта — модель собственного фона арта (полином плюс центральное
свечение), шов вплавлен по кольцу 38–41.6 dp, за краем глифа.

## Почему именно так

- Полуобъём вектором не повторить, а растр в foreground требует вырезать глиф из фона
  без альфы. Арт целиком в background сохраняет вид; цена — нет параллакса глифа.
- Themed icon красится из monochrome-слоя, поэтому силуэт — отдельный вектор.
- Splash Android 12+ рисует adaptive icon целиком с фоном, пока фон не простой и не
  похож на фон окна (источник: developer.android.com/develop/ui/views/launch/splash-screen,
  AOSP SystemUI) — прозрачный foreground splash не опустошит.

## Пересборка при новом арте

Скрипты и превью — вне репозитория:
`~/StudioProjects/gisti-store-assets/icon-variants-2026-11-13/launcher-build/`
(не проверено — вне репозитория):

```bash
python -I fit.py <art.png> <fit.json>      # замер глифа, геометрия монохрома (не проверено — вне репозитория)
python -I build.py <art.png> <fit.json> <worktree> <outdir> <pylib-с-resvg_py>   # (не проверено — вне репозитория)
```

Скрипт сборки пишет background-растры, монохром, legacy mipmap и превью под масками.
Исходники арта и промпты лежат в соседних каталогах ассетов (не проверено — вне репозитория). Варианты иконки делает скилл
`app-icon` (`~/.claude/skills/app-icon`).

## Связанные файлы

- `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `composeApp/src/androidMain/res/drawable/ic_launcher_monochrome.xml`
- `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml`
