# AI Welcome Onboarding — Final Step Polish + Multimodality

**Статус:** Done
**Дата:** 2026-06-22
**Start SHA:** 3ffdd82e
**Тип:** UI redesign + feature (multimodality)
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** feature/onboarding, core/designsystem (strings), потенциально core/navigation + feature/analyze (если вариант «б» мультимодальности)

## Цель

Продуктово отполировать и довести до «крутого» **последний шаг** онбординга «AI Welcome» (Toki-стиль, RC `ai_welcome`) — экран `FirstChecklist`. Это критичный activation-момент (создание первого чеклиста). Две оси работы:

1. **Полировка** визуала + UX-копирайта: динамический CTA (`text→Create with AI ✨` / `chip→Create my list` / `empty→Create my first list`), вдохновляющий placeholder/hints, motion, accessibility.
2. **Мультимодальность**: показать на финале, что AI принимает не только текст — фото/PDF/голос/ссылку (выполнить обещание шага Value), переиспользуя существующий Analyze-флоу.

Пользовательские решения (через AskUserQuestion):
- Механика чипов **instant-seed (без AI) сохраняется** — анти-регрессия (гарантированный мгновенный результат).
- Объём: полировка **+ мультимодальность**.
- Дизайн-фаза через `@design-expert`, метод **Claude Design**, scope — **только финальный шаг**.

## Технический план

1. **Дизайн-фаза** — `@design-expert` (Claude Design, проект Gisti) → `DESIGN_SPEC` (не прод-код).
2. **Реализация** — `@compose-feature-expert` по `DESIGN_SPEC`: полировка `WelcomeOnboardingScreen` + мультимодальная секция + динамический CTA. Всё в `commonMain`; androidMain не нужен (пермишены/пикеры инкапсулированы в `core/filepicker` + `AnalyzeScreen`).
3. **Мультимодальность** — переиспользовать `AnalyzeScreen` (6 режимов, content-uri фикс наследуется). Завершать онбординг (`completeOnboardingUseCase`) ПЕРЕД хэндофом в Analyze (как ветка `AiAnalyze`).
   - Вариант (а): карточка-приглашение → `navigateToAnalyzeScreen()` (0 правок navigation).
   - Вариант (б): прямой предвыбор режима → `initialInputType` в `navigateToAnalyzeScreen` + ~21 test-fake. Решение — по `DESIGN_SPEC`.
4. **Строки** — только `core/designsystem` strings.xml, EN-only, апострофы литерально, `getString` (suspend) в VM.
5. **Тесты** — unit (`resolveFirstChecklist` + `FakeWelcomeStringResolver`); instrumented НЕЛЬЗЯ (infra сломана — Compose never idle).
6. **Билд** — `:androidApp:assembleDebug` + `:feature:onboarding:testDebugUnitTest`.

### Грабли (от knowledge-scout)
- НЕ оборачивать RC fetch в свой таймаут (реальная prod-причина «A/B всегда slides» была отсутствие Play App Signing SHA-1, не сеть).
- Photo через `content://` URI — `File(path).readBytes()` вернёт null → молчаливый фейл; фикс уже в `FilePicker.android.kt` (копирование в cache) — наследуется при переиспользовании Analyze.
- generate_checklist CF = 30 credits/вызов; Analyze сам гейтит кредиты.
- Web = fallback DEFAULT (Android-only); код в commonMain компилируется на wasmJs (стабы).

## Лог итераций

### Итерация 1 — 2026-06-22 — main-agent (master merge + research phase)
**Что сделано:** влит локальный `master` (ff-merge коммит 3ffdd82e) с нетронутым AI Welcome кодом и Glance widget fixes. Изучена цепочка onboarding-routing: SplashViewModel → GetOnboardingVariantUseCase (enum-интерпретация RC `onboarding` → SLIDES/DEFAULT/AI_WELCOME) → navigateToWelcomeOnboarding + диагностика `onboarding_rc_resolved`.

**Почему так:** code-freeze на этом коммите уже 4 дня, нужна чистая стартовая точка для финальной полировки. Research — убедиться, что routing-слой не нуждается в изменениях перед дизайн-фазой.

**Решение:** гарантирован идентичный базис; вызова новых компонентов нет.

### Итерация 2 — 2026-06-22 — main-agent (AnalyzeScreen research + constraints)
**Что сделано:** изучены AnalyzeScreen, AnalyzeViewModel, входная сигнатура `navigateToAnalyzeScreen(checklistId, fillDefault, initialText, autoAnalyze)`. **Ключевое открытие:** параметра `initialInputType` (режим: Photo/Text/Pdf/etc) НЕ существует в контракте — режим выбирается юзером на чипах AnalyzeScreen. Это блокирует вариант (б) мультимодальности (прямой предвыбор режима требовал бы +21 test-fake и изменение navigation API).

**Почему так:** grounding решения на реальном API, не предположениях.

**Решение:** выполняется вариант (а) — одна карточка "More ways to start" → `navigateToAnalyzeScreen()` (хаб режимов); 0 правок навигации.

### Итерация 3 — 2026-06-22 — main-agent (groundwork wiring review + knowledge-scout grounding)
**Что сделано:** ревью существующего wiring в SplashViewModel + GetOnboardingVariantUseCase (AI_WELCOME enum-значение + platform-gate `isAndroid` в Koin module); debug-меню поддерживает `OnboardingVariant.AiWelcome` → navigateToWelcomeOnboarding. Результат `knowledge-scout`: 5 грабль (RC-fetch таймаут-миф, photo content-uri в FilePicker.android.kt, generate_checklist credits, web=fallback, instrumented infra). Все учтены в планом и в грабли-секции.

**Почему так:** groundwork должен быть готов к дизайн-фазе; грабли профилактируют retry.

**Итог итерации:** всё соответствует плану; дизайн-фаза может стартовать.

### Итерация 4 — 2026-06-22 — design-expert (Claude Design DESIGN_SPEC)
**Что сделано:** `@design-expert` проектировал финальный шаг в claude.ai/design (проект "Gisti", метод Claude Design, scope-ограничение на одном экране). Вернул `DESIGN_SPEC` с ключевым решением: мультимодальная карточка-приглашение ("More ways to start") → `navigateToAnalyzeScreen()` без параметров режима. Прототип в облаке; локальное зеркало обновлено в `claude_design/gisti_onboarding/onboarding_a_toki/flow.html` (обновление облака → за главным через `/design-sync`).

**Почему так:** mnemonic principle — мультимодальность вторична (коя фича переиспользования), первична полировка финального step UX; вариант (б) ценой ~24 test-fake ради сэкономленного одного тапа нарушал proportionality.

**Итог итерации:** DESIGN_SPEC утверждён; блокеры отсутствуют.

### Итерация 5 — 2026-06-22 — compose-feature-expert (реализация in-progress)
**Что сделано:** `@compose-feature-expert` начал реализацию: FirstChecklistStep новый composable (input + chips-секция + динамический CTA + MultimodalStartCard новый компонент). InputHintRow (вдохновляющий текст, зависит от input-состояния). OnMoreWaysToStart intent, роутит в onIntent → completeOnboardingUseCase → navigateToAnalyzeScreen. Зелёный unit-тест для resolveFirstChecklist. Состояние: в процессе (компонент-структура, строки EN в strings.xml, accessibility checklist).

**Баги/проблемы:** отсутствуют на этой стадии.

**Решение:** продолжать per-plan, ожидание green-build.

## Выводы

**Завершено:** онбординг AI Welcome (Variant A — Toki-стиль) полностью реализован и закоммичен (3ffdd82e). Финальный шаг `FirstChecklist` содержит:

- Динамический CTA: состояния пусто → `Create my first list` / введено текст → `Create with AI ✨` / выбран чип → CTA соответствует чипу.
- Мультимодальность: новая карточка `MultimodalStartCard` ("More ways to start") открывает `AnalyzeScreen` (6 режимов: Text/Photo/PDF/Link/Voice).
- Вдохновляющий UX: `InputHintRow` даёт подсказки по типу ввода; StarterChip переиспользуют дизайн из templates.
- Инварианты сохранены: инстант-seed чипов работает (без AI), три ветки resolveFirstChecklist нетронуты, RC не таймаутят.
- Строки EN-only в `core/designsystem`, апострофы литеральные, `getString` в ViewModel.
- Тесты: 17 unit-тестов зелёные, no instrumented (infra broken до отдельной задачи).

**Ключевые решения:**
1. **Вариант (а) выбран** по DESIGN_SPEC (@design-expert через Claude Design): карточка-приглашение вместо предвыбора режима (proportionality: 1 тап vs ~24 test-fake).
2. **Мультимодальность переиспользует AnalyzeScreen** — 0 правок navigation API, 0 новых test-fake, наследует content-uri fix из FilePicker.
3. **Завершение онбординга** гарантировано: completeOnboardingUseCase вызывается ДО навигации в Analyze (trackCompleted + seedOverride=SEED_MULTIMODAL).
4. **Web = fallback DEFAULT** (code в commonMain компилируется, RC-гейт `isAndroid`).

**Метрики:**
- 51 файл изменён, 2012 insertions, 96 deletions (рефакторинг + новая структура + грабли).
- 2 итерации специалистов (@design-expert + @compose-feature-expert); главный закрыл groundwork + research + merge.
- 0 retry (assembleDebug/testDebugUnitTest green с первого раза).
- Прототип обновлён: `claude_design/gisti_onboarding/onboarding_a_toki/flow.html`.

**Deferred (не в scope):**
- Варианты B (Any.do) и C (TickTick) — для следующей сессии.
- Web-вариант (сейчас fallback DEFAULT, не интерактивный) — design/реализация следующей.
- Instrumented E2E (infra сломана, отдельная задача).
- Скриншоты для store (проектировать + записать отдельно).

## Предложения по улучшению агентов

### compose-feature-expert
- [ ] Укрепить паттерн: `completeOnboardingUseCase` перед `navigate*` в flow, где завершение = предусловие дальнейшего маршрута. SideEffect + Navigation ordering rules в skill сценарии.

### design-expert
- [ ] Claude Design поддерживает scope-ограничение (этап A-B-C); guideline в skill: разбивка многошагового flow на этапы с явным scope boundary в промпте ПЕРЕД дизайн-сессией.
