---
title: Автотесты на drawer (ModalNavigationDrawer)
date: 2026-05-24
status: deferred
parent_task: "drawer-discoverability work (session 2026-05-24)"
blocking_reason: user-deferred
resume_trigger: "User says «напиши автотесты на шторку» / следующая drawer-related сессия"
estimated_complexity: Standard/Medium
keywords: [drawer, modal-navigation-drawer, compose-ui-test, drawer-navigation, debounce, edit-mode-gesture, navigation-test, regression-prevention]
---

# Drawer Автотесты — Deferred

## Контекст

Session 2026-05-24: user попросил «напиши автотесты на шторку» (drawer). Параллельно работали 4 трека: edge-swipe fix (shipped), pulse hamburger (shipped), drawer autotests (deferred), Google Play In-App Updates (deferred). Скоп тестов проговорен но не реализован.

## Что покрыть тестами

### Compose UI Tests (`composeApp/src/androidUnitTest/` или `androidTest/`)
1. **Drawer открывается тапом по hamburger** — IconButton click → drawerState.isOpen == true
2. **Drawer открывается свайпом от середины** — swipe gesture from x=400 → x=900 → drawerState.isOpen == true
3. **Drawer закрывается тапом по scrim** — tap на затемнённую область → drawerState.isClosed == true
4. **Drawer закрывается свайпом влево** — swipe от центра drawer влево → drawerState.isClosed
5. **gesturesEnabled=false в edit mode** — после `onEditModeChange(true)` свайп от середины НЕ открывает drawer (drag-reorder conflict, см. App.kt:208)
6. **Pulse hamburger останавливается после первого drawer.open()** — проверить через `MainScreenViewModel.markHamburgerHintShown()` интеграционно

### Navigation Tests (drawer items → routes)
7. **Drawer click Home** — drawer закрывается, остаёмся на Main (already on Main → only drawer close)
8. **Drawer click Today** — `navController.currentDestination.route == AppNavRoute.Today`
9. **Drawer click Calendar** — `navController.currentDestination.route == AppNavRoute.Calendar`
10. **Drawer click AI Chat** — `navController.currentDestination.route == AppNavRoute.AiChat`
11. **Drawer click Updates** — `navController.currentDestination.route == AppNavRoute.UpdateFeed`
12. **Drawer click Settings** — `navController.currentDestination.route == AppNavRoute.Settings`
13. **navConsumed debounce — rapid double-tap** — 2 быстрых тапа на Today → только одна navigation (см. App.kt:194-203)

### Premium Banner Tests (`PremiumBanner` внутри drawer)
14. **Premium upgrade click** — navigateToPaywall("update_feed" source)
15. **Premium subscription click (when active)** — navigateToSubscriptionStatus

### Cross-Promo Store Badge Tests (`DrawerStorePromoBadge`, добавлен 2026-06-13)
16. **Android → web-promo** — при `getPlatformName()=="android"` баннер показывает `drawer_promo_web_top`/`drawer_promo_web_bottom` (глобус `Icons.Outlined.Public`), клик → `uriHandler.openUri("https://gisti-ai.com/")`.
17. **Web → Google-Play-promo** — при `getPlatformName()=="web"` баннер показывает `drawer_promo_android_top`/`drawer_promo_android_bottom` + `ic_google_play` Image, клик → `uriHandler.openUri(GOOGLE_PLAY_URL)`.
18. **iOS → web-promo (else-ветка)** — при `getPlatformName()=="ios"` показывает web-promo (iOS не в проде → ведём на веб).
19. **Клик закрывает drawer** — тап по баннеру вызывает `onCloseDrawer()` перед `openUri`.

> ⚠️ Тестируемость: развилка платформы сейчас inline (`platformName == "web"` в `DrawerStorePromoBadge`). Для чистого JVM-юнита (без `getPlatformName()` actual) стоит вынести предикат в pure-функцию `crossPromoTarget(platformName): {url, topLine, bottomLine}` по образцу `shouldUseSinglePaneLayout(platformName)` (web-single-pane-layout-2026-05-29). Тогда тесты 16–18 проверяются на JVM без Compose-runner.

## Стратегия выполнения

- Унит-тесты ViewModel (`MainScreenViewModelTest`) уже расширены под hamburger hint (4 теста). НЕ дублировать там.
- Compose UI tests — отдельный файл `MainScreenDrawerTest.kt` в `composeApp/src/androidUnitTest/` (использовать `ComposeTestRule` + `setContent { App() }` + Koin test module).
- Альтернатива: instrumented test `composeApp/src/androidTest/` для real-device-style тестирования (медленнее, но реалистичнее).
- **Делегировать `@android-expert`** — UI-тесты требуют Compose test runner config (часто не настроен).

## Контекст файлов

- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt:187-275` — `composable<AppNavRoute.Main>` с `ModalNavigationDrawer + drawerContent + MainScreen`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/AppNavigationDrawerContent.kt` — содержимое drawer: `DrawerBrandHeader` (компактная шапка) → `DrawerStorePromoBadge` (cross-promo, platform-зависимый, добавлен 2026-06-13) → Navigate(Home/Calendar/AiChat/UpdateFeed/Settings/Account|SignIn) → Help(RateApp/LeaveFeedback/Support) → About(Privacy/Terms) → `DrawerFooter` (version)
- `feature/home/src/commonMain/.../MainScreen.kt:82-91` — hamburger IconButton + pulse animation

## Как возобновить

1. Прочитать этот файл.
2. Проверить что `compose-ui-test-junit4` или `compose-ui-test-manifest` в зависимостях (если нет — добавить).
3. Делегировать `@android-expert` написать тесты по spec выше.
4. Запустить `./gradlew :composeApp:testDebugUnitTest` (или эквивалент после AGP 9 — `testAndroidHostTest`).
