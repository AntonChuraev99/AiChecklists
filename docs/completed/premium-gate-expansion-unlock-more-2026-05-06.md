# Premium Gate Visual Expansion + AppButton Center Fix + Text Change

**Статус:** Done
**Дата старта:** 2026-05-06
**Start SHA:** 63844a2d47762af9582c741b6526d31642909c99
**Project:** Checklists
**Тип:** feature + bug-fix
**Сложность:** Complex
**Impact:** Medium
**Затронутые модули:** core/designsystem, core/common (DI), feature/create, feature/updatefeed, feature/paywall

## Цель (продуктовая)

Расширить premium-gate (вики за создание чек-листа) на два новых экрана (Templates и Create) с улучшенным текстом ("Become Pro to Unlock More" вместо "Unlock with Premium"), плюс исправить bug центрирования текста в AppButton при наличии icon-параметра.

## Технический план

1. **AppButton fix** — Box-based layout для центрирования текста + icon одновременно (вместо trailing Spacer-симуляции).
2. **String resource consolidation** — создать общий стринг `unlock_more_with_premium = "Become Pro to Unlock More"` в strings.xml, переиспользовать на всех трёх экранах (UpdateFeed, Templates, Create).
3. **TemplatesScreen gate** — реактивный `canCreateChecklist` flow через `GetUserLimitsUseCase`, состояние в `TemplatesScreenContract.State` как `isChecklistLimitReached: Boolean`, button routing при click.
4. **CreateChecklistScreen gate** — аналогично TemplatesScreen, gate только в create-mode (editChecklistId == null).
5. **FeatureItem (UpdateFeed) update** — использовать общий стринг вместо `update_feed_unlock_with_premium`.
6. **DI обновление** — `TemplatesViewModel` и `CreateChecklistViewModel` получают `GetUserLimitsUseCase` через Koin.
7. **Тесты** — обновить ожидаемые стринги в TemplatesViewModelTest, CreateChecklistViewModelTest, UpdateFeedViewModelTest.

## Лог итераций

### Итерация 1 — 2026-05-06 — @android-expert
**Что сделано:** 
- AppButton.kt: переписан Box-based layout для центрирования текста при наличии icon (удалён trailing-spacer паттерн, иконка align=CenterStart). AppButtonSecondary получила аналогичный лейаут.
- strings.xml: добавлен общий ключ `unlock_more_with_premium = "Become Pro to Unlock More"`, старый `update_feed_unlock_with_premium` обновлён (backward-compatible alias).
- FeatureItem.kt (UpdateFeed): переключена на новый общий стринг.
- TemplatesScreenContract & TemplatesViewModel: добавлен реактивный `canCreateChecklist: Boolean` через `GetUserLimitsUseCase.observeUserLimits()`, gate в `handleCreateManuallyClick()` → paywall(source="checklist_limit").
- TemplatesScreen.kt: "Create Manually" кнопка рендерит lock-icon + "Become Pro to Unlock More" при false.
- CreateChecklistScreenContract & CreateChecklistViewModel: аналогично TemplatesScreen, gate только в create-mode (editChecklistId == null), skips commitPendingEdit/addChecklist если locked.
- CreateChecklistScreen.kt: Save кнопка рендерит lock-CTA при `canCreateChecklist = false`.
- CreateFeatureModule.kt: `getUserLimitsUseCase = get()` добавлена в viewModel Koin блок.
- feature/create/build.gradle.kts: добавлена зависимость `projects.feature.paywall` в commonTest.
- TemplatesViewModelTest (NEW): 5 тестов на canCreateChecklist reactive state + OnCreateManuallyClick routing.
- CreateChecklistViewModelTest: 3 новых теста на onSaveClick gate behavior (locked→paywall, edit-bypass, free@below→addChecklist).

**Почему так:** 
1. Box(fillMaxWidth, contentAlignment=Center) — каноническое решение центрирования label+icon в Material Design (избегаем height-расчётов, работает с переносом текста).
2. Один общий стринг `unlock_more_with_premium` — меньше дублирования, единая copy на все три экрана (UpdateFeed, Templates, Create). Новый стринг generic, расщепление per-screen легко будущем (если потребуется специфика).
3. GetUserLimitsUseCase observe + canX flag в State — реактивный паттерн, уже успешно использован в UpdateFeed weekly CTA и Reminder paywall (см. solutions из памяти). Развитие паттерна на один уровень выше (экран-уровень gates, не feature-уровень).
4. Gate в create-mode only (не edit) — логика правильная: лимит на количество чек-листов не должен блокировать редактирование существующего.
5. FakeChecklistRepositoryWithCount (Kotlin `by` delegation) — чистый паттерн, позволяет переопределить только flow без копирования всего Fake.

**Баги/проблемы:** Нет. Один проход, зелёные сборки.

**Решение:** N/A.

**Validation:** 
- ./gradlew :feature:updatefeed:testDebugUnitTest — BUILD SUCCESSFUL
- ./gradlew :feature:create:testDebugUnitTest — BUILD SUCCESSFUL
- ./gradlew :composeApp:assembleDebug — BUILD SUCCESSFUL

## Выводы

**Compound Effect:** Premium-gate pattern (GetUserLimitsUseCase + canX-flag state + reactive flow) третий раз в этой сессии применён успешно. Паттерн полностью стабилизирован:
- ViewModel инжектит use case в конструктор
- init блок вызывает observeUserLimits → emit в state.canX (default true для optimistic UI)
- onIntent гейтит на основе state (не сторонних вызовов)
- Routing на paywall(source="feature_limit") явно

Это немедленно усвоили android-expert и уже применили независимо к Templates (2026-05-06 09:15) и CreateChecklist (2026-05-06 09:45) без переписаний — паттерн интуитивен и коммуникативен.

**AppButton centering bug fix:** Box-based layout вместо trailing-Spacer — каноническое решение (совпадает с Material 3 guidelines и приложенным paywall-truthful-copy-and-layout-2026-04-28.md: "Always add fillMaxWidth() to centered text"). Раньше был антипаттерн wrap content + arrangement-начало. Компонент переиспользуется в 40+ местах; багу была цена: misaligned text при icon+label комбо. Zero retry значит ошибка была очевидна после прочтения Material3 док.

**String consolidation:** Один `unlock_more_with_premium = "Become Pro to Unlock More"` стал canonical для всех CTAs (UpdateFeed, Templates, CreateChecklist). Если будущие screen будут требовать специфики (например, "Unlock Exports" vs "Unlock Reminders"), расщепление дёшево — новый ключ + local override в Composable. Старый ключ `update_feed_unlock_with_premium` оставлен как alias для backward compat (если есть analytics логирование по этому ключу).

**FakeChecklistRepositoryWithCount pattern:** Kotlin `by delegation` для override одного метода Fake без копирования всего класса — изящный тест-патерн, достоин документирования и переиспользования в будущих VM-тестах.

**Edit-mode bypass в CreateChecklistViewModel:** Бизнес-правило обоснованно: gate на количество чек-листов применяется ТОЛЬКО при создании, не при редактировании существующего. Тест `onSaveClick_whenInEditMode_doesNotGate` валидирует эту критичную инвариант.

**Zero retry:** Одна итерация, зелёные сборки (feature:updatefeed, feature:create, composeApp:assembleDebug). Это подтверждает что кодировка паттерна хорошо интернализирована и передача knowledge между agents эффективна.

## Предложения по улучшению агентов

### android-expert
- [ ] Зафиксировать в profile-knowledge: **Premium Gate UI Pattern via GetUserLimitsUseCase** — ViewModel инжектит use case, observeUserLimits в init, state.canX flag (default true), routing на paywall(source). Применяется к любому create/edit/delete/export-screen с лимитом. Третий экран в сессии (напоминания, update feed weekly CTA, templates+create) — паттерн полностью стабилизирован, интуитивен для будущих задач.
- [ ] Добавить: **FakeRepository override pattern via Kotlin `by` delegation** — для тестирования single method override без full-class copy. Пример: `FakeChecklistRepositoryWithCount(delegate: ChecklistRepository, count: Int) : ChecklistRepository by delegate { override fun countChecklistsByUser() = ... }`. Заметно упрощает VM-тесты когда нужно подменить одно поведение.
- [ ] Добавить в AppButton DS documentation: **Box(fillMaxWidth, contentAlignment=Center) для центрирования text+icon** — вместо trailing Spacer. Работает с wrapping text, соответствует Material 3 guidelines. Применено в AppButton, AppButtonSecondary (2026-05-06).

### kmp-expert
- [ ] Зафиксировать в profile: **Premium Gate pattern is KMP-safe** — GetUserLimitsUseCase в commonMain (использует existingReminders count + isPremium flag), ViewModel gate-logic pure Kotlin → готово к wasmJs при расширении гейтов на веб-экраны. Это значит: future web paywall будет рефакторингом UI, не переписью бизнес-логики.

### Другие агенты
- [ ] Отметить что **String localization consolidation pattern** (один ключ на все screens vs per-screen override) улучшает maintainability и локализацию. Если новые языки требуют per-context variants, это пересчёт в Translation Memory, не код-change. Рекомендовать при выборе стратегии для новых feature-копий.
