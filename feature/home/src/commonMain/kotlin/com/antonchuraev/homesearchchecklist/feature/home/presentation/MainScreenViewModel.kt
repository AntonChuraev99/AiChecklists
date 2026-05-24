package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.common.api.formatExpirationDate
import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class MainScreenViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val userDataRepository: UserDataRepository,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val hintsRepository: HintsRepository,
) : AppViewModel<MainScreenState, MainScreenIntent, Nothing>() {

    private val _showLimitDialog = MutableStateFlow(false)

    init {
        syncUserProperties()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val checklistsWithProgress = repository.checklists.flatMapLatest { checklists ->
        if (checklists.isEmpty()) {
            flowOf(emptyList())
        } else {
            val fillFlows = checklists.map { checklist ->
                repository.getDefaultFillByChecklistId(checklist.id).map { fill ->
                    ChecklistWithProgress(
                        checklist = checklist,
                        totalItems = fill?.items?.size ?: checklist.items.size,
                        checkedItems = fill?.items?.count { it.checked } ?: 0
                    )
                }
            }
            combine(fillFlows) { it.toList() }
        }
    }

    override val screenState: StateFlow<MainScreenState> = combine(
        combine(
            checklistsWithProgress,
            getSubscriptionStatusUseCase(),
            userDataRepository.getUserDataFlow().map { it.aiCredits },
        ) { checklists, subscriptionStatus, aiCredits ->
            Triple(checklists, subscriptionStatus, aiCredits)
        },
        combine(
            getUserLimitsUseCase(),
            _showLimitDialog,
            hintsRepository.hamburgerHintShown,
        ) { userLimits, showLimitDialog, hintShown ->
            Triple(userLimits, showLimitDialog, hintShown)
        }
    ) { (checklists, subscriptionStatus, aiCredits), (userLimits, showLimitDialog, hintShown) ->
        MainScreenState.Success(
            checklists = checklists,
            subscriptionStatus = subscriptionStatus,
            formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                formatExpirationDate(it)
            },
            aiCredits = aiCredits,
            userLimits = userLimits,
            showLimitReachedDialog = showLimitDialog,
            showHamburgerHint = !hintShown,
        )
    }.defaultStateIn(MainScreenState.Loading)

    override fun onIntent(intent: MainScreenIntent) {
        when (intent) {
            MainScreenIntent.OnAddChecklistClick -> handleAddChecklistClick()
            MainScreenIntent.OnAddChecklistFromTemplatesClick -> handleAddChecklistFromTemplatesClick()
            MainScreenIntent.OnAiAnalyzeClick -> appNavigator.navigateToAnalyzeScreen()
            is MainScreenIntent.OnChecklistClick -> appNavigator.navigateToChecklistDetail(intent.checklistWithProgress.checklist.id)
            MainScreenIntent.OnPremiumBannerClick -> handlePremiumOrCreditsClick()
            MainScreenIntent.OnCreditsClick -> handlePremiumOrCreditsClick()
            MainScreenIntent.OnDismissLimitDialog -> _showLimitDialog.update { false }
            MainScreenIntent.OnUpgradeToPremiumClick -> {
                _showLimitDialog.update { false }
                appNavigator.navigateToPaywall(source = "main_limit_dialog")
            }
            is MainScreenIntent.OnReorderChecklists -> {
                viewModelScope.launch { repository.reorderChecklists(intent.orderedIds) }
            }
            MainScreenIntent.OnUpdateFeedClick -> appNavigator.navigateToUpdateFeed()
            MainScreenIntent.OnHamburgerHintCompleted -> {
                viewModelScope.launch { hintsRepository.markHamburgerHintShown() }
            }
        }
    }

    private fun handleAddChecklistClick() {
        if (screenState.value.userLimits?.canCreateChecklist == false) {
            appNavigator.navigateToPaywall(source = "main_add_checklist_limit")
        } else {
            appNavigator.navigateToTemplatesScreen()
        }
    }

    private fun handleAddChecklistFromTemplatesClick() {
        if (screenState.value.userLimits?.canCreateChecklist == false) {
            _showLimitDialog.update { true }
        } else {
            appNavigator.navigateToTemplatesScreen()
        }
    }

    private fun syncUserProperties() {
        viewModelScope.launch {
            val userData = userDataRepository.getUserData()
            val checklistCount = repository.checklists.first().size
            val totalFills = repository.getTotalAdditionalFillCount()
            val firstLaunchAt = userDataRepository.getFirstLaunchAtMillis()
            val daysSinceInstall = if (firstLaunchAt > 0) {
                ((currentTimeMillis() - firstLaunchAt) / (24 * 60 * 60 * 1000)).toInt()
            } else {
                0
            }

            analyticsTracker.setUserProperties(
                mapOf(
                    "is_premium" to userData.isPremium,
                    "checklist_count" to checklistCount,
                    "total_fills" to totalFills,
                    "days_since_install" to daysSinceInstall
                )
            )
        }
    }

    private fun handlePremiumOrCreditsClick() {
        val state = screenState.value as? MainScreenState.Success ?: return
        if (state.subscriptionStatus.isActive) {
            appNavigator.navigateToSubscriptionStatus()
        } else {
            appNavigator.navigateToPaywall(source = "main_credits_chip")
        }
    }
}


