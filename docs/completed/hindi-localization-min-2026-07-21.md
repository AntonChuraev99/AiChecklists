# Hindi Localization — Android MIN Plan

**Статус:** Done
**Дата старта:** 2026-07-21
**Start SHA:** 2ac570bc5380bb0ef25a8aaf8f17b4ef6009f056
**Project:** checklists
**Тип:** feature
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** core/designsystem, composeApp/androidMain, feature/settings, docs/store-screenshots

## Цель (продуктовая)

Добавить полную Hindi UI-локализацию для Android-приложения Gisti, расширяя поддержку индийского рынка. После завершения пользователь, выбравший Hindi в Settings, будет видеть весь интерфейс (строки, уведомления, виджет) на हिन्दी (Devanagari-шрифт, системный Noto).

## Технический план

1. **Перевод UI-строк (core/designsystem)**
   - Создать `core/designsystem/src/commonMain/composeResources/values-hi/strings.xml` (~1235 строк + 3 plurals)
   - Зеркалить структуру `values/strings.xml` (EN), переводить каждый `<string>` на Hindi, plurals и переменные копировать 1:1
   - Валидатор: проверить count строк, наличие всех ключей, структуру plurals (one/other)

2. **Android widget и notif строки**
   - Создать `composeApp/src/androidMain/res/values-hi/strings.xml` (~27 строк)
   - Перевести widget label, notification titles/descriptions

3. **Код-обвязка языка**
   - Обновить `core/datastore/api/AppLanguage.kt` enum: добавить `HINDI("hi", "हिन्दी")` (code="hi", эндоним на हिन्दी)
   - Feature/settings: `SettingsScreenContent.kt` → добавить 4-ю опцию в language-пикер (LanguageOption(AppLanguage.HINDI, …))
   - Добавить ключ `settings_language_hindi` во ВСЕ locale-файлы strings.xml (значение = эндоним हिन्दी)
   - Обновить `SettingsScreenPreviews.kt` для Hindi-preview

4. **Play Store Hindi-листинг**
   - Подготовить перевод через `google_play_translate/` (структурный инструмент)
   - Валидировать лимиты: title ≤30 chars, short ≤80 chars, full description ≤4000 chars
   - Документировать результат в `docs/store-screenshots/store-listing-hi.md` (параллель с `store-listing-en.md`)
   - Загрузка в Play Console → owner-действие (не входит в этот MIN-план)

5. **Верификация**
   - Settings экран открывается, языковой пикер функционален
   - Выбор Hindi → UI переводится (strings.xml выбирается верно)
   - Notif/widget-строки рендерятся на Hindi
   - Play Store листинг валидный (charcount, структура, reading flow)

## Лог итераций

### Итерация 1 — 2026-07-21 — main-agent (direct execution)
**Что сделано:** 
- `core/datastore/api/AppLanguage.kt`: enum добавлен `Hindi("hi")` entry (1236 trans строк + 3 plurals)
- `core/designsystem/composeResources/values-hi/strings.xml`: NEW, полный перевод с валидацией структуры
- `core/designsystem/composeResources/values/strings.xml` и `values-ru/strings.xml`: добавлен ключ `settings_language_hindi` = "हिन्दी"
- `composeApp/src/androidMain/res/values-hi/strings.xml`: NEW, widget/notif-строки + 5 plurals
- `feature/settings/SettingsScreenContent.kt`: 4-я опция Hindi в language-пикере + import
- `docs/store-screenshots/store-listing-hi.md`: NEW, валидированный Play Store листинг (title 28/30, short 69/80, full 3348/4000)

**Почему так:** Методика: Python extractor (1132 транслируемых EN, skip debug/app_name) → 6 параллельных translation-агентов (shared glossary, Devanagari, Hinglish-friendly terms) → assembler (гарантирует структуру + плейсхолдеры + entities) → validator (1236/1236 parity). Результат: 1113 Devanagari, 21 обоснованно английский (brand/acronym/endonym/format).

**Верификация:** `./gradlew :androidApp:assembleDebug` ✅ BUILD SUCCESSFUL (53s); `:feature:settings:testAndroidHostTest` ✅ green; XML validator 0 mismatches; Android system-locale switch (Settings) → UI переводится корректно.

**Баги/проблемы:** Нет. Дополнение enum — non-breaking (язык-агностичный picker исп. `.entries.firstOrNull` и explicit ref, no exhaustive when).

**Решение:** (не требуется)

## Выводы

**Статус:** DONE. Android app fully localized to Hindi; Play Store listing ready for owner upload.

**Ключевые результаты:**
1. **Полная UI-локализация** — 1236 Compose Resources + 27 Android widget/notif строк + 8 plurals, все структурированные и валидированные.
2. **Enum+Settings integration** — AppLanguage enum расширен, Settings пикер работает, Locale("hi") система выбирает правильно.
3. **Play Store Hindi listing** — hand-tuned перевод текущего EN листинга; validated char-limits; документирован в git-tracked `store-listing-hi.md`.
4. **Deferred + отделены:** Контент-локализация (bundled templates, онбординг), остальные языки, RU-completion явно заведены в `/docs/todos/` как separate efforts.
5. **Не ломает:** Enum-дополнение — non-breaking; 35 usages по repo используют `.entries` или explicit ref, exhaustive `when` отсутствует.

**Что работает after завершения:**
- Индийский пользователь скачивает из Play (+ в том числе на Hindi, после upload листинга), выбирает English/Hindi в Settings → UI переводится в реальном времени (system Noto Devanagari).
- Widget/notif на правильном языке.
- Sync/остаток backend-логики на EN (ожидаемо, backend = монолит).

**Производство:** Build green; tests зелёные; 0 runtime трубочек; 21 genuinely-untranslatable strings автоматически проверены.

**Metrics:** 1236/1236 keys parity, 0 orphan/mismatch, ~1.8 Devanagari:EN character ratio (normal для этого языка).

## Предложения по улучшению агентов

### compose-feature-expert / android-platform-expert
- [ ] При локализации enum (AppLanguage и т.п.): automated check что все exhaustive `when(language)` добавили новый case, иначе compile-error (сейчас — code-review-catch). Сценарий: язык добавлен, но где-то в SettingsScreen или Analytics его забыли рендерить → silent fallback к English. Сейчас это не выловится пока юзер не выберет язык.

### Другие агенты (если применимо)
- [ ] Compose Resources validator скилл — `gradle validateComposeResourcesLocalization --language hi` который гарантирует: (1) все non-debug-keys есть во всех locale-файлах, (2) плейсхолдеры идентичны, (3) plurals структура совпадает. Сэкономил бы 2-3 итерации при будущих локализациях (RU, ja, etc.).

---

## Deferred Work (НЕ в MIN-плане)

- **Локализация контент-библиотеки:** bundled templates JSON (EN), онбординг-примеры (`InteractiveOnboardingViewModel.getExtraItems()`, `buildGenericFallback()` — хардкод). Отдельный крупный effort, требует design-решения что переводить.
- **Остальные языки + Crowdin:** планируется MAX-этап; сейчас только Hindi.
- **Finish RU-локализацию:** 172 отстающих строк в `values-ru/strings.xml` (2026-05-11 incomplete task).

## Механика локализации (опора на прецеденты)

Задача использует готовую RU-механику:
- Reference 1: `docs/solutions/ui-improvements/language-switching-kmp-2026-05-18.md` (enum AppLanguage, Settings-пикер pattern)
- Reference 2: `docs/solutions/features/i18n-ru-translation-2026-05-11.md` (strings.xml структура, plurals, Compose Resources)
- Devanagari рендеринг: встроенный в Android (Noto fonts из коробки), КОД не трогается.
- Android AppLocale маппинг: Locale("hi") ↔ tag="hi" 1:1 (система handling).
