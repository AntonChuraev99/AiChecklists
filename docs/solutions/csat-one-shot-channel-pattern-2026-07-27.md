---
title: "One-shot action in composable LaunchedEffect — Channel<Unit> pattern"
date: 2026-07-27
type: pattern
modules: [composeApp, feature/csat]
keywords: [launchedefect, channel, conflated, oneshot, composable, koin-single, state-flow, recomposition, review-callback]
project: checklists
---

# One-shot action in composable LaunchedEffect — Channel<Unit> pattern

## Проблема / Контекст

A one-time action (e.g., launch in-app review, show dialog, navigate) triggered from a composable that observes a state flag is vulnerable to **LaunchedEffect replay** when:

1. The ViewModel is a **Koin `single`** (survives Activity recreation).
2. The action flag is a **Boolean state** (or `Boolean` field in `State` sealed).
3. A **recomposition** occurs while the flag is still `true`.

On every recomposition, `LaunchedEffect(flag)` re-runs if `flag` is still true — the action is duplicated.

**Example:** `CsatViewModel.shouldLaunchReview: Boolean = true` in state + `InAppReviewLauncher` composable with:
```kotlin
LaunchedEffect(state.shouldLaunchReview) {
    if (state.shouldLaunchReview) {
        launcher.requestReview()  // DUPLICATE on recomposition
    }
}
```

After a configuration change (rotation), the ViewModel survives, the state is re-composed with `shouldLaunchReview = true` still set, and `requestReview()` fires a **second time** — an impossible funnel (6 `csat_review_completed` against 4 `csat_review_tapped`).

## Решение

Replace the **Boolean flag** with a **`Channel<Unit>(Channel.CONFLATED)`** backed by a Flow. An element is **consumed on receipt** — a re-created collector cannot replay.

### Step 1: ViewModel

```kotlin
class CsatViewModel(
    private val csatManager: CsatManager,
) : AppViewModel<CsatState, CsatIntent, CsatSideEffect>() {
    
    // OLD (broken):
    // private val _shouldLaunchReview = MutableStateFlow(false)
    
    // NEW:
    private val _reviewRequests = Channel<Unit>(Channel.CONFLATED)
    val reviewRequests: Flow<Unit> = _reviewRequests.receiveAsFlow()
    
    override fun onIntent(intent: CsatIntent) {
        when (intent) {
            is CsatIntent.RateLoveIt -> {
                viewModelScope.launch {
                    sendEvent("csat_review_tapped")
                    _reviewRequests.send(Unit)  // Async, buffered (CONFLATED)
                }
            }
            // ...
        }
    }
}
```

The `Channel.CONFLATED` buffer ensures a late subscriber (e.g., LaunchedEffect created after `send`) still receives the element.

### Step 2: Composable collector

```kotlin
@Composable
fun InAppReviewLauncher(
    state: CsatState,
    csatViewModel: CsatViewModel,
) {
    LaunchedEffect(Unit) {  // Key on lifecycle, not action flag
        csatViewModel.reviewRequests.collect { _ ->
            // Single collector, single element = single action
            val launched = launcher.requestReview()
            if (launched) {
                csatViewModel.sendIntent(CsatIntent.ReviewLaunched)
            }
        }
    }
}
```

**Key:** `LaunchedEffect(Unit)` launches the **collector** once per scope creation. Subsequent elements in `reviewRequests` flow trigger the lambda without re-running `collect`.

### Step 3: Tracking the outcome

The launch can fail (no host Activity, no Play Store, torn-down launcher) or be impossible (web/iOS).
Report the outcome as an **enum, not a Boolean** — a Boolean here is exactly the mistake this doc is
about, one bit for four distinct outcomes:

```kotlin
enum class ReviewLaunchOutcome(val analyticsReason: String?) {
    Launched(null),                        // platform accepted the launch
    NoHostActivity("no_host_activity"),
    LaunchFailed("launch_failed"),
    Cancelled("cancelled"),                // collector torn down mid-flight
    Unsupported("unsupported"),            // web / iOS: no review API at all
}

onComplete: (ReviewLaunchOutcome) -> Unit
```

`analyticsReason` is an explicit string, never `name` — R8 rewrites enum names in release and an
obfuscated reason silently poisons the funnel (project precedent: `ChecklistViewMode`).

Three `source` values, split further by `not_shown_reason`:
- `review_launch` — the platform **accepted** the launch. ⚠️ NOT "the card was shown": Play renders
  nothing once the review quota is spent and **reports no error for it**
  ([official docs](https://developer.android.com/guide/playcore/in-app-review) — there is no quota
  error code, and the API never says whether a card appeared or a rating was left). Treat this arm
  as an **upper bound** on impressions.
- `not_shown` — the request never reached the platform; `not_shown_reason` says which of the four.
- `repeat_callback` — safety stamp if the callback fires twice (should flatline at 0).

**The general trap:** an SDK call returning without an exception is not evidence the user saw
anything. Stamp what is actually known ("the launch went through"), and keep what is unmeasurable
(impressions) out of the name.

## Почему именно так

1. **Boolean flag is not one-shot:** A boolean describes a **condition**, not an **event**. LaunchedEffect re-fires on every recomposition as long as the condition holds. Use a type with event semantics: `Channel` (consumed element = one receiver), or `EventFlow` wrapper, or `suspend fun`.

2. **Channel.CONFLATED:** The buffer ensures the element is not lost between `send()` and the collector's `collect()`. Without buffering, a `send()` before `collect()` would be silently dropped.

3. **LaunchedEffect(Unit):** Keying on a constant ensures the collector launches once, independent of action state. The flow itself carries the sequencing.

4. **One collector per scope:** Unlike `State.collect()` which can be re-entered, a single `LaunchedEffect` + `collect` is guaranteed to run once. Subsequent flow elements trigger the lambda, not re-launch `LaunchedEffect`.

5. **Callback stamping:** the outcome enum separates a launch the platform accepted from the paths
   where nothing launched, so the funnel arm is filterable — but it deliberately does NOT claim the
   card was shown, because no API reports that.

6. **Every exit path must report, including cancellation.** The element is gone from the channel the
   moment it is received, so a request can never be retried. If the collector is torn down while the
   platform call is in flight (rotation during a network-bound Play call) and the callback is
   skipped, the request is swallowed whole: no snackbar, no event, and the tap looks like a freeze.
   That is the *inverse* of the bug being fixed — from duplicate action to silent loss. Guard with a
   `reported` flag plus `finally`:

```kotlin
requests.collect {
    var reported = false
    fun report(outcome: ReviewLaunchOutcome) {
        if (!reported) { reported = true; onComplete(outcome) }
    }
    try {
        // …launch, report(Launched) / report(LaunchFailed)…
    } finally {
        report(ReviewLaunchOutcome.Cancelled)  // not suspending → still runs on cancellation
    }
}
```

## Примеры

**Trap: gate the user-visible feedback on "a launch was outstanding", not on the outcome.**

```kotlin
// WRONG: silent close on web/iOS, no Activity, or a failed launch
if (outcome == ReviewLaunchOutcome.Launched) showSnackbar("Thank you for rating!")

// CORRECT: the flag set at tap time decides; the outcome only labels analytics
val closesLaunch = reviewLaunchPending   // set when the user tapped, cleared by this completion
if (closesLaunch) showSnackbar("Thank you for rating!")
```

A user who tapped ❤️ on web expects a visible response, even though no review dialog can exist there.

**Trap: beware double-click during the async callback.**

The callback is async (Play review API takes ~1s). During that time, a second `RateLoveIt` intent could be sent:

```kotlin
CsatIntent.RateLoveIt -> {
    if (state.reviewRequested) return@launch  // Guard: one pending request
    sendEvent("csat_review_tapped")
    _reviewRequests.send(Unit)
}
```

## Связанные файлы

- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/csat/CsatViewModel.kt` — Channel<Unit> implementation
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/csat/InAppReviewLauncher.kt` — LaunchedEffect(Unit) collector
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/csat/InAppReviewLauncher.android.kt` — Platform callback with source stamping
- `composeApp/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/csat/CsatViewModelTest.kt` — `emitsReviewRequestOnlyOnce` test

**See also:** `docs/solutions/prod-healthcheck-2026-07-27-multi-layer-fixes.md` § 4 (CSAT root cause analysis and regression fix).
