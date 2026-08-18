---
title: "Onboarding None Variant — RC Bypass for A/B Testing"
date: 2026-05-10
type: feature
modules: [feature/splash, feature/user, core/remoteconfig/api]
keywords: [remote-config, onboarding-skip, a-b-test, first-run-gate, rc-variant, usecase-pattern, persist-state]
project: gisti-checklists
---

# Onboarding None Variant — RC Bypass for A/B Testing

## Проблема / Контекст

First-run onboarding flows (slides, interactive tutorials, setup screens) требуют A/B-тестирования для оценки impact на конверсию и retention. Нужна способность полностью пропустить onboarding и перевести пользователя сразу в основное приложение через Remote Config без кода-изменений.

Предыдущий паттерн: только два варианта `interactive` и `default` (оба показывают какой-либо flow). Нет режима полного байпаса.

## Решение

Добавлен enum value `NONE` в `GetOnboardingVariantUseCase`, с парсингом из RC ключа `onboarding`:

```kotlin
enum class OnboardingVariant {
    INTERACTIVE, DEFAULT, NONE
}

// RemoteConfigKeys.kt — документирует новое значение:
// Values: "interactive" | "default" | "none"
```

**SplashViewModel handling:**

```kotlin
fun onIntent(intent: SplashContract.Intent) = launchIO {
    val variant = getOnboardingVariantUseCase()
    when (variant) {
        OnboardingVariant.INTERACTIVE -> navigateToInteractiveOnboarding()
        OnboardingVariant.DEFAULT -> navigateToDefaultOnboarding()
        OnboardingVariant.NONE -> {
            completeOnboardingUseCase()  // Mark as passed in DataStore
            navigateToMainScreen()       // Skip directly to app
        }
    }
    setAnalyticsProperty("onboarding_type", variant.name.lowercase())
}
```

**Ключевой момент: persist state as passed**

Когда вариант `NONE`, вызываем `CompleteOnboardingUseCase` который пишет `isOnboardingPassed=true` в DataStore. Это гарантирует что:
- Если RC значение позже flip-нется обратно на `interactive` или `default`, пользователь не видит ретро-показ onboarding
- A/B-тест измеряет **presence/absence фичи**, не её содержание (control group永远 видит skip, treatment group forever видит flow)
- Миграция пользователя из одного варианта в другой не приводит к confusion screens

## Почему именно так

1. **Enum-based dispatch вместо strings:** TYPE_NONE константа в RemoteConfig, enum-распознавание в UseCase — type-safe, нет опечаток RC values
2. **Reuse existing CompleteOnboardingUseCase:** не дублируем логику mark-as-passed, используем существующий паттерн
3. **Analytics tracking:** `onboarding_type` property позволяет сегментировать users по вариантам при анализе retention/conversion
4. **Atomic state change:** `CompleteOnboardingUseCase()` + `navigateToMainScreen()` — один StateFlow update + один navigation command, no race conditions

### Альтернативы которые отклонили

- **Null value в RC:** could set `onboarding = null` но это require null-check everywhere, error-prone
- **Boolean flag `skipOnboarding`:** require два RC keys (onboarding + skipOnboarding), redundant
- **Skip without marking as passed:** пользователь при RC flip видит ретро-показ onboarding, confusion UX

## Примеры

**Конфигурация Remote Config:**

```
Key: onboarding
Value: "none"  (or "interactive", "default")
```

**Результат в SplashViewModel:**

```
User launches app -> RC fetch -> onboarding = "none" -> CompleteOnboardingUseCase() -> navigateToMainScreen() -> User sees Main screen directly (no onboarding)
```

**A/B Метрики:**

```
cohort: control (onboarding_type = "none")
cohort: treatment (onboarding_type = "interactive")

Compare:
- day_1_retention
- premiumization_rate
- avg_checklists_created_per_user
```

## Связанные файлы

- `core/remoteconfig/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/remoteconfig/api/RemoteConfigKeys.kt` — documentation comment
- `feature/splash/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/splash/presentation/SplashViewModel.kt` — handling logic
- `feature/user/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/user/domain/usecase/GetOnboardingVariantUseCase.kt` — enum + parsing
- `feature/splash/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/feature/splash/presentation/SplashViewModelTest.kt` — unit test

## Переиспользуемый паттерн

Этот подход применим к **любому first-run gate**:

- **Paywall preroll:** `paywall_variant = "none"` → пропускает paywall на старте (experimental freemium)
- **Welcome dialog:** `welcome_shown = "none"` → пропускает intro на App-level
- **Tutorial screen:** `tutorial_variant = "none"` → прямой доступ к основным фичам

Pattern шаблон:
1. Define enum с вариантами (включая NONE)
2. Parse из RC в UseCase
3. Handle NONE → `CompleteXxxxxUseCase()` + navigate directly
4. Set `state_is_passed=true` чтобы A/B был atomic

**Benefit:** все first-run gates становятся testable через RC без кода-изменений, статистически независимы (каждый может быть NONE или active в своём темпе).
