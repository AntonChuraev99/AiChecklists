---
title: "Design System & UI Patterns"
description: "Comprehensive guide to Gisti's design system, reusable components, and edge-to-edge UI handling"
category: "UI/UX"
tags: ["design", "compose", "components", "theme", "system-insets"]
author: "Design System Team"
created: 2026-01-25
updated: 2026-01-25
---

# Design System & UI Patterns

Gisti follows a **minimal & clean** design philosophy with a white background and blue accents. This guide covers theme setup, reusable components, illustrations pattern, and system insets handling for edge-to-edge layouts.

## Table of Contents

1. [Color System](#color-system)
2. [Spacing & Dimensions](#spacing--dimensions)
3. [Typography](#typography)
4. [Shape System](#shape-system)
5. [Reusable Components](#reusable-components)
6. [Illustrations Pattern](#illustrations-pattern)
7. [System Insets & Edge-to-Edge](#system-insets--edge-to-edge)
8. [Theme Setup](#theme-setup)
9. [Best Practices](#best-practices)

---

## Color System

Located in: `core/designsystem/src/commonMain/kotlin/.../theme/Color.kt`

### Semantic Color Palette

Gisti uses a **blue-based Material 3 palette** with neutral grays for text and backgrounds.

#### Primary Colors (Blue)
```kotlin
val Blue50 = Color(0xFFE3F2FD)    // Lightest
val Blue100 = Color(0xFFBBDEFB)
val Blue200 = Color(0xFF90CAF9)
val Blue300 = Color(0xFF64B5F6)
val Blue400 = Color(0xFF42A5F5)
val Blue500 = Color(0xFF2196F3)   // Primary (main CTA buttons)
val Blue600 = Color(0xFF1E88E5)
val Blue700 = Color(0xFF1976D2)   // Secondary actions
val Blue800 = Color(0xFF1565C0)
val Blue900 = Color(0xFF0D47A1)   // Darkest
```

#### Neutral Colors
```kotlin
val White = Color(0xFFFFFFFF)       // Background & surface
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)     // Subtle backgrounds
val Gray200 = Color(0xFFEEEEEE)
val Gray300 = Color(0xFFE0E0E0)     // Outlines
val Gray400 = Color(0xFFBDBDBD)
val Gray500 = Color(0xFF9E9E9E)
val Gray600 = Color(0xFF757575)     // Secondary text
val Gray700 = Color(0xFF616161)
val Gray800 = Color(0xFF424242)
val Gray900 = Color(0xFF212121)     // Primary text
```

#### Semantic Colors
```kotlin
val Success = Color(0xFF4CAF50)
val SuccessLight = Color(0xFFE8F5E9)
val Error = Color(0xFFE53935)       // Destructive actions
val ErrorLight = Color(0xFFFFEBEE)
val Warning = Color(0xFFFF9800)
val WarningLight = Color(0xFFFFF3E0)
```

### Color Scheme (Material 3)

```kotlin
val LightColorScheme = lightColorScheme(
    primary = Blue500,                    // Main action color
    onPrimary = White,                   // Text on primary
    primaryContainer = Blue100,          // Light backgrounds
    onPrimaryContainer = Blue900,
    secondary = Blue700,                 // Accent actions
    onSecondary = White,
    tertiary = Blue600,
    background = White,                  // Screen background
    onBackground = Gray900,              // Primary text
    surface = White,                     // Card/surface background
    onSurface = Gray900,
    surfaceVariant = Gray100,           // Subtle surface
    onSurfaceVariant = Gray600,         // Secondary text
    outline = Gray300,                   // Borders/dividers
    error = Error,
    onError = White
)
```

### Usage

```kotlin
import androidx.compose.material3.MaterialTheme

// Apply colors in Composables
Box(
    modifier = Modifier.background(MaterialTheme.colorScheme.primary)
)

Text(
    "Hello",
    color = MaterialTheme.colorScheme.onBackground
)
```

---

## Spacing & Dimensions

Located in: `core/designsystem/src/commonMain/kotlin/.../theme/Dimens.kt`

### Spacing Scale

All spacing follows an **8dp base unit** for consistency:

```kotlin
object AppDimens {
    // Spacing (8dp base unit)
    val SpacingXxs = 2.dp      // Tiny gaps
    val SpacingXs = 4.dp       // Icon spacing
    val SpacingSm = 8.dp       // Default inner padding
    val SpacingMd = 12.dp      // Medium spacing
    val SpacingLg = 16.dp      // Padding inside cards
    val SpacingXl = 24.dp      // Section spacing
    val SpacingXxl = 32.dp     // Large section gaps
    val SpacingXxxl = 48.dp    // Full breaks between sections

    // Screen padding (horizontal/vertical)
    val ScreenPaddingHorizontal = 16.dp
    val ScreenPaddingVertical = 24.dp

    // Component sizes
    val ButtonHeight = 48.dp             // Full buttons
    val ButtonHeightSmall = 36.dp
    val IconSizeSm = 16.dp
    val IconSizeMd = 24.dp               // Standard icons
    val IconSizeLg = 32.dp
    val IconSizeXl = 48.dp               // Large badges
    val IconSizeXxl = 64.dp              // Export option icons

    // Cards & containers
    val CardElevation = 2.dp
    val CardPadding = 16.dp

    // Input fields
    val TextFieldHeight = 56.dp

    // Bars
    val TopBarHeight = 56.dp
    val BottomBarHeight = 80.dp

    // Misc
    val DividerThickness = 1.dp
    val MinTouchTarget = 48.dp           // Accessibility: min touch size
}
```

### Common Spacing Patterns

**Card with padding:**
```kotlin
AppCard {
    Column(modifier = Modifier.padding(AppDimens.CardPadding)) {
        // content
    }
}
```

**Section spacing:**
```kotlin
Column {
    Header()
    Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
    Content()
}
```

**Screen with standard padding:**
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
) {
    // content (no double padding)
}
```

---

## Typography

Located in: `core/designsystem/src/commonMain/kotlin/.../theme/Type.kt`

### Typography Scale

Material 3 typography with clear hierarchy:

| Style | Size | Weight | Use Case |
|-------|------|--------|----------|
| **displayLarge** | 57sp | Normal | Hero headlines (rare) |
| **displayMedium** | 45sp | Normal | Large section headers |
| **displaySmall** | 36sp | Normal | Prominent titles |
| **headlineLarge** | 32sp | SemiBold | Page titles |
| **headlineMedium** | 28sp | SemiBold | Section headers |
| **headlineSmall** | 24sp | SemiBold | Card titles |
| **titleLarge** | 22sp | Medium | Toolbar titles, app top bar |
| **titleMedium** | 16sp | Medium | Dialog titles, important text |
| **titleSmall** | 14sp | Medium | List item titles |
| **bodyLarge** | 16sp | Normal | Main body text |
| **bodyMedium** | 14sp | Normal | Body paragraphs (default) |
| **bodySmall** | 12sp | Normal | Helper text, secondary content |
| **labelLarge** | 14sp | Medium | Button text, prominent labels |
| **labelMedium** | 12sp | Medium | Small badges, labels |
| **labelSmall** | 11sp | Medium | Tiny labels, tags |

### Usage Examples

```kotlin
import androidx.compose.material3.MaterialTheme

// Page title
Text(
    "Your Checklists",
    style = MaterialTheme.typography.headlineLarge
)

// Body text
Text(
    "Organize your tasks with AI",
    style = MaterialTheme.typography.bodyMedium
)

// Button text
Button(onClick = {}) {
    Text("Create", style = MaterialTheme.typography.labelLarge)
}

// Secondary text
Text(
    "Last updated 2 hours ago",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```

---

## Shape System

Located in: `core/designsystem/src/commonMain/kotlin/.../theme/Shape.kt`

### Corner Radius Scale

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // Text fields, small buttons
    small = RoundedCornerShape(8.dp),         // Buttons
    medium = RoundedCornerShape(12.dp),       // Cards, dialogs
    large = RoundedCornerShape(16.dp),        // Full-width containers
    extraLarge = RoundedCornerShape(24.dp)    // Large modals
)
```

### Components Using Shapes

```kotlin
// Button (small, 8dp)
Button(shape = MaterialTheme.shapes.small)

// Card (medium, 12dp)
Card(shape = MaterialTheme.shapes.medium)

// TextField (small, 8dp)
OutlinedTextField(shape = MaterialTheme.shapes.small)

// Large containers (16dp-24dp)
Box(shape = MaterialTheme.shapes.large)
```

---

## Reusable Components

Located in: `core/designsystem/src/commonMain/kotlin/.../components/`

### AppButton

**Primary action button** - use for main CTAs.

```kotlin
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
)
```

**Usage:**
```kotlin
AppButton(
    text = "Create Checklist",
    onClick = { /* ... */ }
)

// With icon
AppButton(
    text = "Create",
    onClick = { /* ... */ },
    icon = Icons.Default.Add
)

// Disabled state
AppButton(
    text = "Save",
    onClick = { /* ... */ },
    enabled = isValid
)
```

**Visual:**
- Height: 48dp
- Background: Primary blue (#2196F3)
- Text color: White
- Corner radius: 8dp
- Content padding: 24dp horizontal, 12dp vertical

---

### AppButtonSecondary

**Secondary action button** - use for alternate/less important actions.

```kotlin
@Composable
fun AppButtonSecondary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
)
```

**Usage:**
```kotlin
AppButtonSecondary(
    text = "Cancel",
    onClick = { /* ... */ }
)
```

**Visual:**
- Height: 48dp
- Background: Transparent
- Border: 1dp primary blue
- Text color: Primary blue
- Corner radius: 8dp

---

### AppButtonText

**Text-only button** - use for subtle/tertiary actions.

```kotlin
@Composable
fun AppButtonText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
)
```

**Usage:**
```kotlin
AppButtonText(
    text = "Learn More",
    onClick = { /* ... */ }
)
```

**Visual:**
- No background
- Text color: Primary blue
- Content padding: 16dp horizontal, 8dp vertical
- Responsive to enabled state

---

### AppButtonDestructive

**Destructive action button** - use for delete/remove operations.

```kotlin
@Composable
fun AppButtonDestructive(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
)
```

**Usage:**
```kotlin
AppButtonDestructive(
    text = "Delete Checklist",
    onClick = { /* ... */ }
)
```

**Visual:**
- Height: 48dp
- Background: Error red (#E53935)
- Text color: White
- Corner radius: 8dp

---

### AppCard

**Container component** - use for content grouping with optional click handling.

```kotlin
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
)
```

**Usage:**
```kotlin
// Non-clickable card
AppCard {
    Text("Card content")
}

// Clickable card
AppCard(
    onClick = { navigateToDetail() }
) {
    Row {
        Text("Apartment Inspection")
        Text("3 items checked")
    }
}
```

**Visual:**
- Background: Surface white
- Corner radius: 12dp (medium)
- Elevation: 2dp
- Internal padding: 16dp
- Fills full width

---

### AppTextField

**Text input component** - supports leading/trailing icons, error state, clear button.

```kotlin
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    showClearButton: Boolean = false
)
```

**Usage:**
```kotlin
// Simple input
var name by remember { mutableStateOf("") }
AppTextField(
    value = name,
    onValueChange = { name = it },
    label = "Checklist name",
    placeholder = "Enter name..."
)

// With error state
AppTextField(
    value = email,
    onValueChange = { email = it },
    label = "Email",
    isError = !isEmailValid,
    errorMessage = if (!isEmailValid) "Invalid email" else null
)

// Multi-line with clear button
AppTextField(
    value = description,
    onValueChange = { description = it },
    placeholder = "Enter description...",
    singleLine = false,
    maxLines = 5,
    showClearButton = true
)

// With leading icon
AppTextField(
    value = searchTerm,
    onValueChange = { searchTerm = it },
    leadingIcon = { Icon(Icons.Default.Search) }
)
```

**Visual:**
- Height: Intrinsic (grows with maxLines)
- Border: 1dp outline gray
- Corner radius: 8dp (small)
- Focused border: Primary blue
- Error border: Error red
- Fills max width

---

### EmptyState

**Empty state container** - displays icon, title, description, and optional action button.

```kotlin
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
)
```

**Usage:**
```kotlin
EmptyState(
    icon = Icons.Default.CheckCircle,
    title = "No Checklists Yet",
    description = "Create your first checklist to get started",
    action = {
        AppButton(
            text = "Create Checklist",
            onClick = { /* ... */ }
        )
    }
)
```

**Visual:**
- Icon: 44dp, primary blue, in 88dp circular background (light blue)
- Title: 22sp, semibold, centered
- Description: 14sp, secondary gray, centered
- Action: Optional button below
- Centered vertically and horizontally
- Horizontal padding: 16dp

---

### AppScaffold

**Screen container** - provides top bar, optional bottom bar, and automatic inset handling.

```kotlin
@Composable
fun AppScaffold(
    title: String? = null,
    onBackButtonClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
)
```

**Usage:**
```kotlin
// Simple screen with title
AppScaffold(
    title = "Checklist Detail"
) {
    ChecklistContent()
}

// With back button
AppScaffold(
    title = "Edit Checklist",
    onBackButtonClick = { navigateBack() }
) {
    EditForm()
}

// With actions
AppScaffold(
    title = "Checklists",
    actions = {
        IconButton(onClick = { /* ... */ }) {
            Icon(Icons.Default.Share)
        }
    }
) {
    ChecklistList()
}

// With bottom bar
AppScaffold(
    title = "Create",
    bottomBar = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.ScreenPaddingHorizontal)
        ) {
            AppButtonSecondary(
                text = "Cancel",
                onClick = { /* ... */ },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(AppDimens.SpacingLg))
            AppButton(
                text = "Save",
                onClick = { /* ... */ },
                modifier = Modifier.weight(1f)
            )
        }
    }
) {
    CreateForm()
}
```

**Visual:**
- Top bar: 56dp height
- Back button: Leading position (auto-mirrors for RTL)
- Title: Centered, 22sp medium
- Actions: Trailing area
- Background: White
- Content padding: Respects Material 3 insets

**Important:** `AppScaffold` **automatically handles system insets** via Material 3's `Scaffold` - no need for additional padding modifiers.

---

## Illustrations Pattern

Located in: `core/designsystem/src/commonMain/kotlin/.../illustrations/FeatureIllustrations.kt`

### Philosophy

Illustrations are **visual storytelling** that explain Gisti's core features. They are:
- **Composable functions** for reusability
- **Minimal and clean** - match design aesthetic
- **Instructional** - show user flow/benefit
- **Scalable** - use theme colors and spacing

### Illustration Components

#### 1. CreateViaAiIllustration()

Shows input formats → AI processing → checklist creation.

**Visual Flow:**
```
[Photo] [PDF] [Text]        ← Input types
[Link]  [Voice]

    ↓ (arrow dots)

   ✨ AI                      ← Processing badge

    ↓ (arrow dots)

[Task extracted...]         ← Result checklist
[Another item...]
[AI-generated...]
```

**Usage:**
```kotlin
HorizontalPager(...) {
    CreateViaAiIllustration()
}
```

---

#### 2. FillViaAiIllustration()

Shows existing checklist + new content → AI filling in items.

**Visual Flow:**
```
🏠 Apartment Check           ← Existing template
☐ Kitchen condition
☐ Windows & doors
☐ Plumbing works

  + [Photo]                  ← Add new input

    ↓ (arrow)

✅ Main Street Apt           ← Filled result
☑ Kitchen condition
☑ Windows & doors
☐ Plumbing works
```

**Usage:**
```kotlin
HorizontalPager(...) {
    FillViaAiIllustration()
}
```

---

#### 3. ExportShareIllustration()

Shows checklist → export formats → share.

**Visual Flow:**
```
Project Review (3/3 ✓)      ← Completed checklist
☑ Review document
☑ Send email
☑ Final approval

    ↓ (arrow)

[PDF] [Text]                ← Export options

[🔗 Share Button]           ← CTA
```

**Usage:**
```kotlin
HorizontalPager(...) {
    ExportShareIllustration()
}
```

---

#### 4. PremiumBenefitsIllustration()

Shows premium subscription benefits.

**Visual Flow:**
```
✨ Unlimited Create via AI    ✓
🔄 Unlimited Fill via AI      ✓
📄 PDF & Text Export         ✓
⚡ 300 AI credits daily       ✓
```

**Usage:**
```kotlin
HorizontalPager(...) {
    PremiumBenefitsIllustration()
}
```

---

### Helper Components

All illustrations use reusable helper composables:

#### InputIcon()
```kotlin
InputIcon(Icons.Outlined.PhotoCamera, "Photo")
// Renders: [Icon] label
// Used for: Input type indicators
```

#### ArrowDots()
```kotlin
ArrowDots()
// Renders: 3 stacked dots
// Used for: Visual separator/transition
```

#### AiBadge()
```kotlin
AiBadge()
// Renders: ✨ AI (badge with border)
// Used for: AI processing indicator
```

#### ChecklistItemRow()
```kotlin
ChecklistItemRow("Task text", isChecked = true)
// Renders: [☑ or ☐] Task text
// Used for: Checklist items in previews
```

#### ExportOption()
```kotlin
ExportOption(Icons.Outlined.PictureAsPdf, "PDF")
// Renders: [PDF Icon], "PDF"
// Used for: Export format options
```

#### PremiumBenefitRow()
```kotlin
PremiumBenefitRow("✨", "Unlimited Create via AI")
// Renders: [emoji] Benefit text [checkmark]
// Used for: Premium feature list
```

---

### Creating New Illustrations

**Guidelines:**
1. Keep illustrations in `FeatureIllustrations.kt`
2. Use `MaterialTheme.colorScheme` for colors (not hardcoded)
3. Use `AppDimens` for spacing
4. Make illustration width `fillMaxWidth()` or `fillMaxWidth(0.85f)`
5. Center content horizontally
6. Include comments documenting the flow
7. Keep file preview items (checklists, etc.) realistic
8. Match the minimal, clean aesthetic

**Example template:**
```kotlin
@Composable
fun YourNewIllustration() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Part 1
        YourComponent1()

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Transition
        ArrowDots()

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Part 2
        YourComponent2()
    }
}
```

---

## System Insets & Edge-to-Edge

### Problem

On modern Android and iOS, **system bars** (status bar, navigation bar) overlay app content. Without proper handling, UI elements can be hidden behind these bars.

### Solution

The design system provides two patterns depending on whether you use `AppScaffold`:

### Pattern 1: WITH AppScaffold (Recommended)

**AppScaffold automatically handles insets** via Material 3's `Scaffold`. No extra work needed.

```kotlin
AppScaffold(
    title = "My Screen",
    onBackButtonClick = { /* ... */ }
) {
    // Content here is automatically padded
    ChecklistList()
}
```

**Why it works:**
- Material 3's `Scaffold` uses `WindowInsets` API
- Automatically pads content away from status/navigation bars
- Works on all platforms

**Screens using AppScaffold:**
- ChecklistDetailScreen
- MainScreen
- CreateChecklistScreen
- TemplatesScreen
- AnalyzeResultPreviewScreen
- SubscriptionStatusScreen
- ShareScreen

---

### Pattern 2: WITHOUT AppScaffold (Custom/Fullscreen)

**For fullscreen or custom layouts**, you MUST manually add inset padding:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()       // ← REQUIRED
        .navigationBarsPadding()   // ← REQUIRED
        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
)
{
    // content
}
```

**Required imports:**
```kotlin
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
```

**Screens without AppScaffold (requiring inset handling):**

1. **OnboardingScreen** - fullscreen pager
   ```kotlin
   Column(
       modifier = Modifier
           .fillMaxSize()
           .statusBarsPadding()
           .navigationBarsPadding()
   ) {
       HorizontalPager(...)
   }
   ```

2. **PaywallScreen** - fullscreen purchase flow
   ```kotlin
   Column(
       modifier = Modifier
           .fillMaxSize()
           .statusBarsPadding()
           .navigationBarsPadding()
   ) {
       HorizontalPager(...)
   }
   ```

3. **SplashScreen** - centered content
   ```kotlin
   Box(
       modifier = Modifier
           .fillMaxSize()
           .statusBarsPadding()
           .navigationBarsPadding(),
       contentAlignment = Alignment.Center
   ) {
       Logo()
   }
   ```

---

### How It Works

#### statusBarsPadding()
- Adds top padding equal to device status bar height
- Prevents content from rendering under status bar
- Needed on both Android and iOS

#### navigationBarsPadding()
- Adds bottom padding equal to device navigation bar height
- Android: system navigation buttons
- iOS: home indicator, safe area

#### Real-world heights (approximate)
- Status bar: 24-48dp (varies by device/OS)
- Navigation bar: 48-88dp (varies by device)

---

### Debugging Insets

To verify insets are working:

1. **Test on real devices** - emulators may have different bar sizes
2. **Check in landscape** - insets change
3. **Verify with compose preview** - may not show full inset picture
4. **Use layout inspector** - Android Studio > Layout Inspector

```kotlin
// Temporary: show visual bounds
Column(
    modifier = Modifier
        .fillMaxSize()
        .border(2.dp, Color.Red)  // Visual debug
        .statusBarsPadding()
        .navigationBarsPadding()
)
```

---

## Theme Setup

Located in: `core/designsystem/src/commonMain/kotlin/.../theme/Theme.kt`

### AppTheme Composable

Applies all design system settings (colors, typography, shapes) to child content:

```kotlin
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
```

### Usage in App

```kotlin
// In composeApp/src/.../App.kt
@Composable
fun App() {
    AppTheme {
        AppScaffold(
            title = "Home"
        ) {
            MainContent()
        }
    }
}
```

### Multi-screen App

```kotlin
@Composable
fun App() {
    AppTheme {
        // All screens now have theme colors/typography
        Navigation()
    }
}

@Composable
private fun Navigation() {
    when (currentRoute) {
        is Route.Home -> MainScreen()
        is Route.Detail -> DetailScreen()
        is Route.Onboarding -> OnboardingScreen()
    }
}
```

**All Material 3 components automatically use the theme:**
- Button colors follow colorScheme
- Text styles follow typography
- Shapes follow shapes definition
- No need to re-apply theme in child screens

---

## Best Practices

### 1. Use Theme Colors, Not Hardcoded Colors

**❌ Wrong:**
```kotlin
Text(
    "Hello",
    color = Color(0xFF2196F3)  // Hardcoded blue
)
```

**✅ Correct:**
```kotlin
Text(
    "Hello",
    color = MaterialTheme.colorScheme.primary
)
```

**Why:** Theme colors adapt to light/dark mode, ensure consistency, allow global theme changes.

---

### 2. Use Design System Components

**❌ Wrong:**
```kotlin
Button(
    onClick = {},
    colors = ButtonDefaults.buttonColors(
        containerColor = Color.Blue,
        contentColor = Color.White
    )
) {
    Text("Save")
}
```

**✅ Correct:**
```kotlin
AppButton(
    text = "Save",
    onClick = {}
)
```

**Why:** Reusable components ensure consistency, reduce code duplication, simplify updates.

---

### 3. Follow Spacing Scale

**❌ Wrong:**
```kotlin
Column(
    modifier = Modifier.padding(horizontal = 13.dp, vertical = 15.dp)
) {
    // spacing not aligned to 8dp scale
}
```

**✅ Correct:**
```kotlin
Column(
    modifier = Modifier.padding(
        horizontal = AppDimens.ScreenPaddingHorizontal,  // 16dp
        vertical = AppDimens.SpacingLg                   // 16dp
    )
) {
    // consistent 8dp scale
}
```

**Why:** Maintains visual rhythm, simplifies design handoff, ensures responsive layouts.

---

### 4. Handle Text in HorizontalPager

**❌ Wrong:**
```kotlin
HorizontalPager(...) {
    Text(
        text = "Description",
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
```

Text may overflow on swipe because it doesn't know its width constraint.

**✅ Correct:**
```kotlin
HorizontalPager(...) {
    Text(
        text = "Description",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()  // REQUIRED
    )
}
```

**Why:** `fillMaxWidth()` tells text to measure against full pager width.

---

### 5. Avoid Double Padding

**❌ Wrong:**
```kotlin
Column(
    modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)
) {
    TextField(
        modifier = Modifier.padding(horizontal = AppDimens.SpacingLg)
        // Now has 16 + 16 = 32dp padding - too much!
    )
}
```

**✅ Correct:**
```kotlin
Column(
    modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)
) {
    TextField(
        modifier = Modifier.fillMaxWidth()
        // Uses parent's padding, no extra
    )
}
```

**Why:** Prevents layout issues, maintains consistent spacing.

---

### 6. System Insets in Custom Layouts

**❌ Wrong:**
```kotlin
Column(
    modifier = Modifier.fillMaxSize()
) {
    HorizontalPager(...) {
        // Status bar overlaps content!
    }
}
```

**✅ Correct:**
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
) {
    HorizontalPager(...) {
        // Content properly spaced from system bars
    }
}
```

**Why:** Required for edge-to-edge layouts to prevent overlaps.

---

### 7. Semantic Text Colors

**❌ Wrong:**
```kotlin
Text("Secondary text", color = Color.Gray)  // Which gray?
```

**✅ Correct:**
```kotlin
Text(
    "Secondary text",
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

// Or for supporting text
Text(
    "Helper text",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodySmall
)
```

**Why:** Semantic colors have meaning, adapt to theme, improve accessibility.

---

### 8. Button Hierarchy

**Primary Action (main CTA):**
```kotlin
AppButton(text = "Create Checklist", onClick = {})
```

**Secondary Action (alternative):**
```kotlin
AppButtonSecondary(text = "Cancel", onClick = {})
```

**Tertiary Action (least important):**
```kotlin
AppButtonText(text = "Learn More", onClick = {})
```

**Destructive Action (delete/remove):**
```kotlin
AppButtonDestructive(text = "Delete", onClick = {})
```

**Why:** Visual hierarchy guides user attention to most important actions.

---

### 9. Empty State Best Practices

**Good:**
```kotlin
EmptyState(
    icon = Icons.Default.CheckCircle,
    title = "All Done!",
    description = "You've completed all your tasks. Create a new checklist to continue.",
    action = {
        AppButton(
            text = "New Checklist",
            onClick = { navigateToCreate() }
        )
    }
)
```

**Why:**
- Icon explains the state visually
- Title is clear and encouraging
- Description provides context
- Action button guides next step

---

### 10. Card Pattern for Lists

**Instead of:** Text strings in a Column

**Use:** AppCard for each item
```kotlin
LazyColumn {
    items(checklists) { checklist ->
        AppCard(
            onClick = { navigateToDetail(checklist.id) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        checklist.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${checklist.items.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight)
            }
        }
    }
}
```

**Why:** Cards provide visual grouping, clear touch targets, better UX.

---

## Resources

- **Color file:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/Color.kt`
- **Dimensions file:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/Dimens.kt`
- **Typography file:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/Type.kt`
- **Shapes file:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/Shape.kt`
- **Components:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/`
- **Container:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/containers/AppScaffold.kt`
- **Illustrations:** `/core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/illustrations/FeatureIllustrations.kt`

---

## Related Documentation

- [Navigation Patterns](../navigation/)
- [State Management & MVI](../architecture/)
- [Localization & Strings](../i18n/)
