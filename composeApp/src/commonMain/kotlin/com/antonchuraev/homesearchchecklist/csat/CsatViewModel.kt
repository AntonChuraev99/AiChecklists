package com.antonchuraev.homesearchchecklist.csat

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CsatRating { NotGood, Okay, LoveIt }

enum class FeedbackChip {
    // Not Good chips — serious problems
    Buggy, Slow, HardToUse, InaccurateAi, TooExpensive,

    // Okay chips — room for improvement
    MoreFeatures, BetterDesign, Faster, MoreTemplates, BetterAi,

    // Positive chips — what users love
    AiQuality, Templates, NiceDesign, EasyExport, EasyToUse,
    ;

    companion object {
        val notGoodChips = listOf(Buggy, Slow, HardToUse, InaccurateAi, TooExpensive)
        val okayChips = listOf(MoreFeatures, BetterDesign, Faster, MoreTemplates, BetterAi)
        val positiveChips = listOf(AiQuality, Templates, NiceDesign, EasyExport, EasyToUse)
    }
}

data class CsatState(
    val showBottomSheet: Boolean = false,
    val selectedRating: CsatRating? = null,
    val selectedChips: Set<FeedbackChip> = emptySet(),
    val feedbackText: String = "",
    val isSubmitted: Boolean = false,
    val isSubmitting: Boolean = false,
    val shouldLaunchReview: Boolean = false,
) : State

sealed interface CsatIntent : Intent {
    data class SelectRating(val rating: CsatRating) : CsatIntent
    data class ToggleChip(val chip: FeedbackChip) : CsatIntent
    data class UpdateText(val text: String) : CsatIntent
    data object Submit : CsatIntent
    data object LaunchReview : CsatIntent
    data object SkipReview : CsatIntent
    data object Dismiss : CsatIntent
    data object ReviewComplete : CsatIntent
    data object ForceShow : CsatIntent // Debug only
}

sealed interface CsatSideEffect : SideEffect

class CsatViewModel(
    private val csatManager: CsatManager,
    private val analyticsTracker: AnalyticsTracker,
) : AppViewModel<CsatState, CsatIntent, CsatSideEffect>() {

    companion object {
        private const val SHOW_DELAY_MS = 5000L
        private const val MAX_FEEDBACK_LENGTH = 500
    }

    private val _screenState = MutableStateFlow(CsatState())
    override val screenState: StateFlow<CsatState> = _screenState.asStateFlow()

    private var csatShownThisSession = false

    init {
        csatManager.startObserving(
            scope = viewModelScope,
            analyticsTracker = analyticsTracker,
            onShouldShow = { showWithDelay() },
        )
    }

    private suspend fun showWithDelay() {
        if (csatShownThisSession) return
        delay(SHOW_DELAY_MS)
        if (csatShownThisSession) return
        csatShownThisSession = true
        csatManager.recordShown()
        analyticsTracker.event("csat_shown")
        _screenState.update { it.copy(showBottomSheet = true) }
    }

    override fun onIntent(intent: CsatIntent) {
        when (intent) {
            is CsatIntent.SelectRating -> handleSelectRating(intent.rating)
            is CsatIntent.ToggleChip -> handleToggleChip(intent.chip)
            is CsatIntent.UpdateText -> handleUpdateText(intent.text)
            CsatIntent.Submit -> handleSubmit()
            CsatIntent.LaunchReview -> handleLaunchReview()
            CsatIntent.SkipReview -> handleClose()
            CsatIntent.Dismiss -> handleDismiss()
            CsatIntent.ReviewComplete -> handleReviewComplete()
            CsatIntent.ForceShow -> handleForceShow()
        }
    }

    private fun handleForceShow() {
        _screenState.update { it.copy(showBottomSheet = true) }
    }

    private fun handleSelectRating(rating: CsatRating) {
        _screenState.update {
            it.copy(
                selectedRating = rating,
                selectedChips = emptySet(),
                feedbackText = "",
                isSubmitted = false,
            )
        }
    }

    private fun handleToggleChip(chip: FeedbackChip) {
        _screenState.update {
            val newChips = if (chip in it.selectedChips) {
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
        val rating = state.selectedRating ?: return

        analyticsTracker.event(
            "csat_submitted",
            mapOf(
                "rating" to rating.name,
                "has_text" to state.feedbackText.isNotBlank(),
                "chips" to state.selectedChips.joinToString(",") { it.name },
            ),
        )

        viewModelScope.launch {
            csatManager.recordOutcome(CsatManager.OUTCOME_SUBMITTED)
        }

        when (rating) {
            CsatRating.LoveIt -> {
                _screenState.update { it.copy(isSubmitted = true) }
            }
            else -> handleClose()
        }
    }

    private fun handleLaunchReview() {
        analyticsTracker.event("csat_review_tapped")
        _screenState.update { it.copy(shouldLaunchReview = true) }
    }

    private fun handleReviewComplete() {
        _screenState.update {
            it.copy(shouldLaunchReview = false, showBottomSheet = false)
        }
        resetState()
    }

    private fun handleDismiss() {
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
                isSubmitted = false,
                isSubmitting = false,
                shouldLaunchReview = false,
            )
        }
    }
}
