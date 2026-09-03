# Onboarding None Variant RC Bypass

**Статус:** Done
**Дата старта:** 2026-05-10
**Start SHA:** 6820ff4dee1041503a19b1a7d5fe77d18bddd9a1
**Project:** gisti-checklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/splash, feature/user, core/remoteconfig/api

## Цель (продуктовая)

Добавить новое значение `"none"` для RC ключа `onboarding` чтобы пропустить оба типа онбординга (interactive slides и default flow) и переместить пользователя сразу на Main screen. Используется для A/B-тестирования критичности onboarding flow на конверсию.

При выборе `"none"` пользователь переходит напрямую в приложение, с пометкой `isOnboardingPassed=true` в DataStore чтобы предотвратить ретро-показ onboarding при flip RC обратно на `interactive` или `default`.

## Технический план

1. ✅ Обновить comment в `RemoteConfigKeys.kt` чтобы задокументировать новое значение `"none"`
2. ✅ Добавить enum value `NONE` в `GetOnboardingVariantUseCase`
3. ✅ Добавить parsing логику `"none"` → `OnboardingVariant.NONE`
4. ✅ Обновить `SplashViewModel` чтобы handle `NONE` вариант: вызвать `CompleteOnboardingUseCase` + `navigateToMainScreen`
5. ✅ Выставить analytics property `onboarding_type="none"` в ViewModel
6. ✅ Написать unit тест `GetOnboardingVariantUseCaseTest.invoke_none_returnsNone`
7. ✅ Обновить `SplashViewModelTest` добавив `CompleteOnboardingUseCase` в createViewModel
8. ✅ Validation: `compileDebugKotlin` + unit tests green

## Лог итераций

### Итерация 1 — 2026-05-10 — главный агент (INIT пропущен)
**Что сделано:** 
- `RemoteConfigKeys.kt`: обновлен comment `// Values: "interactive" | "default" | "none"`
- `GetOnboardingVariantUseCase.kt`: + enum value `NONE`, добавлена ветка `TYPE_NONE → OnboardingVariant.NONE`
- `SplashViewModel.kt`: + параметр `completeOnboardingUseCase`, добавлена ветка обработки `NONE`: call `completeOnboardingUseCase()` + `navigateToMainScreen()` + set analytics property `onboarding_type="none"`
- `GetOnboardingVariantUseCaseTest.kt`: + тест `invoke_none_returnsNone`
- `SplashViewModelTest.kt`: обновлен `createViewModel()` чтобы инъектировать `CompleteOnboardingUseCase` через существующий `FakeUserDataRepository`

**Почему так:**
- RC-байпасс паттерн: любое first-run gate (onboarding, paywall preroll, tutorial) может иметь режим "skip" через RC. Фиксация юзера в `isOnboardingPassed=true` — natural UX для A/B: тестируем presence/absence фичи, не её содержание; если потом вернём фичу, users who already skipped её not reverted back.
- Используется existing `CompleteOnboardingUseCase` (уже в коде), не дублируем логику
- Tests: один positive case достаточен (нет edge cases для enum parse)

**Баги/проблемы:** Нет

**Решение:** N/A

## Выводы

Реализована за 1 итерацию, главный агент сделал сам (complexity Standard). Применён существующий паттерн GetOnboardingVariantUseCase.TYPE_* для новых значений. Persist as passed решает проблему ретро-показа при RC flip.

Паттерн переиспользуем для других first-run gates: paywall preroll, welcome dialogs, tutorial flows.

## Предложения по улучшению агентов

Нет. Паттерн хорошо известен и реализован.
