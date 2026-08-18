**Статус:** Done
**Дата:** 2026-06-13
**Start SHA:** c9f9eae7
**Тип:** Feature (UI / cross-promotion)
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** `composeApp` (navigation/drawer), `core/designsystem` (strings + drawable)

## Цель

Добавить в боковое меню (navigation drawer) кросс-промо store-badge баннер,
показывающий пользователю «другую» платформу:

- **Android** → «Open web version» → `https://gisti-ai.com/`
- **Web (wasmJs)** → «Get it on Google Play» → Google Play listing
- **iOS** (не в проде) / прочее → дефолт «web version»

Баннер размещается в **верхней зоне** drawer (под/в связке с brand header
«Gisti» + tagline), оформлен как выделенный store-badge (рамка + иконка +
двухстрочный текст) по паттерну Swapface `StoreBadges`. Открытие ссылки —
во внешнем браузере/сторе.

Решения пользователя (AskUserQuestion 2026-06-13):
1. Стиль — **store-badge баннер** (выделенный, как Swapface), не обычный
   NavigationDrawerItem.
2. Размещение — **сверху**, под/вместо текущего brand header; шапку drawer
   отрефакторить компактнее (название + ссылка в несколько строк).

## Технический план

1. `@mobile-design-expert` (skill `material-3-skill`):
   - Спроектировать `@Composable` store-badge баннер (Row в рамке, border 1dp,
     radius 12dp, иконка 21–24dp + topLine label / bottomLine bold).
   - Отрефакторить `DrawerBrandHeader` в `AppNavigationDrawerContent.kt`:
     компактная шапка + баннер кросс-промо под ней.
   - Платформенная развилка **внутри** drawer:
     `val platform = remember { getPlatformName() }` (импорт из
     `feature.user.data.device`) → `when(platform) { "web" -> AndroidBadge;
     else -> WebBadge }`. Не плодить параметры, не трогать 2 call-site
     (`MainScreen.kt`, `AdaptiveNavigationShell.kt`).
   - Строки в `core/designsystem` `strings.xml` (EN, benefit-focused),
     без литералов в Kotlin.
   - URL-константы (`WEB_APP_URL = "https://gisti-ai.com/"`,
     `GOOGLE_PLAY_URL = ".../com.antonchuraev.aichecklists"`) в общем месте
     (пакет navigation рядом с drawer), без дублей.
   - Открытие — `LocalUriHandler.current.openUri(URL)` (уже работает
     Android+wasmJs) + `onCloseDrawer()`.
   - Иконка Google Play — перенести drawable из swapface
     (`google_play_store_icon.xml`) в `core/designsystem` composeResources;
     для web-стороны глобус (`Icons.Outlined.Public`/`Language`).
     **НЕ** использовать `Icons.Filled.Apple` (trademark, отсутствует).
   - Insets: НЕ добавлять `navigationBarsPadding` (ModalDrawerSheet сам
     применяет `DrawerDefaults.windowInsets`); баннер внутри корневого
     `verticalScroll`-Column.
2. Обновить deferred-spec `docs/todos/2026-05-24-drawer-autotests.md`
   (перечисляет пункты меню поимённо).
3. Валидация: `:androidApp:compileDebugKotlin` + `:composeApp:compileKotlinWasmJs`.

## Лог итераций

### Итерация 1 — 2026-06-13 — mobile-design-expert

**Что сделано:**
- Создан `core/designsystem/src/commonMain/composeResources/drawable/ic_google_play.xml` — официальный цветной значок Google Play (перенесён из swapfaceandroid).
- Добавлено 6 новых строк в `core/designsystem/src/commonMain/composeResources/values/strings.xml`:
  - `drawer_promo_web_top`, `drawer_promo_web_bottom`
  - `drawer_promo_android_top`, `drawer_promo_android_bottom`
  - 2 contentDescription для иконок.
- Отрефакторен `AppNavigationDrawerContent.kt`:
  - `DrawerBrandHeader` — компактный однострочный ряд (лого 40→32dp, tagline на одну линию).
  - Новый `@Composable DrawerStorePromoBadge` — outlined store-badge (surfaceContainerHigh fill, 1dp outlineVariant border, 12dp corners, clickable+ripple).
  - Платформенная развилка читается ВНУТРИ drawer через `getPlatformName()` (feature.user.data.device): web → Google Play badge; else → web badge.
  - 2 private const URL (WEB_APP_URL, GOOGLE_PLAY_URL).
  - Открытие через `onCloseDrawer()` + `uriHandler.openUri(targetUrl)`.
- Call-site'ы (MainScreen.kt, AdaptiveNavigationShell.kt) не тронуты.

**Почему так:**
- Store-badge (рамка + иконка + двухстрочный лейбл) привычнее для кросс-промо, чем NavigationDrawerItem.
- Платформенная развилка внутри — не требует параметров на уровне call-site, инкапсулирована.
- Image для Google Play (не Icon) сохраняет 4 фирменных цвета (не сплющивает в один tint).
- Компактный brand header освобождает место для баннера.

**Баги/проблемы:**
- Google Play URL продублирован (есть original в feature:paywall) — кандидат на консолидацию.
- iOS → ведёт на веб (else-ветка); Icons.Filled.Apple не использовалась.

**Итог итерации:** код написан; следующий шаг — валидация сборки (`:androidApp:compileDebugKotlin` + `:composeApp:compileKotlinWasmJs`).

## Выводы

**Завершено:** Кросс-платформная кросс-промо store-badge в drawer реализована за одну итерацию; сборка зелёная (:androidApp:compileDebugKotlin + :composeApp:compileKotlinWasmJs PASS).

**Ключевые решения:**
1. **Store-badge компонент** — визуально выделенный баннер (outlined card + иконка + двухстрочный текст) выглядит профессиональнее простого NavigationDrawerItem; привычно для юзеров из Swapface/других мобильных приложений.
2. **Платформенная логика в drawer** — `when(getPlatformName())` вычисляется внутри `DrawerStorePromoBadge`, не требует параметров на уровне call-site'ов (MainScreen, AdaptiveNavigationShell). Инкапсуляция упростила интеграцию.
3. **Image вместо Icon для Google Play** — цветной drawable (фирменные 4 цвета) сохраняется; Icon + tint нарушит фирменность.
4. **LocalUriHandler** — кроссплатформно работает на Android (Intent.ACTION_VIEW) и wasmJs (window.open). Свой expect/actual opener не понадобился.

**Остатки техдолга (не в scope этой задачи):**
- GOOGLE_PLAY_URL продублирован в feature:paywall; консолидация в core-константу требует отдельной рефакторинга.
- Платформенная развилка для юнит-тестов может быть вынесена в pure-функцию `crossPromoTarget(platformName)` (отмечено в deferred spec drawer-autotests).
- Medium-rail (NavigationRail на 600–839dp) не покрыта промо-баннером; баннер только в Compact/Expanded drawer. Это ограничение дизайна (rail очень узкая для store-badge); при необходимости можно добавить компактный вариант.

**Риски:** Нет. Изменения минимальны (2 файла: drawable + strings; рефактор drawer с инкапсуляцией логики). Call-site'ы не трогались.

## Предложения по улучшению агентов
