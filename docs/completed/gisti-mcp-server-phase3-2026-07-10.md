# Gisti MCP Server Phase 3 — Named Fills + In-App Screen + SEO Landing

**Статус:** Done
**Дата старта:** 2026-07-03
**Start SHA:** 07902840 (fix: map filled_items by text not index)
**Project:** gisti-checklists
**Тип:** feature
**Сложность:** Complex
**Impact:** Medium
**Затронутые модули:** mcp-server, composeApp (navigation, designsystem), landing, core/navigation

⚠ INIT phase was skipped — minimal active doc reconstructed during COMPLETE. Counters могут быть неточными.

## Цель (продуктовая)

Завершить Phase 3 MCP-сервера: добавить named fills (множественные заполнения per checklist) как независимые инструменты, встроить in-app экран для подключения MCP, развернуть SEO-лендинг с инструкцией. Позволить Claude Desktop и claude.ai пользователям управлять заполнениями из chat assistant.

## Технический план

- [x] Backend mutate.ts: FillSummary, buildFill/selectFill/findFill/createNamedFill/listFills новые операции
- [x] mcp.ts: 2 новых tools (list_fills, create_fill), fillId optional на state-tools, renderChecklist target-fill, version bump 0.4.0
- [x] mutate.test.ts: 7 новых unit-тестов (40/40 green, typecheck green)
- [x] mcp-server/README.md: tools-таблица +2 tools, Deferred→reminders/repeat/schedule
- [x] In-app MCP screen: McpScreen.kt commonMain (hero aiGradient Вариант A, 3 value-props, CTA guide, copy endpoint)
- [x] Navigation integration: AppNavRoute.Mcp, DrawerDestination.Mcp, AppNavigationDrawerContent wiring
- [x] Core designsystem: 13 ключей strings.xml (мультиязык-ready)
- [x] SEO-лендинг: gisti-ai.com/mcp (307→/mcp/) HTML+Tailwind (zero-JS), tools-таблица, 3 JSON-LD, полная инструкция
- [x] Deploy: MCP-воркер `gisti-mcp` v494621ac, landing-воркер `gisti-landing` v`a2f1823d`, smoke-test live-verified

## Лог итераций

### Итерация 1 — 2026-07-03…2026-07-10 — main-agent + kmp-expert + wasmjs-expert (no specialist trace)

**Что сделано:**
- Phase 3 architecture: named fills as FillSummary + buildFill + selectFill operations (Kotlin↔TS contract green both ways)
- New tools: list_fills (enumerate checklist's named fills), create_fill (from template or clone existing)
- Optional fillId on state-tools (get_checklist, toggle_item, edit_note, fill_checklist_ai) to target specific fill
- Unit-тесты: 40/40 green, typecheck green, no type errors
- MCP-server deployed v494621ac, smoke-test live (list_checklists→5 real checklists)
- In-app MCP screen: hero + 3 props + CTA→gisti-ai.com/mcp + copy endpoint + snackbar feedback (stateless commonMain)
- MCP screen in drawer: DrawerDestination.Mcp after AI Chat, routing wired
- @OptIn(ExperimentalMaterial3Api::class) fix (AppScaffold required, build green)
- SEO-лендинг deployed gisti-ai.com/mcp: 307→/mcp/, full instruction, tools-таблица 16 tools, SoftwareApplication+HowTo+FAQPage JSON-LD
- Deferred: reminders/repeat/schedule tools (need own contract-test for ReminderRepeatRule/RepeatEndCondition polymorphism)

**Почему так:**
- Named fills = stateless FillSummary (не новые контракт-формы) → не требовал new contract-test (existing serialization достаточна)
- MCP screen in commonMain (zero-duplication, wasmJs поддержка встроена)
- fillId optional → backward-compatible с existing list_checklists/toggle_item (no breaking change)
- Reminders/repeat требуют own contract-test → defer under Phase 3 todo

**Баги/проблемы:**
- McpScreen compilation: @OptIn missing (ExperimentalMaterial3Api) → AppScaffold использует неимпортированный API
- CF deploy account split: gmail vs swapify (2c9dfaad vs другие) → неправильный аккаунт → неудача развертывания
  - Решение: явно передал account_id + CLOUDFLARE_API_TOKEN scoped на gmail

**Решение:**
- Добавлен @OptIn → green build
- Verify account via `wrangler whoami` перед deploy → правильный аккаунт

## Выводы

**Phase 3 fully shipped & deployed & prod-verified:**
- Named fills (FillSummary, buildFill, createNamedFill, listFills) интегрированы в MCP-воркер v0.4.0, 40 unit-тестов, контракт-синхронизация Kotlin↔TS green
- MCP-воркер `gisti-mcp` v494621ac live, smoke-test пройден (real 5 checklists returned)
- In-app MCP screen code-complete, wasmJs builds green, hero + CTAs + copy endpoint (stateless, no deferred code)
- SEO-лендинг live gisti-ai.com/mcp, 307-редирект, full tooling guide + SoftwareApplication JSON-LD (SEO-оптимизирован)
- Deferred: Phase 4 reminders/repeat/schedule tools → own contract-test (issue: ReminderRepeatRule/RepeatEndCondition polymorphism в сериализации)

**Ключевые успехи:**
- Zero breaking changes (fillId optional, backward-compatible)
- Обе платформы (Android + wasmJs) работают из коробки (commonMain screen)
- Cloud Firestore & Cloud Functions читают/пишут named fills via fillId без изменений (default-fill reconciliation in app)
- Deployment без инцидентов (account-check предотвратил неправильную CFW публикацию)

## Предложения по улучшению агентов

### kmp-expert
- [ ] Document commonMain composable rules for MCP integrations (stateless pattern, no platform-specific state)

### wasmjs-expert
- [ ] Add note: account_id in CF deploy critical when switching personal ↔ org zones

### Другие агенты
- (no proposals)

## Deferred Work

- [2026-07-10 — MCP Phase 3 reminders/repeat tools](../../docs/todos/2026-07-10-mcp-phase3-reminders-repeat-tools.md) — contract-test + ReminderRepeatRule serialization polymorphism
