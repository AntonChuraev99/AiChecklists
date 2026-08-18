---
title: "Supervise app-wide CoroutineScope with SupervisorJob to prevent single-child failure from cascading"
date: 2026-07-15
type: bug-fix
modules: [core/common, feature/checklist, feature/home]
keywords: [SupervisorJob, CoroutineScope, Koin, app-wide scope, failure isolation, DI]
project: gisti-ai-checklists
---

# Supervise app-wide CoroutineScope with SupervisorJob

## Проблема / Контекст

App-wide `CoroutineScope` singleton is injected into ViewModels and used for operations that must survive the ViewModel's cancellation — particularly long-running writes and syncs. A naive implementation:

```kotlin
single<CoroutineScope> { CoroutineScope(Dispatchers.Default) }
```

**The defect:** When Koin creates a `CoroutineScope` without an explicit `Job` in the context, it supplies a plain `Job()`. A single uncaught exception thrown by any child `launch{}` on this scope **cancels the entire scope's job**, rendering every subsequent `launch{}` on it a silent no-op for the rest of the process. Since this is an app-wide singleton, one failing child (e.g., sync crash, write exception) permanently breaks the scope for all other consumers (credits convergence, user-data persistence, etc.).

**Who is affected:** Any code that does:
```kotlin
appScope.launch {
    repository.updateChecklistTemplate(...)  // throws on disk full → whole scope dies
}
```

After the throw, all later:
```kotlin
appScope.launch {
    repository.syncChecklists()  // silently never runs
}
```

silently become no-ops. No error, no log, no visible symptom — just data loss in production.

**Manifestation:** PR #12 moved folder-delete persistence to `appScope`, uncovering the defect. Folder delete has error paths (Android `FileSystem` exceptions) that, when uncaught, crashed the scope.

## Решение

Use `SupervisorJob()` instead of a plain `Job()`:

```kotlin
single<CoroutineScope> {
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

**Why SupervisorJob:** In Kotlin Coroutines, `SupervisorJob` creates a job hierarchy where **a child exception does NOT cancel sibling jobs**. Each child fails in isolation; the parent scope stays alive. This is the correct choice for a "pool of independent workers" pattern (sync, credits, writes — all independent).

### Companion: runCatching + AppLogger

For operations on this scope, wrap in `runCatching` and log errors:

```kotlin
private fun confirmFolderDelete(...)  {
    appScope.launch {
        runCatching {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.trackEvent(...)
        }.onFailure { e ->
            logger.error(TAG, "Folder delete persist failed: ${e.message}", e)
            // Side effect: show snackbar to user, don't silently fail
            emitSideEffect(ShowSnackbar("Delete failed, retry later"))
        }
    }
}
```

Never silent-catch:
```kotlin
// WRONG: swallows error, user sees nothing, data loss
appScope.launch {
    try {
        repository.updateChecklistTemplate(...)
    } catch (e: Exception) {
        // silent
    }
}
```

## Почему именно так

### SupervisorJob vs Job
- **Job()** (default): When ANY child throws → parent's job is cancelled → all future launches are no-ops.
- **SupervisorJob()**: When a child throws → only that child is failed; parent & siblings stay alive. Each can retry independently.

### Koin's CoroutineScope factory behavior
When you write `CoroutineScope(Dispatchers.Default)`, the Kotlin standard library fills in a missing `Job` from the context's other elements. Since `Dispatchers.Default` has no job, a fresh `Job()` is created. This is correct for *short-lived* scopes (e.g., `viewModelScope`), wrong for *long-lived app-wide* singletons.

### When app-wide scope is needed
App-wide scope outlives ViewModels and is injected where:
1. **Sync operations** that must run across screen rotations / back-stack pops
2. **Deferred writes** that pop their own ViewModel before persisting
3. **Credits/user-data convergence** streams that span multiple screens

Typical error paths: `FileSystem.writeBytes()` on low disk, `Firestore.setData()` on network, parsing exceptions in sync mappers.

## Примеры

### Before (defective)
```kotlin
// core/common/impl/di/CommonCoreModule.kt
single<CoroutineScope> { CoroutineScope(Dispatchers.Default) }

// feature/home/presentation/detail/ChecklistDetailViewModel.kt
private fun confirmFolderDelete(folderId: String) {
    val updatedChecklist = ...
    val updatedFill = ...
    
    // Navigate away (pop) BEFORE persist completes
    navigator.popBackStack()
    
    // Delete on appScope so it survives ViewModel cancellation
    appScope.launch {
        repository.updateFill(updatedFill)
        repository.updateChecklistTemplate(updatedChecklist)
        // If updateChecklistTemplate throws → appScope.job is cancelled
        // All subsequent appScope.launch{} calls silently no-op
    }
}
```

### After (fixed)
```kotlin
// core/common/impl/di/CommonCoreModule.kt
single<CoroutineScope> {
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

// feature/home/presentation/detail/ChecklistDetailViewModel.kt
private fun confirmFolderDelete(folderId: String) {
    val updatedChecklist = ...
    val updatedFill = ...
    
    navigator.popBackStack()
    
    appScope.launch {
        runCatching {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.trackEvent(AnalyticsEvents.FOLDER_DELETED, ...)
        }.onFailure { e ->
            logger.error(TAG, "Folder delete failed: ${e.message}", e)
            emitSideEffect(ShowSnackbar("Delete failed"))
        }
    }
}
```

If `updateChecklistTemplate` throws:
- **Before:** appScope's job is cancelled; sync/credits/other writers become permanently no-op
- **After:** only the folder-delete child fails; sync/credits/other writers continue normally

## Связанные файлы
- `core/common/impl/src/commonMain/kotlin/.../di/CommonCoreModule.kt` — DI definition (line 21)
- `feature/home/src/commonMain/kotlin/.../detail/ChecklistDetailViewModel.kt` — usage example (confirmFolderDelete)
- `feature/home/src/commonTest/.../detail/ChecklistDetailScreenTest.kt` — test coverage (286 tests green)

## Patterns applied
- **Supervision** (Kotlin Coroutines structured concurrency): parent scope does not fail on child exception
- **Koin single with lazy initialization:** app-wide scope created once, shared
- **runCatching + logging:** fail-safe outer boundary for platform-specific operations (FileSystem, Firestore)
