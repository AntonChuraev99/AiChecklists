---
status: In Progress
date: 2026-06-16
start_sha: ece458a91c08a5499bc716268d0859f8de6701dd
type: feature
complexity: Standard
impact: Medium
modules:
  - feature/home (ChecklistDetailScreen, FillDetailScreen)
  - core/designsystem (AppItemMetaChip, strings, link util)
---

# Clickable / pretty links in checklist item name & note

**Статус:** Done
**Дата:** 2026-06-16
**Start SHA:** ece458a91c08a5499bc716268d0859f8de6701dd
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/home, core/designsystem

## Цель

Когда пользователь вводит/вставляет URL в название (`text`) или описание (`note`)
пункта чек-листа, ссылка должна отображаться красиво и быть открываемой:

- **Строка целиком = ссылка** → компактный read-only тег-пилюля «🔗 domain»
  вместо длинного сырого URL (главная жалоба: LinkedIn URL занимал 3 строки).
- **Ссылка внутри текста** → URL заменяется компактным «🔗 domain» на своём месте.
- **Открыть по нажатию** → действие открытия живёт в `ItemDetailsSheet`
  (отдельная строка «Open link»), а не на карточке.

## Технический план

### Дизайн-инварианты (почему так)
- Карточка `ChecklistItemCard` использует 30/70 hit-zone overlay (combinedClickable
  поверх текста) → inline-ссылка под оверлеем не получит тап. Правило
  `ui-card-patterns.md`: на карточке только **read-only** индикаторы, действия — в sheet.
- В sheet имя → переименование, note-row → edit-dialog; «открыть ссылку» = новая
  action-строка (паттерн Reminder/Note/Priority/Delete).
- Открытие: `LocalUriHandler.openUri()` из **синхронного** onClick (web блокирует
  popup из корутины). Альтернатива на карточке не нужна — там read-only.

### Компоненты
1. `core/designsystem/.../util/LinkText.kt` (commonMain): KMP regex `https?://\S+`
   (без `android.util.Patterns`), `extractUrls`, `asWholeUrl`, `displayDomain`,
   `rememberLinkifiedText(raw, …)` → read-only `AnnotatedString` с «🔗 domain».
2. Read-only тег на карточке — переиспользовать `AppItemMetaChip` (icon=Link,
   label=domain) — он уже без onClick.
3. `ChecklistItemCard` (ChecklistDetailScreen.kt 1637-1662): `text` + `note`
   через linkify/тег.
4. `ItemDetailsSheet` (1828, 1873): домен read-only в subtitle note + новая строка
   «Open link» (icon=Link, subtitle=domain, onClick=openUri).
5. `FillItemCard` (FillDetailScreen.kt 328-362): `text` + `note` read-only тег.
6. strings.xml EN+RU: `detail_item_sheet_action_open_link`, contentDescription тега.

## Лог итераций

### Итерация 1 — 2026-06-16 — android-expert

**Что сделано:** Реализована KMP-совместимая библиотека для обнаружения и отображения ссылок в текстовых полях чек-листов.

**Что именно:**
- Новый модуль `core/designsystem/.../util/LinkText.kt` (commonMain): функции `extractUrls(text)` (regex `https?://\S+` без Android-only зависимостей), `String.asWholeUrl()` (проверка целой строки), `displayDomain(url)` (сокращение до домена), `rememberLinkifiedText(raw, linkColor)` (возвращает read-only `AnnotatedString` с заменой URL на «🔗 domain»).
- `ChecklistItemCard` (ChecklistDetailScreen.kt): 
  - Если `text` или `note` целиком ссылка → `AppItemMetaChip(Icons.Filled.Link, domain)` (read-only тег, как для Reminder/Priority).
  - Если ссылка внутри текста → `Text(rememberLinkifiedText(...))` (URL заменяется на «🔗 domain»).
- `ItemDetailsSheet` (ChecklistDetailScreen.kt): домен в subtitle note (read-only); плюс новая action-строка `ItemDetailsSheetRow(icon=Icons.Filled.Link, title="Open link", subtitle=domain, onClick=openUri)` на каждый уникальный URL из text+note. Ошибка openUri → `koinInject<AppLogger>().warning(...)` (НЕ snackbar).
- `FillItemCard` (FillDetailScreen.kt): read-only вид (без sheet, т.к. нет edit-режима) — теже теги + linkified text.
- Localization: EN `detail_item_sheet_action_open_link` = "Open link", RU = "Открыть ссылку" (в `composeResources/values{,-ru}/strings.xml`).

**Почему так:** 
- Карточка использует 30/70 hit-zone overlay → inline-ссылка под ним не получит тап (rule `ui-card-patterns`). Открытие действия живёт в sheet (отдельная строка, как Reminder/Delete/Note).
- `LocalUriHandler.openUri()` на wasmJs = `window.open()` → вызывается ТОЛЬКО из синхронного `onClick` (браузер блокирует popup из coroutine).
- Regex `https?://\S+` работает во всех платформах (commonMain); обрезка trailing-пунктуации (`?.,;`) предотвращает "http://example.com." как целевой URL.

**Баги/проблемы:** 
- Открытие ссылки на `FillDetailScreen` не реализовано → нет ItemDetailsSheet для fills (заведено как deferred, используется только read-only тег).
- `AppLogger` в проекте — DI-интерфейс (не singleton), требует `koinInject()`; сигнатура `warning(tag, message)` без throwable (без exceptio).

**Решение:** Скорректирован код под актуальные контракты `AppLogger` и структуру sheet'ов в обоих экранах. Deferred-ссылка на открытие fills записана в memory.

**Файлы изменены:** 9 (+ desingsystem utils, strings.xml ×2, ChecklistDetailScreen +108, FillDetailScreen +75, youtube-shorts update +34, graphify cache sync).

**Итог итерации:** Реализованы обнаружение URL, linkified-рендер read-only тегов и открытие из sheet (sync onClick для web popup). Валидация: `:androidApp:compileDebugKotlin` + `:composeApp:compileKotlinWasmJs` PASS; `:core:designsystem:testAndroidHostTest` LinkTextTest (15 кейсов) PASS. Deferred: открытие ссылок в `FillDetailScreen` → docs/todos/2026-06-16-open-link-in-filldetailscreen.md.

## Выводы

**Задача завершена**: ссылки (URL) в названии и описании пункта чек-листа теперь отображаются красиво (URL целиком или внутри текста → компактный read-only тег «🔗 domain»). На карточке ссылка не интерактивна (hit-zone overlay, rule `ui-card-patterns`); открытие вынесено в ItemDetailsSheet отдельной action-строкой «Open link» (sync onClick → web popup).

**Ключевые решения:**
1. **KMP-совместимая утилита** — `LinkText.kt` (commonMain) без Android-only зависимостей (regex `https?://\S+` вместо `android.util.Patterns`). Чистые функции `extractUrls`, `displayDomain`, `asWholeUrl` → JVM-тест (15 кейсов, PASS).
2. **Read-only renderинг** — `rememberLinkifiedText` заменяет URL на AnnotatedString с tagged-диапазонами; chip-тег `AppItemMetaChip(Icons.Filled.Link, domain)` на карточке без onClick.
3. **Открытие из sheet** — `LocalUriHandler.openUri()` вызывается из синхронного `ItemDetailsSheetRow.onClick` (критично для web; coroutine → popup=blocked). Ошибка → `AppLogger.warning(...)` (не snackbar).
4. **Две платформы** — ChecklistDetailScreen (edit-режим) + FillDetailScreen (read-only) оба покрыты; открытие в FillDetailScreen отложено (нет sheet).

**Валидация:**
- Компиляция: `:androidApp:compileDebugKotlin` ✓, `:composeApp:compileKotlinWasmJs` ✓
- Тесты: `:core:designsystem:testAndroidHostTest` LinkTextTest (15 кейсов) ✓
- Визуальная проверка: Pixel 9 (EN режим) — теги отображаются, открытие работает ✓

## Предложения по улучшению агентов

### android-expert
- [ ] Документировать паттерн KMP regex-utilities без `java.net.URI`/`android.util.Patterns` для обнаружения URL в commonMain (переиспользуемый для future link-related задач)

### kmp-expert
- [ ] Уточнить best practice для wasmJs popup: всегда вызывать `LocalUriHandler.openUri()` из синхронного onClick на web (избегать coroutine) — добавить в документацию wasmJs interop patterns
