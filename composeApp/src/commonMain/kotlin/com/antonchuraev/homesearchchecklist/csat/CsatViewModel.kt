package com.antonchuraev.homesearchchecklist.csat

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CsatRating { NotGood, Okay, LoveIt }

enum class FeedbackChip {
    // Not Good chips — serious problems
    Buggy, Slow, HardToUse, InaccurateAi, TooExpensive,

    // Okay chips — room for improvement
    MoreFeatures, BetterDesign, Faster, MoreTemplates, BetterAi,
    ;

    companion object {
        val notGoodChips = listOf(Buggy, Slow, HardToUse, InaccurateAi, TooExpensive)
        val okayChips = listOf(MoreFeatures, BetterDesign, Faster, MoreTemplates, BetterAi)
    }
}

data class CsatState(
    val showBottomSheet: Boolean = false,
    val selectedRating: CsatRating? = null,
    val selectedChips: Set<FeedbackChip> = emptySet(),
    val feedbackText: String = "",
    val isSubmitting: Boolean = false,
    val isFeedbackOnly: Boolean = false,
    val showFeedbackThanks: Boolean = false,
) : State

sealed interface CsatIntent : Intent {
    data class SelectRating(val rating: CsatRating) : CsatIntent
    data class ToggleChip(val chip: FeedbackChip) : CsatIntent
    data class UpdateText(val text: String) : CsatIntent
    data object Submit : CsatIntent
    data object Dismiss : CsatIntent

    /** The review request finished; [outcome] says whether the launch went through, and why not. */
    data class ReviewComplete(val outcome: ReviewLaunchOutcome) : CsatIntent
    data object ForceShow : CsatIntent
    data object ForceShowFeedback : CsatIntent
    data object FeedbackThanksShown : CsatIntent
}

sealed interface CsatSideEffect : SideEffect

class CsatViewModel(
    private val csatManager: CsatManager,
    private val analyticsTracker: AnalyticsTracker,
) : AppViewModel<CsatState, CsatIntent, CsatSideEffect>() {

    companion object {
        private const val SHOW_DELAY_MS = 5000L
        private const val MAX_FEEDBACK_LENGTH = 500
        private const val SOURCE_AUTO = "auto"
        private const val SOURCE_MANUAL = "manual"
        private const val SOURCE_FEEDBACK = "feedback"

        /**
         * csat_review_completed closing a launch the platform ACCEPTED.
         *
         * "Accepted", not "shown": Play renders nothing once the review quota is spent and reports
         * no error for it, and the API never says whether a card appeared or a rating was left. So
         * this arm is an upper bound on real review impressions, never a count of them.
         */
        private const val SOURCE_REVIEW_LAUNCH = "review_launch"

        /**
         * csat_review_completed closing a launch that never reached the platform at all — no host
         * Activity, a rejected request, a cancelled launcher, or a platform with no review API.
         * The flow legitimately ended, but counting it as a launch would inflate the funnel.
         * [PARAM_NOT_SHOWN_REASON] splits the arm; without it these four are indistinguishable.
         */
        private const val SOURCE_NOT_SHOWN = "not_shown"

        /** csat_review_completed with no launch outstanding — a repeat callback for the same tap. */
        private const val SOURCE_REPEAT_CALLBACK = "repeat_callback"

        /** Why a `not_shown` completion never launched — [ReviewLaunchOutcome.analyticsReason]. */
        private const val PARAM_NOT_SHOWN_REASON = "not_shown_reason"
    }

    private val _screenState = MutableStateFlow(CsatState())
    override val screenState: StateFlow<CsatState> = _screenState.asStateFlow()

    private var csatShownThisSession = false

    // Which entry opened the current sheet — stamped on csat_dismissed so an auto-show
    // dismissal is distinguishable from a manual-drawer one (a naive dismissed/shown
    // double-counts both). Auto shows also remember their trigger + score for tuning.
    private var entrySource: String = SOURCE_AUTO
    private var currentTriggerEvent: String? = null
    private var currentScore: Int? = null

    // One-shot review requests. CONFLATED, not a state flag: the launcher consumes each element on
    // receipt, so a re-created collector cannot replay it.
    //
    // Why a flag could not work: prod showed 6 csat_review_completed against 4 csat_review_tapped
    // over 30 days — impossible if a completion only ever follows a tap. The guard was a
    // `shouldLaunchReview` Boolean in state, and this ViewModel is a Koin SINGLETON (see AppModule)
    // while the InAppReviewLauncher reading it is a transient composable; the intent bus is
    // asynchronous too (AppViewModel tryEmit -> collector), so the flag stayed latched true for a
    // window after onComplete had already fired. Any composition re-entry in that window (Activity
    // recreation: rotation, dark-mode/locale switch, multi-window resize, "don't keep activities" —
    // all likely right as the user returns from the Play review card) re-ran
    // LaunchedEffect(shouldLaunch = true), calling the Play API a SECOND time for one tap and
    // emitting a second completion. Conflated is the right capacity here: two taps that arrive
    // before the launcher collects are one review, not two.
    private val _reviewRequests = Channel<Unit>(Channel.CONFLATED)
    val reviewRequests: Flow<Unit> = _reviewRequests.receiveAsFlow()

    // Is a review launch outstanding? Set with csat_review_tapped, cleared by the completion that
    // closes it, so csat_review_completed can say WHICH tap it belongs to. The channel above should
    // make a second completion per tap impossible — this stays as the measurement that proves it:
    // SOURCE_REPEAT_CALLBACK must now flatline at zero in prod.
    private var reviewLaunchPending = false

    init {
        csatManager.startObserving(
            scope = viewModelScope,
            analyticsTracker = analyticsTracker,
            onShouldShow = { trigger, score -> showWithDelay(trigger, score) },
        )
    }

    private suspend fun showWithDelay(triggerEvent: String, score: Int) {
        if (csatShownThisSession) return
        delay(SHOW_DELAY_MS)
        if (csatShownThisSession) return
        csatShownThisSession = true
        entrySource = SOURCE_AUTO
        currentTriggerEvent = triggerEvent
        currentScore = score
        csatManager.recordShown()
        // Log WHICH scored moment (and the score) triggered the show — without this the scored
        // model is a black box in analytics (can't see which trigger converts to a survey).
        analyticsTracker.event(
            AnalyticsEvents.Csat.SHOWN,
            mapOf(
                AnalyticsParams.TRIGGER_EVENT to triggerEvent,
                AnalyticsParams.SCORE to score,
            ),
        )
        _screenState.update { it.copy(showBottomSheet = true) }
    }

    override fun onIntent(intent: CsatIntent) {
        when (intent) {
            is CsatIntent.SelectRating -> handleSelectRating(intent.rating)
            is CsatIntent.ToggleChip -> handleToggleChip(intent.chip)
            is CsatIntent.UpdateText -> handleUpdateText(intent.text)
            CsatIntent.Submit -> handleSubmit()
            CsatIntent.Dismiss -> handleDismiss()
            is CsatIntent.ReviewComplete -> handleReviewComplete(intent.outcome)
            CsatIntent.ForceShow -> handleForceShow()
            CsatIntent.ForceShowFeedback -> handleForceShowFeedback()
            CsatIntent.FeedbackThanksShown -> _screenState.update { it.copy(showFeedbackThanks = false) }
        }
    }

    private fun handleForceShow() {
        entrySource = SOURCE_MANUAL
        currentTriggerEvent = null
        currentScore = null
        analyticsTracker.event(AnalyticsEvents.Csat.OPENED, mapOf(AnalyticsParams.SOURCE to SOURCE_MANUAL))
        _screenState.update { it.copy(showBottomSheet = true) }
    }

    private fun handleForceShowFeedback() {
        entrySource = SOURCE_FEEDBACK
        currentTriggerEvent = null
        currentScore = null
        analyticsTracker.event(AnalyticsEvents.Csat.FEEDBACK_OPENED)
        _screenState.update {
            it.copy(
                showBottomSheet = true,
                isFeedbackOnly = true,
            )
        }
    }

    private fun handleSelectRating(rating: CsatRating) {
        analyticsTracker.event(AnalyticsEvents.Csat.RATING_SELECTED, mapOf(AnalyticsParams.RATING to rating.name))

        // "Love It!" → launch the native Play in-app review immediately and close the
        // survey; the thanks snackbar fires on review completion (handleReviewComplete).
        // Auto-launching review on positive sentiment is sentiment-gating, which Google
        // Play discourages — done deliberately per product decision (2026-06-13). See
        // docs/active/csat-loveit-direct-review-2026-06-13.md.
        if (rating == CsatRating.LoveIt) {
            analyticsTracker.event(AnalyticsEvents.Csat.REVIEW_TAPPED)
            reviewLaunchPending = true
            _reviewRequests.trySend(Unit)
            viewModelScope.launch {
                csatManager.recordOutcome(CsatManager.OUTCOME_SUBMITTED)
            }
            _screenState.update {
                it.copy(
                    selectedRating = rating,
                    showBottomSheet = false,
                )
            }
            return
        }

        _screenState.update {
            it.copy(
                selectedRating = rating,
                selectedChips = emptySet(),
                feedbackText = "",
            )
        }
    }

    private fun handleToggleChip(chip: FeedbackChip) {
        val wasSelected = chip in _screenState.value.selectedChips
        analyticsTracker.event(
            AnalyticsEvents.Csat.CHIP_TOGGLED,
            mapOf("chip" to chip.name, "selected" to !wasSelected),
        )
        _screenState.update {
            val newChips = if (wasSelected) {
                it.selectedChips - chip
            } else {
                it.selectedChips + chip
            }
            it.copy(selectedChips = newChips)
        }
    }

    private fun handleUpdateText(text: String) {
        if (text.length <= MAX_FEEDBACK_LENGTH) {
            _screenState.update { it.copy(feedbackText = text) }
        }
    }

    private fun handleSubmit() {
        val state = _screenState.value

        if (state.isFeedbackOnly) {
            analyticsTracker.event(
                AnalyticsEvents.Csat.FEEDBACK_SUBMITTED,
                mapOf(
                    AnalyticsParams.HAD_TEXT to state.feedbackText.isNotBlank(),
                    "text" to state.feedbackText,
                ),
            )
            _screenState.update {
                it.copy(
                    showBottomSheet = false,
                    isFeedbackOnly = false,
                    feedbackText = "",
                    showFeedbackThanks = true,
                )
            }
            return
        }

        val rating = state.selectedRating ?: return

        analyticsTracker.event(
            AnalyticsEvents.Csat.SUBMITTED,
            mapOf(
                AnalyticsParams.RATING to rating.name,
                AnalyticsParams.HAD_TEXT to state.feedbackText.isNotBlank(),
                "text" to state.feedbackText,
                "chips" to state.selectedChips.joinToString(",") { it.name },
            ),
        )

        viewModelScope.launch {
            csatManager.recordOutcome(CsatManager.OUTCOME_SUBMITTED)
        }

        // Only NotGood / Okay reach Submit now — LoveIt launches review on selection.
        _screenState.update { it.copy(showFeedbackThanks = true) }
        handleClose()
    }

    private fun handleReviewComplete(outcome: ReviewLaunchOutcome) {
        // The review request finished → thank the user. Log it so the funnel isn't blind after
        // csat_review_tapped: the store APIs never report whether the user actually rated, so a
        // returned request is the only signal there is.
        //
        // AnalyticsParams.SOURCE carries two independent facts, which is why it is a three-way and
        // not a boolean:
        //   review_launch   — the platform accepted the launch; closes its tap (the funnel arm)
        //   not_shown       — never reached the platform; PARAM_NOT_SHOWN_REASON says why
        //   repeat_callback — no launch outstanding; a second completion for one tap
        // Stamping a request that never launched as review_launch is what previously made the arm
        // unreadable: an Activity-less skip and a real launch were the same event.
        val closesLaunch = reviewLaunchPending
        reviewLaunchPending = false
        val source = when {
            !closesLaunch -> SOURCE_REPEAT_CALLBACK
            outcome == ReviewLaunchOutcome.Launched -> SOURCE_REVIEW_LAUNCH
            else -> SOURCE_NOT_SHOWN
        }
        analyticsTracker.event(
            AnalyticsEvents.Csat.REVIEW_COMPLETED,
            buildMap {
                put(AnalyticsParams.SOURCE, source)
                outcome.analyticsReason?.let { put(PARAM_NOT_SHOWN_REASON, it) }
            },
        )
        _screenState.update {
            it.copy(
                showBottomSheet = false,
                // Gated on closesLaunch, NOT on the outcome: a user who picked "Love It" is owed
                // the thanks snackbar even where no review can launch (web, iOS, no Activity) —
                // otherwise the tap produces no visible response at all. A repeat callback must not
                // re-trigger the snackbar the user already saw, nor clear one another flow is
                // waiting to show.
                showFeedbackThanks = it.showFeedbackThanks || closesLaunch,
            )
        }
        resetState()
    }

    private fun handleDismiss() {
        val hadRating = _screenState.value.selectedRating != null
        analyticsTracker.event(
            AnalyticsEvents.Csat.DISMISSED,
            buildMap<String, Any> {
                put(AnalyticsParams.HAD_RATING, hadRating)
                put(AnalyticsParams.SOURCE, entrySource)
                currentTriggerEvent?.let { put(AnalyticsParams.TRIGGER_EVENT, it) }
                currentScore?.let { put(AnalyticsParams.SCORE, it) }
            },
        )
        viewModelScope.launch {
            csatManager.recordOutcome(CsatManager.OUTCOME_DISMISSED)
        }
        handleClose()
    }

    private fun handleClose() {
        _screenState.update { it.copy(showBottomSheet = false) }
        resetState()
    }

    private fun resetState() {
        _screenState.update {
            it.copy(
                selectedRating = null,
                selectedChips = emptySet(),
                feedbackText = "",
                isSubmitting = false,
                isFeedbackOnly = false,
            )
        }
    }
}
