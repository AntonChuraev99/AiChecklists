# Cloudflare CI Timeout Fix — compose/dev Content Filter

**Статус:** Done
**Дата старта:** 2026-06-03
**Start SHA:** 12c21f1a
**Project:** gisti-checklists
**Тип:** infrastructure / bug-fix
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** build-system / settings.gradle.kts / Cloudflare Workers Builds CI

## Цель (продуктовая)
Восстановить доставку wasmJs-продакшена на https://checklists.gisti.workers.dev. Последние 5 CI-билдов (master, 2026-05-29 – 2026-06-03) падали с `Build took too long and was timed out` на ~21-й минуте, блокируя прод-обновления.

## Технический план
1. Диагностировать причину timeout (конфиг / производительность / объем артефактов).
2. Сравнить конфиг settings.gradle.kts с work-продуктом (reference проект swapfaceandroid, собирается на том же Cloudflare за 13 мин).
3. Найти корень медленного резолва (холодный Maven-резолв).
4. Применить content{}-фильтр к нишевым/медленным репозиториям.
5. Валидировать на CI (build должен пройти < 15 мин).
6. Задеплоить прод.

## Лог итераций

### Итерация 1 — 2026-06-03 — диагностика + фикс (разработчик)

**Что сделано:**
- Дампил логи из 5 последних CI-билдов на Cloudflare Workers Builds: все упали на фазе configuration/dependency-resolution (~21 мин), до компиляции не дошли. Сообщение: `Build took too long and was timed out`.
- Скомпарировал settings.gradle.kts между Gisti и свapfaceandroid (reference, ~40 модулей, собирается на том же CI за 13 мин).
- Нашел дельту: в Gisti settings.gradle.kts репозиторий `maven.pkg.jetbrains.space/public/p/compose/dev` БЕЗ content{}-фильтра, в swapfaceandroid присутствует только с фильтром `includeGroupByRegex("org\\.jetbrains\\.(compose|skiko).*")`.
- Понял механизм: Gradle проверяет репозитории по порядку для каждого артефакта, ищет MD5/SHA на каждом → compose/dev (медленный, JetBrains Space) получает сотни 404-проб за cold-resolve, на Cloudflare кеш user.home не персистируется между билдами → каждый раз холодный резолв добавляет минуты.
- Compose MP 1.11.0 (verified as released May 2026) резолвится с Maven Central, на compose/dev лежат только Compose/Skiko группы → content-фильтр безопасен и полезен.

**Почему так:**
- Compose/Skiko шлют pre-release версии в JetBrains Space (dedicate channel для скоростной итерации), но куренты релизы идут на Maven Central.
- На CI (Cloudflare Workers Builds) нет персистентного Gradle user home между сборками → cold-resolve = режим восстановления для каждого артефакта + сотни проб медленного репо.
- Reference-проект (swapfaceandroid) уже применял эту оптимизацию → доказал pattern.

**Баги/проблемы:**
Нет — диагностика проста, фикс бесспорен (content-фильтр, ничего не потеряем).

**Решение:**
```kotlin
// settings.gradle.kts, pluginManagement + dependencyResolutionManagement блоки
maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
    content { includeGroupByRegex("org\\.jetbrains\\.(compose|skiko).*") }
}
```
- Репозиторий перемещен ПОСЛЕДНИМ в оба блока (pluginManagement + dependencyResolutionManagement).
- Добавлен content{}-фильтр, ограничивающий проб только Compose/Skiko группами.
- Комментарий в коде для будущего (9–16, 29–32 строки): почему медленно, почему content{} нужен, когда это может измениться.

**Результат валидации:**
- `./gradlew composeApp:wasmJsBrowserDistribution` на локальной машине → BUILD SUCCESSFUL in 4m 30s (без timeout, оптимизация Binaryen уже была).
- CI Cloudflare Workers Builds: `BUILD SUCCESSFUL in 9m 14s` (было: ~20 мин timeout).
- Прод задеплоен: `npx wrangler deploy` → https://checklists.gisti.workers.dev, Version f67a36e5-8634-4167-9013-013b8311b841, modified_on 2026-06-03T13:36:35Z.

## Выводы

**Root Cause:** Gradle холодный резолв на CI без фильтра контента → сотни 404-проб на медленный JetBrains Space для каждого артефакта = перевал 20-мин лимита Cloudflare Workers Builds.

**Pattern на будущее:** Нишевые/медленные Maven-репо (compose/dev, jitpack, bytedance, space) ВСЕГДА оборачивать в content{ includeGroupByRegex(...) } фильтр. Иначе они налог на холодный CI-резолв, умножаются на количество неприватных зависимостей (100+ для Gisti).

**Сопутствующее замечание:** Binaryen wasm-opt оптимизирован прежде (один -O2 вместо 7×-O3, ~90с вместо 5–7 мин) → остаточным бутылочным горлышком оказался именно резолв, а не компиляция/оптимизация. В логе диагностики нет спешки на wasmJs-сборке компиляции, спешка была на dependency-resolution.

**Дрифт замечен (вне scope фикса):** CLAUDE.md пишет «Compose MP 1.9.3», по факту libs.versions.toml = 1.11.0. Стоит обновить отдельно.

## Предложения по улучшению агентов

Нет специалистов в scope этой задачи (ops-фикс). Но на будущее:
- Для любого «CI timeout» task: ask to diff settings/build-configs с reference-проектом перед hypotheticals about code.
- Для любой KMP-сборки: validate composition & resolution time на cold-CI, не только локально.

---

**⚠️ INIT phase was skipped** — задача началась как ops-запрос («задеплой прод + почини CI») и не получила классификацию/план до реализации. Документ реконструирован во время COMPLETE. Counters: iterations=1 (фикс), solutions_read=0, memory_hits=0, errors_avoided=1 (стали дёт на перестраивание холодного резолва благодаря reference-проекту, без которого ещё пару часов гадали бы).
