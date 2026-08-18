---
title: "Blur Under Chat Dock — Haze 1.7.2 Downgrade & API Fix"
date: 2026-06-07
type: bug-fix
modules: [core/designsystem, feature/home]
keywords: [glassmorphism, haze-blur, backdrop-blur, api-migration, alpha-version-trap, hazeSource-siblings]
project: gisti-checklists
---

# Blur Under Chat Dock — Haze 1.7.2 Downgrade & API Fix

## Проблема / Контекст

Проект сидел на нестабильной `haze 2.0.0-alpha02`, где backdrop-blur не рендерился вообще. Chat dock отображался как плоская непрозрачная плашка, скрывающая контент под собой, вместо стеклянного overlay с размытием. Попытка использовать `HazeColorEffect.tint()` API и отдельный модуль `haze-blur` привела к визуальному провалу: blur-эффект на canvas не сделалась.

**Симптом:** doc на MainScreen и ChecklistDetailScreen имел тёмный Surface цвет, скрывающий контент; размытие отсутствовало.

**Диагностика:** Сравнение с работающим прод-проектом swapfaceandroid (также Compose Multiplatform) показал, что тот использует хорошо известную и стабилизированную `haze 1.7.2`. Версия различалась на целый минор.

## Решение

### 1. Откат версии Haze

**gradle/libs.versions.toml**
```toml
# Before
haze = "2.0.0-alpha02"

# After
haze = "1.7.2"
```

Удалены алиас `haze-blur` и опциональный модуль из catalog (в 1.7.2 не существует).

**core/designsystem/build.gradle.kts**
```kotlin
# Before
implementation(libs.haze)
implementation(libs.haze.blur)  // ❌ 2.0-alpha only

# After
implementation(libs.haze)  # ✅ All-in-one module in 1.7.2
```

### 2. Миграция API

**Haze 2.0 API (не рабочая):**
```kotlin
hazeEffect(state) {
    blurEffect {
        blurRadius = 32.dp
        colorEffects = listOf(
            HazeColorEffect.tint(color = dockTint)
        )
    }
}
```

**Haze 1.7.2 API (работающая):**
```kotlin
val hazeStyle = remember { HazeStyle(
    blurRadius = 32.dp,
    backgroundColor = MaterialTheme.colorScheme.surface,
    tint = HazeTint(
        color = dockTint,
        alpha = 0.4f  # ⚠️ CRITICAL: alpha < 1.0 for blur to be visible
    )
) }

hazeEffect(state = rememberHazeState(), style = hazeStyle)
```

**GistiChatDock.kt переписан:**
```kotlin
Column(
    modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        .hazeEffect(
            state = remember { HazeState() },
            style = remember { HazeStyle(
                blurRadius = 32.dp,
                backgroundColor = MaterialTheme.colorScheme.surface,
                tint = HazeTint(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    alpha = 0.4f
                )
            ) }
        )
        .background(MaterialTheme.colorScheme.surface)
) {
    // ChatInputRow + other dock content
}
```

### 3. Architecture: hazeSource ↔ hazeEffect Siblings

**Kritical insight:** `hazeSource()` (on content) and `hazeEffect()` (on dock) must be **siblings in z-order**, not nested.

**ChecklistDetailScreen.kt:**
```kotlin
Box {
    LazyColumn(
        modifier = Modifier
            .hazeSource(state = hazeState)  # ← Source of blur sampling
            .nestedScroll(...)
        contentPadding = PaddingValues(bottom = dockHeight)
    ) {
        items(listItems) { /* render */ }
    }
    
    AnimatedVisibility(
        visible = !imeState.isImeVisible,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        GistiChatDock(
            # ← hazeEffect already inside GistiChatDock
            modifier = Modifier.onSizeChanged { dockHeight = it.height.toDp() }
        )
    }
}
```

**MainScreen.kt (newly integrated):**
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    MainScreenContent(
        contentBottomPadding = dockHeight,
        modifier = Modifier.hazeSource(state = mainHazeState)
    )
    
    // Chat dock overlay
    AnimatedVisibility(
        visible = !imeState.isImeVisible,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        GistiChatDock(
            modifier = Modifier.onSizeChanged { dockHeight = it.height.toDp() }
        )
    }
}
```

### 4. Why alpha=0.4f is Critical

Neprozrachnyy tint (alpha=1.0) masks the blur completely — dock appears as opaque Surface color. Only blur becomes visible when tint is sufficiently transparent (alpha <1.0):

| Tint Alpha | Visual Result |
|---|---|
| 1.0 | Opaque Surface (NO blur visible) — **BUG** |
| 0.6 | Faint blur visible |
| 0.4 | Clearly visible blur + backdrop color shift |
| 0.2 | Too-light backdrop (less visual separation) |

**For this app:** white background (#FBFAF8) + HazeTint(primaryColor, alpha=0.4f) strikes balance: blur is obvious, dock is visually anchored.

### 5. Haze 1.7.2 Compatibility

| Feature | Haze 1.7.2 | Haze 2.0-alpha |
|---|---|---|
| Compose MP support | ✅ 1.9.3+ | ✅ 1.9.3+ (but broken) |
| Kotlin version | ✅ 2.3.0+ | ✅ 2.3.0+ |
| API 31+ glassmorphism | ✅ Native blur | ❌ Canvas sampling bug |
| API 30 fallback | ✅ Scrim overlay | ✅ (but not tested) |
| wasmJs support | ✅ Scrim (no native blur) | ✅ Scrim (no native blur) |
| Single module (no haze-blur split) | ✅ | ❌ Requires haze + haze-blur |

## Почему именно так

### Alpha версии UI-библиотек опасны
- 2.0.0-alpha02 была **непроверена в реальном приложении**
- Backdrop blur (самый сложный feature Haze) имела баг на уровне canvas sampling → not rendered
- Обнаружение только **после коммита** — классический trap alpha
- **Правило:** не использовать alpha версии для core UI patterns; лучше взять stable + ждать новых features

### Роль cross-project validation
- swapfaceandroid (тот же Compose MP stack) доказывал, что haze 1.7.2 работает на production
- Это дало точку сравнения за минуты, а не часы диагностики
- **Урок:** для критичных UI libs всегда check reference project в том же стеке

### HazeTint.alpha механика
- Haze рендерит многослойно: backdrop-sampling → blur → tint-overlay
- Если tint.alpha == 1.0, он полностью перекрывает blurred backdrop (оверпейнт)
- Если tint.alpha < 1.0, через tint светит размытый контент
- **Это не просто "lighten/darken" — это alpha-composition фундамент**

### Siblings pattern для hazeSource/hazeEffect
- Haze работает через z-order: sampling source → blur effect в слоях
- `hazeSource(Box)` + `hazeEffect(Dock)` в одном Box родителе → correct z-ordering
- **Вложенность (hazeEffect внутри hazeSource) → self-sampling → no-op**
- **Proof:** старая попытка на 2.0 имела вложенность → blur never rendered

## Примеры

### Before (2.0.0-alpha02, broken)
```kotlin
GistiChatDock(modifier = Modifier
    .fillMaxWidth()
    .hazeEffect(state) {
        blurEffect {
            blurRadius = 32.dp
            colorEffects = listOf(
                HazeColorEffect.tint(color = primaryColor)  # No alpha control
            )
        }
    }
)

# Visual: solid Surface color, no blur visible
```

### After (1.7.2, working)
```kotlin
GistiChatDock(modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
    .hazeEffect(
        state = rememberHazeState(),
        style = remember { HazeStyle(
            blurRadius = 32.dp,
            backgroundColor = surface,
            tint = HazeTint(primaryColor, alpha = 0.4f)
        ) }
    )
    .background(surface)
)

# Visual: glassmorphism blur visible, backdrop shows through tint
```

## Связанные файлы

- `gradle/libs.versions.toml` — haze version (2.0.0-alpha02 → 1.7.2)
- `core/designsystem/build.gradle.kts` — remove haze.blur
- `core/designsystem/src/commonMain/kotlin/.../gisti/GistiChatDock.kt` — API migration, single GistiGlassChatDock + ChecklistDetailChatDock wrapper
- `feature/home/src/commonMain/kotlin/.../MainScreen.kt` — dock moved from Scaffold.bottomBar to floating overlay
- `feature/home/src/commonMain/kotlin/.../MainScreenContent.kt` — +contentBottomPadding param, hazeSource on container
- `feature/home/src/commonMain/kotlin/.../detail/ChecklistDetailScreen.kt` — remove TEMP probe, archive Haze 2.0 code
- `core/designsystem/src/androidHostTest/.../ChecklistDockGlassScreenshotTest.kt` — +mainGlassDock_backdropBlur test case

## Validation

**Build (AGP 9 KMP targets):**
```bash
:androidApp:compileDebugKotlin       # ✅ 14s
:composeApp:compileKotlinWasmJs      # ✅ 46s
:composeApp:recordRoborazziAndroidHostTest  # ✅ 2 golden PNGs generated
```

**Runtime (Pixel_9, APK):**
- MainScreen chat dock: glassmorphism blur visible under overlay ✓
- ChecklistDetailScreen chat dock: same ✓
- IME hide/show: dock slides up/down correctly ✓
- Scroll content under dock: blur effect tracks movement ✓

## Pending / Deferred

- [ ] iOS compilation (haze 1.7.2 supports iOS, but app not released — low risk)
- [ ] Detekt analysis (build clean, no warnings)
