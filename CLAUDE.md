# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Checklists is a Kotlin Multiplatform (KMP) application targeting Android and iOS. It uses Jetpack Compose Multiplatform for the UI layer. The app helps users create smart checklists powered by AI - paste text, upload files, or share links and AI extracts the key points automatically.

**Main features:**
- Creating and managing checklists
- Tracking progress within checklists
- Creating checklists from templates
- AI-powered automatic checklist generation from uploaded data (photos, PDFs, text files, web links, or pasted text)

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
  analyze/           # AI-powered data analysis and checklist generation
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

## Design System

Located in `core/designsystem/`. Style: **Minimal & Clean** with white background and blue accents.

### Colors (theme/Color.kt)
- **Primary**: Blue (#2196F3) - buttons, icons, accents
- **Background/Surface**: White (#FFFFFF)
- **Text Primary**: Gray900 (#212121)
- **Text Secondary**: Gray600 (#757575)
- **Outline/Dividers**: Gray300 (#E0E0E0)

### Spacing (theme/Dimens.kt)
Use `AppDimens` constants: `SpacingXs` (4dp), `SpacingSm` (8dp), `SpacingMd` (12dp), `SpacingLg` (16dp), `SpacingXl` (24dp), `SpacingXxl` (32dp). Screen padding: 16dp horizontal, 24dp vertical.

### Components (components/)
- `AppButton` / `AppButtonSecondary` / `AppButtonText` - styled buttons
- `AppCard` - card with subtle shadow (12dp corners, 2dp elevation)
- `AppTextField` - outlined text field
- `EmptyState` - centered icon + title + description
- `AppScaffold` - standard screen wrapper with top bar

### Usage
Wrap app in `AppTheme { }` (see App.kt). Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `AppDimens` for consistent styling.

## Key Patterns

- **API/impl split**: Core modules expose interfaces in `api`, implementations in `impl`
- **expect/actual**: Used for platform-specific code (logging, database builders)
- **StateFlow**: All reactive state management uses Kotlin Flow
- **Typesafe project accessors**: Reference modules as `projects.core.common.api` in Gradle

## AI Analyze Feature

Located in `feature/analyze/`. Allows users to input data (photo, PDF, text file, web link, or raw text) for AI analysis that generates checklist items automatically.

### Architecture

```
feature/analyze/
  domain/
    model/             # AnalyzeInputData (sealed interface), AnalyzeResult
    analyzer/          # AiAnalyzer interface
    repository/        # AnalyzeRepository interface
  data/
    analyzer/          # StubAiAnalyzer (mock implementation)
    repository/        # AnalyzeRepositoryImpl
  presentation/
    AnalyzeScreen.kt   # UI for input selection and analysis
    AnalyzeViewModel.kt
    AnalyzeScreenContract.kt
    picker/            # Platform-specific file picker (expect/actual)
  di/
    AnalyzeFeatureModule.kt
```

### Input Types (AnalyzeInputData)
- `Photo` - Image file path
- `PdfDocument` - PDF file path
- `TextFile` - Text file path
- `WebLink` - URL to analyze
- `RawText` - User-entered text

### AI Analyzer Interface
`AiAnalyzer` defines the contract for AI analysis. Currently uses `StubAiAnalyzer` that returns mock data. To implement real AI:
1. Create new class implementing `AiAnalyzer`
2. Replace binding in `analyzeFeatureModule`

### Integration
- Access via "AI Analysis" button on MainScreen
- Results can create new checklist or add items to existing one

## Localization & String Resources

All user-facing strings are externalized to `core/designsystem/src/commonMain/composeResources/values/strings.xml`. Use `stringResource(Res.string.key_name)` from `org.jetbrains.compose.resources` package.

### Adding New Strings
1. Add entry to `strings.xml` with descriptive key
2. Import: `import aichecklists.core.designsystem.generated.resources.Res`
3. Import: `import aichecklists.core.designsystem.generated.resources.*`
4. Import: `import org.jetbrains.compose.resources.stringResource`
5. Use: `stringResource(Res.string.your_key)`

### String Naming Convention
- Prefix with screen name: `main_`, `create_`, `analyze_`, `onboarding_`
- Common strings use generic names: `save`, `cancel`, `ok`, `error`
- Use snake_case: `main_empty_description`

## Dependencies

Versions are centralized in `gradle/libs.versions.toml`. Key dependencies:
- Kotlin 2.3.0, Compose Multiplatform 1.9.3
- Koin 4.1.1 (DI), Room 2.8.4 (database), Navigation Compose 2.9.1

## UX Research Findings

### Navigation Flow
```
Splash → Onboarding → Main (Home) ↔ [Create, Analyze, Debug]
```

### Screen-by-Screen Analysis

#### Splash Screen
- Clean loading with progress indicator
- Clear branding with app title

#### Onboarding Screen
- Good value proposition communication
- Single clear CTA ("Get Started" button)
- Proper spacing hierarchy

#### Main Screen (Home)
**Empty State**: Shows icon, title, description, dual CTAs (Create + AI Analyze)
**Success State**: Lists checklists as simple cards

**Issues Identified**:
- Checklist cards show only name - missing item count, progress, timestamps
- Cards lack visual feedback for clickability
- Duplicate CTAs in empty state AND bottom bar

#### Create Checklist Screen
- Title input + items list + save button
- Dialog for adding items

**Issues Identified**:
- No edit/delete UI for items (only add)
- No validation feedback for empty inputs
- Items displayed without checkboxes or delete buttons

#### Analyze Screen
- 5 input type cards (Photo, PDF, Text File, Web Link, Raw Text)
- Context-sensitive input area
- Loading state with spinner
- Result dialog with item list

**Issues Identified**:
- Result preview limited to 5 items without scroll

### Critical UX Issues

1. **Missing Interactivity**: Checklist cards appear clickable but navigation not implemented
2. **Incomplete Item Management**: Create screen lacks edit/delete actions for items
3. **No Input Validation**: Empty checklist names allowed without feedback
4. **Redundant UI**: Duplicate Create/Analyze buttons in multiple locations
5. **Limited Data Display**: Checklists show only names without counts or progress

### Recommended Improvements

1. **Enhance Checklist Cards**:
   - Add item count badge
   - Show completion progress indicator
   - Add timestamps or last-modified dates
   - Make cards visually clickable (chevron indicator)

2. **Improve Create Screen**:
   - Add swipe-to-delete or delete buttons for items
   - Add inline edit capability
   - Show validation errors
   - Add item reordering via drag handles

3. **Streamline Navigation**:
   - Remove duplicate CTAs - keep only in bottom bar
   - Add proper back stack management
   - Add confirmation dialogs for destructive actions

4. **Visual Enhancements**:
   - Add checkboxes to item cards
   - Show progress bars on checklist cards
   - Add empty state animations
   - Improve loading states with skeletons

5. **Accessibility**:
   - Add content descriptions to all icons
   - Ensure 48dp minimum touch targets
   - Verify color contrast ratios

## Product Copy Guidelines

### Target Audience
Anyone who needs to create structured checklists from unstructured data. Users may have meeting notes, articles, requirements documents, or any text that needs to be converted into actionable checklist items.

### Core Value Proposition
1. **AI-powered extraction** - paste any text and get a structured checklist
2. **Multiple input formats** - photos, PDFs, text files, web links, or raw text
3. **Track progress** - manage and complete checklist items

### Copy Principles
- **Simple & Clear**: Match the minimal design with concise language
- **Benefit-focused**: Tell users what they gain, not just what the feature does
- **Action-oriented**: Use active verbs for buttons (Create, Analyze, Save)
- **Helpful empty states**: Guide users to the next action when content is missing
- **No jargon**: Avoid technical terms; use everyday language

### Tone of Voice
- Friendly but professional
- Encouraging (motivate users to try AI analysis)
- No exclamation marks except in empty states

### Button Label Guidelines
| Do | Don't |
|----|----|
| Create Checklist | Add New |
| AI Analysis | AI Analyze |
| Save | Submit |
| Get Started | Continue |

### Implemented UX Improvements (January 2026)

1. **Enhanced Checklist Cards** (`MainScreenContent.kt`):
   - Added progress bar showing completion status (checked/total items)
   - Added item count display (e.g., "3/5")
   - Added chevron icon indicating card is clickable
   - Shows "No elements" text for empty checklists

2. **Streamlined CTA Buttons** (`MainScreen.kt`, `MainScreenContent.kt`):
   - Removed duplicate Create/Analyze buttons from empty state
   - Bottom bar now shows consistently for both empty and non-empty states
   - Single source of truth for action buttons

3. **Item Management in Create Screen** (`CreateChecklistScreen.kt`):
   - Added delete button (X icon) to each checklist item
   - Users can now remove items they've added

4. **Input Validation** (`CreateChecklistViewModel.kt`, `CreateChecklistScreenContract.kt`):
   - Added `nameError` field to state
   - Validates checklist name is not blank before saving
   - Shows inline error message on text field when validation fails
   - Clears error when user starts typing
