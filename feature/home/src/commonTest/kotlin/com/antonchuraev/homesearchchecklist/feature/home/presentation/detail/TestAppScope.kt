package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Test double for [ChecklistDetailViewModel]'s `appScope` — the app-wide scope that must OUTLIVE the
 * ViewModel (production: `CoroutineScope(Dispatchers.Default)` from `commonCoreModule`). It exists
 * because `confirmFolderDelete` pops its own back-stack entry — cancelling `viewModelScope` — before
 * the delete is persisted.
 *
 * Three properties are required at once, which is why neither obvious one-liner works:
 *
 *  1. **Independent of `viewModelScope`** — otherwise the test cannot tell the fix from the bug.
 *  2. **Cancelled when the test ends** — a scope that outlives `runTest` keeps running against an
 *     already-`resetMain()`ed dispatcher and reports `UncaughtExceptionsBeforeTest` /
 *     "Dispatchers.Main was accessed…" against whatever test JUnit happens to run NEXT. This is what
 *     `CoroutineScope(testDispatcher)` (no Job tied to the test) gets wrong.
 *  3. **Eagerly dispatched on the test scheduler** — `backgroundScope` alone inherits [TestScope]'s
 *     default `StandardTestDispatcher`, so `appScope.launch { }` only QUEUES. Every existing assert
 *     in these classes was written against the old `viewModelScope.launch` running eagerly (Main is
 *     an `UnconfinedTestDispatcher` here), so plain `backgroundScope` makes them read state that was
 *     never written — a false RED that looks like a production bug.
 *
 * Taking `backgroundScope.coroutineContext` supplies the Job (properties 1 + 2 — it is not the VM's
 * Job, and `runTest` cancels it at the end); overriding the dispatcher with the class's
 * unconfined [dispatcher] supplies property 3. Same scheduler throughout, so virtual time still
 * works and `advanceUntilIdle()` still drains this scope.
 *
 * NB: eager dispatch means a test cannot observe the pre-write frame. The one test that must —
 * `confirmFolderDelete_whileInsideDeletedFolder_persistsDespiteViewModelBeingCleared` — deliberately
 * installs a `StandardTestDispatcher` as Main instead of using this helper.
 */
internal fun TestScope.appScopeDouble(dispatcher: TestDispatcher): CoroutineScope =
    CoroutineScope(backgroundScope.coroutineContext + dispatcher)
