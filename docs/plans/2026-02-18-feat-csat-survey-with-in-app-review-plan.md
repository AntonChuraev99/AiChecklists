---
title: "feat: Add CSAT survey with in-app review"
type: feat
date: 2026-02-18
deepened: 2026-02-18
---

# feat: Add CSAT survey with in-app review

## Enhancement Summary

**Deepened on:** 2026-02-18
**Research agents used:** 7 (ModalBottomSheet patterns, Google Play Review API, Analytics listener architecture, Institutional learnings, CSAT UX best practices, kotlinx-datetime, ShareLauncher pattern analysis)

### Key Improvements from Research
1. **Emoji labels changed**: "Bad/Okay/Great" → "Not Good / It's Okay / Love It!" — softer wording reduces social desirability bias, users more willing to give honest negative feedback
2. **Trigger delay increased**: 1s → 3s — research shows users need time to process their action result before a survey
3. **InAppReviewLauncher fixed**: Use `LocalActivity.current` (not unsafe `LocalContext as Activity`) — available in project's activity-compose 1.12.2
4. **Trigger architecture upgraded**: Option A (extend interface) → Option C (Decorator + SharedFlow) — zero changes to existing code
5. **New dependency discovered**: `kotlinx-datetime:0.7.1` needed for `todayIn()` API — not currently in project
6. **CMP bugs documented**: BackHandler workaround for keyboard-dismisses-sheet bug (#5015)
7. **Google Play policy risk clarified**: Sentiment gating enforcement strengthened in 2025 with AI detection

## Overview

Add a CSAT (Customer Satisfaction) bottom sheet that collects user sentiment via 3 emoji faces. On positive feedback, offer to launch the native In-App Review dialog. On negative/neutral feedback, save feedback locally for the internal review system (separate feature, out of scope).

## Problem Statement / Motivation

1. No mechanism to collect structured user feedback
2. No way to route happy users toward leaving a store review
3. Negative feedback goes directly to public 1-2★ reviews instead of an internal system

## Proposed Solution

### UI Flow (2 states)

```
State 1: Emoji Selection
┌─────────────────────────────────────┐
│       How do you like Gisti?        │
│                                     │
│    😞         😐         😊         │
│  Not Good   It's Okay  Love It!    │
└─────────────────────────────────────┘

          ↓ (tap emoji)

State 2a: Feedback Form (😞 or 😐)
┌─────────────────────────────────────┐
│       How do you like Gisti?        │
│                                     │
│    😞        [😐]        😊         │
│                                     │
│  ┌─────────────────────────────────┐│
│  │ Tell us more (optional)...     ││
│  └─────────────────────────────────┘│
│                                     │
│  ┌─────────────────────────────────┐│
│  │          Submit                 ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
          ↓ (submit) → save feedback, close sheet

State 2b: Thank You + Review (😊)
┌─────────────────────────────────────┐
│       How do you like Gisti?        │
│                                     │
│    😞         😐        [😊]        │
│                                     │
│            ✅                        │
│     Thank you for your              │
│        feedback!                    │
│                                     │
│  Would you mind leaving a           │
│  review? It helps us a lot!         │
│                                     │
│  ┌─────────────────────────────────┐│
│  │       Rate on Google Play       ││
│  └─────────────────────────────────┘│
│        Maybe later                  │
└─────────────────────────────────────┘
          ↓ (tap "Rate on Google Play")
      → Launches In-App Review API
```

**Simplification vs. v0 plan:** Removed multi-select chips (YAGNI — no data yet on what users will say). Free text gives richer qualitative signal. Chips can be added in v2 after analyzing real feedback patterns. Also removed the separate "expanded form" state for happy users — tapping 😊 immediately shows thank-you + review prompt.

#### Research Insights: UI/UX

**Emoji Labels — "Not Good / It's Okay / Love It!"**
- Research from Customer Thermometer, Zonka Feedback, and Affiniv shows "Bad" creates social desirability bias — users avoid it because it feels judgmental. "Not Good" is softer and yields more honest negative feedback.
- "Love It!" with exclamation matches the app's casual tone and is language-barrier-friendly.
- ACM (2023) and SmartSurvey confirm: always pair emojis with text labels — emoji-only scales cause ambiguity.

**Bottom Sheet vs. Center Dialog:**
- Refiner 2025 data: center dialogs get 42.6% response vs 34.8% for bottom sheets. However, ModalBottomSheet is the established pattern in this codebase and fits Material Design conventions. Keeping bottom sheet for consistency; response rate is still strong for a 1-tap interaction.

**Text Feedback Impact:**
- Text comment fields have 60-80% skip rate when mandatory (SurveyMonkey). Our text field is optional and only appears for negative/neutral ratings — this preserves the emoji completion rate while capturing qualitative signal from users who want to elaborate.

**Thank-You Duration:**
- Show for 1.5-2 seconds, then user sees the review prompt. No auto-dismiss needed since the user controls the next action (tap "Rate" or "Maybe later").

### Trigger: Single Action Counter

One trigger rule: **show CSAT when the user has completed >= 2 meaningful actions** (creating a checklist by any method, creating a fill, or completing an AI fill) AND cooldown has expired.

```
csat_action_count >= 2 AND (csat_last_shown_date is null OR > 30 days ago)
```

**What counts as a "meaningful action":**
- Creating a checklist manually (`CreateChecklistViewModel`)
- Creating a checklist from template (`TemplatePreviewViewModel`)
- Creating a checklist via AI (`AnalyzeResultPreviewViewModel`, mode = create)
- Creating an additional fill (`ChecklistDetailViewModel.createNewFill()`)
- Completing an AI fill — new or to default (`AnalyzeResultPreviewViewModel`, mode = fill/fillDefault)

**Why one counter, not four:** All triggers answer the same question — "Has this user engaged enough to have an opinion?" The distinction between manual/AI/create/fill doesn't matter for deciding when to show a survey. One counter, one threshold, one integration point.

#### Research Insights: Trigger Timing

**>= 2 actions is validated by industry data:**
- Refiner 2025 recommends "3 uses within 48 hours" for feature satisfaction; our >= 2 total is slightly more permissive but appropriate for an app where each action (creating a checklist) is a high-intent moment.
- Facebook's aha moment is "7 friends in 10 days." Gisti's aha moment is "Created a checklist AND used it" — which maps to >= 2 actions.

**Don't trigger on 1st action (the aha moment):**
- Amplitude and Appcues research: first use has novelty bias — feedback will be inflated positive and not representative. Wait for the 2nd use when the feature becomes part of the workflow.

### Suppression Rules

- **Dismiss without emoji** → no cooldown, show again on next trigger
- **Submit or dismiss after emoji selection** → 30-day cooldown
- **Session guard** → show at most once per app session (in-memory flag)
- **Screen filter** → don't show during Splash, Onboarding, Paywall, or Debug screens
- **Sheet conflict guard** → don't show if another ModalBottomSheet is visible (e.g., FillTargetBottomSheet)

#### Research Insights: Cooldown

**30-day cooldown is industry standard** (Refiner 2025). Consider adaptive extensions in v2:
- After negative feedback: extend to 60-90 days (re-asking before fixing anything feels tone-deaf)
- After positive + review: extend to 90+ days (user already gave everything)
- After dismissal without answering: shorten to 14-21 days (later moment may be better)

### In-App Review — Google Play Policy

> **Risk: CRITICAL.** Google Play prohibits asking the user's opinion before showing the review dialog. Their October 2025 policy update strengthened enforcement with AI-powered detection. Consequences include review removal, warning banners, and app suspension.

**Mitigation:** After 😊, we show a "Rate on Google Play" **button** — the review dialog is launched by explicit user tap, not automatically. If Google suppresses the dialog (quota exceeded), the button simply does nothing — UX is not broken.

**Fallback:** If this becomes a policy issue, decouple In-App Review from CSAT entirely and show it on a separate trigger (e.g., after 5th checklist). The architecture supports this change with a one-line modification in `CsatViewModel`.

#### Research Insights: Google Play Review API

**Quota is opaque and time-bound:**
- Google intentionally does not disclose exact quota numbers. Community testing suggests ~3-4 shows per year per user, roughly monthly.
- The dialog will NOT show in debug/sideloaded builds — must use Internal Test Track for QA.
- `FakeReviewManager` from `com.google.android.play.core.review.testing` is available for automated tests (returns success but shows no UI).

## Technical Considerations

### File Structure (no new Gradle module)

All CSAT code lives in `composeApp/` — no separate feature module needed. This is a bottom sheet + ViewModel + one expect/actual composable.

```
composeApp/src/
  commonMain/kotlin/.../csat/
    CsatViewModel.kt            # ViewModel + State/Intent contract
    CsatBottomSheet.kt          # ModalBottomSheet composable
    CsatManager.kt              # Trigger evaluation + DataStore persistence
    ObservableAnalyticsTracker.kt  # Decorator for event observation
    AnalyticsEvent.kt           # Simple data class
  androidMain/kotlin/.../csat/
    InAppReviewLauncher.android.kt  # Google Play review-ktx
  iosMain/kotlin/.../csat/
    InAppReviewLauncher.ios.kt      # Stub (iOS not released yet)
```

**6 files total.** `ObservableAnalyticsTracker` and `AnalyticsEvent` could live in `core/common/api` if reuse is desired, but for now keeping everything colocated in `composeApp/.../csat/` avoids touching shared modules.

### CsatViewModel + Contract

```kotlin
// CsatViewModel.kt

data class CsatState(
    val showBottomSheet: Boolean = false,
    val selectedRating: CsatRating? = null,  // null = emoji selection state
    val feedbackText: String = "",
    val isSubmitting: Boolean = false,
    val shouldLaunchReview: Boolean = false,  // triggers InAppReviewLauncher composable
) : State

enum class CsatRating { NotGood, Okay, LoveIt }

sealed interface CsatIntent : Intent {
    data class SelectRating(val rating: CsatRating) : CsatIntent
    data class UpdateText(val text: String) : CsatIntent
    data object Submit : CsatIntent
    data object LaunchReview : CsatIntent
    data object SkipReview : CsatIntent
    data object Dismiss : CsatIntent
    data object ReviewComplete : CsatIntent  // called by InAppReviewLauncher onComplete
}

sealed interface CsatSideEffect : SideEffect {
    data object CloseSheet : CsatSideEffect
}
```

Register via `viewModelOf(::CsatViewModel)` in `AppModule` (not `single` — let Compose scope it). Place `koinViewModel()` call in `App.kt` outside `NavHost`.

#### Research Insights: MVI Pattern

**From `docs/solutions/architecture/mvi-pattern.md`:**
- Name intents after **user actions** (`SelectRating`), not state changes (`SetRating`) — project convention
- Use `_screenState.update { it.copy(...) }` for immutable updates
- Navigation must happen after state update, not during intent handler

**SideEffect usage:** `LaunchInAppReview` removed from SideEffect — instead use `shouldLaunchReview: Boolean` in State to control the `InAppReviewLauncher` composable. This follows the ShareLauncher pattern: `if (state.shouldShare) { ShareLauncher(...) }`.

### DataStore Keys (2 keys)

| Key | Type | Description |
|-----|------|-------------|
| `csat_action_count` | Int | Lifetime counter of meaningful user actions |
| `csat_last_shown_date` | Int (epoch day) | Date of last CSAT show for 30-day cooldown |

**Epoch day as Int:** `LocalDate.toEpochDays()` returns an Int (~20,502 for Feb 2026). No overflow risk. `AppDatastore` supports Int natively.

#### Research Insights: DataStore

**CRITICAL from `docs/solutions/runtime-errors/datastore-multiple-instances-crash.md`:**
- NEVER create a new DataStore instance — use the existing `UserAppDatastoreProvider.instance` singleton.
- Multiple DataStore instances for the same file cause `IllegalStateException`.
- CSAT should reuse the existing `AppDatastore` instance, not create a separate one.

**From kotlinx-datetime research:**
- `kotlinx-datetime:0.7.1` must be added as a new dependency (not currently in project).
- Use `Clock.System.todayIn(TimeZone.currentSystemDefault())` — simpler API than `Clock.System.now().toLocalDateTime(...).date`.
- `todayIn` is an extension function — requires `import kotlinx.datetime.todayIn`.
- `kotlin.time.Clock` is in stdlib since Kotlin 2.1.20 (project has 2.3.0).

### CsatManager (trigger + persistence)

```kotlin
// CsatManager.kt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class CsatManager(private val datastore: AppDatastore) {

    companion object {
        private const val KEY_ACTION_COUNT = "csat_action_count"
        private const val KEY_LAST_SHOWN_DATE = "csat_last_shown_date"
        private const val COOLDOWN_DAYS = 30
        private const val MIN_ACTIONS = 2
    }

    private fun todayEpochDays(): Int =
        Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()

    suspend fun onUserAction() {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        datastore.saveInt(KEY_ACTION_COUNT, count + 1)
    }

    suspend fun shouldShowCsat(): Boolean {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        if (count < MIN_ACTIONS) return false

        val lastShownDay = datastore.observeInt(KEY_LAST_SHOWN_DATE, 0).first()
        val today = todayEpochDays()

        return lastShownDay == 0 || (today - lastShownDay) >= COOLDOWN_DAYS
    }

    suspend fun recordShown() {
        datastore.saveInt(KEY_LAST_SHOWN_DATE, todayEpochDays())
    }
}
```

### How ViewModels Trigger CSAT — Decorator Pattern (no coupling)

**Chosen approach: Option C — ObservableAnalyticsTracker (Decorator + SharedFlow)**

Instead of modifying the `AnalyticsTracker` interface or injecting into existing ViewModels, wrap the tracker with a decorator that emits events on a `SharedFlow`. Zero changes to any existing code.

```kotlin
// ObservableAnalyticsTracker.kt
class ObservableAnalyticsTracker(
    private val delegate: AnalyticsTracker
) : AnalyticsTracker by delegate {

    private val _events = MutableSharedFlow<AnalyticsEvent>(
        extraBufferCapacity = 64  // Fire-and-forget: emitter never suspends
    )
    val events: SharedFlow<AnalyticsEvent> = _events.asSharedFlow()

    override fun event(name: String, params: Map<String, Any>) {
        delegate.event(name, params)                    // Forward to Firebase
        _events.tryEmit(AnalyticsEvent(name, params))   // Broadcast to observers
    }
}

data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap())
```

**DI change (only in platform modules):**
```kotlin
// PlatformModule.android.kt — one line changed
single<AnalyticsTracker> { ObservableAnalyticsTracker(Analytics) }

// PlatformModule.ios.kt — one line changed
single<AnalyticsTracker> { ObservableAnalyticsTracker(StubAnalyticsTracker) }
```

**CsatManager consumes events:**
```kotlin
// In CsatManager
private val csatTriggerEvents = setOf("checklist_created", "fill_created", "default_fill_updated")

fun startObserving(scope: CoroutineScope, analyticsTracker: AnalyticsTracker) {
    val observable = analyticsTracker as? ObservableAnalyticsTracker ?: return
    scope.launch {
        observable.events
            .filter { it.name in csatTriggerEvents }
            .collect { onUserAction() }
    }
}
```

**Zero changes to:** `AnalyticsTracker.kt` interface, `Analytics.kt` (Firebase impl), any existing ViewModel, any existing test.

#### Research Insights: Architecture

**Why Decorator > Listener > Direct injection:**
- `by delegate` (Kotlin delegation) forwards `setUserId`/`screenView` automatically — only `event()` is overridden
- `MutableSharedFlow` is thread-safe by design (unlike `CopyOnWriteArrayList` which is JVM-only)
- `extraBufferCapacity = 64` — `tryEmit` never suspends; if buffer overflows, events are dropped (acceptable for CSAT counters)
- No `replay` — new collectors don't receive past events (correct for CSAT)

**Testability:**
- Test `ObservableAnalyticsTracker` once: verify events emit on flow
- Test `CsatManager` once: verify it filters and increments counter
- No need to verify CSAT calls in every existing ViewModel test

### InAppReviewLauncher — @Composable expect fun

Following the established `ShareLauncher` pattern:

```kotlin
// commonMain
@Composable
expect fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
)

// androidMain
@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    val activity = LocalActivity.current  // Safe, typed as ComponentActivity?
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(shouldLaunch) {
        if (!shouldLaunch) return@LaunchedEffect
        if (activity == null || activity.isFinishing) {
            currentOnComplete()
            return@LaunchedEffect
        }
        try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReview()     // suspend via review-ktx
            manager.launchReview(activity, reviewInfo)   // suspend via review-ktx
        } catch (_: Exception) { /* quota exceeded or error — silent fail */ }
        currentOnComplete()
    }
}

// iosMain — stub, iOS not released
@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    LaunchedEffect(shouldLaunch) {
        if (shouldLaunch) onComplete()
    }
}
```

#### Research Insights: In-App Review

**Use `LocalActivity.current` (NOT `LocalContext.current as Activity`):**
- `LocalActivity` was added in `activity-compose:1.10.0`. Project has `1.12.2`.
- Casting `LocalContext` can cause `ClassCastException` when context is a `ContextWrapper`. There is now a lint rule that warns about this.

**Use `rememberUpdatedState` for the callback:**
- If the parent recomposes with a new lambda while the review flow is in progress, the effect still calls the latest version without restarting.

**`review-ktx` provides native suspend extensions:**
```kotlin
import com.google.android.play.core.ktx.requestReview   // suspend extension
import com.google.android.play.core.ktx.launchReview     // suspend extension
```
No need for `.await()` — these are proper suspend functions.

**Debug builds won't show the dialog.** For QA testing:
- Use Internal Test Track (add tester account, download from Play Store once)
- Use `FakeReviewManager` for automated tests

**Dependencies to add to `libs.versions.toml`:**

```toml
[versions]
playReview = "2.0.2"
kotlinxDatetime = "0.7.1"

[libraries]
play-review-ktx = { module = "com.google.android.play:review-ktx", version.ref = "playReview" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
```

### CsatBottomSheet — Implementation Details

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsatBottomSheet(state: CsatState, onIntent: (CsatIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(CsatIntent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title — always visible
            Text(
                text = stringResource(Res.string.csat_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),  // Prevent overflow per design system
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AppDimens.SpacingLg))

            // Emoji row — always visible
            EmojiRow(selectedRating = state.selectedRating, onSelect = { ... })

            Spacer(Modifier.height(AppDimens.SpacingLg))

            // Animated state-dependent content
            AnimatedContent(
                targetState = state.selectedRating,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) + slideInVertically { it / 4 })
                        .togetherWith(fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
            ) { rating ->
                when (rating) {
                    null -> { /* empty — waiting for selection */ }
                    CsatRating.NotGood, CsatRating.Okay -> FeedbackForm(...)
                    CsatRating.LoveIt -> ThankYouContent(...)
                }
            }
        }
    }
}
```

#### Research Insights: ModalBottomSheet

**`animateContentSize()` on the outer Column:**
- This smooths the sheet height change when content transitions between emoji-only → feedback form → thank-you. Place **before** `.padding()` in modifier chain — order matters.

**`skipPartiallyExpanded = true`:**
- CSAT is a focused, task-oriented overlay. The partially expanded state makes no sense here — user needs to see the full emoji row immediately.

**Keyboard handling (for feedback text field):**
- Place `imePadding()` on the FeedbackForm Column, NOT on the outer Column with `animateContentSize()` — avoids double-animation.
- Delay focus request by 350ms to wait for `AnimatedContent` transition to finish:
  ```kotlin
  LaunchedEffect(Unit) { delay(350); focusRequester.requestFocus() }
  ```

**CMP 1.9.x Bug: BackHandler workaround (#5015):**
- On Android, pressing Back while keyboard is open inside the sheet dismisses the entire sheet instead of just closing the keyboard.
- Fix: add `BackHandler` inside the sheet content:
  ```kotlin
  val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
  BackHandler(enabled = imeVisible) { focusManager.clearFocus() }
  ```

**Sheet conflict guard:**
- If CSAT triggers while `FillTargetBottomSheet` is visible, two sheets would overlap. Guard: only trigger CSAT from Main screen (after navigation completes), or check `AppNavigator` current route.

### Placement in App.kt

```kotlin
// App.kt
val csatViewModel: CsatViewModel = koinViewModel()
val csatState by csatViewModel.screenState.collectAsState()

Box {
    NavHost(...) { /* existing routes */ }

    if (csatState.showBottomSheet) {
        CsatBottomSheet(
            state = csatState,
            onIntent = csatViewModel::sendIntent,
        )
    }
}

// InAppReviewLauncher — side-effect composable, no UI
InAppReviewLauncher(
    shouldLaunch = csatState.shouldLaunchReview,
    onComplete = { csatViewModel.sendIntent(CsatIntent.ReviewComplete) },
)
```

`CsatViewModel` checks current route before showing — suppresses during Splash, Onboarding, Paywall, Debug via `AppNavigator` observation.

#### Research Insights: Global Overlay

**On Android, ModalBottomSheet creates its own Dialog window** — it renders above all content regardless of composition tree position. The `Box` wrapper is for code clarity, not z-ordering.

**ViewModel scoping:** `koinViewModel()` at `App` level lives as long as the entire app composition. It survives navigation. This is correct for a global overlay.

**No state restoration needed:** The CSAT sheet is ephemeral UI. If the process is killed, the sheet should NOT reappear. Using in-memory state (defaults to `false`) is correct. Do NOT persist `showBottomSheet` in DataStore.

### Analytics Events (3)

| Event | Params | When |
|-------|--------|------|
| `csat_shown` | — | Bottom sheet appeared |
| `csat_submitted` | `rating`, `has_text: Boolean` | Submit tapped |
| `csat_review_tapped` | — | "Rate on Google Play" tapped |

All other metrics are derivable: dismiss rate = shown - submitted, skip rate = submitted(great) - review_tapped.

## Acceptance Criteria

- [x] `ModalBottomSheet` appears after 2nd meaningful user action (if cooldown expired)
- [x] Shows 3 emoji (😞 😐 😊) with labels "Not Good / It's Okay / Love It!"
- [x] Tapping 😞/😐 expands to text field + Submit button via `AnimatedContent`
- [x] Tapping 😊 shows thank-you message + "Rate on Google Play" button + "Maybe later"
- [x] "Rate on Google Play" launches In-App Review API via `LocalActivity.current`
- [x] Text field is optional, max 500 characters, with `imePadding()` for keyboard
- [x] Submit saves feedback locally (rating + text + timestamp)
- [ ] Dismiss without emoji → no cooldown; dismiss after emoji or submit → 30-day cooldown
- [x] CSAT shows at most once per session (in-memory flag)
- [ ] CSAT does not show during Splash, Onboarding, Paywall, Debug, or when another sheet is open
- [x] 3 analytics events fire correctly
- [x] Sheet appears with ~3s delay after navigation (`LaunchedEffect + delay(3000)`)
- [ ] `BackHandler` workaround prevents keyboard-dismisses-sheet bug
- [x] `ObservableAnalyticsTracker` decorator is registered in platform DI modules
- [x] `kotlinx-datetime:0.6.2` and `play-review-ktx:2.0.2` added to `libs.versions.toml`

## Dependencies & Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Google Play policy — CSAT before review | **High** (AI-enforced since 2025) | Button-driven review, not automatic; fallback to decouple |
| In-App Review API quota exceeded | Low | Button does nothing — no UX breakage |
| Multiple triggers in one session | Low | In-memory `csatShownThisSession` flag in CsatViewModel |
| DataStore multiple instances crash | **High** | Reuse `UserAppDatastoreProvider.instance` — never create new instance |
| CMP 1.9.x keyboard-dismisses-sheet bug | Medium | `BackHandler` workaround documented above |
| Review dialog not shown in debug builds | Low | Use Internal Test Track for QA; `FakeReviewManager` for unit tests |
| `kotlinx-datetime` not in project | Low | Add `0.7.1` to `libs.versions.toml` |

## Success Metrics

| Metric | Target | Industry Benchmark |
|--------|--------|--------------------|
| CSAT completion rate (submitted / shown) | > 30% | 24.8% avg, 36.1% mobile (Refiner 2025) |
| Positive ratings (😊) | > 50% | — |
| In-App Review tap rate | > 40% | — |
| Google Play average rating increase | +0.1★ per quarter | — |

## Testing Strategy

### Unit Tests
- `ObservableAnalyticsTracker`: verify events emit on flow, verify delegation
- `CsatManager`: verify counter increment, cooldown logic, trigger evaluation
- Use `FakeAnalyticsTracker` (no Mockito needed)
- Use `runTest` + `advanceUntilIdle()` for coroutine testing

### QA Testing
- In-App Review: Internal Test Track (add tester account, download from Play Store)
- `FakeReviewManager` for automated E2E tests
- Debug menu: add "Reset CSAT" and "Force Show CSAT" buttons

## References

### Internal
- Bottom sheet pattern: `feature/home/.../detail/ChecklistDetailScreen.kt:588-665`
- ShareLauncher expect/actual: `feature/sharing/.../share/ShareLauncher.kt`
- AnalyticsTracker: `core/common/api/.../AnalyticsTracker.kt`
- DataStore API: `core/datastore/api/.../AppDatastore.kt`
- DataStore crash solution: `docs/solutions/runtime-errors/datastore-multiple-instances-crash.md`
- MVI pattern: `docs/solutions/architecture/mvi-pattern.md`
- App.kt: `composeApp/src/commonMain/.../App.kt`

### External
- [Google Play In-App Review API](https://developer.android.com/guide/playcore/in-app-review)
- [Google Play In-App Review Testing](https://developer.android.com/guide/playcore/in-app-review/test)
- [play-review-ktx 2.0.2](https://mvnrepository.com/artifact/com.google.android.play/review-ktx)
- [kotlinx-datetime 0.7.1](https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.7.1)
- [Apple SKStoreReviewController](https://developer.apple.com/documentation/storekit/skstorereviewcontroller)
- [Refiner: In-App Survey Response Rates 2025](https://refiner.io/blog/in-app-survey-response-rates/)
- [Refiner: Survey Timing Guide](https://refiner.io/blog/in-app-survey-timing/)
- [Customer Thermometer: Emoji Surveys](https://www.customerthermometer.com/feedback-surveys/the-ultimate-guide-to-using-emoji-in-surveys-and-business-communication/)
- [RevenueCat: Hack App Store Ratings](https://www.revenuecat.com/blog/engineering/how-to-hack-your-app-store-ratings/)
- [CMP Issue #5015: BackHandler Bug](https://github.com/JetBrains/compose-multiplatform/issues/5015)
