---
title: Extract FilePicker from feature:analyze to core (avoid lateral feature dep)
date: 2026-05-15
status: resolved
resolution_date: 2026-06-17
parent_task: docs/active/item-attachments-2026-05-15.md
blocking_reason: ""
resolution_note: "Extracted to new core/filepicker/api (Compose-enabled; core/common/api kept data-only). 14 files (picker + recorder + player) git-mv'd + package-renamed feature.analyze.presentation.{picker,recorder} -> core.filepicker.api.{picker,recorder}. feature.analyze dep removed from home + aichat:impl (composeApp got a direct core.filepicker.api dep — transitive leak via implementation). Verified androidApp:compileDebugKotlin + composeApp:compileKotlinWasmJs PASS, dependency-tree clean (home/aichat no longer depend on analyze). iOS pending next iOS build cycle."
resume_trigger: "N/A — resolved 2026-06-17."
estimated_complexity: Standard
keywords: [refactor, architecture, file-picker, lateral-coupling, core-extraction, expect-actual]
---

# Extract FilePicker from feature:analyze to core

## Что отложено

`FilePicker` (expect class + 3 actuals) сейчас живёт в `feature/analyze/src/.../picker/`. После Phase 4 attachments фичи `feature:home` пришлось добавить `implementation(projects.feature.analyze)` в `build.gradle.kts` чтобы получить доступ к `rememberFilePickerLauncher`. Это **lateral feature-to-feature coupling** — `home` не должен зависеть от `analyze`.

Anchor в коде: `feature/home/build.gradle.kts:41-44` (комментарий с ссылкой на этот todo).

## Контекст

Архитектура проекта (CLAUDE.md «Module Structure»):
- `core/*` — переиспользуемые слои
- `feature/*` — изолированные фичи, должны зависеть только от `core/*` и `feature/checklist` (общая модель)

`feature:home` сейчас зависит от `feature:analyze` ради 5 файлов FilePicker'а — это тащит транзитивно весь Gemini SDK, аналитический engine, etc. Bundle size + slower compile.

## Что нужно сделать при возобновлении

- [ ] Создать новый модуль `core/filepicker/api` (или просто положить файлы в `core/common/api` — обсудить)
- [ ] Переместить файлы:
  - `feature/analyze/.../picker/FilePicker.kt` → `core/.../FilePicker.kt`
  - `feature/analyze/.../picker/FilePickerResult.kt` → `core/.../FilePickerResult.kt`
  - `feature/analyze/src/androidMain/.../picker/FilePicker.android.kt` → `core/.../androidMain/.../FilePicker.android.kt`
  - `feature/analyze/src/iosMain/.../picker/FilePicker.ios.kt` → ...
  - `feature/analyze/src/wasmJsMain/.../picker/FilePicker.wasmJs.kt` → ...
- [ ] Обновить imports в `feature:analyze` (AnalyzeViewModel, AnalyzeScreen) и в `feature:home` (ChecklistDetailScreen)
- [ ] `feature/home/build.gradle.kts` — удалить `implementation(projects.feature.analyze)` и `Pending:` комментарий, добавить `implementation(projects.core.filepicker.api)` (или оставить через core/common/api)
- [ ] `feature/analyze/build.gradle.kts` — добавить `implementation(projects.core.filepicker.api)` (или ничего, если в core/common/api)
- [ ] Запустить `./gradlew :androidApp:compileDebugKotlin` для верификации

## Как возобновить (для cold-start агента)

Это чистый refactor — никаких behavior changes. Перемещаем 5 файлов между модулями + обновляем imports в 2 местах.

Tip: предпочтительно `core/common/api` (текущий host для `AttachmentStorage` и подобных платформенных абстракций) — не плоди новый модуль если можешь добавить в существующий. Размер `core/common/api` сейчас небольшой, FilePicker туда вписывается.

Anchor в коде, который надо удалить после refactor:
```
feature/home/build.gradle.kts:41-44
// Pending: docs/todos/2026-05-15-extract-filepicker-to-core.md
// FilePicker currently lives under feature:analyze; sharing it across features
// creates a lateral coupling. Extract to core/common/api or new core/filepicker.
implementation(projects.feature.analyze)
```
