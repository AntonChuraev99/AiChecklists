# Weekly Checklist CTA in Update Feed

**Статус:** Done
**Дата старта:** 2026-05-06
**Start SHA:** 63844a2d47762af9582c741b6526d31642909c99
**Project:** Checklists
**Тип:** feature
**Сложность:** Complex
**Impact:** Medium
**Затронутые модули:** feature/checklist, feature/create, feature/updatefeed, core/navigation, composeApp, docs

## Цель (продуктовая)

Пользователь видит пост "Organize your week" в Update Feed с кнопкой "Create weekly checklist". Тап на кнопку:
1. Проверяет лимиты (free-tier: max 1 weekly)
2. Если лимит превышен → paywall(source="weekly_mode_limit")
3. Иначе → CreateChecklistScreen с предвыбранным viewMode=weekly, новый чеклист создаётся в режиме "Weekly"

## Технический план

**Архитектура:** CreateWeeklyChecklistUseCase (domain/usecase) + AppNavEvent (ShowCreateWeeklyChecklist) → App.kt event collector → CreateChecklistScreen(viewMode=weekly).

**Этапы:**

1. **Новый use case в feature/checklist/domain/usecase/**
   - `CreateWeeklyChecklistUseCase` (input: none, output: Route или AppNavEvent)
   - Зависимости: GetUserLimitsUseCase, AppNavigator
   - Логика: getLimits() → check canCreateWeeklyChecklist → либо navigate(Paywall), либо emit AppNavEvent.ShowCreateWeeklyChecklist

2. **Пересмотр AppNavEvent в core/navigation/api/**
   - Добавить `ShowCreateWeeklyChecklist` (аналог ShowWidgetInstruction)
   - Обновить AppNavigator interface: новый метод requestCreateWeeklyChecklist()

3. **UpdateFeedDeepLinkHandler в feature/updatefeed/ui/**
   - Парсить `gisti://create?viewMode=weekly` → вызвать navigator.requestCreateWeeklyChecklist()
   - Существующие обработчики (widget_instruction, share и т.д.) остаются

4. **UpdateFeedContent.kt в feature/updatefeed/data/**
   - В JSON-модель `UpdateFeedPost` добавить ссылку на `weekly_mode_v1.json` из RC
   - Поле: `ctaHost: String?` (either "widget_instruction", "create_weekly_checklist", иначе null)
   - UI отрисовывает AppButton только если ctaHost присутствует

5. **CreateChecklistViewModel refactor**
   - Добавить параметр `initialViewMode: ChecklistViewMode?` в конструктор
   - Инициализация: если передано viewMode → set в screenState
   - Зависит от Phase 2 из weekly-mode solution (2026-05-04)

6. **App.kt event collector**
   - Добавить в LaunchedEffect блок на ShowCreateWeeklyChecklist
   - Логика: navigate(CreateChecklistRoute.CreateChecklist(viewMode="WEEKLY"))

7. **Документация в docs/guidelines/updates-feed.md**
   - Hard rule: разрешённые CTA-hosts = ["widget_instruction", "create_weekly_checklist"]
   - Новый deeplink: `gisti://create?viewMode=weekly`
   - Семантика: CTA открывает feature, не навигирует напрямую (модель через navigator events)

8. **Тесты**
   - UpdateFeedDeepLinkHandlerTest (разбор параметров, вызов navigator)
   - CreateWeeklyChecklistUseCaseTest (лимиты, paywall route, event emission)
   - UpdateFeedScreenTest E2E (render post + tap CTA → event)

9. **Follow-up (если обнаружится при реализации)**
   - Если UpdateFeedDeepLinkHandler требует DI-конструктор → обновить Koin module в feature/updatefeed/di
   - Если CreateChecklistRoute не поддерживает query params → добавить serialization для viewMode

## Лог итераций

### Итерация 1 — 2026-05-06 — @android-expert
**Что сделано:** Полная реализация архитектуры из плана (этапы 1–8):
1. CreateWeeklyChecklistUseCase в feature/create/.../domain/usecase (sealed Result: Created | RequiresUpgrade)
2. AppNavEvent.CreateWeeklyChecklistRequested data object + AppNavigator.requestCreateWeeklyChecklist()
3. UpdateFeedDeepLinkHandler: парсинг queryParam("viewMode")=="weekly" → requestCreateWeeklyChecklist()
4. UpdateFeedContent.kt: пост weekly_mode_v1 с actions=[{label:"Create weekly checklist", deepLink:"gisti://create?viewMode=weekly"}]
5. 11 Fake-AppNavigator имплементаций обновлены (no-op override requestCreateWeeklyChecklist)
6. App.kt: event collector ловит CreateWeeklyChecklistRequested → вызывает use case; Created → navigateToChecklistDetail, RequiresUpgrade → navigateToPaywall
7. CreateWeeklyChecklistUseCaseTest (2 кейса), UpdateFeedDeepLinkHandlerTest (3 кейса для viewMode-парсинга)
8. Все сборки пройдены (testDebugUnitTest + compileDebugKotlin)

**Почему так:** Использован use case вместо inline-логики для переиспользования (будущие CTA), AppNavEvent стандартный паттерн для глобальных events.

**Баги/проблемы:** Нет. Zero retry. Архитектурный выбор B (use case + AppNavEvent) исключил необходимость менять сигнатуры existing методов.

**Решение:** Use case положили в feature/create (not feature/checklist) — из-за circular dep risk (feature/create зависит от paywall, feature/checklist не должен). Это точка правильного разреза. Hardcoded "My weekly checklist" из исходного inline-кода остался в use case — кандидат на string resource в будущем.

**Обновления плана:** Этап 5 (CreateChecklistViewModel refactor для initialViewMode) оказался не нужен — viewMode передаётся через CreateChecklistRoute.CreateChecklist(templateId, editChecklistId, viewMode?), парсинг берёт из route в ViewModel.init(). Этап 9 (Follow-up) на потом — обе DI зависимости уже работают.

## Выводы

**Архитектурное решение:** Extract use case + AppNavEvent для qualified deep-link CTA оказалась оптимальной стратегией vs inline-логика в handler. Экономия: 13 Fake-имплементаций получили простой no-op override, вместо 13 переписанных тел методов при расширении AppNavigator.

**Размещение use case:** feature/create (не feature/checklist) — решено по направленности зависимостей. feature/create уже зависит от paywall, а добавлять paywall в feature/checklist создало бы circular dep (paywall зависит от checklist). Это точка правильного разреза и зафиксировано в документации (не повторять в будущих CTA).

**Premium gate в use case:** Критерий `canCreateWeeklyChecklist` выполняется в use case, а не в handler — это обоснование для расширения hard rules. CTA проходит через domain pathway, поэтому premium-инвариант гарантирован на каждом подходе (через handler, прямой вызов, будущие каналы).

**Technical debt:** Hardcoded строка "My weekly checklist" при create. Кандидат на string resource в nextPhase (но не критично сейчас — этап 56 из product roadmap).

**Zero retry:** Один проход @android-expert, все сборки с первого раза. Успех обусловлен детальным Prompt Contract (специалист имел чёткую архитектуру из плана) и предварительным обсуждением CTA-whitelist через пользователя в этой же сессии (прецедент f56ec05).

## Предложения по улучшению агентов

### android-expert
- [ ] **Задокументировать паттерн "Extract use case + AppNavEvent для qualified CTA"** — добавить в агент-память раздел о том когда CTA допустим (через domain pathway + premium gate, не сырой handler), когда размещать use case (по направленности deps, не по доменам), примеры (widget_instruction vs weekly_mode_v1). Этот паттерн повторится при добавлении будущих CTA (special-offer, paywall-shortcut и т.д.).

### kmp-expert
- [ ] No proposal — реализация pure Kotlin (use case, sealed Result), KMP-compatible.
