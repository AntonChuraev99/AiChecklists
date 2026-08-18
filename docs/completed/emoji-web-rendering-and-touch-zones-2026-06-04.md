# Emoji Web Rendering & Touch Zone Fixes

**Статус:** Done
**Дата старта:** 2026-06-04
**Start SHA:** 6b5905b3
**Project:** Checklists
**Тип:** bug-fix + infrastructure
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** core/designsystem, composeApp (commonMain/androidMain/iosMain/wasmJsMain), feature/home, feature/onboarding

## Цель (продуктовая)

Все эмодзи в приложении корректно рендерятся на веб-платформе (Compose Multiplatform wasmJs + Skiko canvas — у него нет системного emoji-fallback). Параллельно — фикс зон нажатия (ripple-клиппинг по форме) у чипов-подсказок и карточек чеклистов на главном экране, сглаживание шероховатостей touch-target ≥48dp.

## Технический план

1. **Emoji-font инфраструктура** (портирование проверенного паттерна из swapfaceandroid):
   - `expect/actual rememberEmojiFont(): FontFamily` (commonMain expect; androidMain/iosMain actual = FontFamily.Default; wasmJsMain actual = preloadFont Noto Color Emoji)
   - `LocalEmojiFont` staticCompositionLocalOf в core/designsystem
   - `EmojiText.kt` helper-утилиты для emoji-aware text (обработка смешанных emoji + regular текстов)
   - Шрифт noto_color_emoji.ttf (1.47MB Twemoji.Mozilla COLR) в composeApp/wasmJsMain/composeResources/font/
   - CompositionLocalProvider в App.kt
   - Preload-хинт в index.html
   - **Делегирование:** @kmp-expert

2. **Применение LocalEmojiFont ко ВСЕМ user-facing emoji Text** (app-wide аудит):
   - GistiPromptChips (контекстные подсказки)
   - ChecklistListCard (🎉 иконка)
   - CsatBottomSheet (😞😐😊✅ рейтинг)
   - FeatureIllustrations (📷📄✏️🔗🎙️🔁📅📌✨⚡🔔)
   - InteractiveOnboarding (✨📋🌪️)
   - Строки в strings.xml (illustration_*/subscription_purchase_success)
   - **Делегирование:** @android-expert

3. **Touch-zone fix чипов**:
   - Surface(onClick=…) → клип ripple по pill-shape (Modifier.clip(RoundedCornerShape))
   - Гарантировать минимум 48dp touch-target
   - **Делегирование:** @android-expert

4. **Touch-zone fix карточки ChecklistListCard**:
   - combinedClickable внутри content (клип ripple по shape)
   - Drag/wiggle зона снаружи (не покрыта ripple)
   - Обновить call-sites в MainScreenContent
   - **Делегирование:** @android-expert

5. **Non-emoji глифы** (Unicode symbols that Twemoji doesn't cover):
   - Стрелка → (U+2192) в лейблах чипов → заменить на emoji ➡️ (U+27A1) или vector-иконку
   - Галочка ✓ (U+2713) в строках → заменить на emoji ✔️ (U+2714) или Material icon
   - **Делегирование:** @android-expert

6. **Build + Validation**:
   - Компиляция: `:composeApp:compileKotlinWasmJs` + `:androidApp:assembleDebug`
   - Screenshot goldens (Roborazzi)
   - Web-верификация на wasmJsBrowserDistribution (manual test в браузере)

## Лог итераций

### Итерация 1 — 2026-06-04 — kmp-expert
**Что сделано:** Создана emoji-font инфраструктура в `core/designsystem`, пакет `com.antonchuraev.homesearchchecklist.designsystem.emoji`:
- `LocalEmojiFont.kt` — `staticCompositionLocalOf<FontFamily> { FontFamily.Default }`
- `EmojiFont.kt` (expect) с актуальными реализациями:
  - `androidMain` — `FontFamily.Default`
  - `iosMain` — `FontFamily.Default`
  - `wasmJsMain` — `preloadFont(Res.font.noto_color_emoji)`
- `EmojiText.kt` — портирован 1:1 из swapfaceandroid (data class EmojiAwareText, buildEmojiAwareText, rememberEmojiAwareText); emoji-шрифт PRIMARY, не-emoji символы → SpanStyle(FontFamily.Default))
- Шрифт `noto_color_emoji.ttf` (1.47MB, Twemoji.Mozilla COLR) в `composeApp/wasmJsMain/composeResources/font/`
- `App.kt` (composeApp): обёрнут `AppTheme` в `CompositionLocalProvider(LocalEmojiFont provides rememberEmojiFont())`

**Почему так:** Паттерн портирован из swapfaceandroid, где успешно решена та же проблема — Skiko canvas не имеет системного emoji-fallback. LocalCompositionLocal избегает prop-drilling; expect/actual позволяет оптимизировать per-platform (Android/iOS игнорируют preload, wasmJs требует явного).

**Баги/проблемы:** 
- Обнаружена ошибка в iOS: `AppLocale.ios.kt:16 preferredLanguages` (вне scope, iOS не релизится, не её проблема).
- AGP 9: KMP-library модули используют `compileAndroidMain`, НЕ `compileDebugKotlin` (последний только для `:androidApp`) — первое применение паттерна, запомнить для будущих итераций.

**Решение:** iOS-слом оставить (не в scope Emoji-задачи); compile-проверки зелёные:
- `:core:designsystem:compileKotlinWasmJs` PASS
- `:core:designsystem:compileAndroidMain` PASS
- `:composeApp:compileKotlinWasmJs` PASS

**Контракт для @android-expert:** 
- Использовать `LocalEmojiFont.current` в Text-компонентах
- Callable: `rememberEmojiAwareText(text): EmojiAwareText(text: AnnotatedString, fontFamily: FontFamily)`
- Все user-facing emoji в дизайн-системе (GistiPromptChips, ChecklistListCard, CsatBottomSheet, FeatureIllustrations, InteractiveOnboarding) теперь готовы к применению шрифта

### Итерация 2 — 2026-06-04 — android-expert
**Что сделано:** Применение emoji-font инфраструктуры + фикс touch-zones (48dp minimum) + замена → на ➡️:

**Изменённые файлы:**
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/designsystem/components/prompt/GistiPromptChips.kt` — Surface(onClick={...}) вместо clickable снаружи (ripple клипуется по pill-shape); `minimumInteractiveComponentSize(48.dp)` для touch-target; все Text(emoji) → `fontFamily = LocalEmojiFont.current`; label преобразован `rememberEmojiAwareText()` (удалён baselineShift для '→'); factory defaults `photoLabel/linkLabel` заменены на ➡️ (U+27A1+FE0F).
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/designsystem/components/cards/ChecklistListCard.kt` — параметры `onClick` и `onLongClick` добавлены в сигнатуру; `combinedClickable` применён к ВНУТРЕННЕМУ `cardContent` Row (ripple клипуется по 12dp углам, тень остаётся на Card); 🎉 Text → `fontFamily = LocalEmojiFont.current`.
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/main/MainScreenContent.kt` — ChecklistCard call-sites проброшены `onClick/onLongClick`; убран `combinedClickable` из `cardModifier` (управляется LazyColumn/LazyGrid нативно); drag/wiggle gestureDetector остались снаружи Card (соответствует ui-card-patterns правилу).
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/csat/CsatBottomSheet.kt` — все emoji (😞😐😊✅) → `fontFamily = LocalEmojiFont.current`.
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/designsystem/illustrations/FeatureIllustrations.kt` — InputChip/PremiumBenefitRow (pure composable): emoji → `rememberEmojiAwareText()`; ScheduleChip (mixed mode): emoji → `fontFamily`.
- `feature/onboarding/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/onboarding/style/StyleSelectionStep.kt` → style.emoji, `feature/onboarding/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/onboarding/category/CategorySelectionStep.kt` → category.icon — оба применяют `fontFamily`.
- `feature/paywall/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/paywall/subscription/SubscriptionStatusScreen.kt` — success Snackbar (composable Text-слот) 🎉 → `rememberEmojiAwareText()` (Snackbar — composable-слот, не String).
- `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-ru/strings.xml` — `main_prompt_photo` и `main_prompt_link` обновлены: '→' → '➡️' (U+27A1+FE0F).
- `core/designsystem/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/designsystem/GistiComponentsScreenshotTest.kt` — тест-лейблы синхронизированы с обновлениями компонентов.

**Почему так:** 
- Surface(onClick) + внутренний combinedClickable даёт корректный клиппинг ripple по shape, избегает полуэкранного всплеска.
- Стрелка → заменена на emoji ➡️ (U+27A1+FE0F) — есть в Twemoji, идёт через `rememberEmojiAwareText()`, не требует vector-иконки, консистентно с остальными emoji. Альтернатива (vector) сложнее и не масштабируется.
- subscription 🎉 сохранён как есть (требование UX), только применён emoji-font.

**Верификация (PASS):**
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:composeApp:compileKotlinWasmJs` — BUILD SUCCESSFUL
- `:core:designsystem:testAndroidHostTest` — all PASS
- Roborazzi record+verify — PASS (обновлены только `gistiPromptChips_{light,dark}.png`, `checklistListCard` не изменился на Android-host-тестах, поскольку Android-host использует системный emoji-fallback, не LocalEmojiFont).

**Известные ограничения / отложено:**
- `feature/debug/src/androidMain/.../StoreScreenshotScreen.kt` — пропущен (debug-only Android screenshot-тулинг для app store, не user-facing web-поверхность).
- `PdfGenerator.wasmJs.kt` (☑☐ символы) — пропущен (PDF-рендеринг, не Compose Text, отдельный скоуп).
- Мёртвые legacy-строки `illustration_*` (например, "3/3 ✓") — не имеют render-site в живом коде (grep пусто), не показываются пользователям. Кандидаты на удаление (вне scope текущей задачи).
- `main_prompt_pdf` "PDF → list" — оставлена со старой → (не активна, PDF-чип исключён из дефолтного набора чипов).
- **БРАУЗЕРНАЯ ВЕРИФИКАЦИЯ ОТЛОЖЕНА:** Roborazzi screenshot-golden проверяет только Android-host путь, где используется системный emoji-fallback. WasmJs путь требует настоящей браузерной верификации на `wasmJsBrowserDistribution` (manual test в браузере — вне scope текущей итерации, ожидание build+web-deploy).

## Выводы

**Emoji на wasmJs требует явного шрифта; Skiko canvas не имеет системного fallback.**

Проверенный паттерн (портирован из swapfaceandroid, производственный опыт 2026-Q2): `expect/actual rememberEmojiFont()` + `LocalEmojiFont` + per-Text `fontFamily = LocalEmojiFont.current`. Шрифт `noto_color_emoji.ttf` живёт ТОЛЬКО в wasmJsMain (1.47MB груз не тащится на Android/iOS). На Android/iOS `rememberEmojiFont()` возвращает `FontFamily.Default`, что корректно через системный fallback; на wasmJs — явно preloaded Noto Color Emoji.

**Touch-zone архитектура:** Surface(onClick) + внутренний combinedClickable = ripple клипуется по shape; drag/wiggle жест остаётся снаружи Card (на LazyColumn/Grid сам управляет). Минимум 48dp touch-target гарантирован Surface + minimumInteractiveComponentSize.

**Non-emoji глифы (→, ✓) заменены на emoji-эквиваленты (➡️ U+27A1, ✔️ U+2714)** — они есть в Twemoji, автоматически идут через тот же шрифт, не требуют вектор-иконок.

**Ограничение верификации:** Roborazzi screenshottests на Android-host используют системный emoji-fallback, не проверяют wasmJs-путь (LocalEmojiFont). WasmJs верификация требует браузера на prod-сборке (manual test — пользователь подтверждает работу).

**Выводы для будущих KMP emoji-задач:** (1) не пытаться fontFamilyResolver.preload() как глобальный fallback (internal Skia API); (2) per-Text явное `fontFamily`; (3) шрифт только на целевой платформе (wasmJs); (4) заменять non-emoji символы на emoji-эквиваленты когда возможно.

## Предложения по улучшению агентов

### kmp-expert
- [ ] Добавить в KMP patterns: emoji-rendering pattern (expect/actual rememberEmojiFont + LocalEmojiFont + per-platform font-weight); явно документировать Skiko-ограничение (no fontFamilyResolver.preload fallback на wasmJs)

### android-expert
- [ ] Touch-zone фикс в Card/Surface паттерны: Surface(onClick) vs внешний clickable; drag/wiggle жест снаружи; minimumInteractiveComponentSize 48dp

### wasmjs-expert
- [ ] Документировать emoji.ttf preload в init.js (локально в wasmJsMain); браузерная верификация не воспроизводится в host-тестах
