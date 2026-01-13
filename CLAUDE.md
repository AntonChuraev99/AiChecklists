# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HomeSearchChecklist is a Kotlin Multiplatform (KMP) application targeting Android and iOS. It uses Jetpack Compose Multiplatform for the UI layer. The app simplifies entering checklists and validating data against them.

**Main features:**
- Entering and managing a list of checklists
- Tracking progress within checklists (supports multiple objects per checklist)
- Creating checklists from templates
- AI-powered automatic checklist filling from uploaded data

## Build Commands

```bash
./gradlew build                           # Full build for all targets
./gradlew composeApp:assembleDebug        # Android debug APK
./gradlew composeApp:bundleRelease        # Android release bundle
./gradlew composeApp:connectedAndroidTest # Run Android instrumented tests
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode.

## Architecture

### Module Structure

The project follows a modular architecture with API/impl separation for core modules:

```
composeApp/          # Main application entry points (Android/iOS)
core/
  common/api         # AppViewModel base class, AppDispatchersProvider, AppLogger interfaces
  common/impl        # Platform-specific implementations
  designsystem/      # Compose theme and reusable UI components
  datastore/api|impl # Preferences persistence abstraction
  navigation/api|impl # AppNavigator and AppNavRoute definitions
feature/
  checklist/         # Domain models, Room database, repository
  create/            # Create checklist screen
  home/              # Main screen
  onboarding/        # First-run experience
  splash/            # Launch screen
  debug/             # Developer tools
  user/              # User profile
```

### MVI Pattern

ViewModels extend `AppViewModel<State, Intent, SideEffect>` from `core:common:api`. Each feature screen follows this pattern:

- `*ScreenContract.kt` - defines `State` and `Intent` sealed interfaces
- `*ViewModel.kt` - extends `AppViewModel`, implements `onIntent()`
- `*Screen.kt` - Composable that observes `screenState` and calls `sendIntent()`

### Dependency Injection

Uses Koin 4.1. Each module defines its own Koin module, aggregated in `appModule`. ViewModels are injected via `koinViewModel()`.

### Navigation

Type-safe navigation using Kotlinx Serialization. Routes are defined as `@Serializable` objects implementing `AppNavRoute` in `core:navigation:api`. `AppNavigator` interface handles navigation actions.

### Database

Room 2.8 with KSP for code generation. Database classes are in `feature:checklist`. Platform-specific `DatabaseBuilder` implementations use `expect/actual` pattern.

## Key Patterns

- **API/impl split**: Core modules expose interfaces in `api`, implementations in `impl`
- **expect/actual**: Used for platform-specific code (logging, database builders)
- **StateFlow**: All reactive state management uses Kotlin Flow
- **Typesafe project accessors**: Reference modules as `projects.core.common.api` in Gradle

## Dependencies

Versions are centralized in `gradle/libs.versions.toml`. Key dependencies:
- Kotlin 2.3.0, Compose Multiplatform 1.9.3
- Koin 4.1.1 (DI), Room 2.8.4 (database), Navigation Compose 2.9.1
