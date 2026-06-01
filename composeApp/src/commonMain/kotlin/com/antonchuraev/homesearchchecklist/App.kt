package com.antonchuraev.homesearchchecklist

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.csat.CsatBottomSheet
import com.antonchuraev.homesearchchecklist.csat.CsatIntent
import com.antonchuraev.homesearchchecklist.csat.CsatViewModel
import com.antonchuraev.homesearchchecklist.csat.InAppReviewLauncher
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppLocaleEnvironment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.customAppLocale
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.WidgetInstructionOverlay
import com.antonchuraev.homesearchchecklist.navigation.AdaptiveNavigationShell
import com.antonchuraev.homesearchchecklist.navigation.DrawerDestination
import com.antonchuraev.homesearchchecklist.navigation.EmptyDetailPlaceholder
import com.antonchuraev.homesearchchecklist.navigation.shouldUseSinglePaneLayout
import com.antonchuraev.homesearchchecklist.gestures.ApplyEdgeSwipeExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.feedback_thanks_message
import aichecklists.core.designsystem.generated.resources.chat_dock_ask_about
import aichecklists.core.designsystem.generated.resources.chat_ambiguous_match
import aichecklists.core.designsystem.generated.resources.chat_apply_error
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added_to
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added_many_to
import aichecklists.core.designsystem.generated.resources.chat_dispatch_add_empty
import aichecklists.core.designsystem.generated.resources.chat_dispatch_renamed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_already_done
import aichecklists.core.designsystem.generated.resources.chat_dispatch_completed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_empty
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_with_many
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_with_one
import aichecklists.core.designsystem.generated.resources.chat_dispatch_deleted
import aichecklists.core.designsystem.generated.resources.chat_dispatch_fill_load_failed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_find_blank
import aichecklists.core.designsystem.generated.resources.chat_dispatch_find_no_match
import aichecklists.core.designsystem.generated.resources.chat_dispatch_find_success
import aichecklists.core.designsystem.generated.resources.chat_dispatch_item_not_found
import aichecklists.core.designsystem.generated.resources.chat_dispatch_moved_many
import aichecklists.core.designsystem.generated.resources.chat_dispatch_moved_one
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_checklist_match
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_checklists
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_reminders_on_day
import aichecklists.core.designsystem.generated.resources.chat_dispatch_operation_failed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_reminder_set
import aichecklists.core.designsystem.generated.resources.chat_extract_fail
import aichecklists.core.designsystem.generated.resources.chat_feedback_blank_hint
import aichecklists.core.designsystem.generated.resources.chat_feedback_submitted
import aichecklists.core.designsystem.generated.resources.chat_generic_error
import aichecklists.core.designsystem.generated.resources.chat_history_load_error
import aichecklists.core.designsystem.generated.resources.chat_insufficient_credits
import aichecklists.core.designsystem.generated.resources.chat_completion_error
import aichecklists.core.designsystem.generated.resources.chat_mic_permission_denied
import aichecklists.core.designsystem.generated.resources.chat_not_found
import aichecklists.core.designsystem.generated.resources.chat_recording_cancelled
import aichecklists.core.designsystem.generated.resources.chat_requires_premium
import aichecklists.core.designsystem.generated.resources.chat_thumb_up_thanks
import aichecklists.core.designsystem.generated.resources.chat_transcribe_empty
import aichecklists.core.designsystem.generated.resources.chat_transcribe_error
import aichecklists.core.designsystem.generated.resources.chat_transcribing
import aichecklists.core.designsystem.generated.resources.chat_unknown_intent_hint
import aichecklists.core.designsystem.generated.resources.chat_voice_too_short
import aichecklists.core.designsystem.generated.resources.chat_preview_cancelled_message
import aichecklists.core.designsystem.generated.resources.chat_agent_round_limit
import aichecklists.core.designsystem.generated.resources.chat_panel_greeting
import aichecklists.core.designsystem.generated.resources.main_prompt_photo
import aichecklists.core.designsystem.generated.resources.main_prompt_add
import aichecklists.core.designsystem.generated.resources.main_prompt_remind
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDefaultPromptChips
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.preview.TemplatePreviewScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.OnboardingsScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.ScreenCatalogScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.StoreScreenshotScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreen
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatRoute
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatScreenIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatScreenSideEffect
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatViewModel
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChatFeaturesHelpSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AgentPlanCard
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatMessageBubble
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatPreviewCard
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatTypingIndicator
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiInlineChatPanel
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatAttachmentSourceSheet
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder.rememberAudioRecorderLauncher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarRoute
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayRoute
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallRoute
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.SubscriptionStatusScreen
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.ShareScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.UpdateFeedScreen
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext

/**
 * Substitutes `%1$s`, `%2$s`, … placeholders with the given args (positional).
 * Local copy of `applyFormatArgs` from ChatRoute (which is `internal` to feature/aichat/impl).
 * Must be kept in sync with the canonical version.
 */
private fun applyFormatArgsLocal(template: String, args: List<String>): String {
    if (args.isEmpty()) return template
    var result = template
    args.forEachIndexed { index, arg ->
        val placeholder = "%${index + 1}\$s"
        result = result.replace(placeholder, arg)
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
@Preview
fun App() {
    // Koin is started globally in GistiApplication.onCreate().
    // We hold a remembered reference to the already-started Koin so that
    // NavDisplay recompositions never lose the scope — `remember` keeps the same instance
    // across recompositions without ever calling stopKoin. This pattern fixes Crashlytics
    // issue edf060726547384d4e61afa21b1529ea (Koin #2240, marked wontfix).
    val koin = remember { GlobalContext.get() }
    KoinContext(koin) {
        val logger: AppLogger = remember { koin.get<AppLogger>() }
        LaunchedEffect(Unit) {
            logger.debug("App", "App composable start, Koin ref stable")
        }

        // Nav3: AppNavigator owns the NavBackStack (SnapshotStateList<NavKey>).
        // No NavController, no async Channel — mutations are synchronous.
        // Splash is seeded inside AppNavigatorImpl.init {} (DI-time, BEFORE first
        // composition). A LaunchedEffect-based seed would run after composition
        // and crash NavDisplay with "backstack cannot be empty".
        val navigator: AppNavigator = remember { koin.get<AppNavigator>() }

        val themeRepository: ThemeRepository = remember { koin.get<ThemeRepository>() }
        val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.Light)
        val dynamicColor by themeRepository.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (themeMode) {
            AppThemeMode.Light -> false
            AppThemeMode.Dark -> true
            AppThemeMode.System -> systemDark
        }

        val languageRepository: LanguageRepository = remember { koin.get<LanguageRepository>() }
        val language by languageRepository.language.collectAsStateWithLifecycle(initialValue = AppLanguage.System)
        LaunchedEffect(language) { customAppLocale = language.tag }

        val googleAuthRepository: GoogleAuthRepository = remember { koin.get<GoogleAuthRepository>() }
        val userDataRepository: UserDataRepository = remember { koin.get<UserDataRepository>() }
        // [AuthDiag] TEMP — remove after web Google-login fix is verified
        val authDiagLogger: AppLogger = remember { koin.get<AppLogger>() }
        val userData by userDataRepository.getUserDataFlow()
            .collectAsStateWithLifecycle(initialValue = UserData())
        val googleAuthState by googleAuthRepository.authState
            .collectAsStateWithLifecycle(initialValue = GoogleAuthState.NotAuthenticated)

        // [AuthDiag] TEMP — exposes whether the Firebase auth state and the
        // DataStore-backed userData.isGoogleLinked (what the drawer reads) are in
        // sync. If firebaseAuthState=Authenticated but isGoogleLinked=false, the
        // drawer shows "Sign in" despite being logged in. Remove after fix.
        LaunchedEffect(googleAuthState, userData.isGoogleLinked, userData.googleEmail) {
            authDiagLogger.debug(
                "AuthDiag",
                "STATE firebaseAuthState=" + googleAuthState::class.simpleName +
                    " | userData.isGoogleLinked=" + userData.isGoogleLinked +
                    " | userData.googleEmail=" + (userData.googleEmail ?: "null"),
            )
        }

        val scope = rememberCoroutineScope()

        val handleSignIn: () -> Unit = {
            scope.launch {
                authDiagLogger.debug("AuthDiag", "handleSignIn: click -> calling signInWithGoogle()")
                googleAuthRepository.signInWithGoogle()
                    .onSuccess { user ->
                        authDiagLogger.debug(
                            "AuthDiag",
                            "handleSignIn: signInWithGoogle SUCCESS uid=" + user.firebaseUid.take(6) +
                                " email=" + user.email,
                        )
                        val idToken = googleAuthRepository.getIdToken()
                        if (idToken == null) {
                            authDiagLogger.error(
                                "AuthDiag",
                                "handleSignIn: getIdToken() returned NULL -> linkGoogleAccount SKIPPED, drawer will NOT update",
                            )
                            return@launch
                        }
                        userDataRepository.linkGoogleAccount(
                            idToken = idToken,
                            platform = getPlatformName(),
                        )
                            .onSuccess {
                                authDiagLogger.debug(
                                    "AuthDiag",
                                    "handleSignIn: linkGoogleAccount SUCCESS email=" + it.googleEmail +
                                        " -> isGoogleLinked should now be true",
                                )
                            }
                            .onFailure {
                                authDiagLogger.error(
                                    "AuthDiag",
                                    "handleSignIn: linkGoogleAccount FAILED (" + it.message +
                                        ") -> Firebase signed in but drawer stays 'Sign in'",
                                    it,
                                )
                            }
                    }
                    .onFailure { e ->
                        authDiagLogger.error("AuthDiag", "handleSignIn: signInWithGoogle FAILED (" + e.message + ")", e)
                    }
            }
        }

        val handleSignOut: () -> Unit = {
            scope.launch {
                googleAuthRepository.signOut()
                userDataRepository.clearGoogleAccountData()
            }
        }

        val csatViewModel: CsatViewModel = koinInject()
        val csatState by csatViewModel.screenState.collectAsState()

        var showWidgetInstruction by remember { mutableStateOf(false) }
        val createWeeklyChecklistUseCase: CreateWeeklyChecklistUseCase = koinInject()
        LaunchedEffect(Unit) {
            navigator.events.collect { event ->
                when (event) {
                    AppNavEvent.ShowWidgetInstruction -> {
                        if (!showWidgetInstruction) {
                            showWidgetInstruction = true
                        }
                    }
                    AppNavEvent.CreateWeeklyChecklistRequested -> {
                        when (val result = createWeeklyChecklistUseCase()) {
                            is CreateWeeklyChecklistUseCase.Result.Created ->
                                navigator.navigateToChecklistDetail(result.checklistId, clearBackStack = true)
                            CreateWeeklyChecklistUseCase.Result.RequiresUpgrade ->
                                navigator.navigateToPaywall(source = "weekly_mode_limit")
                        }
                    }
                }
            }
        }

        AppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
            AppLocaleEnvironment {
            val snackbarHostState = remember { SnackbarHostState() }
            val feedbackThanksMessage = stringResource(Res.string.feedback_thanks_message)
            LaunchedEffect(csatState.showFeedbackThanks) {
                if (csatState.showFeedbackThanks) {
                    snackbarHostState.showSnackbar(feedbackThanksMessage)
                    csatViewModel.sendIntent(CsatIntent.FeedbackThanksShown)
                }
            }

            // ── App-level Inline Chat Panel ────────────────────────────────────────
            // One shared inline panel hosted at the root Box level so both MainScreen
            // and ChecklistDetailScreen can open it without importing feature/aichat.
            // Each caller provides only `onOpenInlineChat(checklistId?, label?)` —
            // no aichat import required.
            //
            // Route-gating: panel is collapsed automatically on ANY top-route change, so
            // an open dock never travels across navigation (e.g. into ChecklistDetail).
            var chatSheetOpen by rememberSaveable { mutableStateOf(false) }
            var chatSheetContextId by rememberSaveable { mutableStateOf<Long?>(null) }
            var chatSheetContextLabel by rememberSaveable { mutableStateOf<String?>(null) }

            // Collapse the chat dock on ANY top-route change so an open dock never
            // "travels" with navigation — e.g. opening a checklist from Main must close
            // the input sheet instead of leaving it floating over the detail screen.
            // The dock re-opens only via an explicit AskGisti / chip tap on the new screen.
            // (A dock opened in place does NOT re-trigger this: backStack.last is unchanged,
            // so currentTopRoute is stable and the effect does not re-run.)
            val currentTopRoute = remember(navigator.backStack.toList()) {
                navigator.backStack.lastOrNull()
            }
            LaunchedEffect(currentTopRoute) {
                if (chatSheetOpen) {
                    chatSheetOpen = false
                }
            }

            // Callback passed down to home screens — no aichat import needed.
            // For MainScreen: called with (null, null) — no checklist context.
            // For ChecklistDetailScreen: called with (checklistId, checklistName).
            val onOpenChatSheet: (Long?, String?) -> Unit = { checklistId, checklistName ->
                chatSheetContextId = checklistId
                chatSheetContextLabel = checklistName
                chatSheetOpen = true
            }

            // ChatViewModel singleton for the sheet (same ViewModel survives sheet hide/show,
            // keeping message history intact as required by the spec).
            val chatViewModel: ChatViewModel = koinViewModel()
            val chatUiState by chatViewModel.screenState.collectAsStateWithLifecycle()

            // Context label for the sheet header: "Ask about «Grocery list»…"
            val chatDockAskAboutFmt = stringResource(Res.string.chat_dock_ask_about)

            // Pre-resolve all chat_* messages for the sheet side-effect handler.
            // stringResource() is @Composable so it must be called here (not inside LaunchedEffect).
            // Using the same pattern as ChatRoute.kt.
            val sm_unknownIntentHint = stringResource(Res.string.chat_unknown_intent_hint)
            val sm_genericError = stringResource(Res.string.chat_generic_error)
            val sm_applyError = stringResource(Res.string.chat_apply_error)
            val sm_extractFail = stringResource(Res.string.chat_extract_fail)
            val sm_ambiguousMatch = stringResource(Res.string.chat_ambiguous_match)
            val sm_notFound = stringResource(Res.string.chat_not_found)
            val sm_requiresPremium = stringResource(Res.string.chat_requires_premium)
            val sm_dispatchAdded = stringResource(Res.string.chat_dispatch_added)
            val sm_dispatchAddedTo = stringResource(Res.string.chat_dispatch_added_to)
            val sm_dispatchAddedManyTo = stringResource(Res.string.chat_dispatch_added_many_to)
            val sm_dispatchAddEmpty = stringResource(Res.string.chat_dispatch_add_empty)
            val sm_dispatchRenamed = stringResource(Res.string.chat_dispatch_renamed)
            val sm_dispatchDeleted = stringResource(Res.string.chat_dispatch_deleted)
            val sm_dispatchItemNotFound = stringResource(Res.string.chat_dispatch_item_not_found)
            val sm_dispatchCompleted = stringResource(Res.string.chat_dispatch_completed)
            val sm_dispatchAlreadyDone = stringResource(Res.string.chat_dispatch_already_done)
            val sm_dispatchCreatedEmpty = stringResource(Res.string.chat_dispatch_created_empty)
            val sm_dispatchCreatedWithOne = stringResource(Res.string.chat_dispatch_created_with_one)
            val sm_dispatchCreatedWithMany = stringResource(Res.string.chat_dispatch_created_with_many)
            val sm_dispatchReminderSet = stringResource(Res.string.chat_dispatch_reminder_set)
            val sm_dispatchNoRemindersOnDay = stringResource(Res.string.chat_dispatch_no_reminders_on_day)
            val sm_dispatchMovedOne = stringResource(Res.string.chat_dispatch_moved_one)
            val sm_dispatchMovedMany = stringResource(Res.string.chat_dispatch_moved_many)
            val sm_dispatchFindBlank = stringResource(Res.string.chat_dispatch_find_blank)
            val sm_dispatchFindNoMatch = stringResource(Res.string.chat_dispatch_find_no_match)
            val sm_dispatchFindSuccess = stringResource(Res.string.chat_dispatch_find_success)
            val sm_dispatchOperationFailed = stringResource(Res.string.chat_dispatch_operation_failed)
            val sm_dispatchNoChecklists = stringResource(Res.string.chat_dispatch_no_checklists)
            val sm_dispatchNoChecklistMatch = stringResource(Res.string.chat_dispatch_no_checklist_match)
            val sm_dispatchFillLoadFailed = stringResource(Res.string.chat_dispatch_fill_load_failed)
            val sm_insufficientCredits = stringResource(Res.string.chat_insufficient_credits)
            val sm_completionError = stringResource(Res.string.chat_completion_error)
            val sm_historyLoadError = stringResource(Res.string.chat_history_load_error)
            val sm_feedbackSubmitted = stringResource(Res.string.chat_feedback_submitted)
            val sm_feedbackBlankHint = stringResource(Res.string.chat_feedback_blank_hint)
            val sm_micPermissionDenied = stringResource(Res.string.chat_mic_permission_denied)
            val sm_voiceTooShort = stringResource(Res.string.chat_voice_too_short)
            val sm_recordingCancelled = stringResource(Res.string.chat_recording_cancelled)
            val sm_thumbUpThanks = stringResource(Res.string.chat_thumb_up_thanks)
            val sm_previewCancelled = stringResource(Res.string.chat_preview_cancelled_message)
            val sm_transcribing = stringResource(Res.string.chat_transcribing)
            val sm_transcribeEmpty = stringResource(Res.string.chat_transcribe_empty)
            val sm_transcribeError = stringResource(Res.string.chat_transcribe_error)
            val sm_agentRoundLimit = stringResource(Res.string.chat_agent_round_limit)
            val chatPanelGreeting = stringResource(Res.string.chat_panel_greeting)

            val sheetMessages = remember(sm_unknownIntentHint, sm_genericError) {
                mapOf(
                    "chat_unknown_intent_hint" to sm_unknownIntentHint,
                    "chat_generic_error" to sm_genericError,
                    "chat_apply_error" to sm_applyError,
                    "chat_extract_fail" to sm_extractFail,
                    "chat_ambiguous_match" to sm_ambiguousMatch,
                    "chat_not_found" to sm_notFound,
                    "chat_requires_premium" to sm_requiresPremium,
                    "chat_dispatch_added" to sm_dispatchAdded,
                    "chat_dispatch_added_to" to sm_dispatchAddedTo,
                    "chat_dispatch_added_many_to" to sm_dispatchAddedManyTo,
                    "chat_dispatch_add_empty" to sm_dispatchAddEmpty,
                    "chat_dispatch_renamed" to sm_dispatchRenamed,
                    "chat_dispatch_deleted" to sm_dispatchDeleted,
                    "chat_dispatch_item_not_found" to sm_dispatchItemNotFound,
                    "chat_dispatch_completed" to sm_dispatchCompleted,
                    "chat_dispatch_already_done" to sm_dispatchAlreadyDone,
                    "chat_dispatch_created_empty" to sm_dispatchCreatedEmpty,
                    "chat_dispatch_created_with_one" to sm_dispatchCreatedWithOne,
                    "chat_dispatch_created_with_many" to sm_dispatchCreatedWithMany,
                    "chat_dispatch_reminder_set" to sm_dispatchReminderSet,
                    "chat_dispatch_no_reminders_on_day" to sm_dispatchNoRemindersOnDay,
                    "chat_dispatch_moved_one" to sm_dispatchMovedOne,
                    "chat_dispatch_moved_many" to sm_dispatchMovedMany,
                    "chat_dispatch_find_blank" to sm_dispatchFindBlank,
                    "chat_dispatch_find_no_match" to sm_dispatchFindNoMatch,
                    "chat_dispatch_find_success" to sm_dispatchFindSuccess,
                    "chat_dispatch_operation_failed" to sm_dispatchOperationFailed,
                    "chat_dispatch_no_checklists" to sm_dispatchNoChecklists,
                    "chat_dispatch_no_checklist_match" to sm_dispatchNoChecklistMatch,
                    "chat_dispatch_fill_load_failed" to sm_dispatchFillLoadFailed,
                    "chat_insufficient_credits" to sm_insufficientCredits,
                    "chat_completion_error" to sm_completionError,
                    "chat_history_load_error" to sm_historyLoadError,
                    "chat_feedback_submitted" to sm_feedbackSubmitted,
                    "chat_feedback_blank_hint" to sm_feedbackBlankHint,
                    "chat_mic_permission_denied" to sm_micPermissionDenied,
                    "chat_voice_too_short" to sm_voiceTooShort,
                    "chat_recording_cancelled" to sm_recordingCancelled,
                    "chat_thumb_up_thanks" to sm_thumbUpThanks,
                    "chat_preview_cancelled_message" to sm_previewCancelled,
                    "chat_transcribing" to sm_transcribing,
                    "chat_transcribe_empty" to sm_transcribeEmpty,
                    "chat_transcribe_error" to sm_transcribeError,
                    "chat_agent_round_limit" to sm_agentRoundLimit,
                )
            }

            // Sheet side-effect handler — mirrors ChatRoute logic exactly.
            LaunchedEffect(chatViewModel) {
                chatViewModel.sideEffect.collect { effect ->
                    when (effect) {
                        is ChatScreenSideEffect.ShowSnackbar -> {
                            val text = sheetMessages[effect.messageKey] ?: effect.messageKey
                            snackbarHostState.showSnackbar(text)
                        }
                        is ChatScreenSideEffect.ShowAssistantMessage -> {
                            val template = sheetMessages[effect.messageKey] ?: effect.messageKey
                            val resolved = applyFormatArgsLocal(template, effect.args)
                            chatViewModel.sendIntent(
                                ChatScreenIntent.AppendAssistantMessage(
                                    text = resolved,
                                    linkedChecklistId = effect.linkedChecklistId,
                                    askAiForText = effect.askAiForText,
                                )
                            )
                        }
                        ChatScreenSideEffect.NavigateBack -> {
                            // "Back" inside panel = collapse the inline panel
                            chatSheetOpen = false
                        }
                        is ChatScreenSideEffect.NavigateToChecklist -> {
                            chatSheetOpen = false
                            navigator.navigateToChecklistDetail(effect.checklistId)
                        }
                        ChatScreenSideEffect.NavigateToPaywall -> {
                            navigator.navigateToPaywall(source = "chat_sheet_credits")
                        }
                        ChatScreenSideEffect.RequestRecordAudioPermission -> {
                            // No-op in sheet host — AudioRecorder is only wired in ChatRoute.
                            // Voice input in the sheet reuses the same trigger-flag path.
                        }
                        is ChatScreenSideEffect.OpenFilePicker -> Unit // handled via trigger-flag
                    }
                }
            }

            // When the panel opens, seed the context checklist so Layer 1/2/3 requests
            // default to the right checklist. Also re-seeds when context changes without
            // the panel closing (e.g. user taps another checklist's dock while panel is open).
            LaunchedEffect(chatSheetOpen, chatSheetContextId) {
                if (chatSheetOpen) {
                    chatViewModel.sendIntent(
                        ChatScreenIntent.OnSetContextChecklist(chatSheetContextId)
                    )
                }
            }

            // Panel help sheet flag — shown when the "?" banner icon is tapped
            var chatPanelHelpSheetOpen by remember { mutableStateOf(false) }

            // Auto-focus the input when the panel expands so the keyboard rises and the
            // cursor lands in the field immediately. The panel slides in over ~300ms; a
            // short delay lets the TextField attach before requestFocus() (otherwise the
            // FocusRequester throws "not attached"). runCatching guards that race.
            val chatInputFocusRequester = remember { FocusRequester() }
            LaunchedEffect(chatSheetOpen) {
                if (chatSheetOpen) {
                    delay(150L)
                    runCatching { chatInputFocusRequester.requestFocus() }
                }
            }

            // Sheet local state: recording timer, drag-cancel, attachment sheet flag
            var chatSheetDragCancel by remember { mutableStateOf(false) }
            var chatSheetAttachmentSheet by remember { mutableStateOf(false) }
            var chatSheetRecordingMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(chatUiState.isRecording) {
                if (chatUiState.isRecording) {
                    chatSheetRecordingMs = 0L
                    while (true) {
                        delay(1_000L)
                        chatSheetRecordingMs += 1_000L
                    }
                } else {
                    chatSheetRecordingMs = 0L
                }
            }

            // Sheet file pickers — same pattern as ChatRoute
            val sheetImagePicker = rememberFilePickerLauncher(type = FilePickerType.IMAGE) { result ->
                if (result != null) {
                    chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(ChatAttachment(result.filePath, result.mimeType ?: "image/*", result.fileName, 0L)))
                }
                chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
            }
            val sheetPdfPicker = rememberFilePickerLauncher(type = FilePickerType.PDF) { result ->
                if (result != null) {
                    chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(ChatAttachment(result.filePath, result.mimeType ?: "application/pdf", result.fileName, 0L)))
                }
                chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
            }
            val sheetTextPicker = rememberFilePickerLauncher(type = FilePickerType.TEXT) { result ->
                if (result != null) {
                    chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(ChatAttachment(result.filePath, result.mimeType ?: "text/plain", result.fileName, 0L)))
                }
                chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
            }
            val sheetAudioPicker = rememberFilePickerLauncher(type = FilePickerType.AUDIO) { result ->
                if (result != null) {
                    chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(ChatAttachment(result.filePath, result.mimeType ?: "audio/*", result.fileName, 0L)))
                }
                chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
            }
            val sheetAudioRecorder = rememberAudioRecorderLauncher(
                onResult = { result ->
                    chatViewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(
                        recordingPath = result?.filePath,
                        mimeType = result?.mimeType ?: "audio/m4a",
                    ))
                },
                onError = { chatViewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(recordingPath = null)) },
            )
            LaunchedEffect(chatUiState.attachmentPickerType) {
                when (chatUiState.attachmentPickerType) {
                    AttachmentSource.Image -> sheetImagePicker.launch()
                    AttachmentSource.Pdf -> sheetPdfPicker.launch()
                    AttachmentSource.Text -> sheetTextPicker.launch()
                    AttachmentSource.Audio -> sheetAudioPicker.launch()
                    null -> Unit
                }
                if (chatUiState.attachmentPickerType != null) {
                    chatViewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {

            // Scene strategy is platform-dependent so the web layout differs from Android/iOS:
            // - Android / iOS: list-detail two-pane on Medium/Expanded windows (unchanged).
            // - Web (wasmJs): single-pane — the checklist list fills the whole content area
            //   and a tapped checklist replaces it in place instead of opening a second
            //   detail pane beside it. The listPane()/detailPane() entry metadata below is
            //   simply ignored by SinglePaneSceneStrategy, so no entry changes are needed.
            val platformName = remember { getPlatformName() }
            val sceneStrategy = if (shouldUseSinglePaneLayout(platformName)) {
                remember { SinglePaneSceneStrategy<NavKey>() }
            } else {
                rememberListDetailSceneStrategy<NavKey>()
            }

            val selectedDestination = remember(navigator.backStack.toList()) {
                val topLevel = navigator.backStack.findLast { key ->
                    key is AppNavRoute.Main || key is AppNavRoute.Today ||
                    key is AppNavRoute.Calendar || key is AppNavRoute.AiChat ||
                    key is AppNavRoute.UpdateFeed || key is AppNavRoute.Settings
                }
                when (topLevel) {
                    is AppNavRoute.Today -> DrawerDestination.Today
                    is AppNavRoute.Calendar -> DrawerDestination.Calendar
                    is AppNavRoute.AiChat -> DrawerDestination.AiChat
                    is AppNavRoute.UpdateFeed -> DrawerDestination.UpdateFeed
                    is AppNavRoute.Settings -> DrawerDestination.Settings
                    else -> DrawerDestination.Main
                }
            }

            val showShell = navigator.backStack.any { key ->
                key is AppNavRoute.Main || key is AppNavRoute.Today ||
                key is AppNavRoute.Calendar || key is AppNavRoute.AiChat ||
                key is AppNavRoute.UpdateFeed || key is AppNavRoute.Settings
            }

            val shellOnNavigate: (String) -> Unit = { dest ->
                when (dest) {
                    DrawerDestination.Main -> {
                        val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                        if (mainIdx >= 0) {
                            while (navigator.backStack.size > mainIdx + 1) {
                                navigator.backStack.removeAt(navigator.backStack.size - 1)
                            }
                        }
                    }
                    DrawerDestination.Today -> navigator.navigateToToday()
                    DrawerDestination.Calendar -> navigator.navigateToCalendar()
                    DrawerDestination.AiChat -> navigator.navigateToAiChat()
                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                    DrawerDestination.Settings -> navigator.navigateToSettings()
                }
            }

            val renderNav: @Composable (DrawerState?) -> Unit = { drawerState ->
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { navigator.onBack() },
                sceneStrategy = sceneStrategy,
                entryProvider = entryProvider {

                    entry<AppNavRoute.Splash> {
                        SplashScreen()
                    }

                    entry<AppNavRoute.Onboarding> {
                        OnboardingScreen()
                    }

                    entry<AppNavRoute.InteractiveOnboarding> {
                        InteractiveOnboardingScreen()
                    }

                    entry<AppNavRoute.Main>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        var isEditMode by rememberSaveable { mutableStateOf(false) }
                        ApplyEdgeSwipeExclusion(
                            enabled = drawerState != null && drawerState.isClosed && !isEditMode
                        )
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainScreen(
                                drawerState = drawerState,
                                isEditMode = isEditMode,
                                onEditModeChange = { isEditMode = it },
                                // Open the sheet (null = no checklist context) instead
                                // of navigating full-screen. Full-screen chat remains
                                // reachable from the drawer (AppNavRoute.AiChat entry below).
                                onNavigateToAiChat = { onOpenChatSheet(null, null) },
                                // Top-bar "+" and the leading "New list" prompt chip both
                                // route to manual checklist creation (Templates). Creation
                                // moved to the top of the screen; the bottom is a clean chat dock.
                                onCreateFromTemplatesClick = { navigator.navigateToTemplatesScreen() },
                            )
                        }
                    }

                    entry<AppNavRoute.CreateChecklistRoute.CreateChecklist> { route ->
                        CreateChecklistScreen(editChecklistId = route.editChecklistId)
                    }

                    entry<AppNavRoute.CreateChecklistRoute.Templates>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        TemplatesScreen()
                    }

                    entry<AppNavRoute.CreateChecklistRoute.TemplatePreview>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        TemplatePreviewScreen(templateId = route.templateId)
                    }

                    // Debug screens — only available in debug builds
                    if (AppBuildConfig.isDebug) {
                        entry<AppNavRoute.Debug> {
                            DebugScreen(
                                onShowCsat = { csatViewModel.sendIntent(CsatIntent.ForceShow) }
                            )
                        }

                        entry<AppNavRoute.StoreScreenshot> {
                            StoreScreenshotScreen()
                        }

                        entry<AppNavRoute.ScreenCatalog> {
                            ScreenCatalogScreen()
                        }

                        entry<AppNavRoute.Onboardings> {
                            OnboardingsScreen()
                        }
                    }

                    entry<AppNavRoute.Analyze> { route ->
                        AnalyzeScreen(checklistId = route.checklistId, fillDefault = route.fillDefault)
                    }

                    entry<AppNavRoute.AnalyzeResultPreview> {
                        AnalyzeResultPreviewScreen()
                    }

                    entry<AppNavRoute.ChecklistDetail>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        ChecklistDetailScreen(
                            checklistId = route.checklistId,
                            focusItemId = route.focusItemId,
                            onOpenChatSheet = { checklistId, checklistName ->
                                onOpenChatSheet(checklistId, checklistName)
                            },
                        )
                    }

                    entry<AppNavRoute.FillDetail>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        FillDetailScreen(fillId = route.fillId)
                    }

                    entry<AppNavRoute.FillsList>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        FillsListScreen(checklistId = route.checklistId)
                    }

                    entry<AppNavRoute.Paywall> { route ->
                        PaywallRoute(sourceOverride = route.source)
                    }

                    entry<AppNavRoute.SubscriptionStatus> { route ->
                        SubscriptionStatusScreen(showSuccessMessage = route.showSuccessMessage)
                    }

                    entry<AppNavRoute.ShareChecklist> { route ->
                        ShareScreen(checklistId = route.checklistId)
                    }

                    entry<AppNavRoute.Settings> {
                        SettingsScreen(
                            onBackClick = { navigator.onBack() },
                            drawerState = drawerState,
                        )
                    }

                    entry<AppNavRoute.UpdateFeed> {
                        UpdateFeedScreen(
                            onBackClick = { navigator.onBack() },
                            drawerState = drawerState,
                        )
                    }

                    entry<AppNavRoute.Today>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        TodayRoute(
                            drawerState = drawerState,
                            onCreateChecklistClick = { navigator.navigateToTemplatesScreen() },
                        )
                    }

                    entry<AppNavRoute.Calendar>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        CalendarRoute(
                            drawerState = drawerState,
                            onCreateChecklistClick = { navigator.navigateToTemplatesScreen() },
                        )
                    }

                    entry<AppNavRoute.AiChat> {
                        ChatRoute(
                            drawerState = drawerState,
                            snackbarHostState = snackbarHostState,
                            onBack = { navigator.onBack() },
                            onNavigateToChecklist = { checklistId ->
                                navigator.navigateToChecklistDetail(checklistId)
                            },
                            onNavigateToPaywall = {
                                navigator.navigateToPaywall(source = "chat_credits_chip")
                            },
                        )
                    }
                }, // end entryProvider
            ) // end NavDisplay
            } // end renderNav lambda

            if (showShell) {
                AdaptiveNavigationShell(
                    selectedDestination = selectedDestination,
                    onNavigate = shellOnNavigate,
                    onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                    onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                    versionName = AppBuildConfig.versionName,
                    isGoogleLinked = userData.isGoogleLinked,
                    googleEmail = userData.googleEmail,
                    googleDisplayName = userData.googleDisplayName,
                    onSignInClick = handleSignIn,
                    onSignOutClick = handleSignOut,
                    content = renderNav,
                )
            } else {
                renderNav(null)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )

            // ── GistiInlineChatPanel — inline bottom-anchored overlay ─────────────
            // Rendered inside the root Box so it overlays the nav content.
            // Panel is route-gated: auto-collapses on navigation away from dock routes
            // (see LaunchedEffect(currentTopRoute) above).
            // Chat history is preserved (singleton ChatViewModel survives collapse).
            //
            // The redesigned panel shows a single fixed-height "latest answer" frame
            // instead of a scrolling message list. hasLastAnswer decides whether we
            // surface the latest assistant turn / confirm card, or the empty greeting.
            val resolvedContextLabel = chatSheetContextLabel?.let { name ->
                chatDockAskAboutFmt.replace("%1\$s", name)
            }

            // The latest assistant turn to surface, if any (skips user messages).
            val lastAssistantMessage = remember(chatUiState.messages) {
                chatUiState.messages.lastOrNull { it.role == ChatRole.Assistant }
            }

            // hasLastAnswer drives the panel's answer-frame vs empty-state switch.
            // Order matches the lastAnswerContent when-branches below: a pending
            // confirm card / typing indicator / last assistant bubble all count.
            val hasLastAnswer = chatUiState.pendingPreview != null ||
                chatUiState.pendingAgentPlan != null ||
                chatUiState.isProcessing ||
                lastAssistantMessage != null

            GistiInlineChatPanel(
                isVisible = chatSheetOpen,
                hasLastAnswer = hasLastAnswer,
                contextLabel = resolvedContextLabel,
                onDismiss = { chatSheetOpen = false },
                onExpandClick = {
                    chatSheetOpen = false
                    navigator.navigateToAiChat()
                },
                onHelpClick = { chatPanelHelpSheetOpen = true },
                lastAnswerContent = {
                    // Fixed-height frame (96..160dp set by the panel): scroll a long answer
                    // inside instead of growing the dock. Priority mirrors ChatContent so the
                    // SAME confirm cards render here — closing the prior dock blocker where
                    // preview / plan cards never appeared and commands couldn't be confirmed.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                                vertical = AppDimens.SpacingMd,
                            ),
                    ) {
                        when {
                            chatUiState.pendingAgentPlan != null -> {
                                AgentPlanCard(
                                    plan = chatUiState.pendingAgentPlan!!,
                                    onApply = { chatViewModel.sendIntent(ChatScreenIntent.OnAgentPlanApply) },
                                    onCancel = { chatViewModel.sendIntent(ChatScreenIntent.OnAgentPlanCancel) },
                                )
                            }
                            chatUiState.pendingPreview != null -> {
                                val preview = chatUiState.pendingPreview!!
                                ChatPreviewCard(
                                    preview = preview,
                                    onApply = { chatViewModel.sendIntent(ChatScreenIntent.OnPreviewApply) },
                                    onCancel = { chatViewModel.sendIntent(ChatScreenIntent.OnPreviewCancel) },
                                    onReject = { chatViewModel.sendIntent(ChatScreenIntent.OnPreviewReject) },
                                    onItemTextChange = { chatViewModel.sendIntent(ChatScreenIntent.OnPreviewItemTextChange(it)) },
                                    showRejectButton = preview.toolCall !is com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall.CreateChecklistFromAttachment,
                                )
                            }
                            chatUiState.isProcessing -> {
                                ChatTypingIndicator()
                            }
                            lastAssistantMessage != null -> {
                                ChatMessageBubble(
                                    message = lastAssistantMessage,
                                    onFeedbackClick = { msg ->
                                        chatViewModel.sendIntent(ChatScreenIntent.OnFeedbackOpen(msg))
                                    },
                                    onThumbUpClick = { msg ->
                                        chatViewModel.sendIntent(ChatScreenIntent.OnThumbUpClick(msg))
                                    },
                                    onAskAiFallback = lastAssistantMessage.askAiForText?.let { text ->
                                        { chatViewModel.sendIntent(ChatScreenIntent.OnAskAiFallback(text)) }
                                    },
                                    onOpenChecklist = lastAssistantMessage.linkedChecklistId?.let { id ->
                                        {
                                            chatSheetOpen = false
                                            navigator.navigateToChecklistDetail(id)
                                        }
                                    },
                                    showSenderLabel = true,
                                )
                            }
                        }
                    }
                },
                emptyStateContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                                vertical = AppDimens.SpacingMd,
                            ),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    ) {
                        Text(
                            text = chatPanelGreeting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GistiPromptChips(
                            chips = gistiDefaultPromptChips(
                                photoLabel = stringResource(Res.string.main_prompt_photo),
                                addLabel = stringResource(Res.string.main_prompt_add),
                                remindLabel = stringResource(Res.string.main_prompt_remind),
                            ),
                            onChipClick = { /* chips pre-fill the input — no-op in panel context */ },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                inputContent = {
                    ChatInputRow(
                        text = chatUiState.inputText,
                        onTextChange = { chatViewModel.sendIntent(ChatScreenIntent.OnInputChange(it)) },
                        onSend = { chatViewModel.sendIntent(ChatScreenIntent.OnSendClick) },
                        onAttachFileClick = { chatSheetAttachmentSheet = true },
                        onVoiceRecordingStarted = { sheetAudioRecorder.start() },
                        onVoiceRecordingStopped = { sheetAudioRecorder.stop() },
                        onVoiceRecordingCancelled = { sheetAudioRecorder.cancel() },
                        onHelpClick = { chatPanelHelpSheetOpen = true },
                        hasAttachments = chatUiState.pendingAttachments.isNotEmpty(),
                        isEnabled = !chatUiState.isProcessing,
                        canSend = chatUiState.canSend,
                        isRecording = chatUiState.isRecording,
                        isTranscribing = chatUiState.isTranscribing,
                        onDragCancelChanged = { chatSheetDragCancel = it },
                        focusRequester = chatInputFocusRequester,
                    )
                },
            )
            } // Box

            // Chat attachment source sheet — shown when user taps the clip icon inside the chat sheet
            if (chatSheetAttachmentSheet) {
                ChatAttachmentSourceSheet(
                    onSourceSelected = { source ->
                        chatSheetAttachmentSheet = false
                        chatViewModel.sendIntent(ChatScreenIntent.OnPickAttachment(source))
                    },
                    onDismiss = { chatSheetAttachmentSheet = false },
                )
            }

            // Inline panel "?" help sheet — shown when the banner help icon is tapped
            if (chatPanelHelpSheetOpen) {
                AiChatFeaturesHelpSheet(
                    onDismiss = { chatPanelHelpSheetOpen = false },
                )
            }

            // CSAT survey — global overlay
            if (csatState.showBottomSheet) {
                CsatBottomSheet(
                    state = csatState,
                    onIntent = csatViewModel::sendIntent,
                )
            }

            // Widget instruction overlay — triggered by gisti://widget_instruction deeplink
            if (showWidgetInstruction) {
                ModalBottomSheet(
                    onDismissRequest = { showWidgetInstruction = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    WidgetInstructionOverlay(
                        onDone = { showWidgetInstruction = false }
                    )
                }
            }

            // In-App Review launcher — side-effect composable, no UI
            InAppReviewLauncher(
                shouldLaunch = csatState.shouldLaunchReview,
                onComplete = { csatViewModel.sendIntent(CsatIntent.ReviewComplete) },
            )
            } // AppLocaleEnvironment
        }
    }
}
