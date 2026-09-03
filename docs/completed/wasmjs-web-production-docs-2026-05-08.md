# WasmJs Web Target — Production Documentation Update

**Статус:** Done
**Дата старта:** 2026-05-08
**Start SHA:** bc3ca480
**Project:** Gisti AI Checklists
**Тип:** documentation
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** docs/, wrangler.jsonc, CLAUDE.md (project-level), deployment infrastructure

## Цель (продуктовая)

Актуализировать публичную документацию проекта, чтобы отразить:
1. Веб-версия Gisti запущена в production на https://checklists.churaevanton.workers.dev/
2. Инфраструктура: Cloudflare Workers Static Assets (вместо Firebase Hosting)
3. Технический stack веб-версии (Room 3.0 OPFS, Firebase JS SDK, KMP wasmJs)
4. Ссылка на solution doc Phase 4 (File I/O + Share + Print)

После завершения документация будет актуальна для новых contributors и публичных users.

## Технический план

1. **CLAUDE.md (project-level)**
   - Обновить `## Project Overview` с информацией о web-версии (URL, статус — production)
   - Добавить web-таргет в `## Build Commands` (npm run build для web)
   - Обновить `## Architecture` — добавить web-таргет как наравне с Android/iOS

2. **wrangler.jsonc**
   - Проверить header-комментарий, добавить real URL если его нет

3. **docs/plans/2026-05-07-feat-finish-wasmjs-web-plan.md**
   - Добавить COMPLETED-блок с датой завершения (2026-05-07) и ссылкой на solution doc Phase 4

4. **docs/solutions/features/wasmjs-web-target-cloudflare-2026-05-08.md** (новый)
   - Solution document с описанием деплоя, Stack, Patterns
   - Будет создан в COMPLETE-фазе

5. **docs/solutions/INDEX.md**
   - Добавить запись для нового solution doc
   - Будет обновлён в COMPLETE-фазе (главный агент)

## Лог итераций

### Итерация 1 — 2026-05-08 — doc-writer (COMPLETE фаза)
**Что сделано:** Создан постоянный solution document `docs/solutions/features/wasmjs-web-target-cloudflare-2026-05-08.md` с полным описанием деплоя, архитектуры, паттернов и gotchas. Обновлена project memory (dangling reference заменена на правильную). Активный документ переведён в статус Done. Выведены STATS_ROW и INDEX_ROW для главного агента.

**Почему так:** web-таргет (Cloudflare Workers vs Firebase Hosting) был выбран за несколько часов до COMPLETE-фазы (главный агент принял решение), но документация не была актуализирована. COMPLETE-фаза закрывает документирование: всё, что произошло в Phases 1–9, теперь отражено в постоянном solution doc. Dangling reference в MEMORY.md (на несуществующий файл 2026-05-07) заменена на новую ссылку.

**Баги/проблемы:** Не было — это чистое документирование уже завершённой работы. Phase 4 (AI + Sharing) была завершена в предыдущей сессии, Phase 9 (deploy) произошла на Cloudflare вместо Firebase Hosting. Документация отставала на 1 день.

**Решение:** Структурированный solution doc с 9 основными секциями (TL;DR, URL, trade-off table, playbook, config anatomy, headers, backend CORS, secrets injection, bundle size, patterns, pitfalls, troubleshooting, maintenance). Keywords выбраны с фокусом на domain umbrella (wasmjs, cloudflare, web-deploy) + конкретные технологии (workers, opfs, init-js, coop-coep).

**Zero-retry — один вывод STATS_ROW + INDEX_ROW.**

## Выводы

1. **Cloudflare Workers Static Assets выигрывает по скорости для распределяемых KMP wasm артефактов.** Одна строка `"site": { "bucket": "..." }` в `wrangler.jsonc` заменяет Firebase init + заголовки в `firebase.json`. Кроме того, push-to-deploy CI/CD из коробки (vs Firebase требует GitHub Actions или ручной CLI). Trade-off таблица явно указывает, когда Firebase может быть нужен (custom domain, Firebase Hosting specific features).

2. **COOP/COEP/CORP — критичны для KMP wasmJs с SharedArrayBuffer.** Cloudflare предоставляет их автоматически; Firebase требует явной конфигурации. Build-time секреты (via Gradle task + шаблон) позволяют хранить Firebase web config без коммита в git. Паттерн повторяем для любого KMP wasm проекта с платформо-специфичной конфигурацией.

3. **Wasm streaming в Playwright ~26 MB → truncation.** Реальные браузеры работают отлично (Compose 1.9.3 ~26 MB streams clean в Chrome/Firefox). Playwright бьётся на известной ошибке (WA streaming timeout + chunking в Playwright's Chromium). Решение: unit tests (commonTest ComposeUiTest) + manual smoke test в настоящем Chrome. E2E render-тесты skip'аны логично (не производственный блокер).

4. **6 Cloud Functions должны быть CORS-aware перед web deployment.** Паттерн: единая `create_error_response()` / `create_success_response()` wrapper, которая добавляет CORS на все endpoints. Preflight (OPTIONS) обработчик нужен на каждой функции. Deployment — explicit, не `firebase deploy` всех функций (слишком широко).

5. **Dangling doc reference в project memory → потеря контекста для будущих сессий.** Phase 4 doc существовал, но MEMORY.md указывала на несуществующий файл `wasmjs-phase4-web-io-2026-05-07.md`. Новый solution doc на 2026-05-08 закрывает оба: фазы Phase 4 (реализация) и Phase 9 (deploy). Lesson: MEMORY.md должна быть вычищена/перестроена в конце COMPLETE-фазы, не просто добавить ссылку на новый док.

## Предложения по улучшению агентов

### kmp-expert
- [ ] Добавить в раздел "wasmJs platform gotchas": `kotlinx-datetime` версия ДОЛЖНА совпадать со Kotlin compiler stdlib версией (0.7.x → Kotlin 2.3.20, 0.8.x → Kotlin 2.4+). Мismatch → `IrTypeAliasSymbolImpl already bound` на wasmJs IR linker (строже чем JVM).

### android-expert / kmp-expert (совместное)
- [ ] Cross-platform file I/O pattern: when web target needs sync read but browser API is async (file picker), suggest staging map pattern (`wasm-blob://uuid` keys → globalThis Map in init.js). Applicable beyond Checklists.

### doc-writer
- [ ] Docs-only COMPLETE задачи (Complexity=Standard, Impact=Medium, iterations=1, no code changes) → всё ещё требуют повной STATS_ROW + INDEX_ROW в финальном ответе (даже если нет solution doc создания, update memory только). Текущее правило неясное.
