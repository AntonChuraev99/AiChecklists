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

data class CsatState(
    val showBottomSheet: Boolean = false,
    val selectedRating: CsatRating? = null,
    val feedbackText: String = "",
    val isSubmitting: Boolean = false,
    val shouldLaunchReview: Boolean = false,
) : State

sealed interface CsatIntent : Intent {
    data class SelectRating(val rating: CsatRating) : CsatIntent
    data class UpdateText(val text: String) : CsatIntent
    data object Submit : CsatIntent
    data object LaunchReview : CsatIntent
    data object SkipReview : CsatIntent
    data object Dismiss : CsatIntent
    data object ReviewComplete : CsatIntent
}

sealed interface CsatSideEffect : SideEffect

class CsatViewModel(
    private val csatManager: CsatManager,
    private val analyticsTracker: AnalyticsTracker,
) : AppViewModel<CsatState, CsatIntent, CsatSideEffect>() {

    companion object {
        private const val SHOW_DELAY_MS = 3000L
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
            is CsatIntent.UpdateText -> handleUpdateText(intent.text)
            CsatIntent.Submit -> handleSubmit()
            CsatIntent.LaunchReview -> handleLaunchReview()
            CsatIntent.SkipReview -> handleClose()
            CsatIntent.Dismiss -> handleDismiss()
            CsatIntent.ReviewComplete -> handleReviewComplete()
        }
    }

    private fun handleSelectRating(rating: CsatRating) {
        _screenState.update { it.copy(selectedRating = rating) }
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
            ),
        )

        // TODO: Save feedback locally for internal review system (separate feature)

        handleClose()
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
        // If user dismissed after selecting a rating, apply cooldown (already recorded in showWithDelay)
        // If dismissed without selecting — no extra cooldown, just close
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
                feedbackText = "",
                isSubmitting = false,
                shouldLaunchReview = false,
            )
        }
    }
}
