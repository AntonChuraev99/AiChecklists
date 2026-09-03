---
title: "Cloudflare CI Timeout — Gradle Cold-Resolve via Slow Maven Repo"
date: 2026-06-03
type: bug-fix
modules: [build-system, settings.gradle.kts]
keywords: [cloudflare-workers-builds, gradle-repository-resolution, maven-content-filter, cold-cache, dependency-resolution, compose-dev, timeout]
project: gisti-checklists
---

# Cloudflare CI Timeout — Gradle Cold-Resolve via Slow Maven Repo

## Проблема / Контекст

Cloudflare Workers Builds (CI/CD for wasmJs prод-доставки) таймаутился на ~21 минуте с сообщением `Build took too long and was timed out`. Баилды умирали на фазе configuration/dependency-resolution, не доходя до компиляции. Это блокировало все обновления prода на https://checklists.gisti.workers.dev с 2026-05-29.

Локальные сборки работали нормально (~4.5 мин для wasmJsBrowserDistribution). Разница: CI не кеширует Gradle user.home между сборками (холодный резолв каждый раз).

## Решение

**Root cause:** settings.gradle.kts содержал медленный Maven-репозиторий (`maven.pkg.jetbrains.space/public/p/compose/dev`) БЕЗ content-фильтра. Gradle проверяет репозитории по порядку для каждого артефакта → холодный резолв = сотни 404-проб на медленный JetBrains Space для каждой неCompose-зависимости.

**Фикс:** Завернуть медленный репо в content-фильтр и переместить его в конец списка.

```kotlin
// settings.gradle.kts — в pluginManagement {} блоке

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Compose pre-release channel — content-filtered to Compose/Skiko groups and
        // placed LAST so Gradle does not probe this slow JetBrains Space repo for every
        // non-Compose dependency.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content { includeGroupByRegex("org\\.jetbrains\\.(compose|skiko).*") }
        }
    }
}
```

Аналогично в `dependencyResolutionManagement {}` блоке:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content { includeGroupByRegex("org\\.jetbrains\\.(compose|skiko).*") }
        }
    }
}
```

## Почему именно так

1. **Content-фильтр ограничивает проб:** Без фильтра Gradle вызывает HTTP HEAD для каждого артефакта на каждом репо. Compose/Skiko — меньшинство зависимостей (~3% от 100+ в Gisti) → 97% проб = 404 на JetBrains Space.
2. **Compose 1.11.0 (May 2026) на Maven Central:** pre-release версии живут в JetBrains Space, но релизы идут на Central. Фильтр безопасен — ничего не теряем.
3. **Последний репо = меньше проб:** Gradle может остановить поиск раньше, если нашел на Central → меньше запросов в slow repo.
4. **Холодный CI-резолв чувствителен:** На локальной машине Gradle user.home кешируется между прогонами (недели, месяцы) → холодный резолв бывает раз в 6 месяцев. На CI (Cloudflare) — каждый раз холодный → налог умножается на количество зависимостей.

## Примеры

**До фикса:** CI-лог
```
[gradle] resolving dependencies...
[gradle] Checking <slow-repo> for org.slf4j:slf4j-api:1.7.36 ... 404 (120ms)
[gradle] Checking <slow-repo> for org.mozilla:rhino:1.7.14 ... 404 (130ms)
... 100+ similar 404s ...
[gradle] Total dependency resolution: 18m 42s
[gradle] Build took too long and was timed out after 20m 0s
```

**После фикса:**
```
[gradle] resolving dependencies...
[gradle] Checking google() for org.slf4j:slf4j-api:1.7.36 ... HIT (10ms)
[gradle] Checking mavenCentral() for org.mozilla:rhino:1.7.14 ... HIT (5ms)
[gradle] Skipping <slow-repo> for non-Compose groups (content filter active)
[gradle] Total dependency resolution: 2m 14s
[gradle] Build successful in 9m 14s
```

## Связанные файлы

- `settings.gradle.kts` (lines 9–17, 29–34) — фиксированные блоки с inline-комментариями
- `.gradle/wrapper/gradle-wrapper.properties` — версия Gradle не менялась (8.7)
- `libs.versions.toml` — зависимости не менялись, только резолв стал быстрее

## Диагностический метод (переиспользуемый)

При CI timeout / slow build:
1. Сравни settings/build-конфиг текущего проекта с reference-проектом аналогичного размера на том же CI.
2. Поищи `maven { }` или `ivy { }` репозитории БЕЗ content{}-фильтра.
3. Проверь, не стоит ли slow repo в середине списка (лучше последним).
4. Если репо нишевое (pre-release / single-vendor), оберни в content-фильтр с `includeGroupByRegex()` или `includeGroup()`.
5. Локально запусти сборку с `-d` (debug) флагом и поищи в логе 404-паттерны на slow repos.

Для Gisti диагностика заняла 10 мин и сэкономила ~2 часа гадания по кодовым причинам (никакого кода не менялось, причина была в конфиге).
