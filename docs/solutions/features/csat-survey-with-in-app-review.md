---
title: "CSAT Survey with In-App Review Integration"
category: features
module: csat
tags:
  - csat
  - customer-satisfaction
  - in-app-review
  - google-play
  - analytics
  - bottom-sheet
  - user-feedback
  - decorator-pattern
  - mvi
  - expect-actual
symptoms:
  - No structured way to collect user feedback
  - Missing mechanism to convert happy users into Play Store reviewers
  - Negative feedback bypasses internal channels and goes directly to public 1-2 star reviews
  - No visibility into user sentiment before public reviews
components:
  - CsatBottomSheet (UI)
  - CsatViewModel (State Management)
  - CsatManager (Trigger & Persistence)
  - InAppReviewLauncher (Platform-specific Review API)
  - ObservableAnalyticsTracker (Event Broadcasting)
trigger: ">=2 meaningful user actions AND 30-day cooldown expired"
dependencies_added:
  - kotlinx-datetime:0.6.2
  - play-review-ktx:2.0.2
date_created: 2026-02-19
---

# CSAT Survey with In-App Review Integration

## Problem Statement

The app had three core problems:

1. **No mechanism to collect structured user feedback** — No way to systematically gather user opinions about the product
2. **No way to route happy users toward leaving a store review** — Missing opportunity to convert satisfied users into Play Store reviewers
3. **Negative feedback goes directly to public 1-2 star reviews** — Unhappy users have no internal feedback channel

## Solution Overview

Implemented a CSAT (Customer Satisfaction) survey that:
- Triggers after 2+ meaningful user actions
- Uses emoji-based rating (Not Good / It's Okay / Love It!)
- Routes happy users to Google Play In-App Review
- Collects feedback from unhappy users internally
- Respects 30-day cooldown between displays

## Architecture

### File Structure

```
composeApp/src/
  commonMain/kotlin/.../csat/
    CsatBottomSheet.kt         # UI - Bottom sheet with emoji rating
    CsatViewModel.kt           # MVI ViewModel + State + Intent definitions
    CsatManager.kt             # Trigger logic + persistence
    ObservableAnalyticsTracker.kt  # Event observation decorator
    InAppReviewLauncher.kt     # expect declaration
  androidMain/kotlin/.../csat/
    InAppReviewLauncher.android.kt  # actual - Google Play In-App Review
  iosMain/kotlin/.../csat/
    InAppReviewLauncher.ios.kt      # actual - Stub (iOS not released)
```

### Key Components

| Component | File | Responsibility |
|-----------|------|----------------|
| **UI** | `CsatBottomSheet.kt` | ModalBottomSheet with emoji selection, feedback form, thank-you content |
| **ViewModel** | `CsatViewModel.kt` | MVI state management (CsatState, CsatIntent) |
| **Manager** | `CsatManager.kt` | Trigger evaluation + DataStore persistence |
| **Analytics Decorator** | `ObservableAnalyticsTracker.kt` | Broadcasts analytics events via SharedFlow |
| **In-App Review** | `InAppReviewLauncher.kt` | expect/actual for Google Play Review API |

## Implementation Details

### 1. State Management (MVI Pattern)

```kotlin
// CsatViewModel.kt
data class CsatState(
    val showBottomSheet: Boolean = false,
    val selectedRating: CsatRating? = null,    // NotGood, Okay, LoveIt
    val feedbackText: String = "",
    val isSubmitting: Boolean = false,
    val shouldLaunchReview: Boolean = false,   // Triggers platform review
) : State

sealed interface CsatIntent : Intent {
    data class SelectRating(val rating: CsatRating) : CsatIntent
    data class UpdateText(val text: String) : CsatIntent
    data object Submit : CsatIntent
    data object LaunchReview : CsatIntent
    data object SkipReview : CsatIntent
    data object Dismiss : CsatIntent
    data object ReviewComplete : CsatIntent
    data object ForceShow : CsatIntent  // Debug only
}

enum class CsatRating { NotGood, Okay, LoveIt }
```

### 2. Trigger Logic (CsatManager)

```kotlin
// CsatManager.kt
class CsatManager(private val datastore: AppDatastore) {

    companion object {
        private const val KEY_ACTION_COUNT = "csat_action_count"
        private const val KEY_LAST_SHOWN_DATE = "csat_last_shown_date"
        private const val COOLDOWN_DAYS = 30
        private const val MIN_ACTIONS = 2
    }

    private val csatTriggerEvents = setOf(
        "checklist_created",
        "fill_created",
        "default_fill_updated",
    )

    suspend fun shouldShowCsat(): Boolean {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        if (count < MIN_ACTIONS) return false

        val lastShownDay = datastore.observeInt(KEY_LAST_SHOWN_DATE, 0).first()
        val today = todayEpochDays()

        return lastShownDay == 0 || (today - lastShownDay) >= COOLDOWN_DAYS
    }
}
```

**Trigger Conditions:**
- User completes ≥2 meaningful actions
- 30-day cooldown expired (or first time)
- Not shown this session (in-memory guard)

### 3. Analytics Decorator Pattern

```kotlin
// ObservableAnalyticsTracker.kt
class ObservableAnalyticsTracker(
    private val delegate: AnalyticsTracker
) : AnalyticsTracker by delegate {

    private val _events = MutableSharedFlow<AnalyticsEvent>(
        extraBufferCapacity = 64  // tryEmit never suspends
    )
    val events: SharedFlow<AnalyticsEvent> = _events.asSharedFlow()

    override fun event(name: String, params: Map<String, Any>) {
        delegate.event(name, params)
        _events.tryEmit(AnalyticsEvent(name, params))
    }
}
```

**Why Decorator Pattern?**
- Zero changes to existing ViewModels
- Existing `AnalyticsTracker` interface unchanged
- CSAT can observe events without coupling

### 4. In-App Review (expect/actual)

```kotlin
// InAppReviewLauncher.kt (commonMain)
@Composable
expect fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
)

// InAppReviewLauncher.android.kt (androidMain)
@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    val activity = LocalActivity.current

    LaunchedEffect(shouldLaunch) {
        if (!shouldLaunch) return@LaunchedEffect
        try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReview()
            manager.launchReview(activity, reviewInfo)
        } catch (_: Exception) {
            // Quota exceeded or other error - silent fail
        }
        onComplete()
    }
}
```

### 5. App.kt Integration (Global Overlay)

```kotlin
// App.kt
@Composable
fun App() {
    KoinApplication(...) {
        val csatViewModel: CsatViewModel = koinInject()
        val csatState by csatViewModel.screenState.collectAsState()

        AppTheme {
            NavHost(...) { /* routes */ }

            // CSAT survey — global overlay
            if (csatState.showBottomSheet) {
                CsatBottomSheet(
                    state = csatState,
                    onIntent = csatViewModel::sendIntent,
                )
            }

            // In-App Review launcher — side-effect composable
            InAppReviewLauncher(
                shouldLaunch = csatState.shouldLaunchReview,
                onComplete = { csatViewModel.sendIntent(CsatIntent.ReviewComplete) },
            )
        }
    }
}
```

### 6. Debug Menu Integration (Callback Pattern)

```kotlin
// DebugScreen.kt (feature/debug module)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = koinViewModel(),
    onShowCsat: () -> Unit = {},  // Callback from App.kt
) {
    val items = listOf(
        // ...
        DebugItem(
            Icons.Default.ThumbUp,
            "Show CSAT Survey",
            "Force show CSAT bottom sheet for testing"
        ) {
            onShowCsat()
        }
    )
}

// App.kt
composable<AppNavRoute.Debug> {
    DebugScreen(
        onShowCsat = { csatViewModel.sendIntent(CsatIntent.ForceShow) }
    )
}
```

**Why Callback Pattern?**
- Avoids circular dependency (debug → composeApp)
- Debug module doesn't need CSAT internals
- Clean module separation

## UI Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    CSAT Bottom Sheet                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│              How do you like Gisti?                         │
│                                                             │
│     😕            😐            😍                          │
│   Not Good     It's Okay     Love It!                       │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ IF Not Good / It's Okay:                                    │
│   ┌─────────────────────────────────────────┐              │
│   │ Tell us more (optional)...              │              │
│   └─────────────────────────────────────────┘              │
│                    [Submit]                                 │
├─────────────────────────────────────────────────────────────┤
│ IF Love It!:                                                │
│   Thank you for your feedback!                              │
│   Would you mind leaving a review?                          │
│                                                             │
│         [Rate on Google Play]                               │
│            Maybe later                                      │
└─────────────────────────────────────────────────────────────┘
```

## Testing

### Test Coverage (8 Tests)

| Test | Flow Covered |
|------|--------------|
| `csat_appearsAfterSecondChecklistCreation` | Trigger threshold (2 actions) |
| `csat_negativeRatingShowsFeedbackForm` | Not Good → feedback form |
| `csat_neutralRatingShowsFeedbackForm` | It's Okay → feedback form |
| `csat_positiveRatingShowsThankYouAndReviewPrompt` | Love It! → review prompt |
| `csat_submitFeedbackCloseSheet` | Submit → sheet closes |
| `csat_maybeLaterClosesSheet` | Maybe later → sheet closes |
| `csat_dismissWithoutSelectionClosesSheet` | Back press → sheet closes |
| `csat_doesNotAppearAfterSingleAction` | 1 action → no CSAT |

### Running Tests

```bash
# Run CSAT tests only
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.antonchuraev.aichecklists.CsatFlowTest

# Run on specific emulator (Small_Phone)
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```

## Analytics Events

| Event | Parameters | When |
|-------|------------|------|
| `csat_shown` | — | Bottom sheet displayed |
| `csat_submitted` | `rating`, `has_text` | User submitted feedback |
| `csat_review_tapped` | — | User tapped "Rate on Google Play" |

## Key Technical Decisions

1. **Decorator Pattern for Analytics** — Wraps existing tracker, broadcasts via SharedFlow
2. **State-driven Review Launch** — Uses `shouldLaunchReview` boolean, not SideEffects
3. **Single Action Counter** — One counter for all meaningful actions
4. **Epoch Days for Cooldown** — `kotlinx-datetime` with `toEpochDays()` for persistence
5. **3-Second Delay** — Lets user process their action before showing CSAT
6. **Session Guard** — In-memory flag prevents duplicate shows
7. **Button-Driven Review** — User explicitly taps button (Google Play policy)
8. **Soft Emoji Labels** — "Not Good / It's Okay / Love It!" reduces bias

## Dependencies Added

```toml
# gradle/libs.versions.toml
kotlinx-datetime = "0.6.2"
play-review = "2.0.2"

[libraries]
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
play-review-ktx = { module = "com.google.android.play:review-ktx", version.ref = "play-review" }
```

## Related Files

- Plan: `docs/plans/2026-02-18-feat-csat-survey-with-in-app-review-plan.md`
- Tests: `composeApp/src/androidTest/kotlin/com/antonchuraev/aichecklists/CsatFlowTest.kt`
- Similar pattern: `feature/sharing/.../ShareLauncher.kt` (expect/actual)

## Git Commits

```
df17d7b feat(csat): add CSAT survey with in-app review
509273a test(csat): add UI tests for CSAT bottom sheet
```

## Future Enhancements

- [ ] A/B test different thresholds (2 vs 3 vs 5 actions)
- [ ] Version-based reset (re-trigger after major updates)
- [ ] Segment targeting (different triggers for free vs premium)
- [ ] Feedback storage for internal review system
