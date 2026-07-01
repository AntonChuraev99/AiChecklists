package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
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

    /**
     * Session-only "hide until restart" for the sync banner. Set when the user dismisses the
     * banner; it resets to false on process restart because the ViewModel is recreated — that
     * is exactly the desired "1 dismiss hides it until the next launch" behavior. The lifetime
     * dismiss count that drives the permanent hide lives in [HintsRepository].
     */
    private val _syncBannerDismissedThisSession = MutableStateFlow(false)

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
            hintsRepository.syncBannerDismissCount,
            _syncBannerDismissedThisSession,
        ) { userLimits, showLimitDialog, hintShown, syncDismissCount, syncDismissedThisSession ->
            HomeFlags(userLimits, showLimitDialog, hintShown, syncDismissCount, syncDismissedThisSession)
        }
    ) { (checklists, subscriptionStatus, userData), flags ->
        // The sync banner is for users with something worth syncing: shown only once the user has
        // more than one checklist (an empty list / single auto-created checklist isn't nagged),
        // never when already Google-linked, suppressed for this session once dismissed, and hidden
        // forever after enough lifetime dismissals.
        val showSyncBanner = !userData.isGoogleLinked &&
            checklists.size > SYNC_BANNER_MIN_CHECKLISTS &&
            flags.syncBannerDismissCount < SYNC_BANNER_MAX_DISMISSALS &&
            !flags.syncBannerDismissedThisSession
        MainScreenState.Success(
            checklists = checklists,
            subscriptionStatus = subscriptionStatus,
            formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                formatExpirationDate(it)
            },
            aiCredits = userData.aiCredits,
            userLimits = flags.userLimits,
            showLimitReachedDialog = flags.showLimitDialog,
            showHamburgerHint = !flags.hamburgerHintShown,
            isGoogleLinked = userData.isGoogleLinked,
            googleEmail = userData.googleEmail,
            googleDisplayName = userData.googleDisplayName,
            showSyncBanner = showSyncBanner,
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
            MainScreenIntent.OnDismissSyncBanner -> handleDismissSyncBanner()
        }
    }

    /**
     * Dismiss the sync banner: hide it for this session (in-memory, resets on restart) and bump
     * the persistent lifetime count so that after [SYNC_BANNER_MAX_DISMISSALS] dismissals it never
     * shows again. The banner vanishing from the list is the visible feedback for the tap.
     */
    private fun handleDismissSyncBanner() {
        _syncBannerDismissedThisSession.update { true }
        viewModelScope.launch { hintsRepository.incrementSyncBannerDismissCount() }
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
            // Click — the funnel entry. Pairs with login_success / login_failed so the drop-off
            // and the exact failure (error_code + error_message) are visible in analytics instead
            // of only as a "couldn't sign in" snackbar with no cause.
            analyticsTracker.event(AnalyticsEvents.Auth.LOGIN_STARTED)
            val result = googleAuthRepository.signInWithGoogle()
            result.onSuccess { googleUser ->
                val idToken = googleAuthRepository.getIdToken()
                if (idToken == null) {
                    // Google credential obtained but Firebase token missing — a distinct failure
                    // from a cancelled/blocked sign-in; surface it rather than returning silently.
                    analyticsTracker.event(
                        AnalyticsEvents.Auth.LOGIN_FAILED,
                        mapOf(AnalyticsParams.ERROR_CODE to "null_id_token"),
                    )
                    _sideEffect.emit(MainScreenSideEffect.ShowSnackbar("sign_in_unavailable"))
                    return@launch
                }
                val platform = getPlatformName()
                // Google sign-in already succeeded above; the account-linking write is a distinct
                // failure surface. Without this guard a throw here emits neither login_success nor
                // login_failed — the linking step would fail silently in analytics and show the
                // user nothing.
                try {
                    userDataRepository.linkGoogleAccount(idToken, platform)
                } catch (e: Exception) {
                    analyticsTracker.event(
                        AnalyticsEvents.Auth.LOGIN_FAILED,
                        buildMap {
                            put(AnalyticsParams.ERROR_CODE, "link_failed")
                            e.message?.let { put(AnalyticsParams.ERROR_MESSAGE, it) }
                        },
                    )
                    _sideEffect.emit(MainScreenSideEffect.ShowSnackbar("sign_in_unavailable"))
                    return@launch
                }
                analyticsTracker.event(AnalyticsEvents.Auth.LOGIN_SUCCESS)
                _sideEffect.emit(MainScreenSideEffect.ShowSnackbar("google_sign_in_success"))
            }.onFailure { e ->
                analyticsTracker.event(
                    AnalyticsEvents.Auth.LOGIN_FAILED,
                    buildMap {
                        put(AnalyticsParams.ERROR_CODE, e::class.simpleName ?: "unknown")
                        e.message?.let { put(AnalyticsParams.ERROR_MESSAGE, it) }
                    },
                )
                _sideEffect.emit(MainScreenSideEffect.ShowSnackbar(signInErrorSnackbarKey(e)))
            }
        }
    }

    /**
     * Maps a Google sign-in failure to a user-facing snackbar key with a concrete explanation.
     * Matches on the STABLE Credential Manager type id that the Android auth provider forwards as
     * the message prefix (e.g. "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL: ..."
     * — see AndroidGoogleAuthProvider.signIn), which is not R8-obfuscated. Unknown causes fall back
     * to the generic key so the user always gets some explanation instead of a bare "try again".
     */
    private fun signInErrorSnackbarKey(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("TYPE_NO_CREDENTIAL", ignoreCase = true) ||
                msg.contains("No credentials", ignoreCase = true) -> "sign_in_no_account"
            msg.contains("TYPE_USER_CANCELED", ignoreCase = true) ||
                msg.contains("cancel", ignoreCase = true) -> "sign_in_cancelled"
            msg.contains("SignInWithIdp", ignoreCase = true) ||
                msg.contains("blocked", ignoreCase = true) ||
                msg.contains("internal error", ignoreCase = true) -> "sign_in_unavailable"
            msg.contains("network", ignoreCase = true) ||
                msg.contains("TYPE_INTERRUPTED", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) -> "sign_in_network"
            else -> "google_sign_in_failed"
        }
    }

    private fun handleSignOutClick() {
        viewModelScope.launch {
            syncRepository.stopListening()
            googleAuthRepository.signOut()
            userDataRepository.clearGoogleAccountData()
        }
    }

    private companion object {
        /**
         * The sync banner stays hidden until the user has MORE than this many checklists. At 1 the
         * user typically only has the auto-created starter list (or none at all and sees the
         * activation hero), so there is nothing worth syncing yet — keep the first-run quiet.
         */
        const val SYNC_BANNER_MIN_CHECKLISTS = 1

        /** After this many lifetime dismissals the sync banner never appears again. */
        const val SYNC_BANNER_MAX_DISMISSALS = 3
    }
}

/**
 * Second flow group for [MainScreenViewModel.screenState] — Kotlin has no 5-arity tuple, so the
 * five UX/limit flows are folded into a named holder for a readable `combine` transform.
 */
private data class HomeFlags(
    val userLimits: UserLimits?,
    val showLimitDialog: Boolean,
    val hamburgerHintShown: Boolean,
    val syncBannerDismissCount: Int,
    val syncBannerDismissedThisSession: Boolean,
)


