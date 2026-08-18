---
title: "Chat Dock Glassmorphism — Haze 1.7.0 Integration"
date: 2026-06-06
type: feature
modules: [core/designsystem, feature/home, feature/aichat]
keywords: [glassmorphism, haze-blur, chat-dock, ime-hide, composable-overlay, bottom-navigation]
project: gisti-checklists
status: superseded
---

# Chat Dock Glassmorphism: Haze 1.7.0 Integration

> ⚠️ **SUPERSEDED 2026-06-07** — This document describes an implementation on **haze 2.0.0-alpha02**, which did **NOT render backdrop blur** on production. The version claim in this file is incorrect; the actual version was alpha and broken. See [Blur Under Chat Dock — Haze 1.7.2 Downgrade](blur-under-chat-dock-haze-1.7.2-downgrade-2026-06-07.md) for the corrected solution using stable **haze 1.7.2**.

## Проблема / Контекст

Chat dock на ChecklistDetailScreen занимал `Scaffold.bottomBar`, что:
1. Требовало явного contentPadding на скролл-список (и часто забывали его добавлять)
2. Не поддерживал glassmorphism-эффект (прозрачный blur поверх контента)
3. Требовал expect/actual для wasmJs (Haze поддерживает только Android/Desktop в старых версиях)
4. При поднятии IME клавиатура накрывала dock (плохой UX)

**Требование:**
- Переместить dock в оверлей (Box слой поверх контента)
- Применить glassmorphism blur (Haze 1.7.0)
- Скрыть dock при IME с AnimatedVisibility
- Автоматическое contentPadding на списке (через измеренную высоту дока)

## Решение

### 1. Dependency Update

**gradle/libs.versions.toml:**
```toml
haze = "1.7.0" # Supports Compose MP 1.9.3, no expect/actual for wasmJs
```

**core/designsystem/build.gradle.kts:**
```kotlin
dependencies {
    // ... existing
    implementation(libs.haze)
}
```

### 2. GistiChatDock Refactor

**core/designsystem/.../gisti/GistiChatDock.kt:**

```kotlin
@Composable
fun GistiChatDock(
    modifier: Modifier = Modifier,
    onSendMessage: (String) -> Unit,
    // ... other params
) {
    // Single-owner insets: navbar padding, no double-padding from parent
    val insets = WindowInsets.navigationBars
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            )
            .padding(insets.only(bottom = true))
            .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingMd)
            .hazeEffect(
                state = remember { HazeState() },
                style = HazeMaterials.regular()
            )
    ) {
        // ... dock content (ChatInputRow, etc.)
    }
}
```

### 3. ChecklistDetailScreen Integration

**feature/home/.../detail/ChecklistDetailScreen.kt:**

```kotlin
@Composable
fun ChecklistDetailScreen(
    checklistId: String,
    modifier: Modifier = Modifier
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val imeState = rememberImeState()
    var dockHeight by remember { mutableStateOf(0.dp) }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main content with hazeSource
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState) // Source for blur effect
                .nestedScroll(connection = scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                bottom = dockHeight + AppDimens.SpacingLg // Space for dock overlay
            ),
            // ... other params
        ) {
            // Items rendering
        }
        
        // Chat dock overlay (bottom-anchored)
        AnimatedVisibility(
            visible = !imeState.isImeVisible, // Hide when keyboard shows
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { dockHeight = it.height.toDp() } // Measure height for padding
        ) {
            GistiChatDock(
                onSendMessage = { viewModel.sendIntent(Intent.OnSendMessage(it)) },
                // ... other params
            )
        }
    }
}
```

### 4. Haze Configuration

Haze 1.7.0 использует `HazeMaterials` для стилизации:

```kotlin
// Existing call (composable, не val):
val style = HazeMaterials.regular() // Compiles, но переоценивается на каждый recompose

// ✅ Правильно: style вынесен в val перед лямбдой
val hazeStyle = remember { HazeMaterials.regular() }

Column(
    modifier = Modifier
        .hazeEffect(
            state = remember { HazeState() },
            style = hazeStyle // Passed as stable reference
        )
)
```

### 5. IME State Detection

**Composable IME helper (expect/actual, androidMain/wasmJsMain):**

```kotlin
// commonMain
expect fun rememberImeState(): ImeState

data class ImeState(
    val isImeVisible: Boolean
)

// androidMain
actual fun rememberImeState(): ImeState {
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    var isImeVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val listener = ViewCompat.OnApplyWindowInsetsListener { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            isImeVisible = imeVisible
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
    }
    
    return remember(isImeVisible) { ImeState(isImeVisible) }
}

// wasmJsMain
actual fun rememberImeState(): ImeState {
    // Web has no native IME state tracking — assume always visible
    // (Users don't have persistent soft keyboards on web)
    return remember { ImeState(false) }
}
```

### 6. Weekly Checklist Support

**feature/home/.../detail/weekly/WeeklyChecklistDetailContent.kt:**

Аналогично ChecklistDetailScreen — dock overlay + contentPadding.

### 7. Fallback for API ≤30

Haze 1.7.0 автоматически fallback-ит на scrim-overlay (полупрозрачный слой) на старых API:

```kotlin
@Composable
fun GistiChatDock(/*...*/) {
    // Haze handles scrim fallback internally
    // API 31+: glassmorphism blur
    // API 30: scrim overlay (safe fallback)
}
```

## Почему именно так

1. **Haze 1.7.0 (exact version):** совместима с Compose MP 1.9.3 и Kotlin 2.3.0, не требует expect/actual для wasmJs.
2. **Overlay вместо bottomBar:** даёт полную контроль над слоями (dock над списком, а не слайд-out), поддерживает glassmorphism.
3. **AnimatedVisibility + IME detection:** интуитивный UX — dock уходит, когда пользователь начинает печатать (освобождает место для IME).
4. **Измеренная высота дока → contentPadding:** гарантирует, что последний элемент списка видим, не скрыт доком.
5. **hazeSource на списке + hazeEffect на доке:** siblings (не вложенность) — это как Haze ожидает. Самосэмплинг (hazeEffect на контейнере, который сам себя пытается размыть) = no-op.
6. **Single-owner insets:** navbar padding только у дока (не у списка), предотвращает double-padding.
7. **Scrim fallback на API ≤30:** безопасный fallback, нет UI-breaks на старых устройствах.

## Примеры

**Before (Scaffold.bottomBar approach):**
```
Scaffold(
    bottomBar = {
        GistiChatDock()
    },
    content = { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues + extraPadding
        )
    }
)
```
❌ dock скрыт у scroll, нет блура, IME overlap, awkward contentPadding management

**After (Overlay + Haze approach):**
```
Box {
    LazyColumn(
        contentPadding = PaddingValues(bottom = dockHeight)
    ) { /* items */ }
    
    AnimatedVisibility(
        visible = !imeState.isImeVisible
    ) {
        GistiChatDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .hazeEffect(hazeStyle)
        )
    }
}
```
✅ dock над списком, glassmorphism блур, IME скрывает dock автоматически, ясный contentPadding

## Связанные файлы

- `core/designsystem/build.gradle.kts` — +haze 1.7.0
- `core/designsystem/.../gisti/GistiChatDock.kt` — overlay + hazeEffect
- `feature/home/.../detail/ChecklistDetailScreen.kt` — Box layout, AnimatedVisibility, hazeSource
- `feature/home/.../detail/weekly/WeeklyChecklistDetailContent.kt` — analog dock integration
- `gradle/libs.versions.toml` — haze version bump
- `core/designsystem/.../strings.xml` — dock-related strings (EN+RU)

## Pending User Validation

- [ ] Dock blur on API ≤30 vs API 31+ (scrim vs glassmorphism)
- [ ] IME hide/show animation smoothness
- [ ] Scroll-to-bottom behavior при IME dismiss
- [ ] Multi-line input field overflow inside dock
