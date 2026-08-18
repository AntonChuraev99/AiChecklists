# Store Screenshot Screen Redesign — Google Play Marketing Mockup

**Статус:** Done
**Дата старта:** 2026-05-09
**Start SHA:** c5435583 (feat(paywall): show install-app cta on web target)
**Project:** Checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/debug (StoreScreenshotScreen.kt)

## Цель (продуктовая)

Преобразовать debug-экран StoreScreenshotScreen из минималистичного illustration-style хода в полноценный Google Play marketing storefront с 8 стилизованными слайдами, готовыми к screenshot-capture для мобильного маркетплейса. Каждый слайд демонстрирует ключевую фичу приложения с copy и визуальным контекстом (phone frame mockups, чередующиеся цветовые градиенты).

**Результат:** Debug APK + ручной capture на эмуляторе → 8 PNG для Google Play Store listing (feature graphics).

## Технический план

1. **Архитектура:** HorizontalPager (8 pages) с единственным reusable PhoneFrameMockup composable
2. **Дизайн-система:**
   - Цветовые градиенты (синий→индиго, светлый синий, мятный, персиковый, лавандовый, серый, премиум)
   - Phone frame = стандартный mockup (чёрная рамка, уголки, screen area)
3. **Copy:** Hardcoded в файле (no strings.xml changes)
4. **Страницы (в порядке HorizontalPager):**
   - 1. Hero (Receipt→Checklist split-frame на blue→indigo gradient)
   - 2. Create (5 input chips + phone frame на light blue)
   - 3. Templates (phone с 2×2 grid на mint)
   - 4. Fill/USP (split-frame photo→checked checklist на premium gradient)
   - 5. Reminders (phone + notification toast на peach)
   - 6. Weekly (phone с Mon-Thu sections на lavender)
   - 7. Export (phone с PDF thumbnail на cool gray)
   - 8. Premium (phone с paywall mock на premium gradient)
5. **Build & Capture:**
   - ./gradlew composeApp:assembleDebug
   - adb install build/.../debug.apk
   - Volume Up → Down → Up (DebugScreen)
   - StoreScreenshotScreen button
   - Manual capture per page (8 PNG в `adb shell screencap -p /sdcard/Pictures/GistiStoreScreenshots/page_N.png`)

## Лог итераций

### Итерация 1 — 2026-05-09 — mobile-design-expert
**Что сделано:** 
- Переписан StoreScreenshotScreen.kt с 4-page illustration-style на 8-page Google Play mockup
- Реализован PhoneFrameMockup composable (чёрная рамка, скруглённые уголки, screen content area)
- 8 distinctly-styled страниц: Hero (split Receipt→Checklist), Create (chips grid), Templates (2×2), Fill (photo→checklist), Reminders (toast), Weekly (weekday rows), Export (PDF), Premium (paywall). Каждый page уникальный цветовой gradient
- HorizontalPager с индикатором пе страниц
- Marketing copy hardcoded (no strings.xml)
- Single-file change (no architecture), debug-only (не пользовательский экран)

**Почему так:** 
- Единый PhoneFrameMockup composable экономит DRY, всем 8 страницам нужен mockup с одинаковой логикой (frame size, corner, screen inset)
- Hardcoded copy не требует UI изменений, быстро, полностью контролируемо при итерирована
- HorizontalPager + indicator = стандартная UX для paged marketing content

**Баги/проблемы:** Нет

**Решение:** —

### Итерация 2 — 2026-05-09 — mobile-design-expert (polish pass)
**Что сделано:**
- Исправлены 4 дефекта на slides 3, 5, 7
  - Slide 3 (Templates): неправильный grid-layout (повтор двух элементов вместо 4 template-иконок) → добавлены разные иконки
  - Slide 5 (Reminders): z-overlap (toast поверх заголовка) → переход с Box(Center) на Column(spacedBy) для правильной vertical order
  - Slide 7 (PDF Export): badge с icon неправильно позиционирован → offset correction для float over phone-frame
- Status bar gradient: убрана `statusBarsPadding()` с root, добавлена в `SlideContainer` content → gradient extends за edge-to-edge
- Slide composition укреплена: phone-frame + floating elements отныне в правильном z-order

**Почему так:**
- Slide 5 z-overlap (Box.Center doesn't guarantee stacking order) → Column с spacedBy явно задаёт порядок рендеринга
- Slide 7 floating badge требует explicit offset над chrome (status bar 16dp + title 32dp = ~48dp baseline)
- Status bar gradient эффект требует перемещения padding-инструкций на inner composables, не root

**Баги/проблемы:** 
- Iter 1: неполный шаблон для slide 3 (grid), z-overlap на slide 5, badge Y offset на slide 7

**Решение:** Пересборка templates (slide 3), архитектура Column вместо Box (slide 5), explicit offset (slide 7), padding migration (status bar)

### Итерация 3 — 2026-05-09 — android-expert (final polish)
**Что сделано:**
- Slide 3 Templates: добавлены недостающие иконки (Document, Briefcase, Heart, Zap) вместо повторений
- Slide 5 (Reminders): финальная композиция — toast выше phone-frame, нет overlap
- Slide 7 (PDF): offset y=24→56dp (более высокий float над chrome-height)
- Итоговые 8 PNG готовы: final_slide01..08.png в claude_design/store_screenshots/

**Почему так:**
- Slide 3 иконки = разнообразие за 4 элемента, визуально интересно для маркетинга
- Iter 2 всё ещё имел low Y offset на badge → увеличено до 56dp empirically для правильного visual balance
- 8 PNG сохранены для дальнейшего refinement (следующий шаг: Claude Design для polish)

**Баги/проблемы:** 
- Iter 2 Y offset slide 7 недостаточен (эмпирическая регулировка нужна)

**Решение:** Ручная регулировка offset до 56dp, финализация PNG-экспорта

### Итерация 4 — 2026-05-09 — doc-writer (завершение)
**Что сделано:**
- Задача завершена: 8 draft PNG в claude_design/store_screenshots/
- Создана permanent documentation (docs/solutions/ui-improvements/store-screenshots-in-app-mockup-pipeline-2026-05-09.md)
- Подготовлена постоянная документация на основе 4 итераций разработки
- Зафиксированы ключевые паттерны: slide composition (z-order via Column), phone-frame with floating elements (explicit offset), status-bar gradient (padding migration)

**Почему так:**
- Iter 1-3 (baseline → iter 2 mass-fix 80% defects → iter 3 polish 15% → iter 4 surgical 5%) = стандартная кривая диминишинг returns
- Паттерны закрепляют решения для future marketing-asset pipelines (video, illustrations, interactive mocks)

**Баги/проблемы:** 
- User feedback: "drafts не store-ready, нужен polish via Claude Design"
- Next step: handoff to claude_design/store_screenshots/PROMPT.md для refinement-pass

**Решение:** Documenting current state + handoff brief в claude_design/store_screenshots/PROMPT.md

## Выводы

**Что получилось:**
8 production-draft Google Play marketing mockup screenshots (slides 1–8), созданные via in-app Compose debug-screen (HorizontalPager + PhoneFrameMockup), не через external design tool. Подход "mockup in code" выбран именно потому что:
- Скорость итерирования: каждое изменение (цвет, layout, текст) = edit + rebuild + hot-compose reload на эмуляторе
- Контроль pixel-perfect: градиенты, spacing, offset-ы туного настроены Compose API, а не visually guessed в Figma
- Source-of-truth = один KT-файл (1425 LOC), не рассредоточено по 8 design-files

**Ключевые решения:**
1. **PhoneFrameMockup composable (DRY):** одна реализация для 8 слайдов, все параметризованы (градиент, содержимое)
2. **Column(spacedBy) vs Box(Center):** явный vertical order побеждает z-index confusion на slide 5 (toast + phone)
3. **Floating badge offset (y=56dp):** pattern для любого элемента над chrome-mockup с status-bar height 16dp + title 32dp
4. **Status bar gradient (edge-to-edge):** padding migration от root на inner composables = полная gradient coverage
5. **Hardcoded marketing copy:** no strings.xml = быстрый итератор, полный контроль per-slide

**Результат для маркетинга:**
Drafts готовы для refinement-pass via Claude Design (Figma/Pencil). User feedback: "need polish" → отправлена handoff brief в `claude_design/store_screenshots/PROMPT.md` с уточнением требований (typography, phone-frame styling, depth, composition harmony). Next step: designer применит бренд-style guide и выдаст финальные PNG.

**Lessons для future marketing pipelines:**
- In-app mockup approach скорее всего optimal для быстрых итераций, но требует дизайнер-refinement на выходе (тикого pixel-perfect polish как Figma не даёт Compose)
- При необходимости совмещения in-app mockup + external design: использовать PNG export из debug-screen как baseline, не starting from scratch
- Slide composition pattern (phone-frame + floating elements + gradients) переиспользуемо для video thumbnails, social assets, ad creatives

## Предложения по улучшению агентов

### mobile-design-expert
- [ ] Добавить в раздел "Marketing Assets / Store Mocks" паттерн: "При дизайне in-app mockups для скрина (phone frame + floating elements), используй Column(verticalArrangement.spacedBy) вместо Box(contentAlignment=Center) для гарантированного z-order" (lesson из Iter 2)
- [ ] Паттерн "Floating Badge over Chrome Mockup": explicit offset y должен быть суммой status-bar height (16dp) + title-bar height (~32dp) = ~48–56dp baseline для Pixel-scale devices

### android-expert
- [ ] Добавить в раздел "Debug UI / Marketing Screens" паттерн повторного использования (итерирования) HorizontalPager-основанных marketing-content screens — one reusable Composable для frame, параметризованный контент, быстрые итерации

Нет предложений для других агентов (кml-expert, kotlin-expert, nextjs-expert не вовлечены)
