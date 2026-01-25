---
title: "Kotlin Multiplatform Architecture Patterns"
description: "Analysis of KMP patterns used in Gisti: expect/actual declarations, module separation, and navigation strategies"
categories:
  - architecture
  - kotlin-multiplatform
  - kmp
keywords:
  - expect/actual
  - API/impl separation
  - type-safe navigation
  - platform-specific code
created: 2025-01-25
updated: 2025-01-25
---

# Kotlin Multiplatform Architecture Patterns in Gisti

This document analyzes the Kotlin Multiplatform (KMP) patterns employed in the Gisti checklist application. The project demonstrates production-grade KMP architecture with clean separation of concerns across Android and iOS platforms.

## Overview

Gisti uses three core architecture patterns for KMP:

1. **expect/actual** - Platform-specific implementations for build config, database, file I/O, and date formatting
2. **API/impl module separation** - Core modules expose interfaces in `:api`, implementations in `:impl`
3. **Type-safe navigation** - Kotlinx Serialization-based routes with centralized navigator

## Pattern 1: expect/actual for Platform-Specific Code

The `expect/actual` pattern is Kotlin's native mechanism for declaring platform-specific implementations. Gisti uses this extensively for:

- Build configuration
- Database initialization
- File reading
- Date formatting
- API key configuration

### AppBuildConfig: Debug Detection

**Purpose**: Conditionally enable debug features (debug menu) based on build type.

**Common Declaration** (`composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/AppBuildConfig.kt`):

```kotlin
/**
 * Platform-specific build configuration.
 * Provides access to build-time flags like debug mode.
 */
expect object AppBuildConfig {
    /**
     * Returns true if this is a debug build.
     * Used to conditionally enable debug features like debug menu.
     */
    val isDebug: Boolean
}
```

**Android Implementation** (`composeApp/src/androidMain/kotlin/.../AppBuildConfig.android.kt`):

```kotlin
import com.antonchuraev.aichecklists.BuildConfig

actual object AppBuildConfig {
    actual val isDebug: Boolean = BuildConfig.DEBUG
}
```

- Uses Gradle-generated `BuildConfig.DEBUG` flag
- Simple and reliable, leverages Android's built-in build configuration

**iOS Implementation** (`composeApp/src/iosMain/kotlin/.../AppBuildConfig.ios.kt`):

```kotlin
import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

actual object AppBuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = run {
        // Check if running in Xcode debug configuration
        val infoDictionary = NSBundle.mainBundle.infoDictionary
        val configuration = infoDictionary?.get("Configuration") as? String
        configuration?.contains("Debug", ignoreCase = true) == true
                || kotlin.native.Platform.isDebugBinary
    }
}
```

- iOS lacks a built-in `BuildConfig`, so implementation:
  - Checks Info.plist "Configuration" entry
  - Falls back to Kotlin/Native's `Platform.isDebugBinary`
- More complex due to iOS platform limitations

**Usage** (`composeApp/src/commonMain/kotlin/.../App.kt`):

```kotlin
if (AppBuildConfig.isDebug) {
    composable<AppNavRoute.Debug> {
        DebugScreen()
    }

    composable<AppNavRoute.StoreScreenshot> {
        StoreScreenshotScreen()
    }
}
```

### DatabaseBuilder: Room Initialization

**Purpose**: Initialize Room database with platform-specific file paths and configurations.

**Common Declaration** (`core/common/api/src/commonMain/kotlin/.../DatabaseBuilder.kt`):

```kotlin
expect inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String,
): RoomDatabase.Builder<T>
```

**Android Implementation** (`core/common/api/src/androidMain/kotlin/.../DatabaseBuilder.android.kt`):

```kotlin
import androidx.room.Room
import androidx.room.RoomDatabase

actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String
): RoomDatabase.Builder<T> {
    val appContext = AppContextHolder.context
    val dbFile = appContext.getDatabasePath("${databaseName}.db")
    return Room.databaseBuilder<T>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
```

Key points:
- Uses Android Context from `AppContextHolder` singleton
- Stores database in Android's standard database directory
- Uses absolute path to ensure proper file placement
- Leverages `Room.databaseBuilder` with context

**iOS Implementation** (`core/common/api/src/iosMain/kotlin/.../DatabaseBuilder.ios.kt`):

```kotlin
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String
): RoomDatabase.Builder<T> {
    val dbFilePath = documentDirectory() + "/${databaseName}.db"
    return Room.databaseBuilder<T>(
        name = dbFilePath,
    )
}
```

Key points:
- Uses iOS Foundation framework to locate Documents directory
- `ExperimentalForeignApi` required for Kotlin/Native interop
- No Context parameter needed (not applicable on iOS)
- Returns path-based builder instead of context-based

**Pattern insight**: This demonstrates how expect/actual abstracts platform differences while maintaining a single common API. The iOS version shows Kotlin/Native FFI (Foreign Function Interface) complexity.

### FileReader: File I/O Abstraction

**Purpose**: Read file contents as bytes or strings, abstracting platform-specific file APIs.

**Common Declaration** (`feature/analyze/src/commonMain/kotlin/.../FileReader.kt`):

```kotlin
/**
 * Platform-specific file reading utilities.
 */
expect object FileReader {
    /**
     * Reads file content as ByteArray.
     * @param filePath absolute path to the file
     * @return file content as bytes or null if failed
     */
    fun readBytes(filePath: String): ByteArray?

    /**
     * Reads file content as String.
     * @param filePath absolute path to the file
     * @return file content as string or null if failed
     */
    fun readText(filePath: String): String?
}
```

**Android Implementation** (`feature/analyze/src/androidMain/kotlin/.../FileReader.android.kt`):

```kotlin
import java.io.File

actual object FileReader {
    actual fun readBytes(filePath: String): ByteArray? {
        return try {
            File(filePath).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    actual fun readText(filePath: String): String? {
        return try {
            File(filePath).readText()
        } catch (e: Exception) {
            null
        }
    }
}
```

- Straightforward use of JVM's `java.io.File` APIs
- Simple error handling with try-catch returning null on failure

**iOS Implementation** (`feature/analyze/src/iosMain/kotlin/.../FileReader.ios.kt`):

```kotlin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object FileReader {
    actual fun readBytes(filePath: String): ByteArray? {
        return try {
            // Handle file:// URLs
            val path = if (filePath.startsWith("file://")) {
                NSURL.URLWithString(filePath)?.path ?: filePath
            } else {
                filePath
            }

            val data = NSData.dataWithContentsOfFile(path) ?: return null
            val bytes = ByteArray(data.length.toInt())
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            bytes
        } catch (e: Exception) {
            null
        }
    }

    actual fun readText(filePath: String): String? {
        return try {
            // Handle file:// URLs
            val path = if (filePath.startsWith("file://")) {
                NSURL.URLWithString(filePath)?.path ?: filePath
            } else {
                filePath
            }

            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        } catch (e: Exception) {
            null
        }
    }
}
```

iOS-specific complexities:
- **URL handling**: Converts `file://` URLs to paths (iOS picker returns URLs)
- **Memory management**: Uses `usePinned` to pin ByteArray before C interop
- **memcpy**: Low-level memory copy from NSData to ByteArray
- **ExperimentalForeignApi**: Required for direct C interop
- **NSString conversion**: Implicit conversion to Kotlin String for text

### Date Formatting: Localized Date Output

**Purpose**: Format subscription expiration timestamps in platform conventions.

**Common Declaration** (`feature/paywall/src/commonMain/kotlin/.../DateFormatter.kt`):

```kotlin
// In PaywallScreenContract or similar
expect fun formatExpirationDate(timestamp: Long): String
```

**Android Implementation** (`feature/paywall/src/androidMain/kotlin/.../DateFormatter.android.kt`):

```kotlin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatExpirationDate(timestamp: Long): String {
    return try {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        formatter.format(date)
    } catch (e: Exception) {
        ""
    }
}
```

- Uses JVM `SimpleDateFormat` (legacy but standard)
- Returns empty string on error

**iOS Implementation** (`feature/paywall/src/iosMain/kotlin/.../DateFormatter.ios.kt`):

```kotlin
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatExpirationDate(timestamp: Long): String {
    return try {
        val date = NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
        val formatter = NSDateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        formatter.stringFromDate(date)
    } catch (e: Exception) {
        ""
    }
}
```

Key difference:
- Timestamp conversion: Android uses milliseconds directly, iOS uses seconds
- `timestamp / 1000.0` needed for iOS (`Double` conversion)
- Uses Foundation's `NSDateFormatter` instead of SimpleDateFormat

### RevenueCat API Key Configuration

**Purpose**: Inject platform-specific RevenueCat API keys without hardcoding in common code.

**Common Declaration** (`feature/paywall/src/commonMain/kotlin/.../RevenueCatInitializer.kt`):

```kotlin
expect fun getPlatformApiKey(): String

object RevenueCatInitializer {
    private var isInitialized = false

    fun initialize(apiKey: String, appUserId: String? = null, isDebug: Boolean = false) {
        if (isInitialized) return

        if (isDebug) {
            Purchases.logLevel = LogLevel.DEBUG
        }

        val configuration = PurchasesConfiguration(apiKey) {
            appUserId?.let { this.appUserId = it }
            pendingTransactionsForPrepaidPlansEnabled = true
        }
        Purchases.configure(configuration)

        isInitialized = true
    }

    fun isConfigured(): Boolean = Purchases.isConfigured
}
```

**Android Implementation** (`feature/paywall/src/androidMain/kotlin/.../RevenueCatInitializer.android.kt`):

```kotlin
actual fun getPlatformApiKey(): String = PaywallConfig.ANDROID_API_KEY
```

**iOS Implementation** (`feature/paywall/src/iosMain/kotlin/.../RevenueCatInitializer.ios.kt`):

```kotlin
actual fun getPlatformApiKey(): String = PaywallConfig.IOS_API_KEY
```

Pattern insight: This shows how expect functions can be minimal adapters that select platform-specific constants. The common code uses the result without knowing where it came from.

## Pattern 2: API/impl Module Separation

Gisti separates interface definitions (API) from implementations (impl) to enable:

- Dependency inversion at the module level
- Easy testing with mock implementations
- Clear contracts between modules

### Module Structure

```
core/
  ├── common/
  │   ├── api/           # Interfaces & expect declarations
  │   │   ├── commonMain/
  │   │   ├── androidMain/
  │   │   └── iosMain/
  │   └── impl/          # Implementations & DI
  │       └── commonMain/
  │
  ├── navigation/
  │   ├── api/           # AppNavRoute, AppNavigator interface
  │   └── impl/          # AppNavigatorImpl
  │
  ├── datastore/
  │   ├── api/           # Preferences interface
  │   └── impl/          # Implementation
  │
  └── remoteconfig/
      ├── api/
      └── impl/
```

### core:common:api vs core:common:impl

**API Module** (`core/common/api/build.gradle.kts`):

```gradle
kotlin {
    androidTarget { }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // No Koin dependency - API only
        }
        androidMain.dependencies {
            implementation(libs.room.ktx)
        }
    }
}
```

Characteristics:
- Contains expect declarations
- Room database interfaces and builders
- Minimal external dependencies
- No DI framework dependency (not tied to Koin)
- Targets both platforms' sourceSets

**Impl Module** (`core/common/impl/build.gradle.kts`):

```gradle
kotlin {
    androidTarget { }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common.api)  // Depends on API
            implementation(libs.koin.library)         // Pulls in Koin
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

Characteristics:
- Depends on `:api` module
- Contains actual/concrete implementations
- Includes Koin DI definitions
- Provides Koin modules for injection

### Navigation Module: AppNavigator Pattern

**API** (`core/navigation/api`):

```kotlin
// Routes - type-safe navigation targets
@Serializable
sealed interface AppNavRoute {
    @Serializable
    data object Splash : AppNavRoute

    @Serializable
    data object Main : AppNavRoute

    @Serializable
    data class ChecklistDetail(val checklistId: Long) : AppNavRoute

    @Serializable
    sealed interface CreateChecklistRoute : AppNavRoute {
        @Serializable
        data class CreateChecklist(
            val templateId: Int? = null,
            val editChecklistId: Long? = null
        ) : CreateChecklistRoute

        @Serializable
        data object Templates : CreateChecklistRoute
    }
    // ... more routes
}

// Navigator interface - contract for navigation
interface AppNavigator {
    fun installNavController(navController: NavController)
    fun onBack()
    fun navigateToMainScreen(clearBackStack: Boolean = false)
    fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean = false)
    fun navigateToAnalyzeScreen(checklistId: Long? = null)
    // ... more navigation methods
}
```

Key pattern elements:
- **Sealed hierarchies**: `AppNavRoute` and `CreateChecklistRoute` form hierarchies
- **Serializable**: All routes use Kotlinx Serialization for type-safe navigation
- **Interface contract**: `AppNavigator` defines all possible navigation actions
- **Centralized definition**: Single source of truth for app navigation

**Implementation** (`core/navigation/impl`):

```kotlin
class AppNavigatorImpl() : AppNavigator {
    private lateinit var navController: NavController

    override fun installNavController(navController: NavController) {
        this.navController = navController
    }

    override fun navigateToMainScreen(clearBackStack: Boolean) {
        if (clearBackStack) {
            navController.navigate(AppNavRoute.Main) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate(AppNavRoute.Main)
        }
    }

    override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {
        if (clearBackStack) {
            navController.navigate(AppNavRoute.ChecklistDetail(checklistId)) {
                popUpTo(AppNavRoute.Main) { inclusive = false }
            }
        } else {
            navController.navigate(AppNavRoute.ChecklistDetail(checklistId))
        }
    }

    // ... other navigation implementations
}
```

Pattern benefits:
- **Type safety**: Routes and parameters are Serializable data classes
- **Back stack control**: Methods support `clearBackStack` for special transitions
- **Single responsibility**: Navigator only handles navigation, not business logic

### App.kt: Navigation Setup

```kotlin
@Composable
fun App() {
    KoinApplication(
        application = { modules(appModule) }
    ) {
        val viewModel: AppViewModel = koinViewModel()
        val navController = rememberNavController().also {
            viewModel.installNavController(it)
        }

        AppTheme {
            NavHost(
                navController = navController,
                startDestination = AppNavRoute.Splash
            ) {
                composable<AppNavRoute.Splash> {
                    SplashScreen()
                }

                composable<AppNavRoute.Main> {
                    MainScreen()
                }

                composable<AppNavRoute.ChecklistDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.ChecklistDetail>()
                    ChecklistDetailScreen(checklistId = route.checklistId)
                }

                // Debug routes conditional on build config
                if (AppBuildConfig.isDebug) {
                    composable<AppNavRoute.Debug> {
                        DebugScreen()
                    }
                }

                // ... other routes
            }
        }
    }
}
```

Integration points:
- **Koin DI**: `KoinApplication` provides dependency injection
- **AppViewModel**: Holds `AppNavigator` instance
- **Type-safe routing**: `toRoute<AppNavRoute.ChecklistDetail>()` extracts parameters
- **Conditional screens**: Debug screens only available when `AppBuildConfig.isDebug`

## Pattern 3: Feature Module Structure

Each feature module follows a consistent structure with domain/data/presentation separation:

### Paywall Feature Module Example

```
feature/paywall/
├── domain/
│   ├── model/
│   │   ├── SubscriptionStatus.kt
│   │   ├── PurchaseResult.kt
│   │   ├── UserLimits.kt
│   │   └── PaywallProduct.kt
│   ├── repository/
│   │   └── PaywallRepository.kt        # Interface only
│   └── usecase/
│       ├── GetSubscriptionStatusUseCase.kt
│       ├── GetOfferingsUseCase.kt
│       ├── PurchaseProductUseCase.kt
│       ├── RestorePurchasesUseCase.kt
│       └── GetUserLimitsUseCase.kt
├── data/
│   ├── RevenueCatInitializer.kt        # expect/actual
│   ├── PaywallConfig.kt
│   └── repository/
│       └── PaywallRepositoryImpl.kt
├── presentation/
│   ├── PaywallScreen.kt
│   ├── PaywallViewModel.kt
│   ├── PaywallScreenContract.kt        # State/Intent definitions
│   ├── SubscriptionStatusScreen.kt
│   ├── SubscriptionStatusViewModel.kt
│   └── DateFormatter.kt                # expect/actual
└── di/
    └── PaywallFeatureModule.kt
```

### DI Configuration

```kotlin
val paywallFeatureModule = module {
    single<PaywallRepository> { PaywallRepositoryImpl() }

    // Use cases
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

Pattern highlights:
- **Single repository**: One `PaywallRepository` instance shared across app
- **Factory use cases**: Each use case created on demand
- **ViewModel factories**: Koin 4.1+ `viewModelOf` simplifies VM creation
- **Constructor injection**: Dependencies resolved via DI container

## Best Practices Observed

### 1. Consistent Error Handling
All platform-specific code uses try-catch returning null or empty string:

```kotlin
// FileReader
fun readBytes(filePath: String): ByteArray? {
    return try {
        // implementation
    } catch (e: Exception) {
        null
    }
}
```

### 2. Documentation in Common Code
Documentation is placed in common `expect` declarations, not duplicated in actual:

```kotlin
/**
 * Reads file content as ByteArray.
 * @param filePath absolute path to the file
 * @return file content as bytes or null if failed
 */
expect object FileReader {
    fun readBytes(filePath: String): ByteArray?
}
```

### 3. Minimal Platform Code
Platform implementations are as thin as possible:

```kotlin
// Good: Android implementation is one line
actual fun getPlatformApiKey(): String = PaywallConfig.ANDROID_API_KEY

// vs verbose:
actual fun getPlatformApiKey(): String {
    return PaywallConfig.ANDROID_API_KEY  // No, keep it concise
}
```

### 4. Type-Safe Navigation
Never use string-based routes or raw parameter passing:

```kotlin
// Good
fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean = false)
composable<AppNavRoute.ChecklistDetail> { backStackEntry ->
    val route = backStackEntry.toRoute<AppNavRoute.ChecklistDetail>()
    ChecklistDetailScreen(checklistId = route.checklistId)
}

// Bad - don't do this
navController.navigate("checklist/$checklistId")  // Error-prone
```

### 5. Conditional Feature Availability
Use `AppBuildConfig.isDebug` to conditionally include screens:

```kotlin
if (AppBuildConfig.isDebug) {
    composable<AppNavRoute.Debug> { DebugScreen() }
}
```

## Common Patterns Summary

| Pattern | Purpose | Example |
|---------|---------|---------|
| **expect/actual** | Platform-specific code | `AppBuildConfig`, `DatabaseBuilder`, `FileReader` |
| **API/impl modules** | Dependency inversion | `core:common:api` vs `core:common:impl` |
| **Type-safe routes** | Navigation contracts | `@Serializable AppNavRoute` + `AppNavigator` |
| **Feature modules** | Modular architecture | `feature:paywall` with domain/data/presentation |
| **Koin DI** | Dependency injection | `paywallFeatureModule`, `viewModelOf` |
| **Conditional screens** | Debug-only features | `if (AppBuildConfig.isDebug) { composable<...> }` |

## Trade-offs and Considerations

### expect/actual Complexity
- **Pro**: Native APIs for each platform
- **Con**: Platform divergence can be hidden in implementations
- **Mitigation**: Comprehensive testing on both platforms

### Memory Management on iOS
- **Pro**: Direct control via FFI for performance
- **Con**: `ExperimentalForeignApi` requires opt-in and careful memory handling
- **Example**: `memcpy` in `FileReader.ios.kt` for byte array conversion

### API/impl Overhead
- **Pro**: Clear contracts and testability
- **Con**: Extra module boilerplate
- **Mitigation**: Standardized module template across project

### Navigation Type Safety
- **Pro**: Compile-time route validation
- **Con**: Large sealed hierarchies can become unwieldy
- **Mitigation**: Group related routes (e.g., `CreateChecklistRoute`)

## Related Patterns

- **Sealed class hierarchies**: Kotlin's exhaustive when expressions for route handling
- **Inline expect functions**: Generic `getDatabaseBuilder<T>` without boxed types
- **Lazy initialization**: `RevenueCatInitializer.isInitialized` prevents duplicate setup
- **Singleton objects**: All expect/actual declarations use `object` for single instance

## References

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [expect/actual Declarations](https://kotlinlang.org/docs/multiplatform-mobile-create-first-app.html)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Compose Navigation Type-Safe Routes](https://developer.android.com/jetpack/compose/navigation/typesafe)
- [Koin Dependency Injection](https://insert-koin.io/)
