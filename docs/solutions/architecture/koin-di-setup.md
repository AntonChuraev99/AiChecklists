---
title: Koin Dependency Injection Setup
description: Comprehensive guide to the Koin 4.1 DI architecture in the Gisti multiplatform application
categories: [architecture, dependency-injection, koin, kotlin-multiplatform]
tags: [koin, di, modules, viewmodels, expect-actual, feature-modules]
author: Gisti Architecture Team
date_created: 2026-01-25
last_updated: 2026-01-25
---

# Koin Dependency Injection Setup

This document provides a comprehensive analysis of how Dependency Injection is organized and implemented in Gisti using Koin 4.1 for a Kotlin Multiplatform application.

## Overview

Gisti uses **Koin 4.1** as its dependency injection framework. The DI architecture is organized into:

1. **Core modules** - Common and cross-cutting services
2. **Feature modules** - Feature-specific dependencies
3. **Platform modules** - Platform-specific (Android/iOS) implementations

All modules are aggregated in a single root `appModule` in the main application.

## Module Organization

### Hierarchy

```
appModule (commonMain)
├── Core Modules
│   ├── commonCoreModule
│   ├── navigationCoreModule
│   └── remoteConfigModule
├── Feature Modules
│   ├── checklistFeatureModule
│   ├── createFeatureModule
│   ├── homeFeatureModule
│   ├── analyzeFeatureModule
│   ├── paywallFeatureModule
│   ├── sharingFeatureModule
│   ├── userFeatureModule
│   ├── splashFeatureModule
│   ├── onboardingFeatureModule
│   └── debugFeatureModule
└── Platform Module
    └── platformModule() [expect/actual]
```

### Root Module: appModule

**File:** `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/di/AppModule.kt`

```kotlin
val appModule = module {
    includes(
        commonCoreModule,
        navigationCoreModule,
        remoteConfigModule,
        checklistFeatureModule,
        createFeatureModule,
        onboardingFeatureModule,
        debugFeatureModule,
        homeFeatureModule,
        splashFeatureModule,
        userFeatureModule,
        analyzeFeatureModule,
        paywallFeatureModule,
        sharingFeatureModule,
        platformModule()
    )
    viewModelOf(::AppViewModel)
}

expect fun platformModule(): Module
```

**Key Points:**
- Uses `includes()` to compose all feature and core modules
- Declares the root `AppViewModel`
- Declares `platformModule()` as `expect`, implemented separately for Android and iOS
- Single entry point for application dependency initialization

### Initialization

**File:** `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt`

```kotlin
@Composable
fun App() {
    KoinApplication(
        application = { modules(appModule) }
    ) {
        val viewModel: AppViewModel = koinViewModel()
        // ... rest of app
    }
}
```

The root `KoinApplication` composable initializes Koin with `appModule`, making it available throughout the application.

## Core Modules

### 1. Common Core Module

**File:** `core/common/impl/src/commonMain/kotlin/.../di/CommonCoreModule.kt`

```kotlin
val commonCoreModule = module {
    single<AppLogger> { createLogger() }
    single<AppDispatchersProvider> { AppDispatchersProvider.DEFAULT }
    single<CoroutineScope> { CoroutineScope(Dispatchers.IO) }
}
```

**Provides:**
- `AppLogger` - Cross-app logging interface
- `AppDispatchersProvider` - Coroutine dispatcher abstraction
- `CoroutineScope` - Application-wide scope for IO operations

**Scope:** Singleton (one instance shared across app)

### 2. Navigation Core Module

**File:** `core/navigation/impl/src/commonMain/kotlin/.../di/NavigationModule.kt`

```kotlin
val navigationCoreModule = module {
    single<AppNavigator> { AppNavigatorImpl() }
}
```

**Provides:**
- `AppNavigator` - Type-safe navigation controller

**Scope:** Singleton

### 3. Remote Config Module

**File:** `core/remoteconfig/impl/src/commonMain/kotlin/.../di/RemoteConfigModule.kt`

Abstracts Firebase Remote Config for server-driven features (templates, feature flags, etc.).

## Feature Modules

Feature modules follow a consistent pattern: **API/impl separation** with feature-specific dependencies.

### Pattern: Feature Module Structure

Each feature module contains:
- **Repositories** - Data layer abstraction
- **Use Cases** - Business logic (optional, if complex)
- **ViewModels** - Presentation layer

### Example 1: Home Feature Module

**File:** `feature/home/src/commonMain/kotlin/.../di/HomeFeatureModule.kt`

```kotlin
val homeFeatureModule = module {
    // Simple ViewModel without parameters
    viewModelOf(::MainScreenViewModel)

    // ViewModels with constructor parameters
    viewModel { (checklistId: Long) ->
        ChecklistDetailViewModel(checklistId, get(), get(), get())
    }
    viewModel { (fillId: Long) ->
        FillDetailViewModel(fillId, get(), get())
    }
    viewModel { (checklistId: Long) ->
        FillsListViewModel(checklistId, get(), get())
    }
}
```

**Key Patterns:**
- `viewModelOf(::MainScreenViewModel)` - Simple parameter-free ViewModel
- `viewModel { (param: Type) -> ViewModelClass(..., get(), get()) }` - ViewModel with parameters
- Dependencies injected via `get()` lambda

### Example 2: Analyze Feature Module

**File:** `feature/analyze/src/commonMain/kotlin/.../di/AnalyzeFeatureModule.kt`

```kotlin
val analyzeFeatureModule = module {
    // Data layer
    single<FirebaseAiService> { FirebaseAiServiceImpl(logger = get()) }

    // Domain layer (repository)
    single<AnalyzeRepository> {
        AnalyzeRepositoryImpl(
            firebaseAiService = get(),
            checklistRepository = get(),
            userDataRepository = get()
        )
    }

    // Presentation layer
    viewModel { (checklistId: Long?) ->
        AnalyzeViewModel(
            checklistId = checklistId,
            analyzeRepository = get(),
            checklistRepository = get(),
            appNavigator = get(),
            userDataRepository = get(),
            getSubscriptionStatusUseCase = get()
        )
    }

    viewModel {
        AnalyzeResultPreviewViewModel(
            appNavigator = get(),
            checklistRepository = get()
        )
    }
}
```

**Layers:**
1. **Data Layer** - External services (Firebase)
2. **Domain Layer** - Repositories and business logic
3. **Presentation Layer** - ViewModels

### Example 3: Paywall Feature Module

**File:** `feature/paywall/src/commonMain/kotlin/.../di/PaywallFeatureModule.kt`

```kotlin
val paywallFeatureModule = module {
    single<PaywallRepository> { PaywallRepositoryImpl() }

    // Use cases - factory scope (new instance each time)
    factory { GetSubscriptionStatusUseCase(get()) }
    factory { GetOfferingsUseCase(get()) }
    factory { PurchaseProductUseCase(get()) }
    factory { RestorePurchasesUseCase(get()) }
    factory { GetUserLimitsUseCase(get(), get(), get()) }

    // ViewModels
    viewModelOf(::PaywallViewModel)
    viewModelOf(::SubscriptionStatusViewModel)
}
```

**Scope Distinction:**
- `single` - One instance, cached for app lifetime
- `factory` - New instance per request (use cases here are lightweight)
- `viewModelOf` - ViewModel scope (lifecycle-bound)

## ViewModel Injection

### Pattern 1: Simple Injection (No Parameters)

**Use Case:** Screens without route parameters

```kotlin
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = koinViewModel(),
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    // ...
}
```

### Pattern 2: Injection with Parameters

**Use Case:** Screens with route parameters (e.g., `ChecklistDetail(checklistId)`)

```kotlin
@Composable
fun ChecklistDetailScreen(
    checklistId: Long,
    viewModel: ChecklistDetailViewModel = koinViewModel { parametersOf(checklistId) }
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    // ...
}
```

**Key Points:**
- `koinViewModel()` is the standard function from `org.koin.compose.viewmodel.koinViewModel`
- `parametersOf(checklistId)` passes route parameters to Koin
- ViewModels match parameters by **type order** in the module definition

### Example: Matching Parameters in Module

Module definition:
```kotlin
viewModel { (checklistId: Long) ->
    ChecklistDetailViewModel(checklistId, get(), get(), get())
}
```

Screen injection:
```kotlin
viewModel: ChecklistDetailViewModel = koinViewModel { parametersOf(checklistId) }
```

Koin matches `checklistId: Long` from the module to the parameter passed via `parametersOf()`.

## Platform-Specific Modules

### expect/actual Pattern

The application uses Kotlin's `expect/actual` mechanism to handle platform differences in dependency provision.

### Android Platform Module

**File:** `composeApp/src/androidMain/kotlin/.../di/PlatformModule.android.kt`

```kotlin
actual fun platformModule(): Module = module {
    single { AppContextHolder.context }
    single { GeminiConfig(apiKey = BuildConfig.GEMINI_API_KEY) }
    single { DeviceIdProvider(get()) }
}
```

**Android-Specific Dependencies:**
- `AppContextHolder.context` - Android Context (requires `AppContextHolder.context` initialization in main activity)
- `GeminiConfig` - API key from `BuildConfig` generated at compile time
- `DeviceIdProvider` - Uses Android Context to get device ID

### iOS Platform Module

**File:** `composeApp/src/iosMain/kotlin/.../di/PlatformModule.ios.kt`

```kotlin
actual fun platformModule(): Module = module {
    single {
        val apiKey = NSBundle.mainBundle.objectForInfoDictionaryKey("GEMINI_API_KEY") as? String ?: ""
        GeminiConfig(apiKey = apiKey)
    }
    single { DeviceIdProvider() }
}
```

**iOS-Specific Differences:**
- No Android Context needed
- API key loaded from `Info.plist` via `NSBundle.mainBundle`
- `DeviceIdProvider` initializes without parameters
- Different implementation of platform services

### Key Benefits

1. **Single Codebase** - Common code in `commonMain`, platform specifics in `androidMain`/`iosMain`
2. **Type Safe** - Compiler ensures all `expect` declarations have `actual` implementations
3. **Flexible Configuration** - Each platform can provide different implementations
4. **Minimal Duplication** - Platform differences isolated to a single module

## Dependency Resolution Patterns

### Get Dependencies via `get()`

Inside module lambdas, use `get()` to retrieve previously registered dependencies:

```kotlin
single {
    MyRepository(
        logger = get(),           // AppLogger
        dispatchers = get(),      // AppDispatchersProvider
        database = get()          // From other module
    )
}
```

Koin infers the type from the parameter type in the receiving class.

### Inter-Module Dependencies

Modules can depend on types defined in other modules because all modules are composed together in `appModule`:

```kotlin
// In homeFeatureModule:
viewModel { (checklistId: Long) ->
    ChecklistDetailViewModel(
        checklistId,
        get(),              // checklistRepository from checklistFeatureModule
        get(),              // appNavigator from navigationCoreModule
        get()               // logger from commonCoreModule
    )
}
```

This works because `appModule` includes all modules before loading any of them.

## Module Registration Scope Types

| Scope | Lifetime | Use Case |
|-------|----------|----------|
| `single` | Application | Singletons - services, repositories, configs |
| `factory` | Request | New instance per injection - lightweight use cases |
| `viewModelOf` | ViewModel | Lifecycle-bound to ViewModel |
| `viewModel` | ViewModel | Custom ViewModel factory with parameters |

## Best Practices

### 1. Module Organization

```
feature/myfeature/src/commonMain/kotlin/...
├── di/
│   └── MyFeatureModule.kt          # All DI definitions
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── model/
│   ├── remote/
│   └── repository/
└── presentation/
    ├── MyViewModel.kt
    └── MyScreen.kt
```

### 2. Consistent Naming

- Modules: `*FeatureModule`, `*CoreModule`
- Module variables: `camelCase` lowercase (e.g., `homeFeatureModule`)
- Avoid prefixes like `di` in variable names

### 3. Explicit Type Declarations

```kotlin
// ✅ Good - explicitly types the interface
single<ChecklistRepository> { ChecklistRepositoryImpl(...) }

// ❌ Avoid - implicit typing
single { ChecklistRepositoryImpl(...) }
```

Explicit types make dependencies clear and refactoring safer.

### 4. Minimize Parameter Passing to ViewModels

Use `koinViewModel { parametersOf(...) }` only for route parameters:

```kotlin
// ✅ Good - only route parameter
viewModel { (checklistId: Long) ->
    ChecklistDetailViewModel(checklistId, get(), get(), get())
}

// ❌ Avoid - passing multiple dependencies as parameters
viewModel { (repo: Repository, nav: Navigator, logger: Logger) ->
    ...
}
```

Let Koin inject dependencies; only pass parameters from the route.

### 5. Avoid Over-Injection

```kotlin
// ✅ Good - what this ViewModel needs
ChecklistDetailViewModel(
    checklistId,
    checklistRepository,
    appNavigator
)

// ❌ Avoid - injecting unused dependencies
ChecklistDetailViewModel(
    checklistId,
    checklistRepository,
    appNavigator,
    logger,          // unused
    dispatchers,     // unused
    themeManager     // unused
)
```

### 6. Feature Module Dependencies

Feature modules can depend on:
- Core modules (safe - always loaded first)
- Other feature modules (be cautious - may create circular dependencies)
- Never on app-level code

## Troubleshooting

### "No instance found" Error

**Problem:** Koin can't find a dependency

```
org.koin.error.NoBeanDefFoundException: No definition found for class X
```

**Solutions:**
1. Check the dependency is registered in a module included in `appModule`
2. Verify the module is spelled correctly in `includes()`
3. Check type matches - `get()` must match the type registered
4. For parameters, ensure `parametersOf()` types match module definition

### Circular Dependencies

**Problem:** Module A depends on B, B depends on A

```kotlin
// Module A
single { ServiceA(get<ServiceB>()) }

// Module B
single { ServiceB(get<ServiceA>()) }  // ❌ Circular!
```

**Solutions:**
1. Extract common dependency to a separate module
2. Use interfaces and dependency inversion
3. Lazy inject: `single { ServiceA(lazy { get<ServiceB>() }) }`

### Different Instances Across App

**Problem:** Using `factory` when you need `single`, or vice versa

**Solutions:**
- Use `single` for stateful services that maintain state
- Use `factory` for stateless helpers or lightweight objects
- ViewModels are always scoped per ViewModel instance (separate scope)

## Configuration Examples

### Configuration with Feature Flags

```kotlin
// In a feature module
single {
    val config = get<RemoteConfig>()
    val isFeatureEnabled = config.getBoolean("feature_x_enabled")
    ServiceX(isFeatureEnabled)
}
```

### Conditional Registration

```kotlin
module {
    if (BuildConfig.DEBUG) {
        single<Logger> { DebugLogger() }
    } else {
        single<Logger> { ReleaseLogger() }
    }
}
```

### Factory Functions

```kotlin
// For complex object construction
single {
    DatabaseFactory.createDatabase(
        name = "app.db",
        context = get(),  // Android Context
        logger = get()
    )
}
```

## Summary

Gisti's Koin architecture provides:

1. **Modular Organization** - Each feature owns its dependencies
2. **Platform Flexibility** - `expect/actual` for platform-specific code
3. **Type Safety** - Compile-time verification of dependencies
4. **Clear Scope Management** - Singleton, factory, and ViewModel scopes
5. **Navigation Integration** - Route parameters directly injected to ViewModels
6. **Testing Friendly** - Easy to mock or override dependencies (covered in separate testing guide)

The architecture scales well as features are added without modifying existing modules, following the Open/Closed Principle.
