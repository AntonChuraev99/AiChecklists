---
title: Attachments full support on iOS
date: 2026-05-15
status: deferred
parent_task: docs/active/item-attachments-2026-05-15.md
blocking_reason: waiting-for-decision
resume_trigger: When iOS app is published to App Store (currently iOS code-only, not released — see CLAUDE.md "iOS release strategy" section)
estimated_complexity: Standard
keywords: [attachments, ios, uikit, uidocumentpicker, uidocumentinteractioncontroller, deferred]
---

# Attachments full support on iOS

## Что отложено

Полноценная реализация `AttachmentStorage` и `AttachmentOpener` на iOS target. Сейчас:
- `core/common/api/src/iosMain/.../AttachmentStorage.ios.kt` — все методы `throw NotImplementedError`
- `core/common/api/src/iosMain/.../PlatformCapabilities.ios.kt` — `attachmentsSupported = false`
- `core/common/api/src/iosMain/.../AttachmentOpener.ios.kt` — `openExternally` returns false
- В UI секция Attachments на iOS не отображается

## Контекст

Per `CLAUDE.md`: «iOS release strategy: iOS version will be published after Android revenue covers the Apple Developer Program fee ($99/year). Until then, iOS target exists in code but is not actively released.» Поэтому всё, что выходит за пределы code-correctness (компилируемость) — отложено до релиза iOS.

## Что нужно сделать при возобновлении

- [ ] Реализовать `AttachmentStorage.ios.kt`:
  - `storeAttachment()` — `NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory)` → копировать содержимое source URL → `<documents>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>`. Вернуть `absoluteString` пути.
  - `deleteAttachment()` / `deleteAttachmentsFor*()` — `NSFileManager.removeItemAtURL`
  - `probeImage()` — через `UIImage(contentsOfFile:)` + `.size` (но это full decode; для cheap header probe лучше `CGImageSourceCreateWithURL` + `CGImageSourceCopyPropertiesAtIndex`)
  - `sizeOf()` — через `NSFileManager.attributesOfItemAtPath()[NSFileSize]`
- [ ] Реализовать `AttachmentOpener.ios.kt` через `UIDocumentInteractionController.presentPreviewAnimated(true)` (открывает файл в превью) или `presentOpenInMenuFromRect(...)` (показывает «Open in» меню всех совместимых apps).
- [ ] Перевести `PlatformCapabilities.attachmentsSupported = true` на iOS
- [ ] Найти существующий iOS FilePicker (`feature/analyze/src/iosMain/.../picker/FilePicker.ios.kt` уже существует — `UIDocumentPickerViewController`) и убедиться, что он отдаёт URLs совместимые с `storeAttachment`
- [ ] Тесты: instrumented UI test или ручной QA на симуляторе

## Как возобновить (для cold-start агента)

iOS уже имеет рабочий `FilePicker` под `feature/analyze/src/iosMain/.../picker/FilePicker.ios.kt` (использует `UIDocumentPickerViewController`). Логика такая же как Android — picker возвращает file URL, ViewModel передаёт его в `storeAttachment`. Storage layer должен скопировать содержимое в Documents directory.

Ключевые файлы:
- `core/common/api/src/iosMain/.../AttachmentStorage.ios.kt`
- `core/common/api/src/iosMain/.../AttachmentOpener.ios.kt`
- `core/common/api/src/iosMain/.../PlatformCapabilities.ios.kt`
- `composeApp/src/iosMain/.../di/PlatformModule.ios.kt` (Koin)
- `feature/analyze/src/iosMain/.../picker/FilePicker.ios.kt` (reference для UIKit interop патернов)

Документ-эталон: `docs/active/item-attachments-2026-05-15.md` Phase 2 — там описано как Android impl делает то же самое через `FileProvider` + `ContentResolver`.
