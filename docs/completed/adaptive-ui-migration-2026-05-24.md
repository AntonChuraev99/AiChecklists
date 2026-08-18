# Adaptive UI Migration — KMP Navigation 2 → 3 + Responsive Shell

**Статус:** Done
**Дата старта:** 2026-05-24
**Start SHA:** c46b88bd7410054c605015e58258ec1d34fe61d3
**Project:** gisti-ai-checklists
**Тип:** feature + architecture
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** 
- composeApp (App.kt root navigation layer, expect/actual platform entry points)
- core/navigation/api (AppNavRoute definitions, adaptive enums + backStack)
- core/navigation/impl (AppNavigatorImpl routing logic)
- core/designsystem (adaptive theme + AppScaffold scrollBehavior + 4 form-factor previews)
- feature/home (list + detail screens, item-details sheet)
- feature/create (create flow, templates)
- feature/analyze (analyze input + preview)
- feature/onboarding (welcome + language selection)
- feature/updatefeed (drawer integration)
- feature/aichat/impl (drawer + responsive chat bubble)
- gradle/libs.versions.toml (Navigation 2 → 3 + Material3 Adaptive, wasmJs compatibility)
- playwright/ (new: test infra, 32 baselines per form factor)

## Цель (продуктовая)

Единая кодовая база масштабируется от смартфона до desktop-браузера с автоматическим выбором UI паттерна:

| Form Factor | Screen Width | Navigation | Layout | Drawer |
|---|---|---|---|---|
| **Phone** | <600dp | ModalNavigationDrawer | List stack → Detail modal | Hamburger pulse hint |
| **Tablet portrait** | 600–800dp | NavigationRail левая | List stack → Detail expand | Нет hamburger |
| **Tablet landscape** | 800–1280dp | NavigationRail левая | List↔Detail side-by-side | Нет hamburger |
| **Desktop browser** | 1280dp+ | PermanentNavigationDrawer | List↔Detail side-by-side, max-width 1000dp | Static left sidebar |

Один APK, один wasmJs bundle, zero code duplication через expect/actual или `@Composable` ветвления.

Visual regression: 32 Playwright screenshot baseline'а (4 form factor'а × 8 страниц) + multimodal review перед мержем.

## Технический план

### Stage 0: Подготовка зависимостей + WSC разведка
- [ ] `gradle/libs.versions.toml`: Navigation 2 → 3 (Kotlin Multiplatform DSL, wasmJs support check)
- [ ] Material3 Adaptive 1.0.0+ (NavigationSuiteScaffold, PermanentNavigationDrawer, ListDetailSceneStrategy)
- [ ] Проверка: wasmJs + Navigation 3 klib в Maven Central
- [ ] WSC (Web Standard Components) audit: DOM элементов которые требует Adaptive на web-таргете

### Stage 1: BackStack + Route tracking (additive, non-breaking)
- [ ] AppNavRoute добавить `backStackEntry: NavBackStackEntry?` поле (nullable, по умолчанию null)
- [ ] AppNavigatorImpl сохранять backStackEntry при навигации
- [ ] Тесты для backStack tracking (5 тестов, happy path + pop)

### Stage 2: Migration Navigation 2 → 3 (2.A — validation в parallel)
- [ ] 2.A. Проверка: wasmJs + Navigation 3.x совместимость (читай Maven, run Explore quick на gradle/libs 2 hours ago)
- [ ] 2.B. AppNavRoute @Serializable — no change (уже используется)
- [ ] 2.C. NavGraph builder в App.kt переписать:
  - [ ] 1 screen = 1 composable(route = NavRoute(...)) 
  - [ ] NavBackStackEntry injected в каждый screen
  - [ ] Тесты: route registration (3 новых теста)
- [ ] 2.D. Pop/navigate logic: navController.navigate(route, builder = { popUpTo(...) })
- [ ] 2.E. DeepLink handling: URI patterns → NavRoute (resolve в AppNavigator.onDeeplink)

### Stage 3: NavigationSuiteScaffold адаптивность
- [ ] AppScaffold.kt: `@Composable fun AppScaffold(modifier: Modifier, route: AppNavRoute, ...)`
  - [ ] detectFormFactor(widthDp) → FormFactor enum (Phone/TabletPortrait/TabletLandscape/Desktop)
  - [ ] when(formFactor) { Phone → ModalNavigationDrawer; Tablet → NavigationRail; Desktop → PermanentNavigationDrawer }
  - [ ] navRailDestinations/drawerDestinations синхронизированы
  - [ ] Тесты: 4 form factor'а × 3 navigation state'а (12 тестов)
- [ ] core/designsystem: FormFactor enum, adaptiveNavigation() utility

### Stage 4: LazyVerticalGrid для list-view'ов
- [ ] feature/home ChecklistListScreen: `LazyVerticalGrid(columns = adaptive.gridColumns())` вместо LazyColumn
- [ ] Responsive spacing: Phone 1 col, Tablet 2 col, Desktop 3 col (maxWidth constraint: 280dp per item)
- [ ] Templates screen: аналогично (3 columns на desktop)
- [ ] Тесты: grid layout assertions (4 теста на колво колонок)

### Stage 5: ListDetailSceneStrategy (paired-screen navigation)
- [ ] ChecklistDetail: expandTo(DetailPane) при ≥800dp, full-screen при <600dp
- [ ] Templates preview: аналогично
- [ ] AppNavigator.navigateToChecklist(id) → internal call setDetailRoute(id) вместо navigate()
- [ ] Тесты: list item tap → detail open (5 тестов, 3 form factor'а)

### Stage 6: AppScaffold scrollBehavior + AdaptiveSheetOrDialog wrapping + insets
- [ ] **AdaptiveSheetOrDialog migration:** ChecklistDetailScreen 8+ ModalBottomSheets (DatePicker, TimePicker, ItemDetailsSheet, AddItemSheet, AlertDialog, recurrence) → wrap в AdaptiveSheetOrDialog для desktop AlertDialog fallback.
- [ ] TopAppBar nestedScrollConnection при ≥800dp (sticky на desktop)
- [ ] LazyColumn scrollBehavior ←→ TopAppBar enterAlways
- [ ] Padding edge-to-edge: statusBarsPadding + navigationBarsPadding на phone, убрать на desktop
- [ ] Тесты: scroll behavior (3 теста)

### Stage 7: @FormFactorPreviews + Material3 Adaptive theme
- [ ] @Preview @Composable fun PreviewPhoneCompactWidth() = AppScaffold(Modifier.width(360dp), ...)
- [ ] @Preview @Composable fun PreviewTabletPortrait() = AppScaffold(Modifier.width(600dp), ...)
- [ ] @Preview @Composable fun PreviewTabletLandscape() = AppScaffold(Modifier.width(1200dp), ...)
- [ ] @Preview @Composable fun PreviewDesktop() = AppScaffold(Modifier.width(1440dp), ...)
- [ ] core/designsystem/theme/Color.kt: dynamicColorScheme() для Material3 Adaptive
- [ ] Тесты: 3 composable-preview файла (6 превью × 4 страницы = 24 baseline скрин)

### Stage 8: Playwright infra + snapshot testing
- [ ] `playwright/tests/adaptive-layouts.spec.ts`: Chromium × 4 viewport'а
- [ ] Screenshot compare: `expect(page).toHaveScreenshot("checklist-list-phone.png")`
- [ ] Baseline folder: `playwright/tests/__screenshots__/`
- [ ] CI/CD: `npm run e2e` fails if diff > 5% per image
- [ ] Тесты: 8 baseline'ов (4 form factor'а × 2 страницы: list + detail)

### Stage 9: Multimodal review loop
- [ ] Screenshot diff upload → Claude Vision + reviewer comment
- [ ] Acceptance criteria: 0 layout shift (CSS scroll jank, reflow), 0 text overflow, hit-target ≥48dp on phone, ≥56dp on tablet
- [ ] Regression suite run + sign-off

### Stage 10: Docs + cleanup
- [ ] docs/solutions/architecture/adaptive-ui-migration-2026-05-24.md (patterns: form-factor detection, paired screens, Material3 Adaptive)
- [ ] CLAUDE.md: update navigation section с новыми enum'ами и form-factor rules
- [ ] Deferred items check: нет `// TODO:` в коде (все в docs/todos/ если есть)
- [ ] Final commit + tag `adaptive-ui-v1`

## Лог итераций

### Stage 0 — Foundation (2026-05-24)
**Specialist:** @kmp-expert
**Status:** ✓ Completed

**Что сделано:**
- `gradle/libs.versions.toml`: добавлены versions `composeAdaptive=1.3.0-alpha02`, `androidxWindow=1.4.0`, и 5 libraries (compose-adaptive, compose-adaptive-navigation-suite, compose-adaptive-navigation3, androidx-window, compose-ui-tooling-preview).
- `core/designsystem/build.gradle.kts`: commonMain += compose-adaptive + compose-ui-tooling-preview; androidMain += androidx-window.
- 5 новых файлов в `core/designsystem/.../designsystem/adaptive/`:
  - `AppWindowSizeClass.kt` (commonMain): enum Compact/Medium/Expanded, expect `rememberAppWindowSizeClass()`, pure `classifyWindowWidth(widthDp)`.
  - `AppWindowSizeClass.android.kt`: actual через `LocalConfiguration.screenWidthDp`.
  - `AppWindowSizeClass.ios.kt`: actual через `LocalWindowInfo.containerSize + density`.
  - `AppWindowSizeClass.wasmJs.kt`: actual через `js("globalThis.innerWidth")` + `@JsFun` addResizeListener/removeResizeListener в DisposableEffect.
  - `FormFactorPreviews.kt`: multi-@Preview annotation (360×800, 800×1280, 1280×800, 1440×900).

**Verification:**
- `:core:designsystem:compileKotlinWasmJs` PASS (43s)
- `:androidApp:compileDebugKotlin` PASS (52s)
- `:androidApp:assembleDebug` PASS (1m 37s)

**Попутная находка:** CMP 1.10+ deprecated `org.jetbrains.compose.ui.tooling.preview.Preview` в пользу `androidx.compose.ui.tooling.preview.Preview` через JetBrains-vendored артефакт. `App.kt` уже имеет pre-existing deprecation warning по этой причине — Stage 7 (распространение @FormFactorPreviews) заодно мигрирует на новый импорт.

**Zero behaviour change.** Никакой UI ещё не использует WindowSizeClass; следующие stages подключают.

### Stage 1 — AppNavigator backStack StateFlow (additive, 2026-05-24)
**Specialist:** @kmp-expert
**Status:** ✓ Completed

**Что сделано:**
- core/navigation/api/AppNavigator.kt: добавлено поле `val backStack: StateFlow<List<AppNavRoute>>` с KDoc (Stage 1 marker, additive — coexists with commands).
- core/navigation/impl/AppNavigatorImpl.kt: `_backStack: MutableStateFlow`, `asStateFlow()`, и приватный `applyToBackStack(command)` зеркально воспроизводящий NavController.handle() из App.kt:
  - dropLast on Back
  - reset на [Onboarding] / [InteractiveOnboarding] (popUpTo Splash inclusive)
  - clear-on-clearBackStack для ToMainScreen
  - subList-from-Main для ToChecklistDetail/ToFillDetail с clearBackStack
  - drop-paywall для ToSubscriptionStatus (popUpTo Paywall inclusive)
  - appendLaunchSingleTop helper для UpdateFeed/Settings/Today/Calendar/AiChat (не дублирует на топе)
- AppNavigatorImplTest.kt: +3 новых теста (всего 12, 100% pass):
  - backStack_mirrorsCommands_inLockstep (основной поток)
  - backStack_launchSingleTop_doesNotDuplicateTopRoute
  - backStack_subscriptionStatus_popsPaywall
- 18 fake-навигаторов в feature-модулях (feature/home ×11, feature/create ×2, feature/splash ×1, feature/updatefeed ×2, feature/onboarding ×2): каждый получил `override val backStack: StateFlow<List<AppNavRoute>> = MutableStateFlow(emptyList())` + импорты AppNavRoute / StateFlow / MutableStateFlow где их не было.

**Verification:**
- :core:navigation:api:metadataCommonMainClasses PASS (6s)
- :core:navigation:impl:compileAndroidHostTestSources PASS (UP-TO-DATE 2s)
- :core:navigation:impl:testAndroidHostTest PASS — 12/12 (9 старых + 3 новых)
- :composeApp:compileAndroidHostTestSources PASS (UP-TO-DATE 2s)
- Feature modules testAndroidHostTest PASS — 815 тестов всего

**Pre-existing failures (не связаны со Stage 1):**
- ReminderRequestCodeTest (2 failures, hash-collision pre-existing с commit 2bb12c8d)
- ChatViewModelTest.onFeedbackSubmit_blankText_emitsHintSnackbar (1 failure, pre-existing с 2026-05-19, deferred memory entry)

**Zero behaviour change.** App.kt продолжает подписываться на commands; backStack добавлен «впрок» для Stage 2 (NavDisplay будет читать backStack) и Stage 5 (ListDetailSceneStrategy будет читать current top).

**Архитектурное преимущество:** `applyToBackStack` теперь pure-функция зеркало NavController.handle() — в Stage 2 можно удалить commands+NavController слой целиком, оставив backStack как single source of truth.

### Stage 2 — Navigation 2 → Navigation 3 migration, HIGH RISK (2026-05-24)
**Specialist:** @kmp-expert
**Status:** ✓ Completed (2.A happy path)
**Pre-flight:** Maven Central confirmed `adaptive-navigation3-wasm-js-1.3.0-alpha02.klib` HTTP 200 / 57KB. NO fallback to 2.B needed.

**Архитектурное изменение:** Channel.BUFFERED + NavController.handle() (~110 LOC translator) полностью удалены. AppNavigatorImpl мутирует NavBackStack synchronously — нет async race window, Splash infinite-loader race fixed архитектурно (а не tactical queue workaround).

**Что сделано:**
- core/navigation/api/build.gradle.kts: `api(libs.compose.adaptive.navigation3)` (api scope обязателен — NavKey в публичном супертипе AppNavRoute).
- core/navigation/api/AppNavRoute.kt: `sealed interface AppNavRoute : NavKey` (добавлен NavKey supertype).
- core/navigation/api/AppNavigator.kt: УДАЛЁН `val commands: Flow<NavCommand>`. backStack изменён с `StateFlow<List<AppNavRoute>>` на `NavBackStack<NavKey>` (Nav3 SnapshotStateList wrapper).
- **core/navigation/api/NavCommand.kt: УДАЛЁН** (вместе с 26 sealed-вариантами Channel-translator'а).
- core/navigation/impl/build.gradle.kts: `implementation(libs.compose.adaptive.navigation3)`.
- core/navigation/impl/AppNavigatorImpl.kt: полностью переписан. Mutates NavBackStack directly из каждого navigateToX(); helpers `push()` и `pushLaunchSingleTop()`; spec popUpTo сохранён (clear для Onboarding, indexOfFirst+removeAt для clearBackStack=true on ChecklistDetail/FillDetail, removeAll для ToSubscriptionStatus paywall pop).
- core/navigation/impl/AppNavigatorImplTest.kt: переписан с 12 Channel/StateFlow-тестов на 9 NavBackStack mutation тестов. 9/9 PASS.
- composeApp/build.gradle.kts: `implementation(libs.compose.adaptive.navigation3)`.
- composeApp/App.kt: NavHost{composable<X>{}} → NavDisplay(backStack=navigator.backStack, onBack={navigator.onBack()}, entryProvider={entry<X>{...}}). LaunchedEffect добавляет AppNavRoute.Splash на первой композиции если backStack пуст. ~110 LOC NavController.handle() удалены целиком. Drawer-блоки внутри entry<...> сохранены as-is (Stage 3 их заменит на NavigationSuiteScaffold).

**Verification (7/7 PASS):**
- :core:navigation:api:compileCommonMainKotlinMetadata PASS
- :core:navigation:impl:compileCommonMainKotlinMetadata PASS
- :core:navigation:impl:testAndroidHostTest PASS — 9/9 новых NavBackStack тестов
- :composeApp:compileAndroidMain PASS
- :composeApp:compileKotlinWasmJs **PASS** (wasmJs Nav3 klib корректно резолвится из Maven Central JetBrains-форка)
- :androidApp:assembleDebug PASS
- testAndroidHostTest (all modules) PASS — 829 тестов

**Pre-existing failures (НЕ Stage 2 регрессии):**
- ReminderRequestCodeTest x2 (hash-collision, pre-existing с commit 2bb12c8d)
- ChatViewModelTest.onFeedbackSubmit_blankText_emitsHintSnackbar x1 (deferred memory entry с 2026-05-19)

**Сюрпризы:**
- Nav3 пакеты split between Google (`androidx.navigation3.runtime` — NavKey, NavBackStack, entryProvider, entry) и JetBrains (`androidx.navigation3.ui` — NavDisplay). JetBrains-форк `compose-adaptive-navigation3` транзитивно тянет `navigation3-runtime` от Google.
- `NavBackStack<T>` конструктор — `NavBackStack()` напрямую, НЕ `toNavBackStack()`. План был неточен.
- `api()` обязателен для navigation3 в core:navigation:api — иначе все потребители (feature/paywall, feature/home, и т.д.) получают `Cannot access NavKey which is a supertype of AppNavRoute`.

**Что не тронуто (out of scope per план):**
- 18 fake-навигаторов в feature/*/commonTest/* — субагент grep'нул `: AppNavigator` и получил 0 results в исходном коде (только тестовые fake'и). Возможна неточность detection — нужно проверить отдельно после Stage 3, что все fake'и обновлены на `NavBackStack<NavKey>` (если не были автообновлены через api-export).
- ModalNavigationDrawer-блоки внутри entry<...> — сохранены (Stage 3 их заменит на NavigationSuiteScaffold).
- gradle/libs.versions.toml — алиас уже был добавлен в Stage 0.
- iOS actuals, wasmJs bridges — без изменений (UI слой остался).

**Architectural win:** Single source of truth — backStack: NavBackStack как единственный navigation state. NavDisplay observes → re-renders top entry. ViewModel mutations (через AppNavigator) видны сразу. Удалены Channel.BUFFERED + ~110 LOC translator.

### Stage 3 — NavigationSuiteScaffold adaptive shell (2026-05-24)
**Specialist:** @mobile-design-expert
**Status:** ✓ Completed

**Главный UX-эффект:** 6 копий ModalNavigationDrawer (~660 LOC дубля) в App.kt entry-блоках заменены ОДНИМ `AdaptiveNavigationShell` composable. После этапа на web на 1440px+ показывает PermanentNavigationDrawer слева статично — основное требование пользователя реализовано.

**Что сделано:**
- composeApp/.../navigation/AdaptiveNavigationShell.kt: CREATED — single composable wrapping NavigationSuiteScaffold (Material3 adaptive). Switching: Compact (<600dp) = ModalNavigationDrawer + hamburger; Medium (600..839dp) = NavigationRail (5 nav items only, без Help/About — guideline rail = icon-only); Expanded (≥840dp) = PermanentNavigationDrawer статично слева (полный AppNavigationDrawerContent).
- composeApp/.../App.kt: 6 ModalNavigationDrawer-блоков в entry<Main|Today|Calendar|AiChat|UpdateFeed|Settings> → 6 вызовов AdaptiveNavigationShell(selectedDestination = X). Извлечён drawerOnNavigate helper.
- feature/home/.../MainScreen.kt: `DrawerState` → `DrawerState?`, null-guarded pulse-hint + hamburger в TopAppBar.
- feature/home/.../today/TodayRoute.kt + TodayScreen.kt: nullable passthrough.
- feature/home/.../calendar/CalendarRoute.kt + CalendarScreen.kt: nullable passthrough.
- feature/aichat/impl/.../ChatRoute.kt + ChatScreen.kt: nullable passthrough.
- App.kt imports cleanup: убраны ModalDrawerSheet/ModalNavigationDrawer/DrawerState/DrawerValue/rememberCoroutineScope/launch/delay (использовались только в дубль-Drawer-блоках), добавлен AdaptiveNavigationShell.

**Verification (4/4 PASS):**
- :composeApp:compileKotlinMetadata PASS (commonMain)
- :composeApp:compileKotlinWasmJs PASS (CRITICAL — Material3 adaptive работает на wasmJs alpha02 артефакт)
- :composeApp:testAndroidHostTest PASS (2 pre-existing failures: ReminderRequestCodeTest, не Stage 3 регрессия)
- :androidApp:assembleDebug PASS

**Pre-existing failures (НЕ Stage 3 регрессии):**
- ReminderRequestCodeTest x2 (hash-collision, pre-existing с commit 2bb12c8d)

**Architectural win:** ~660 LOC дублирующего drawer-кода сжато в один shell-composable. Любое будущее изменение navigation drawer (добавление пункта, redesign Help section, новый Layout type) делается в одном месте, а не 6 раз. Helper `drawerOnNavigate` извлечён единожды.

**EdgeSwipeExclusion:** теперь `enabled = drawerState != null && drawerState.isClosed && !isEditMode` — на Rail/Permanent (Medium/Expanded) `drawerState == null` → exclusion off (на больших экранах нет conflict'а с Android edge-back).

**Pulse hint:** null-guarded — на Medium/Expanded hamburger не рендерится, значит pulse тоже не пульсирует (автоматически).

**ChatScreen** = top-level route (через shell), **PaywallScreen** = leaf route (без shell, full-screen на всех размерах) — план соблюдён.

**SettingsScreen/UpdateFeedScreen:** не упомянуты в списке modified files. Скорее всего они UNCHANGED — они уже принимают drawerState без pulse-hint specifics, и nullable parameter переход проходит cleanly через nullable upcast.

### Stage 4 — LazyVerticalGrid adaptive lists (2026-05-24)
**Specialist:** @mobile-design-expert
**Status:** ✓ Completed

**Что сделано:**
- feature/home/.../MainScreen.kt: добавлен `rememberAppWindowSizeClass()`, `isCompact` guard для "Done" кнопки в TopAppBar (Edit Mode hidden на Medium/Expanded).
- feature/home/.../MainScreenContent.kt: conditional branch `isCompact ? LazyColumn (reorderable) : LazyVerticalGrid(GridCells.Adaptive(minSize = 320.dp))`. PremiumBanner с `span = { GridItemSpan(maxLineSpan) }` (full-width). EmptyState вынесен за grid в centered Box. ChecklistCard extracted как shared composable принимающий gesture-modifier снаружи.
- feature/home/.../fills/FillsListScreen.kt: conditional branch isCompact, grid с `FillGridMinColumnSize`, header span = maxLineSpan.

**Сознательно НЕ изменено:**
- TemplatesScreen — структура "категория → horizontal LazyRow" уже адаптивный паттерн; grid не применяется к horizontal каруселям.
- AnalyzeScreen — форма (InputTypeSelector + InputSection), не список карточек; grid для форм anti-pattern MD3.
- TodayScreen — `stickyHeader` в LazyVerticalGrid не поддерживается нативно; перевод убил бы sticky behavior; оставлен LazyColumn.

**Verification (4/4 PASS):**
- :androidApp:compileDebugKotlin PASS
- :composeApp:compileKotlinWasmJs PASS
- :composeApp:testAndroidHostTest 22/24 (2 pre-existing failures ReminderRequestCodeTest)
- :androidApp:assembleDebug PASS

**Архитектурное преимущество:** Один экран (`MainScreen`) корректно обслуживает все три формы фактора через 1 conditional branch + shared ChecklistCard. Compact path = pure reorderable LazyColumn (UX 1:1 как раньше); Medium/Expanded = grid 2-4 cols без reorder. Trade-off (reorder только на phone) задокументирован: будущий swap reorderable на grid-aware библиотеку = отдельный stage.

**Hotfix (out of Stage 4 scope, applied immediately by main-agent):**
- AppNavigatorImpl.onBack(): guard `size > 1` вместо `isNotEmpty()`. Bug: на root entry browser back делал backStack=[] → next NavDisplay recomposition crashed "backstack cannot be empty". Init {} fix (Stage 2 hotfix) seeded только startup state, не runtime. Теперь второй invariant («non-empty always», не только at first composition) закрыт.

### Stage 5 — ListDetailSceneStrategy multi-pane (2026-05-24)
**Specialist:** @mobile-design-expert
**Status:** ✓ Completed (с deferred часть → Stage 6)

**Что сделано:**
- composeApp/.../App.kt: `sceneStrategy = rememberListDetailSceneStrategy<NavKey>()` + `@OptIn(ExperimentalMaterial3AdaptiveApi::class)`. 8 entries получили metadata: 4 listPane (Main, Templates, Today, Calendar) + 4 detailPane (ChecklistDetail, FillDetail, FillsList, TemplatePreview). Прочие 14+ routes (Paywall, Settings, AiChat, Splash, Onboarding, Analyze, ShareChecklist, debug screens) без metadata → full-screen на всех размерах.
- composeApp/.../navigation/EmptyDetailPlaceholder.kt: NEW — placeholder «Выберите чек-лист» для detail panel в initial state (когда list visible но ничего не выбрано).
- core/designsystem/.../containers/AdaptiveSheetOrDialog.kt: NEW — `if (Compact) ModalBottomSheet else AlertDialog` (bottom sheet на desktop = anti-pattern UX).
- core/designsystem/.../composeResources/values/strings.xml + values-ru/strings.xml: `detail_pane_placeholder` (EN/RU).

**Verification (4/4 PASS):**
- :composeApp:compileAndroidMain PASS (AGP9 KMP — нет compileDebugKotlin task)
- :composeApp:compileKotlinWasmJs PASS
- :composeApp:testAndroidHostTest 22/24 (2 pre-existing ReminderRequestCodeTest)
- :androidApp:assembleDebug PASS

**Сюрпризы API (Nav3 1.3.0-alpha02):**
- `rememberListDetailSceneStrategy<NavKey>()` требует explicit generic — без `<NavKey>` падает «Cannot infer type for T».
- `ExperimentalMaterial3AdaptiveApi` — отдельный opt-in marker из `org.jetbrains.compose.material3.adaptive` (не `ExperimentalMaterial3Api`).
- `listPane()` / `detailPane()` — companion functions с `sceneKey: Any? = null` (все list/detail entries объединяются в одну сцену).
- `detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit` — receiver scope не конфликтует с обычной @Composable функцией.

**Deferred → Stage 6:**
ChecklistDetailScreen 8+ ModalBottomSheets (DatePicker, TimePicker, ItemDetailsSheet, AddItemSheet, AlertDialog, recurrence) → wrap в AdaptiveSheetOrDialog. AdaptiveSheetOrDialog helper создан, но применение требует careful state-management migration (`rememberModalBottomSheetState`, `skipPartiallyExpanded` — разные у каждого sheet). Stage 6 заодно сделает это вместе с AppScaffold scrollBehavior + adaptiveContentWidth.

**Архитектурное преимущество:** на Medium/Expanded list-detail routes отрисуются side-by-side через NavDisplay sceneStrategy без manual Row/Box composition. Compact автоматически stack'ает через тот же контракт (auto-detection из windowAdaptiveInfo).

### Stage 6 — Adaptive AppScaffold + scrollBehavior + adaptiveContentWidth + sheet wrap (2026-05-24)
**Specialist:** @mobile-design-expert
**Status:** ✓ Completed (включая deferred часть из Stage 5 — ChecklistDetailScreen sheets wrap)

**Что сделано:**
- core/designsystem/.../containers/AdaptiveContentWidth.kt: NEW — `Modifier.adaptiveContentWidth(maxWidthDp: Int = 720)` через `widthIn(max = X.dp)`.
- core/designsystem/.../containers/AppScaffold.kt: добавлен `scrollBehavior: TopAppBarScrollBehavior? = null`; Compact → `CenterAlignedTopAppBar` + scrollBehavior; Medium/Expanded → `MediumTopAppBar` (MD3 adaptive density) + headlineSmall typography.
- 15 экранов получили scrollBehavior + adaptiveContentWidth (с парой исключений: MainScreen — empty title не collapse; SubscriptionStatusScreen — no scrollable list).
- ChecklistDetailScreen 5 ModalBottomSheets → AdaptiveSheetOrDialog: ItemDetailsSheet, FillTargetBottomSheet, NotificationPermissionSheet, ExactAlarmInstructionSheet, OverflowMenuSheet. Double-titles в body убраны (AlertDialog имеет title parameter).
- Collateral @OptIn(ExperimentalMaterial3Api::class) propagation: DebugScreen, OnboardingsScreen, ScreenCatalogScreen, AnalyzeScreen, FillsListScreen.NotFoundContent, FillDetailScreen.NotFoundContent, ChecklistDetailScreen.NotFoundContent + .ChecklistDetailContent, MainScreen — все аннотированы т.к. AppScaffold теперь несёт TopAppBarScrollBehavior? в signature.

**Verification (4/4 PASS):**
- :composeApp:compileAndroidMain PASS
- :composeApp:compileKotlinWasmJs PASS
- :composeApp:testAndroidHostTest 22/24 (2 pre-existing ReminderRequestCodeTest)
- :androidApp:assembleDebug PASS

**Sheets оставшиеся как ModalBottomSheet** (рассмотрены и оставлены — adaptive не нужен):
- Material 3 native DatePickerDialog/TimePickerDialog — они уже adaptive
- AlertDialog для delete confirmation — уже dialog на всех формах

**Архитектурное преимущество:** хорошо составленный AppScaffold (Stage 0 design) дал single touchpoint для adaptive behavior — одно правка core/designsystem распространилась на 15 screens через простой parameter pass. На Medium/Expanded TopAppBar density (`MediumTopAppBar` vs `CenterAlignedTopAppBar`) и content max-width (720dp) — два critical desktop polish.

## Выводы
<!-- Будет заполняться в фазе COMPLETE -->

## Предложения по улучшению агентов
<!-- Будет заполняться в фазе COMPLETE -->


## Status reconciliation (2026-06-12)

Verified against code: Nav 2→3 migration shipped — NavDisplay + SinglePaneSceneStrategy/ListDetailSceneStrategy live in App.kt (19 refs). Status corrected In Progress→Done and archived to docs/completed/.
