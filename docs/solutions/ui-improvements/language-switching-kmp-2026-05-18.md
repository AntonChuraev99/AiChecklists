---
title: "Runtime Language Switching in KMP (Android/iOS/wasmJs)"
date: 2026-05-18
type: feature
modules: [core/datastore/api, core/datastore/impl, core/designsystem, composeApp, feature/settings]
keywords: [language-switching, locale, kmp, expect/actual, composition-local, datastore, android-locale, ios-nsdefaults, wasmjs-shim, navigator-languages]
project: Checklists
---

# Runtime Language Switching in KMP (Android/iOS/wasmJs)

## Проблема / Контекст

Compose Multiplatform 1.9.3 не имеет встроенного API для runtime-смены локали приложения. JetBrains рекомендует community pattern через `expect/actual` + `CompositionLocal` + `key()` переустановку (issue compose-multiplatform#4197).

**Задача:** реализовать выбор языка в SettingsScreen (System / English / Русский), синхронизированный на всех трёх платформах, с persisten­ством в DataStore и полной переработкой UI при смене языка.

## Решение

### Architecture

```
LanguageRepository (interface, core/datastore/api)
  ├─ getLanguage(): Flow<AppLanguage>
  └─ setLanguage(language: AppLanguage): Unit
      └─ persists to DataStore "language" key

AppLocale (expect/actual)
  ├─ commonMain: customAppLocale StateFlow<String?>, LocalAppLocale accessor
  ├─ androidMain: Locale.setDefault() + Configuration.setLocale()
  ├─ iosMain: NSUserDefaults["AppleLanguages"] binding
  └─ wasmJsMain: globalThis.__customLocale bridge

App.kt
  └─ collect(languageFlow)
      └─ customAppLocale = language.tag
      └─ AppLocaleEnvironment { key(customAppLocale) { ... } }
          └─ force recomposition on language change

SettingsScreen
  └─ SelectLanguage intent
      └─ SettingsViewModel.persistLanguage()
          └─ repository.setLanguage()
              └─ App.kt listener updates customAppLocale
                  └─ composition key() triggers recomposition
```

### Platform Implementation Details

#### Android (AppLocale.android.kt)

```kotlin
@Composable
actual infix fun LocalAppLocale.provides(): ProvidedValue<String?> {
    val deviceDefault = remember { Locale.getDefault().toLanguageTag() }
    return when (customAppLocale) {
        null -> {
            Locale.setDefault(Locale.forLanguageTag(deviceDefault))
            Configuration.setLocale(Locale.forLanguageTag(deviceDefault))
            // resourcesApi.updateConfiguration() for Drawable/String pre-load
        }
        else -> {
            Locale.setDefault(Locale.forLanguageTag(customAppLocale))
            Configuration.setLocale(Locale.forLanguageTag(customAppLocale))
        }
    }
    return CompositionLocalProvider(
        LocalConfiguration provides Configuration.setLocale(...)
    )
}
```

**Key points:**
- `Locale.setDefault()` affects `Locale.getDefault()` calls in String.format(), date formatting, etc.
- `Configuration.setLocale()` updates res layout/drawable selectors in Compose
- Device-default captured on first composition for System-mode fallback
- No `@OptIn` required (standard Android API)

#### iOS (AppLocale.ios.kt)

```kotlin
@OptIn(InternalComposeUiApi::class)
@Composable
actual infix fun LocalAppLocale.provides(): ProvidedValue<String?> {
    NSUserDefaults.standardUserDefaults.setObject(
        listOf(customAppLocale ?: Locale.current.toLanguageTag()),
        forKey = "AppleLanguages"
    )
    val locale = Locale(customAppLocale ?: "")
    return CompositionLocalProvider(
        LocalLocaleSystem provides LocaleSystem(locale.RawValue)
    )
}
```

**Key points:**
- iOS reads `AppleLanguages` array from NSUserDefaults (UIKit convention)
- `@OptIn(InternalComposeUiApi)` required for `Locale.RawValue` access (KT-67852 workaround)
- Platform respects our override on app restart

#### wasmJs (AppLocale.wasmJs.kt + index.html)

```kotlin
// AppLocale.wasmJs.kt
@OptIn(kotlin.js.ExperimentalWasmJsInterop)
@Composable
actual infix fun LocalAppLocale.provides(): ProvidedValue<String?> {
    val deviceDefault = remember { Locale.current.toLanguageTag() }
    __customLocale = customAppLocale ?: deviceDefault
    return CompositionLocalProvider(
        LocalAppLocaleInternal provides (__customLocale ?: deviceDefault)
    )
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop)
private external var __customLocale: String?
```

```html
<!-- index.html — inline shim BEFORE init.js -->
<script>
  // Shim Navigator.languages for Locale.current.toLanguageTag()
  try {
    Object.defineProperty(navigator, 'languages', {
      get() { return [globalThis.__customLocale || navigator.language] },
      configurable: true
    })
  } catch (e) {
    console.warn('[shim] sealed Navigator in WebView:', e.message)
  }
  var __customLocale = null // storage bridge to Kotlin
</script>
<script type="module" src="./init.js"></script>
```

**Key points:**
- `Navigator.languages` shim MUST precede `init.js` (Firebase JS SDK reads navigator at startup)
- `try/catch` handles sealed `navigator` in WebViews (TikTok, Instagram)
- `var __customLocale` (not `let`) for maximum browser compat
- Shim updates reflect in `Locale.current` reads downstream

### DataStore & Repository

```kotlin
// AppLanguage enum
enum class AppLanguage(val tag: String) {
    System(""),
    English("en-US"),
    Russian("ru")
}

// LanguageRepository interface
interface LanguageRepository {
    fun getLanguage(): Flow<AppLanguage>
    suspend fun setLanguage(language: AppLanguage)
}

// LanguageRepositoryImpl
class LanguageRepositoryImpl(private val appDataStore: DataStore<AppPreferences>) : LanguageRepository {
    override fun getLanguage(): Flow<AppLanguage> =
        appDataStore.data.map { prefs ->
            AppLanguage.entries.find { it.tag == prefs.language } ?: AppLanguage.System
        }
    
    override suspend fun setLanguage(language: AppLanguage) {
        appDataStore.updateData { it.copy(language = language.tag) }
    }
}
```

### Strings Localization

**values/strings.xml (English):**
```xml
<string name="settings_language">Language</string>
<string name="settings_language_system">System</string>
<string name="settings_language_english">English</string>
<string name="settings_language_russian">Русский</string>
```

**values-ru/strings.xml (Russian):**
```xml
<string name="settings_language">Язык</string>
<string name="settings_language_system">Система</string>
<string name="settings_language_english">English</string>
<string name="settings_language_russian">Русский</string>
```

**Note:** `English` and `Русский` are **endonyms** — kept identical in both files. Endonym is the name a language uses for itself; users expect to see these names regardless of current UI language.

### SettingsScreen Integration

```kotlin
// SettingsScreenContract.kt
sealed interface SettingsScreenContract {
    data class State(
        val selectedTheme: AppThemeMode,
        val selectedLanguage: AppLanguage,  // NEW
        // ... other fields
    )
    
    sealed interface Intent {
        data class SelectTheme(val theme: AppThemeMode) : Intent
        data class SelectLanguage(val language: AppLanguage) : Intent  // NEW
    }
}

// SettingsViewModel.kt
class SettingsViewModel(
    themeRepository: ThemeRepository,
    private val languageRepository: LanguageRepository  // NEW
) : AppViewModel<State, Intent, SideEffect>() {
    init {
        combine(
            themeRepository.getTheme(),
            languageRepository.getLanguage()  // NEW
        ) { theme, language ->
            updateState { copy(selectedTheme = theme, selectedLanguage = language) }
        }.launchIn(viewModelScope)
    }
    
    override fun onIntent(intent: Intent) = when (intent) {
        is SelectLanguage -> {
            languageRepository.setLanguage(intent.language)  // NEW
            emitSideEffect(ShowSnackbar(messageKey = "language_changed"))  // NEW
        }
        // ... other intents
    }
}

// SettingsScreenContent.kt — Language section ABOVE Appearance
Column {
    Text("Language", style = headlineSmall)
    SelectableGroup {
        AppLanguage.entries.forEach { lang ->
            LanguageOption(
                label = stringResource(when (lang) {
                    System -> Res.string.settings_language_system
                    English -> Res.string.settings_language_english
                    Russian -> Res.string.settings_language_russian
                }),
                selected = state.selectedLanguage == lang,
                onClick = { sendIntent(SelectLanguage(lang)) }
            )
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(SpacingMd)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = SpacingMd))
    }
}
```

### App.kt Integration

```kotlin
@Composable
fun App() {
    val languageRepository = koinViewModel<LanguageRepository>()
    val language by languageRepository.getLanguage().collectAsState(AppLanguage.System)
    
    // Update customAppLocale when language changes
    LaunchedEffect(language) {
        AppLocale.customAppLocale.value = language.tag.ifEmpty { null }
    }
    
    AppTheme {
        AppLocaleEnvironment {  // Wrap content for composition key()
            NavHost(...)
        }
    }
}
```

### Test Coverage

```kotlin
// SettingsViewModelTest.kt
@Test
fun init_loadsCurrentLanguageFromRepository() = runTest {
    val expectedLanguage = AppLanguage.Russian
    fakeLanguageRepository.setLanguage(expectedLanguage)
    
    val viewModel = SettingsViewModel(
        themeRepository = fakeThemeRepository,
        languageRepository = fakeLanguageRepository
    )
    
    assertEquals(expectedLanguage, viewModel.screenState.value.selectedLanguage)
}

@Test
fun selectLanguage_persistsToRepository() = runTest {
    // ...
    viewModel.sendIntent(SelectLanguage(AppLanguage.English))
    assertEquals(AppLanguage.English, fakeLanguageRepository.getLanguage().first())
}

@Test
fun selectLanguage_emitsNewState() = runTest {
    // ...
    viewModel.sendIntent(SelectLanguage(AppLanguage.Russian))
    assertEquals(AppLanguage.Russian, viewModel.screenState.value.selectedLanguage)
}
```

## Почему именно так

### 1. expect/actual over inline Koin mocking

Kotlin/Wasm compiler restrictions make it infeasible to mock `Locale.current` in tests. The expect/actual pattern allows platform-specific implementations that test suites can patch independently (Android tests override via `Robolectric`, iOS/wasmJs skip locale assertions).

### 2. CompositionLocal + key() over manual recomposition

Re-creating `NavHost` or calling `recompose()` manually is fragile. The Compose framework's built-in `key()` guard triggers recomposition atomically when `customAppLocale` changes — no manual lifecycle management.

### 3. HTML shim before init.js

Firebase JS SDK calls `navigator.languages` during initialization. A shim added after `init.js` loads is too late; the SDK caches the value. Inline script before `<script type="module">` guarantees interception.

### 4. Endonym convention

Translating language names (e.g., `settings_language_english` → `Английский` in Russian strings.xml) confuses users — they expect to see "English" when selecting from the menu, not a translated name. Endonyms are standard UX across platforms (Android Settings, iOS Settings, Chrome).

### 5. System mode as empty string (not dedicated enum variant)

Storing the device default as `Locale.getDefault().toLanguageTag()` and treating `language == null` as System-mode is leaner than `AppLanguage.System("device-default")`. It avoids magic values and makes the data model cleaner: `""` = System fallback, `"en-US"` / `"ru"` = explicit override.

## Примеры

### Simple locale override for testing

```kotlin
// In a test
@Test
fun chatUI_displaysRussianStringsAfterLanguageSwitch() = runTest {
    val viewModel = ChatViewModel(languageRepository)
    viewModel.switchLanguage(AppLanguage.Russian)
    
    composeTestRule.setContent {
        AppLocaleEnvironment {
            ChatScreen(viewModel)
        }
    }
    
    composeTestRule.onNodeWithText("Отправить").assertIsDisplayed()
}
```

### Graceful fallback in wasmJs

```kotlin
// If Navigator.languages is sealed (WebView), shim falls back to navigator.language
Object.defineProperty(navigator, 'languages', {
    get() {
        return [globalThis.__customLocale || navigator.language]
        // If __customLocale not set, returns [navigator.language]
    }
})
```

## Связанные файлы

- `core/datastore/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/datastore/api/AppLanguage.kt` — enum definition
- `core/datastore/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/datastore/impl/LanguageRepositoryImpl.kt` — persistence layer
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/AppLocale.kt` — commonMain accessor + state
- `core/designsystem/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/AppLocale.android.kt` — Android implementation
- `core/designsystem/src/iosMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/AppLocale.ios.kt` — iOS implementation
- `core/designsystem/src/wasmJsMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/AppLocale.wasmJs.kt` — wasmJs implementation
- `composeApp/src/wasmJsMain/resources/index.html` — Navigator shim
- `feature/settings/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/settings/presentation/SettingsViewModel.kt` — UI integration
- `feature/settings/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/settings/ui/SettingsScreenContent.kt` — Settings UI
