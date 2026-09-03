---
title: "Extract FilePicker & Recorder into core/filepicker/api Module"
date: 2026-06-17
type: architecture
modules: [core/filepicker, feature/home, feature/aichat, feature/analyze]
keywords: [filepicker, audio-recorder, modularization, core-module, composable, kmp, dependency-layer, picker-interface, recorder-playback, transitive-leak]
project: gisti-checklists
---

# Extract FilePicker & Recorder into core/filepicker/api Module

## Problem / Context

File pickers and audio recorders were scattered across features (`feature/aichat/impl`, `feature/analyze`, `feature/home`) as local implementations. When new features needed these capabilities, they either:
- Duplicated the implementations
- Created circular/transitive dependencies by pulling from unrelated features
- Mixed Composable UI code with core data-only modules (`core/common/api`)

The **core/common/api module intentionally remains data-only** (no Compose dependencies) for minimal footprint in non-UI targets (iOS/Android background services). Audio/file picker UI (`@Composable`) operations needed a home.

**Key leak:** `core/common/api` → `feature/home` → `feature/aichat/impl` transitive relationship via `composeApp/App.kt` (silently importing pickers as if part of core).

## Solution

Created **new module `core/filepicker/api`** (Compose-enabled) housing:
- **ImagePicker** interface + `@Composable` launcher (`rememberImagePicker()`)
- **FilePicker** interface + `@Composable` launcher (`rememberFilePicker()`)
- **AudioRecorder** interface + `@Composable` playback UI (`AudioPlayer()`)
- **AudioRecorderLauncher** (`rememberAudioRecorderLauncher()`)

Moved 14 files via `git mv`:
- **Pickers:** `ImagePickerImpl.kt`, `FilePickerImpl.kt` (androidMain), wasmJs stubs
- **Recorder/Player:** `AudioRecorderImpl.kt`, `AudioPlayerImpl.kt`, `RecordingState.kt`, `AudioTrack.kt`, `AudioPlayer.kt`, `AudioRecorderLauncher.kt` (androidMain + wasmJs)
- **Build:** `core/filepicker/{api,impl}/build.gradle.kts`

Updated dependency graph:
```
core/common/api (data-only) ✓
  ↓
core/filepicker/api (Compose UI) ✓ [NEW]
  ↓
feature/{home,aichat/impl,analyze} (now clean)
```

Removed transitive `feature/analyze` from `feature/home` and `feature/aichat/impl` `build.gradle.kts`. Updated `settings.gradle.kts` to include the new module.

## Why This Way

1. **Compose boundary enforcement:** `core/filepicker/api` depends on `compose.ui` (allowed in this module by design), isolating Compose concerns from data-only core.
2. **Reusability:** Any feature can now safely depend on `core/filepicker/api` without pulling in unrelated features.
3. **Minimal surface:** Single module exports all picker/recorder abstractions; implementations (androidMain/wasmJs) remain internal.
4. **No transitive leaks:** `composeApp/App.kt` now explicitly imports from `core/filepicker/api`, not through feature chains.

## Implementation Details

**Dependency declarations:**
```kotlin
// In core/filepicker/api/build.gradle.kts
dependencies {
    implementation(compose.ui)  // Composable launchers live here
    api(projects.core.common.api)  // For Result<T> and core enums
}

// In feature/home/build.gradle.kts (and others)
implementation(projects.core.filepicker.api)  // Direct, clean
```

**Android implementation example (remains in androidMain):**
```kotlin
// core/filepicker/api/src/androidMain/kotlin/ImagePickerImpl.kt
actual suspend fun rememberImagePicker(): ImagePickerLauncher {
    // Uses Android Intent + Launcher contract
}
```

**wasmJs stub (no-op):**
```kotlin
// core/filepicker/api/src/wasmJsMain/kotlin/ImagePickerImpl.kt
actual suspend fun rememberImagePicker(): ImagePickerLauncher {
    return ImagePickerLauncher { emptyList() }  // No-op for web
}
```

**Import in App.kt (now explicit, not transitive):**
```kotlin
// composeApp/src/commonMain/kotlin/App.kt
import com.gisti.filepicker.rememberImagePicker
import com.gisti.filepicker.rememberFilePicker

@Composable
fun App() {
    val imagePicker = rememberImagePicker()
    val filePicker = rememberFilePicker()
    // ... rest
}
```

## Verification

✅ `:androidApp:compileDebugKotlin` PASS (14 files resolved, no unresolved imports)
✅ `:composeApp:compileKotlinWasmJs` PASS (wasmJs stubs active)
✅ Dependency tree clean (no cycles, transitive leak eliminated)
✅ `:androidApp:connectedAndroidTest` executed locally (Android UI works)

**Note:** iOS not compiled (deferred until iOS cycle starts). Code structure supports iOS implementation via `core/filepicker/api/src/iosMain/kotlin/`.

## Related Files

- `settings.gradle.kts` (added module)
- `core/filepicker/api/build.gradle.kts` (new)
- `feature/home/build.gradle.kts` (removed transitive, added direct)
- `feature/aichat/impl/build.gradle.kts` (removed transitive, added direct)
- `feature/analyze/build.gradle.kts` (removed transitive, added direct)
- `composeApp/build.gradle.kts` (verified no changes needed)

## Future Extensions

If new picker types are needed (e.g., video, document), add them to `core/filepicker/api` public API — no new modules needed.
