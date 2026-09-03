---
title: Attachments full support on wasmJs (web)
date: 2026-05-15
status: resolved
resolution_date: 2026-06-17
parent_task: docs/active/item-attachments-2026-05-15.md
blocking_reason: ""
resolution_note: "Implemented OPFS storage on wasmJs: globalThis.__opfs* JS bridges in init.js.template (store/delete/stat/readAsUrl/readBytes/probeImage/openExternally, Promise never-reject), AttachmentStorage.wasmJs (opfs://attachments/<fillId>/<itemId>/<attachmentId>.<ext>), AttachmentOpener.wasmJs (popup-safe <a>-click), PlatformCapabilities.attachmentsSupported=true, Coil 3 custom OpfsImageFetcher (options.fileSystem; persistent path from Room). async->sync via Promise<JsAny?>+await. Verified :composeApp:compileKotlinWasmJs PASS. Manual browser check pending: rebuild dev bundle, attach image -> reload -> persists."
resume_trigger: "N/A — resolved 2026-06-17."
estimated_complexity: Complex
keywords: [attachments, wasmjs, opfs, web-storage, file-api, blob-url, deferred]
---

# Attachments full support on wasmJs (web)

## Что отложено

Полноценная реализация `AttachmentStorage` на wasmJs target. Сейчас:
- `core/common/api/src/wasmJsMain/.../AttachmentStorage.wasmJs.kt` — `storeAttachment` возвращает `null`, остальные методы no-op
- `core/common/api/src/wasmJsMain/.../PlatformCapabilities.wasmJs.kt` — `attachmentsSupported = false`
- В UI секция Attachments на web просто **не отображается** (gate в `ItemDetailsSheet`)
- `core/common/api/src/wasmJsMain/.../AttachmentOpener.wasmJs.kt` — `openExternally` returns false

## Контекст

Item Attachments фича (Phase 1–4) полностью реализована на Android. Web — отложено на v2:
1. **Storage problem**: `filesDir` не существует в браузере. Object URLs (`blob:`) умирают при reload страницы. Нужен полноценный OPFS (Origin Private File System) backend.
2. **Picker problem**: существующий `feature/analyze/.../picker/FilePicker.wasmJs.kt` уже умеет выбирать файл через `globalThis.__pickFile` (init.js bridge), но возвращает временный blob path. Для durable storage нужен copy в OPFS.
3. **Coil 3 wasmJs**: уже работает (артефакт `coil-wasm-js` 3.4.0), нужно только дать ему путь к OPFS-файлу.

Коммерческая выгода web-attachments сомнительна для v1: web-аудитория Gisti — secondary surface, основной revenue с Android Premium.

## Что нужно сделать при возобновлении

- [ ] Реализовать `OpfsAttachmentStorage` через `navigator.storage.getDirectory()` API:
  - `storeAttachment()` — копировать blob из picker в OPFS файл `attachments/<fillId>/<itemId>/<attachmentId>.<ext>`, вернуть `opfs://...` псевдо-путь
  - `deleteAttachment()` / `deleteAttachmentsFor*()` — удаление через `FileSystemDirectoryHandle.removeEntry()`
  - `probeImage()` — через `createImageBitmap()` для w/h
  - `sizeOf()` — через `FileSystemFileHandle.getFile().size`
- [ ] Расширить Coil 3 чтобы понимал `opfs://` URI (custom Fetcher) ИЛИ конвертировать в `blob:` URL для Coil
- [ ] Реализовать `AttachmentOpener.wasmJs.kt`: создать blob URL и `window.open(url)` (или `<a download>` для скачивания)
- [ ] Перевести `PlatformCapabilities.attachmentsSupported = true` на wasmJs
- [ ] Тесты: интеграционный smoke-test через Karma/Jest для OPFS round-trip
- [ ] Размер OPFS quota check (по умолчанию ~50% от свободного места) + UI snackbar на overflow

## Как возобновить (для cold-start агента)

Открой `docs/active/item-attachments-2026-05-15.md` (или `docs/solutions/features/item-attachments-2026-05-15.md` если переехал в solutions). Прочитай Phase 1+2 — там описана архитектура `AttachmentStorage` expect/actual + `AttachmentStoragePort` interface для FakeAttachmentStorage. wasmJs реализация должна имплементировать тот же `AttachmentStoragePort`.

Ключевые файлы:
- `core/common/api/src/wasmJsMain/.../AttachmentStorage.wasmJs.kt` (текущий стаб)
- `core/common/api/src/wasmJsMain/.../PlatformCapabilities.wasmJs.kt` (флаг)
- `composeApp/src/wasmJsMain/.../resources/init.js.template` (JS bridges — вероятно, добавить OPFS helpers здесь)
- `composeApp/src/wasmJsMain/.../di/PlatformModule.wasmJs.kt` (Koin singleton)

Best practice для OPFS в Compose Multiplatform — глянуть как Room 3 wasmJs driver работает (он уже использует OPFS для SQLite через `sqlite3-web` artifact). Там есть рабочий пример FileSystemHandle JS interop.
