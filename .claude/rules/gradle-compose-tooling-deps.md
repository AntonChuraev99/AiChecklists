# Gradle — Compose tooling / test deps must stay out of release

Loads when you edit any `**/build.gradle.kts`. Applies when adding or moving a Compose / preview / test dependency in a KMP-library or app module.

## Rule — debug/preview/test artifacts never ship in a release-reachable source set

Tooling and test artifacts MUST live in a **debug-only** or **test-only** configuration, never as `implementation` in `commonMain` / `androidMain` (both are release-reachable). Offenders to watch:

| Artifact | Purpose | Correct home |
|---|---|---|
| `androidx.compose.ui:ui-tooling` (`libs.androidx.compose.ui.tooling`) | heavy interactive `@Preview` **renderer** for Android Studio | debug runtime only (see below) — **never** release |
| `androidx.compose.ui:ui-test-*` (`...test.junit4`, `...test.manifest`) | UI test infra | `androidHostTest` / `androidInstrumentedTest` |
| `roborazzi*`, `robolectric` | screenshot / JVM test | `androidHostTest` |

## `@Preview` / `@PreviewLightDark` — split annotation from renderer

- The **annotations** (`@Preview`, `@PreviewLightDark`, `@PreviewParameter`) compile against `compose.components.uiToolingPreview` (CMP; on Android it maps to `androidx.compose.ui:ui-tooling-preview`, which carries `@PreviewLightDark`). It is tiny and **release-safe** — put it in `commonMain.dependencies`.
- The **interactive renderer** (`ui-tooling`) is only needed so Studio can render previews live. Add it **debug-only** at project level, mirroring `composeApp` / `feature:home`:

```kotlin
// after the kotlin { } block
dependencies {
    // AGP9 replacement for debugImplementation(compose.uiTooling) in KMP-library modules.
    // CMP-version-aligned; NOT Google's androidx ui-tooling.
    add("androidRuntimeClasspath", compose.uiTooling)
}
```

## ⛔ Never `implementation(libs.androidx.compose.ui.tooling)` in commonMain/androidMain

Two defects at once: (1) ships a debug-only inspection lib into the production APK (size + attack surface); (2) drags a **Google** `androidx.compose.*` artifact into a JetBrains-**CMP** stack — the one place a Google-vs-CMP compose-artifact version skew can appear (`compose.foundation` compiled against a different `DrawScope` ABI than the loaded `compose.ui` → `IncompatibleClassChangeError`). A pinned older version (e.g. `1.8.3` under CMP `1.11.0` → androidx `1.11.1`) is exactly this skew.

## Self-check when adding a compose/tooling dep

1. Is it a renderer/test/inspection lib? → it belongs in `androidHostTest` or the debug `androidRuntimeClasspath` block, not `implementation` in main.
2. Only need the `@Preview` annotation to compile? → `compose.components.uiToolingPreview`, not `ui-tooling`.
3. Prefer CMP accessors (`compose.uiTooling`, `compose.components.uiToolingPreview`) over pinned Google `androidx.compose.ui:*` — they track the CMP version and avoid the skew.

Verify a release build is clean:
```bash
./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath | grep "ui-tooling"
# expect ONLY *ui-tooling-preview* (annotations); the heavy *ui-tooling* renderer must be ABSENT
```

Precedent: crash `f037e7c0` diag 2026-07-06 — `feature/home` androidMain shipped `androidx.compose.ui:ui-tooling:1.8.3` into release via a wrong `@PreviewLightDark` comment; fixed by the split above. Project memory: `framework-internal-incompatibleclasschangeerror-device-side`.
