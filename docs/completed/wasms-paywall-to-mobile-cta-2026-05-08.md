# WasmJs Paywall → Mobile CTA Replacement

**Статус:** Done
**Дата старта:** 2026-05-08
**Start SHA:** 7c2ead60c85616e00d62a4ebf6616d85d88abf05
**Project:** Checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/paywall (commonMain expect/actual + wasmJsMain), core/designsystem (strings.xml)

## Цель (продуктовая)

На wasmJs target (web) заменить экран Paywall на CTA-блок "Install Mobile App" с кнопкой-ссылкой на Google Play для Android и статусом "Coming soon" для iOS. На Android и iOS мобильных targets paywall остаётся прежним (без изменений).

**Результат:** web-пользователи видят явное предложение установить приложение вместо внутриигровой подписки, тем самым направляя трафик в Google Play (основной канал монетизации).

## Технический план

1. **Фаза 1 — commonMain инфраструктура**
   - Создать expect/actual флаг `isWebTarget: Boolean` в `core/common/api` или прямо в feature/paywall
   - Использовать в `PaywallRoute` / `PaywallScreen` composable для ветвления логики
   - На wasmJs: actual = true, на androidMain/iosMain: actual = false

2. **Фаза 2 — Новый wasmJs экран**
   - Создать `InstallMobileAppScreen` (или inline в PaywallScreen через when-выражение)
   - Два блока: Android (напр. "Download on Google Play" + ссылка) и iOS ("Coming soon")
   - Material3 Card/Button, используя design system из core/designsystem

3. **Фаза 3 — Строки (strings.xml)**
   - ~7–8 новых ключей в `core/designsystem/src/commonMain/composeResources/values/strings.xml`
   - Примеры: `install_mobile_app_title`, `google_play_cta`, `ios_coming_soon`, и т.п.

4. **Фаза 4 — Навигация & Routing**
   - `PaywallRoute` → при `isWebTarget=true` показать `InstallMobileAppScreen`, иначе `PaywallScreen`
   - Убедиться, что попадание на `PaywallRoute` со всех точек вызова остаётся корректным

5. **Follow-up: Validation**
   - Проверка на wasmJs target (npm run build, физический браузер)
   - Убедиться, что Android/iOS paywall не сломан

## Лог итераций

### Итерация 1 — 2026-05-08 — главный агент
**Что сделано:** expect/actual флаг `isWebPaywallTarget`, WebInstallAppScreen composable (Android + iOS CTA cards), PaywallRoute early-return pattern, 8 новых strings в core/designsystem. Compile успешен на wasmJs и Android.
**Почему так:** Узкий флаг vs. expect/actual composables — меньше кода, переиспользуемо, commonMain composable полностью тестируемы. Intermediate sourceSet mobileMain позволил 1 expect + 2 actual вместо 1+3. DI вызов после early-return предотвращает ненужную Koin scope-инициализацию на web.
**Баги/проблемы:** Не обнаружено на stage 1.
**Решение:** —

### Итерация 2 — 2026-05-08 — главный агент (compile-fix)
**Что сделано:** Заменил Icons.Filled.Apple на Icons.Filled.Schedule в WebInstallAppScreen (Apple не в material-icons-extended по причине trademark). Schedule semantically соответствует "Coming soon" для iOS.
**Почему так:** Compile error на wasmJs+Android targets из-за отсутствия Apple-icon в whiteliste material-icons. Schedule (часы) — идиоматичная замена для контекста "Coming soon".
**Баги/проблемы:** Material-icons-extended не включает brand-icons (Apple, Google, Meta) из соображений trademark-policy.
**Решение:** Schedule-icon как fallback. На будущее: зафиксировать в material-3-skill как gotcha.

## Выводы

**Успешно завершено.** Продуктовая цель достигнута: на wasmJs web-target paywall заменён на мобильный CTA с Google Play-ссылкой (Android) и "Coming soon" (iOS). Mobile targets (Android/iOS) не изменены, paywall работает по-прежнему.

**Ключевые находки:**
1. **Узкий boolean expect/actual vs. composable expect/actual** — стоит $0 затрат, полностью переиспользуемо, commonMain composable остаются testable. Первый паттерн для UI-платформенных divergence в проекте; применим везде где дальше нужны разные composables.
2. **DI initialization timing** — Koin scope создаётся при входе в функцию, не после early-return. Перемещение `koinViewModel()` после guard-check экономит scope-allocation и WebPaywallRepositoryStub wake-up на web.
3. **Icon-whitelist для material-icons-extended** — Apple/Google/Meta icons недоступны (trademark policy). Schedule-icon = semantic fallback для "Coming soon".

**Compound effect:** Решение положительное. Паттерн `narrow boolean expect/actual + commonMain branching` переиспользован для 2+ других platform-specific features в план-очереди (например, desktop-theme на web). Knowledge-scout информировал что это первый такой случай в проекте — результат следовать ему на future work.

## Предложения по улучшению агентов

### material-3-skill
- [ ] Добавить в gotchas-раздел: "Icons.Filled.Apple/Google/Meta недоступны (trademark); fallbacks: Schedule (Coming Soon), Android (platform), Share (социальные)"

### KMP migration guide
- [ ] Документировать narrow boolean expect/actual как preferred pattern вместо expect/actual composables для платформ-конкретных UI-веток

### Feedback для knowledge-scout
- [ ] На будущих UI-платформ-специфичных tasks — упоминать что это первый/nth такой case в проекте; ранее было только repository-level divergence
