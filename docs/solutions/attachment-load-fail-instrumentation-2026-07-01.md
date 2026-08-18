---
title: "Attachment Load Failure — Atomic Download & Two-Stage Instrumentation"
date: 2026-07-01
type: bug-fix
modules: [core/common, feature/home]
keywords: [attachment, corrupted-file, atomic-download, launchedefect-cancellation, coil, storage, observability, analytics, load-failed]
project: checklists
---

# Attachment Load Failure — Atomic Download & Instrumentation

## Проблема / Контекст

Users report intermittent "broken image" placeholder appearing for previously-downloaded attachments (checklists, PDF links, photos). The symptom is **sticky per file**: once broken, always broken, and survives app restart.

Two independent failure vectors existed but were conflated:

1. **Cloud download fails** (network error, CORS, App Check rejection) → `AttachmentMaterializeState.Error` → broken-image placeholder
2. **File already downloaded but Coil cannot decode it** → silent failure → broken-image placeholder (completely un-instrumented, no log, no analytics)

### Root Cause (Floating)

Android `AttachmentCloudStorage.download()` wrote directly to the target path via:
```kotlin
val file = attachmentPath.getFile(name)
downloadStream(url).use { stream -> file.writeBytes(stream.readBytes()) }
```

This code runs inside a Compose `LaunchedEffect` in `AttachmentMaterialize.kt`, which is **cancelled** the moment the thumbnail leaves composition (user scrolls, swipes, closes sheet). A cancelled/failed download leaves a **truncated file** at `path`. On the next composition:
- File-validation logic sees `file.exists() && sizeOf(file) > 0` → marks as Ready
- Passes truncated file to Coil for display
- Coil attempts to decode corrupted/incomplete bytes → decode fails
- Broken-image placeholder shown (user sees no error, just broken image)

The bug is **intermittent** (depends on scroll timing + network speed) and **sticky** (truncated file persists; next load still tries corrupted bytes).

**Why decode failures were invisible:** `AsyncImage(onError)` had no callback; errors went to Logcat only, not analytics. Cloud failures logged + emitted analytics, so only materialize-stage issues were visible in production metrics.

## Решение

### 1. Atomic Download (Android)

```kotlin
// core/common/api/src/androidMain/.../AttachmentCloudStorage.android.kt

suspend fun download(url: String, targetPath: AttachmentPath): Result<Unit> {
    val tempFile = targetPath.getFile("${targetPath.name}.part")
    val targetFile = targetPath.getFile(targetPath.name)
    
    return runCatching {
        // Clear any leftover partial file from prior attempt
        tempFile.delete()
        
        // Download to temporary file
        downloadStream(url).use { stream ->
            tempFile.writeBytes(stream.readBytes())
        }
        
        // Atomic rename: only complete files at target path
        tempFile.renameTo(targetFile)
    }.onFailure {
        tempFile.delete()  // cleanup partial write on any exception
    }
}
```

**Why .part pattern:**
- Standard production practice for safe I/O under cancellation (writes to temp, atomic rename on success)
- Prevents half-written state from being seen as valid (sizeOf > 0)
- `getFile(target)` only ever holds a complete file or doesn't exist
- Backward-compatible: existing cache-check logic (`file.exists()`) unaffected

### 2. Two-Stage Instrumentation

**New analytics event:**
```kotlin
// core/common/api/src/commonMain/.../AnalyticsEvents.kt

object Attachment {
    data class LOAD_FAILED(
        val stage: String,           // "materialize" | "decode"
        val has_storage_path: Boolean, // file exists locally?
        val mime_type: String?,
        val error_message: String
    ) : AnalyticsEvent {
        override fun eventName() = "attachment_load_failed"
    }
}

fun reportAttachmentLoadFailure(
    stage: String,
    reason: String,
    throwable: Throwable? = null,
    hasStoragePath: Boolean = false,
    mimeType: String? = null
) {
    // Dual sink: Crashlytics for crash analysis, Analytics for dashboards
    AppLogger.error("Attachment", "Load failed at $stage: $reason", throwable)
    analytics.emit(
        Attachment.LOAD_FAILED(
            stage = stage,
            has_storage_path = hasStoragePath,
            mime_type = mimeType,
            error_message = reason
        )
    )
}
```

**API Extension (backward-compatible):**
```kotlin
// core/common/api/src/commonMain/.../AttachmentMaterialize.kt

suspend fun ensureAttachmentLocal(
    url: String,
    onFailure: (reason: String, throwable: Throwable?) -> Unit = { _, _ -> }
): File? {
    return try {
        val materialized = attachmentStorage.download(url)
        materialized.getOrNull()
    } catch (e: Exception) {
        onFailure("materialize", e)
        null
    }
}
```

**Decode-stage instrumentation (was silent):**
```kotlin
// feature/home/.../detail/AttachmentThumbnail.kt

AsyncImage(
    model = attachment.cloudUrl,
    contentDescription = null,
    onError = { state ->
        // NEW: Report decode failures (previously silent)
        reportAttachmentLoadFailure(
            stage = "decode",
            reason = state.result.throwable?.message ?: "unknown decode error",
            throwable = state.result.throwable,
            hasStoragePath = localFile?.exists() ?: false,
            mimeType = attachment.mimeType
        )
    },
    errorPainter = rememberErrorPainter(label = "Failed to load"),
    modifier = modifier
)
```

## Почему именно так

### Atomic Download
- **Write-then-rename is production-standard** for safe I/O when subject to interruption (process kill, network timeout, cancellation). Only complete files exist at target path — no ambiguous intermediate state.
- **LaunchedEffect cancellation risk is real.** Compose cancels LaunchedEffect the moment the source leaves composition. Downloads > ~100ms are likely to hit this. Atomic pattern makes it safe.
- **Backward-compatible:** Cache logic (`file.exists()`) unaffected. Existing call sites need zero changes.

### Two-Stage Instrumentation
- **Different root causes require different instrumentation.** Network failures (materialize) vs codec issues / corruption (decode) are fixed differently and appear in different logs. Grouping them masks diagnosis.
- **Analytics dashboards need the distinction:** "What % of users hit download failures vs decode failures?" → prioritize fix. Before: only materialize was visible; decode failures looked like user data loss (broken image = missing data), not a loading issue.
- **`has_storage_path` flag is critical:** Distinguishes "file never downloaded" (network error on first access) from "file exists locally but corrupted" (LaunchedEffect trap, timeout during write, or codec mismatch). Different diagnostic paths.

### Callback Pattern (not Exception)
- **Callback allows UI to emit analytics without breaking render.** Throwing from `onError` or `ensureAttachmentLocal` would propagate to ViewModel, require try-catch, and complicate composable structure.
- **Boolean return on `ensureAttachmentLocal()` unchanged** → backward-compatible; all callers expecting `File? | null` get the same contract. No need to hunt down exception handlers.
- **Default empty lambda = zero-cost for callers who don't care about failure tracking** (e.g., FileDetailPage just needs the file). Callback is opt-in observability, not mandatory.

## Примеры

### Before (No Visibility into Decode Failures)

```
User: "My photo attachment shows broken image"
Engineer: Queries logs for errors. Checks network layer → normal.
Engineer: Speculates "Maybe user's device has low storage?"
Engineer: Gives up. Escalates to user: "Try clearing app data."
Internal dashboards show: 0% decode-stage failures (because logging was missing)
```

### After (Clear Failure Stages + Root Cause)

```
User: "My photo attachment shows broken image"
Engineer: Queries Crashlytics for stage="decode" errors
Engineer: Finds 2,847 instances of error="InvalidImageFormat" on Dec 1–7
Engineer: Realizes → old Coil 3.0.0 couldn't decode WebP from certain encoders; upgrade to 3.1.0
OR
Engineer: Finds 347-byte file (truncated); queries LaunchedEffect cancellation logs
Engineer: Realizes → download ran in thumbnail LaunchedEffect; scrolling away mid-download left truncated file
Engineer: Recommends atomic download pattern + validates fix

Analytics dashboard shows:
- stage=materialize: 0.2% (network issues, expected)
- stage=decode: 1.8% (spike on Dec 1–7, correlates with iOS Coil 3.0 release)
→ Prioritize Coil upgrade
```

## Связанные файлы

- `core/common/api/src/androidMain/.../AttachmentCloudStorage.android.kt` — atomic download impl (write `.part` → rename)
- `core/common/api/src/commonMain/.../AnalyticsEvents.kt` — Attachment.LOAD_FAILED event + `reportAttachmentLoadFailure()`
- `feature/home/.../detail/AttachmentMaterialize.kt` — `ensureAttachmentLocal()` callback contract + materialize-stage reporting
- `feature/home/.../detail/AttachmentThumbnail.kt` — `AsyncImage(onError)` decode-stage reporting
- `feature/home/.../detail/AttachmentFullscreenViewer.kt` — decode-stage reporting + error painter
- `feature/home/.../commonTest/.../AttachmentMaterializeTest.kt` — unit test coverage for both onFailure paths
- Project memory: [[attachment-load-failed-instrumentation-and-atomic-download]]
- Related patterns: [[coil3-custom-scheme-fetcher-uri-not-string]], [[filepicker-rememberupdatedstate-closure-trap]]

## Lessons Learned

1. **Partial-file traps are invisible in happy-path validation.** File-size checks (`sizeOf > 0`) pass for truncated files. Add atomic I/O to any `LaunchedEffect` managing writes. Pattern: write to `.part`, rename on success.

2. **Callback API is more composable than exceptions for observability.** Emit analytics without propagating exceptions. Default empty lambda = backward-compatible for callers who don't need failure tracking.

3. **Stage separation reveals root causes.** A single "load failed" event masks different problems (network vs codec vs corruption). Distinguish materialize (cloud transport) from decode (local codec) from the start.

4. **Invisible decode failures are user-data-loss symptoms.** When a user sees "broken image" without error messaging, they think data is gone, not that loading failed. Always wire error callbacks to UX (painter, toast, etc.).

---
