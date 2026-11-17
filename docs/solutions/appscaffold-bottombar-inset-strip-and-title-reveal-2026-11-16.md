---
title: "AppScaffold: полоса навбара под bottomBar не красится; название в toolbar при скролле"
summary: "Слот bottomBar обёрнут в отступ ime∪navigationBars, полосу под ним никто не красит — `bottomBarInsetColor`. Название в toolbar — `titleRevealedOnScroll` по измеренной кромке заголовка."
date: 2026-11-16
type: pattern
modules: [core/designsystem, feature/home]
keywords: [theme, edge-to-edge, navigationBars, AppScaffold, bottomBarInsetColor, titleRevealedOnScroll, derivedStateOf, AppChecklistHeaderCard, layout-debug]
project: Checklists
---

# AppScaffold: полоса навбара под bottomBar и название в toolbar

**Суть:** плашка в `bottomBar` кончается над навбаром, а полоса отступа под ней цвета страницы —
передай `AppScaffold(bottomBarInsetColor = <цвет плашки>)`. Название в toolbar по скроллу —
`AppScaffold(titleRevealedOnScroll = true)` + порог от измеренной нижней кромки названия.

## Проблема / Контекст

Правки владельца из окна layout-debug (Pixel 9). №12: «нижняя системная часть другим цветом» —
серая плашка «Add a task» на экране чек-листа заканчивалась над навбаром, под ней виднелась
страница. Шторка чата при этом красила полосу правильно (проверено кадрами с поданными
нав-инсетами). №10: название в toolbar переносилось на 3–4 строки и появлялось позже, чем
заголовок шапки скрывался.

## Решение

- `AppScaffold` оборачивает слот `bottomBar` в `windowInsetsPadding(ime ∪ navigationBars)`;
  новый параметр `bottomBarInsetColor` (по умолчанию `Unspecified`) красит только полосу ниже
  измеренной высоты бара — скруглённые плечи плашки остаются цвета страницы, высота читается
  при отрисовке, клавиатура ничего не перекомпоновывает. `ChecklistDetailScreen` передаёт
  `AppSurface.bottomChrome()`, когда плашка показана.
- `AppScaffold(titleRevealedOnScroll = true)`: `maxLines = 1` + `Ellipsis` + fade. Порог —
  `AppChecklistHeaderCard(onTitleBottomChanged)` сообщает низ текста названия; показ, когда
  смещение списка ≥ верхний отступ + эта кромка. Скролл читается только в `derivedStateOf`.

## Почему именно так

Фикс цвета внутри компонента дока дал бы тёмные клинья у хостов со скримом — красит хост
слота. Порог от измеренного текста, а не числом, учитывает fontScale, локаль и перенос.

## Связанные файлы

- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/containers/AppScaffold.kt`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/gisti/AppChecklistHeaderCard.kt`
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/detail/ChecklistDetailScreen.kt`
- Задача: `docs/archive/layout-debug-design-fixes-2026-11-16.md`
