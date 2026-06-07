package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.common.api.formatExpirationDate
import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.SyncRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val googleAuthRepository: GoogleAuthRepository,
    private val syncRepository: SyncRepository,
) : AppViewModel<MainScreenState, MainScreenIntent, MainScreenSideEffect>() {

    private val _showLimitDialog = MutableStateFlow(false)

    private val _sideEffect = MutableSharedFlow<MainScreenSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<MainScreenSideEffect> = _sideEffect.asSharedFlow()

    init {
        syncUserProperties()
        viewModelScope.launch {
            googleAuthRepository.restoreSession()
        }
        // Push pending sync changes whenever the checklist list changes.
        // SyncRepository.pushPendingChanges() is a no-op when not signed in
        // (SyncState.Disabled), so this is safe to run unconditionally.
        viewModelScope.launch {
            repository.checklists.collect {
                syncRepository.pushPendingChanges()
            }
        }
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
            userDataRepository.getUserDataFlow(),
        ) { checklists, subscriptionStatus, userData ->
            Triple(checklists, subscriptionStatus, userData)
        },
        combine(
            getUserLimitsUseCase(),
            _showLimitDialog,
            hintsRepository.hamburgerHintShown,
        ) { userLimits, showLimitDialog, hintShown ->
            Triple(userLimits, showLimitDialog, hintShown)
        }
    ) { (checklists, subscriptionStatus, userData), (userLimits, showLimitDialog, hintShown) ->
        MainScreenState.Success(
            checklists = checklists,
            subscriptionStatus = subscriptionStatus,
            formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                formatExpirationDate(it)
            },
            aiCredits = userData.aiCredits,
            userLimits = userLimits,
            showLimitReachedDialog = showLimitDialog,
            showHamburgerHint = !hintShown,
            isGoogleLinked = userData.isGoogleLinked,
            googleEmail = userData.googleEmail,
            googleDisplayName = userData.googleDisplayName,
        )
    }.defaultStateIn(MainScreenState.Loading)

    override fun onIntent(intent: MainScreenIntent) {
        when (intent) {
            MainScreenIntent.OnAddChecklistClick -> handleAddChecklistClick()
            MainScreenIntent.OnAddChecklistFromTemplatesClick -> handleAddChecklistFromTemplatesClick()
            MainScreenIntent.OnAiAnalyzeClick -> handleAiFeatureClick { appNavigator.navigateToAnalyzeScreen() }
            MainScreenIntent.OnAiChatClick -> handleAiFeatureClick {
                viewModelScope.launch { _sideEffect.emit(MainScreenSideEffect.NavigateToAiChat) }
            }
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
            MainScreenIntent.OnSignInClick -> handleSignInClick()
            MainScreenIntent.OnSignOutClick -> handleSignOutClick()
        }
    }

    /**
     * Gates AI feature actions on web: when not signed in with Google on the
     * web platform, shows a "sign in required" snackbar and does not proceed.
     * On Android/iOS, proceeds unconditionally.
     */
    private fun handleAiFeatureClick(onAllowed: () -> Unit) {
        val state = screenState.value as? MainScreenState.Success
        val isWeb = getPlatformName() == "web"
        if (isWeb && state?.isGoogleLinked == false) {
            viewModelScope.launch {
                _sideEffect.emit(MainScreenSideEffect.ShowSnackbar("google_sign_in_required"))
            }
            return
        }
        onAllowed()
    }

    private fun handleAddChecklistClick() {
        if (screenState.value.userLimits?.canCreateChecklist == false) {
            appNavigator.navigateToPaywall(source = "main_add_checklist_limit")
        } else {
            appNavigator.navigateToCreateChecklistScreen()
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

    private fun handleSignInClick() {
        viewModelScope.launch {
            val result = googleAuthRepository.signInWithGoogle()
            result.onSuccess { googleUser ->
                val idToken = googleAuthRepository.getIdToken() ?: return@launch
                val platform = getPlatformName()
                userDataRepository.linkGoogleAccount(idToken, platform)
                _sideEffect.emit(MainScreenSideEffect.ShowSnackbar("google_sign_in_success"))
            }.onFailure {
                _sideEffect.emit(MainScreenSideEffect.ShowSnackbar("google_sign_in_failed"))
            }
        }
    }

    private fun handleSignOutClick() {
        viewModelScope.launch {
            syncRepository.stopListening()
            googleAuthRepository.signOut()
            userDataRepository.clearGoogleAccountData()
        }
    }
}


