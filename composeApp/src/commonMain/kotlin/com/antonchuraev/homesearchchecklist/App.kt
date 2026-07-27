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
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateChecklistFromGalleryTemplateUseCase
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.deeplink.PendingGalleryDeepLink
import com.antonchuraev.homesearchchecklist.csat.CsatBottomSheet
import com.antonchuraev.homesearchchecklist.csat.CsatIntent
import com.antonchuraev.homesearchchecklist.csat.CsatViewModel
import com.antonchuraev.homesearchchecklist.csat.InAppReviewLauncher
import com.antonchuraev.homesearchchecklist.appupdate.AppUpdateLauncher
import com.antonchuraev.homesearchchecklist.sync.UserCreditsSync
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.LocalEmojiFont
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.rememberEmojiFont
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppLocaleEnvironment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.customAppLocale
import com.antonchuraev.homesearchchecklist.desingsystem.theme.persistAppLocale
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.WidgetInstructionOverlay
import com.antonchuraev.homesearchchecklist.navigation.AdaptiveNavigationShell
import com.antonchuraev.homesearchchecklist.navigation.DrawerDestination
import com.antonchuraev.homesearchchecklist.navigation.EmptyDetailPlaceholder
import com.antonchuraev.homesearchchecklist.navigation.shouldUseSinglePaneLayout
// ── v2 navigation A/B arm ────────────────────────────────────────────────────────────────────
// Everything the v2 arm needs is imported here and gated inside this file only. Keeping ALL the
// arm branching in App.kt is deliberate: a reviewer can prove the control arm is untouched by
// reading one diff, which a CompositionLocal (an invisible second channel readable from any
// shared composable) would make impossible.
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxRoute
import com.antonchuraev.homesearchchecklist.navigation.OverviewScreen
import com.antonchuraev.homesearchchecklist.navigation.V2Destination
import com.antonchuraev.homesearchchecklist.navigation.V2NavigationShell
import com.antonchuraev.homesearchchecklist.navigation.V2ShellMetrics
import com.antonchuraev.homesearchchecklist.gestures.ApplyEdgeSwipeExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.weekly_checklist_default_name
import aichecklists.core.designsystem.generated.resources.gallery_deeplink_not_found
import aichecklists.core.designsystem.generated.resources.gallery_deeplink_error
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
import aichecklists.core.designsystem.generated.resources.chat_dispatch_completed_items_removed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_empty
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_from_attachment
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
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_completed_items
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
import aichecklists.core.designsystem.generated.resources.chat_error_offline
import aichecklists.core.designsystem.generated.resources.chat_error_service
import aichecklists.core.designsystem.generated.resources.chat_error_timeout
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
import aichecklists.core.designsystem.generated.resources.chat_move_no_other_lists
import aichecklists.core.designsystem.generated.resources.chat_attach_analyze_empty
import aichecklists.core.designsystem.generated.resources.chat_attach_analyze_failed
import aichecklists.core.designsystem.generated.resources.chat_attach_limit_reached
import aichecklists.core.designsystem.generated.resources.chat_attach_no_files
import aichecklists.core.designsystem.generated.resources.chat_attach_store_failed
import aichecklists.core.designsystem.generated.resources.chat_attach_unsupported_type
import aichecklists.core.designsystem.generated.resources.chat_choice_dismissed_message
import aichecklists.core.designsystem.generated.resources.chat_choice_edit_empty_hint
import aichecklists.core.designsystem.generated.resources.chat_dispatch_attached_many
import aichecklists.core.designsystem.generated.resources.chat_dispatch_attached_one
import aichecklists.core.designsystem.generated.resources.chat_result_moved_to
import aichecklists.core.designsystem.generated.resources.chat_result_remembered_list
import aichecklists.core.designsystem.generated.resources.chat_result_undone_add
import aichecklists.core.designsystem.generated.resources.chat_result_undone_complete
import aichecklists.core.designsystem.generated.resources.chat_undo_item_gone
import aichecklists.core.designsystem.generated.resources.chat_panel_greeting
import aichecklists.core.designsystem.generated.resources.main_create_with_ai_prefill
import aichecklists.core.designsystem.generated.resources.main_prompt_link_prefill
import aichecklists.core.designsystem.generated.resources.main_prompt_remind_prefill
import aichecklists.core.designsystem.generated.resources.main_prompt_plan_day_query
import aichecklists.core.designsystem.generated.resources.checklist_prompt_whats_missing_query
import aichecklists.core.designsystem.generated.resources.checklist_prompt_generate_ideas_query
import aichecklists.core.designsystem.generated.resources.checklist_prompt_summary_query
import aichecklists.core.designsystem.generated.resources.checklist_prompt_add_items_query
import aichecklists.core.designsystem.generated.resources.checklist_prompt_remind_prefill
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiQuickAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiChecklistAction
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
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChoiceResponse
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatFeedbackSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatBody
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatEmptyState
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatMessageBubble
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatTypingIndicator
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatRecordingOverlay
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import androidx.compose.foundation.gestures.AnchoredDraggableState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatDockItemCreateOverride
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiExpandableDockContent
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiFullChatOverlay
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatAttachmentSourceSheet
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.rememberAudioRecorderLauncher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarRoute
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayRoute
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.WelcomeOnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.picker.AddToChecklistPickerScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallRoute
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.SubscriptionStatusScreen
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.ShareScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.UpdateFeedScreen
import com.antonchuraev.homesearchchecklist.mcp.McpScreen
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.activation.ActivationReminderSheet
import com.antonchuraev.homesearchchecklist.activation.WidgetPromoSheet
import com.antonchuraev.homesearchchecklist.feature.onboarding.isWidgetSupported
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext

/**
 * Default trigger time for the new-user activation reminder: TODAY at 20:00 local time, or — if it
 * is already past 20:00 — TOMORROW at 09:00 local time. Returns epoch millis. Kept top-level (pure)
 * + epoch-millis in/out so it is unit-testable without a Composable scope.
 */
internal fun nextActivationReminderTrigger(
    nowEpochMs: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val now = Instant.fromEpochMilliseconds(nowEpochMs)
    val nowLocal = now.toLocalDateTime(timeZone)
    val todayEvening = LocalDateTime(
        year = nowLocal.year,
        monthNumber = nowLocal.monthNumber,
        dayOfMonth = nowLocal.dayOfMonth,
        hour = 20,
        minute = 0,
    )
    val target = if (nowLocal < todayEvening) {
        todayEvening
    } else {
        val tomorrow = now.plus(1, DateTimeUnit.DAY, timeZone).toLocalDateTime(timeZone)
        LocalDateTime(
            year = tomorrow.year,
            monthNumber = tomorrow.monthNumber,
            dayOfMonth = tomorrow.dayOfMonth,
            hour = 9,
            minute = 0,
        )
    }
    return target.toInstant(timeZone).toEpochMilliseconds()
}

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
        // Collect the language DIRECTLY — do NOT use collectAsStateWithLifecycle(initialValue = …).
        // An initialValue placeholder caused an infinite Activity-recreate loop on Android 33+:
        // each (re)composition first sees the placeholder (System → empty LocaleList) and only then
        // the real value (e.g. English → [en]). Because persistAppLocale() → setApplicationLocales()
        // RECREATES the Activity, the placeholder and the real value kept flipping the system locale
        // [] ↔ [en], each flip triggering another recreate → it never settled. A raw Flow collect
        // emits the real DataStore value first, so persistAppLocale converges after at most one
        // (migration) recreate; distinctUntilChanged drops duplicate emissions within a session.
        LaunchedEffect(Unit) {
            languageRepository.language.distinctUntilChanged().collect { lang ->
                customAppLocale = lang.tag    // drives Compose string resources immediately (all platforms)
                persistAppLocale(lang.tag)    // Android 33+: durable system per-app locale (no-op elsewhere)
            }
        }
        // Two complementary layers protect the chosen language against the Google Play
        // Billing sheet resetting the process-global Locale.getDefault():
        //  • Android 33+ — persistAppLocale() hands the locale to the OS, which keeps our
        //    process locale correct across the whole billing round-trip → no flash at all.
        //  • Android <33 — no system per-app-locale API, so MainActivity.onResume re-asserts
        //    synchronously before the next frame. That kills the "stuck in device language
        //    until restart" bug, but a residual one-frame flash on sheet close remains
        //    (accepted, deferred: docs/todos/2026-06-15-locale-flash-on-billing-cmp-limitation.md).

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

        // Live AI-credits / premium sync: Firestore listener on users/{userId} keeps the
        // cached balance current and shared across web/Android (credits are spent server-side).
        val userCreditsSync: UserCreditsSync = koinInject()
        LaunchedEffect(Unit) { userCreditsSync.start() }

        var showWidgetInstruction by remember { mutableStateOf(false) }
        val createWeeklyChecklistUseCase: CreateWeeklyChecklistUseCase = koinInject()
        val weeklyDefaultName = stringResource(Res.string.weekly_checklist_default_name)
        LaunchedEffect(Unit) {
            navigator.events.collect { event ->
                when (event) {
                    AppNavEvent.ShowWidgetInstruction -> {
                        if (!showWidgetInstruction) {
                            showWidgetInstruction = true
                        }
                    }
                    AppNavEvent.CreateWeeklyChecklistRequested -> {
                        when (val result = createWeeklyChecklistUseCase(weeklyDefaultName)) {
                            is CreateWeeklyChecklistUseCase.Result.Created ->
                                navigator.navigateToChecklistDetail(result.checklistId, clearBackStack = true)
                            CreateWeeklyChecklistUseCase.Result.RequiresUpgrade ->
                                navigator.navigateToPaywall(source = "weekly_mode_limit")
                        }
                    }
                }
            }
        }

        // Emoji font is loaded once at the App root and provided via LocalEmojiFont so any
        // composable can render emoji on wasmJs (Skiko has no system emoji fallback there).
        // Android/iOS get FontFamily.Default (their system fonts cover emoji).
        val emojiFont = rememberEmojiFont()
        CompositionLocalProvider(LocalEmojiFont provides emojiFont) {
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

            // ── Gallery deep-link (app.gisti-ai.com/?g=create&template={slug}) ─────────
            // Platform entry points (wasmJs main.kt / Android MainActivity) parse the link and
            // push it into PendingGalleryDeepLink; we observe it here, create the checklist
            // AS-IS (no AI credit) and land on it. Unknown slug / fetch error → snackbar
            // (visible feedback — no silent skip). consume() prevents a recompose re-fire.
            //
            // Analytics lives HERE, not in the UseCase: the domain layer must not know about
            // analytics (same rule that keeps Compose Resources out of it). This is also the one
            // place that sees BOTH the outcome and the deep-link's utm, so every branch is
            // measurable — opened = created(gallery) + failed, with no silent third case.
            val pendingGalleryDeepLink: PendingGalleryDeepLink = koinInject()
            val createFromGalleryUseCase: CreateChecklistFromGalleryTemplateUseCase = koinInject()
            val analyticsTracker: AnalyticsTracker = koinInject()
            val galleryNotFoundMessage = stringResource(Res.string.gallery_deeplink_not_found)
            val galleryErrorMessage = stringResource(Res.string.gallery_deeplink_error)
            LaunchedEffect(Unit) {
                pendingGalleryDeepLink.pending.collect { link ->
                    if (link == null || link.slug.isBlank()) return@collect
                    // Top-of-funnel: fires BEFORE the fetch, so an arrival is counted even when the
                    // create then fails. Without it, "no traffic" and "all slugs broken" look identical.
                    val linkParams: Map<String, Any> =
                        mapOf(AnalyticsParams.TEMPLATE_SLUG to link.slug) + link.utm
                    analyticsTracker.event(AnalyticsEvents.Gallery.DEEPLINK_OPENED, linkParams)
                    when (val result = createFromGalleryUseCase(link.slug)) {
                        is CreateChecklistFromGalleryTemplateUseCase.Result.Created -> {
                            analyticsTracker.event(
                                AnalyticsEvents.Checklist.CREATED,
                                linkParams + mapOf(
                                    AnalyticsParams.SOURCE to ChecklistSource.GALLERY.wire,
                                    AnalyticsParams.CHECKLIST_ID to result.checklistId,
                                ),
                            )
                            navigator.navigateToChecklistDetail(result.checklistId, clearBackStack = true)
                        }
                        // A stale slug means the live gallery page and Firestore have drifted apart —
                        // a content bug that is otherwise invisible (the user just sees a snackbar).
                        CreateChecklistFromGalleryTemplateUseCase.Result.NotFound -> {
                            analyticsTracker.event(
                                AnalyticsEvents.Gallery.DEEPLINK_FAILED,
                                linkParams + mapOf(AnalyticsParams.REASON to "not_found"),
                            )
                            snackbarHostState.showSnackbar(galleryNotFoundMessage)
                        }
                        is CreateChecklistFromGalleryTemplateUseCase.Result.Error -> {
                            analyticsTracker.event(
                                AnalyticsEvents.Gallery.DEEPLINK_FAILED,
                                linkParams + mapOf(
                                    AnalyticsParams.REASON to "error",
                                    AnalyticsParams.ERROR_MESSAGE to (result.cause.message ?: "unknown"),
                                ),
                            )
                            snackbarHostState.showSnackbar(galleryErrorMessage)
                        }
                    }
                    pendingGalleryDeepLink.consume()
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
            // chatSheetOpen is now a WRITE-ONLY MIRROR of the per-screen dock's drag state: the screen
            // owns the AnchoredDraggableState and reports expand/collapse up via onChatExpandedChanged,
            // which flips this. App reads it only for analytics + context-seed + the banner label. The
            // screens never read it back (one-way), so there is no drag↔state feedback loop.
            var chatSheetOpen by rememberSaveable { mutableStateOf(false) }
            var chatSheetContextId by rememberSaveable { mutableStateOf<Long?>(null) }
            var chatSheetContextLabel by rememberSaveable { mutableStateOf<String?>(null) }
            // Bumped on every top-route change → each screen animates its dock back to Peek.
            var routeCollapseSignal by remember { mutableStateOf(0) }

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
                // Tell the mounted screens to collapse their dock to Peek, and clear the mirror.
                routeCollapseSignal++
                if (chatSheetOpen) {
                    chatSheetOpen = false
                }
            }

            // ── Navigation A/B arm (nav_v2_arm) ──────────────────────────────────────────────
            // The arm is held in ONE state holder owned by this composable — deliberately not a
            // CompositionLocal. Every v2 branch below therefore reads `navVariant` by name, so the
            // full set of gated call sites is greppable in this file and nothing outside it can
            // silently read the arm inside code the CONTROL arm also executes.
            //
            // currentArm() is non-suspending and returns CONTROL until the resolver has an answer,
            // so the very first frame always renders the safe arm.
            val navResolver: NavExperimentResolver = koinInject()
            var navVariant by remember { mutableStateOf(navResolver.currentArm()) }
            // LATCHED once a shell is mounted (set in the nav_shell_shown effect below).
            //
            // SplashViewModel awaits ensureResolved() before it navigates, so by the time a tab
            // route exists the arm is already in the resolver's per-process cache and the effect
            // below merely adopts it without suspending. The latch covers the paths that bypass
            // Splash's await (a deep link landing straight on a tab, a process restart into a saved
            // stack): without it a late-arriving arm would flip navVariant under a LIVE screen,
            // which swaps AdaptiveNavigationShell for V2NavigationShell in the `when` far below —
            // disposing and recreating the whole NavDisplay subtree, so the SaveableStateHolder is
            // rebuilt and the user loses scroll position and in-progress edits — and then re-roots
            // the back stack at Inbox mid-task.
            //
            // Once latched the resolver is no longer polled, which also stops the unbounded
            // DataStore + Remote Config round-trip that an unassigned arm would otherwise repeat on
            // every single top-route change, for the life of the install, in the CONTROL arm.
            var armLatched by remember { mutableStateOf(false) }
            // True once ensureResolved() has returned at least once in this process — NOT the same as
            // "an arm was assigned". It gates the shell mount below so no shell is built on the
            // pre-resolution CONTROL seed and then swapped. Seeded from the resolver's own cache, so a
            // launch that already resolved during Splash renders its shell on the very first frame.
            var armResolved by remember { mutableStateOf(navResolver.isArmAssigned()) }
            LaunchedEffect(currentTopRoute) {
                if (!armLatched) {
                    navVariant = navResolver.ensureResolved()
                    armResolved = true
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

            // Funnel: fire ai_chat_opened + screenView(CHAT) each time the inline dock opens.
            // Keyed on chatSheetOpen so it fires on the false→true transition only (the
            // ViewModel is a singleton — its init can't count per-open dock toggles).
            LaunchedEffect(chatSheetOpen) {
                if (chatSheetOpen) {
                    chatViewModel.sendIntent(ChatScreenIntent.OnChatOpened(source = "dock"))
                }
            }

            // Quick-action prefill seeds (resolved in Composable scope — stringResource is @Composable).
            val quickLinkPrefill = stringResource(Res.string.main_prompt_link_prefill)
            val quickRemindPrefill = stringResource(Res.string.main_prompt_remind_prefill)
            val quickPlanDayQuery = stringResource(Res.string.main_prompt_plan_day_query)
            // Seed for the "✨ Create with AI" prompt chip on MainScreen: prefill only (the user
            // completes the topic and taps Send). "Create a checklist for …" hits the Layer-1
            // CreateChecklist trigger, so a create-preview card is shown on send.
            val quickCreateWithAiPrefill = stringResource(Res.string.main_create_with_ai_prefill)

            // Checklist-detail contextual quick-action seeds (resolved here — @Composable scope).
            // WHATS_MISSING / SUMMARY / ADD_ITEMS are reasoning queries sent immediately so the
            // agent (anchored to the open checklist) answers in the dock; REMIND only prefills the
            // input so the user completes the reminder phrase.
            val quickWhatsMissingQuery = stringResource(Res.string.checklist_prompt_whats_missing_query)
            val quickGenerateIdeasQuery = stringResource(Res.string.checklist_prompt_generate_ideas_query)
            val quickSummaryQuery = stringResource(Res.string.checklist_prompt_summary_query)
            val quickAddItemsQuery = stringResource(Res.string.checklist_prompt_add_items_query)
            val quickChecklistRemindPrefill = stringResource(Res.string.checklist_prompt_remind_prefill)

            // Maps a home-screen prompt chip [GistiQuickAction] to its own chat flow.
            // All actions open the inline dock with NO checklist context (home = create-new).
            // - PHOTO / PDF: open dock + trigger the existing attachment picker; the attachment
            //   flow creates a checklist from the picked file (CreateChecklistFromAttachment).
            // - LINK / REMIND: open dock + prefill the input so the user completes the phrase.
            // - PLAN_DAY: open dock + prefill AND send immediately so the answer lands in the dock.
            val onQuickAction: (GistiQuickAction) -> Unit = { action ->
                chatViewModel.sendIntent(ChatScreenIntent.OnSetContextChecklist(null))
                chatSheetContextId = null
                chatSheetContextLabel = null
                chatSheetOpen = true
                when (action) {
                    // "✨ Create with AI": describe-a-checklist-to-the-AI flow. Prefill only (no
                    // attachment, no auto-send) — the dock is already opened above; the user
                    // completes the topic after "Create a checklist for …" and taps Send, which
                    // hits the Layer-1 CreateChecklist preview.
                    GistiQuickAction.CREATE_WITH_AI ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillInput(quickCreateWithAiPrefill))
                    GistiQuickAction.PHOTO ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPickAttachment(AttachmentSource.Image))
                    GistiQuickAction.PDF ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPickAttachment(AttachmentSource.Pdf))
                    GistiQuickAction.LINK ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillInput(quickLinkPrefill))
                    GistiQuickAction.REMIND ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillInput(quickRemindPrefill))
                    GistiQuickAction.PLAN_DAY ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillAndSend(quickPlanDayQuery))
                }
            }

            // ── New-user activation bundle (RC flag activation_bundle_v1) ──────────
            // Resolved once per composition. Read AFTER SplashViewModel awaited fetchAndActivate(),
            // so by the time MainScreen mounts the flag is fresh. Fail-open default ON.
            val remoteConfigProvider: RemoteConfigProvider = koinInject()
            val activationBundleEnabled = remember {
                remoteConfigProvider.getBoolean(
                    RemoteConfigKeys.ACTIVATION_BUNDLE_V1,
                    RemoteConfigDefaults.ACTIVATION_BUNDLE_V1,
                )
            }
            val activationAnalytics: AnalyticsTracker = koinInject()

            // Hero typed input / chip → the flagship "turn anything into a checklist with AI"
            // flow: route to the Analyze screen with the topic prefilled and autoAnalyze = true,
            // so Gemini GENERATES the checklist items and the user lands on the result preview
            // (AI-generated items → edit → Create). This is the reliable item-generating path
            // (generate_checklist CF via AnalyzeRepository); the old Layer-1 local CreateChecklist
            // parser produced a title-only EMPTY checklist (no items) and was the wrong logic.
            // FIRST_AI_CHECKLIST_CREATED still fires from AnalyzeResultPreviewViewModel on confirm
            // (fromActivation flag), so the activation funnel is unbroken.
            val onActivationGenerate: (String) -> Unit = { prompt ->
                navigator.navigateToAnalyzeScreen(
                    initialText = prompt,
                    fillDefault = false,
                    autoAnalyze = true,
                )
            }
            val onActivationChipTapped: (String, String) -> Unit = { chipKey, prompt ->
                activationAnalytics.event(
                    AnalyticsEvents.Activation.CHIP_TAPPED,
                    mapOf(AnalyticsParams.CHIP_KEY to chipKey),
                )
                onActivationGenerate(prompt)
            }

            // Maps a checklist-detail contextual prompt chip [GistiChecklistAction] to its chat flow,
            // anchored to a SPECIFIC checklist. Called from:
            //  - the chips ABOVE the input on ChecklistDetailScreen (the dock isn't open yet → this
            //    opens it with that checklist as context), and
            //  - the chips inside the already-open dock (context is re-seeded to the same id).
            //
            // IMPORTANT (context-vs-send ordering): OnSetContextChecklist is sent SYNCHRONOUSLY here,
            // immediately before OnPrefillAndSend. Both are sequential sendIntent() calls processed in
            // order by the ViewModel, so the agent request carries the checklist context. We do NOT
            // rely on the LaunchedEffect(chatSheetOpen, chatSheetContextId) below (it re-seeds context
            // too, but only AFTER recomposition — too late for the synchronous send in OnPrefillAndSend).
            val onChecklistQuickAction: (Long, String?, GistiChecklistAction) -> Unit = { checklistId, checklistName, action ->
                chatSheetContextId = checklistId
                chatSheetContextLabel = checklistName
                chatSheetOpen = true
                chatViewModel.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId))
                when (action) {
                    // Reasoning chips: forceAgent=true routes straight to the reasoning agent,
                    // bypassing Layer 1/2 which mis-classify these as FindItems → "Nothing matches"
                    // (Amplitude bug, 2026-06-02). The agent uses read_checklist on the open list.
                    GistiChecklistAction.WHATS_MISSING ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillAndSend(quickWhatsMissingQuery, forceAgent = true))
                    GistiChecklistAction.GENERATE_IDEAS ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillAndSend(quickGenerateIdeasQuery, forceAgent = true))
                    GistiChecklistAction.SUMMARY ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillAndSend(quickSummaryQuery, forceAgent = true))
                    GistiChecklistAction.ADD_ITEMS ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillAndSend(quickAddItemsQuery, forceAgent = true))
                    // REMIND only pre-fills the input (user picks the time), so no send/classify.
                    GistiChecklistAction.REMIND ->
                        chatViewModel.sendIntent(ChatScreenIntent.OnPrefillInput(quickChecklistRemindPrefill))
                }
            }

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
            val sm_dispatchCreatedFromAttachment = stringResource(Res.string.chat_dispatch_created_from_attachment)
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
            val sm_dispatchCompletedItemsRemoved = stringResource(Res.string.chat_dispatch_completed_items_removed)
            val sm_dispatchNoCompletedItems = stringResource(Res.string.chat_dispatch_no_completed_items)
            val sm_insufficientCredits = stringResource(Res.string.chat_insufficient_credits)
            val sm_completionError = stringResource(Res.string.chat_completion_error)
            // F1 connectivity-aware error replies. Keep in step with ChatRoute.kt's map.
            val sm_errorOffline = stringResource(Res.string.chat_error_offline)
            val sm_errorService = stringResource(Res.string.chat_error_service)
            val sm_errorTimeout = stringResource(Res.string.chat_error_timeout)
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
            // D1 reversible-action replies (Undo / move-to-list). A key missing from this map
            // renders as the raw key in the bubble — nothing fails, it just ships "chat_result_undone_add".
            val sm_resultUndoneAdd = stringResource(Res.string.chat_result_undone_add)
            val sm_resultUndoneComplete = stringResource(Res.string.chat_result_undone_complete)
            val sm_resultMovedTo = stringResource(Res.string.chat_result_moved_to)
            val sm_undoItemGone = stringResource(Res.string.chat_undo_item_gone)
            val sm_moveNoOtherLists = stringResource(Res.string.chat_move_no_other_lists)
            // D2 memory-of-choice disclosure. Same sync rule — also lives in ChatRoute.kt's map.
            val sm_resultRememberedList = stringResource(Res.string.chat_result_remembered_list)
            // Pre-existing gap found while wiring D2: emitted as a ShowAssistantMessage since D1 but
            // never added to either map, so every choice cancel printed the raw key into the bubble.
            val sm_choiceDismissed = stringResource(Res.string.chat_choice_dismissed_message)
            // The whole attach contour — both success replies and the entire error surface — plus the
            // blank-edit hint. Same gap: emitted, translated, never resolved, so the user read
            // "chat_dispatch_attached_one" where the confirmation should be. Guarded now by
            // ChatMessageKeyResolutionTest; keep both maps in step when adding to either.
            val sm_attachNoFiles = stringResource(Res.string.chat_attach_no_files)
            val sm_attachLimitReached = stringResource(Res.string.chat_attach_limit_reached)
            val sm_attachUnsupportedType = stringResource(Res.string.chat_attach_unsupported_type)
            val sm_attachAnalyzeEmpty = stringResource(Res.string.chat_attach_analyze_empty)
            val sm_attachAnalyzeFailed = stringResource(Res.string.chat_attach_analyze_failed)
            val sm_attachStoreFailed = stringResource(Res.string.chat_attach_store_failed)
            val sm_dispatchAttachedOne = stringResource(Res.string.chat_dispatch_attached_one)
            val sm_dispatchAttachedMany = stringResource(Res.string.chat_dispatch_attached_many)
            val sm_choiceEditEmptyHint = stringResource(Res.string.chat_choice_edit_empty_hint)

            // NOT remember()-ed. `stringResource` resolves asynchronously in Compose Multiplatform
            // and returns "" for the first frames; a remember() keyed on a SUBSET of these strings
            // caches the map while the rest are still empty, and those entries stay empty forever —
            // the reply then renders as an empty assistant bubble. (Found 2026-07-15 on :9090: the
            // dock keyed on 2 of ~40 strings and shipped a blank bubble for every dispatch result.)
            // Rebuilding a 40-entry map per recomposition is noise next to the canvas draw.
            val sheetMessages = run {
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
                    "chat_dispatch_created_from_attachment" to sm_dispatchCreatedFromAttachment,
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
                    "chat_dispatch_completed_items_removed" to sm_dispatchCompletedItemsRemoved,
                    "chat_dispatch_no_completed_items" to sm_dispatchNoCompletedItems,
                    "chat_insufficient_credits" to sm_insufficientCredits,
                    "chat_completion_error" to sm_completionError,
                    "chat_error_offline" to sm_errorOffline,
                    "chat_error_service" to sm_errorService,
                    "chat_error_timeout" to sm_errorTimeout,
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
                    "chat_result_undone_add" to sm_resultUndoneAdd,
                    "chat_result_undone_complete" to sm_resultUndoneComplete,
                    "chat_result_moved_to" to sm_resultMovedTo,
                    "chat_undo_item_gone" to sm_undoItemGone,
                    "chat_move_no_other_lists" to sm_moveNoOtherLists,
                    "chat_result_remembered_list" to sm_resultRememberedList,
                    "chat_choice_dismissed_message" to sm_choiceDismissed,
                    "chat_attach_no_files" to sm_attachNoFiles,
                    "chat_attach_limit_reached" to sm_attachLimitReached,
                    "chat_attach_unsupported_type" to sm_attachUnsupportedType,
                    "chat_attach_analyze_empty" to sm_attachAnalyzeEmpty,
                    "chat_attach_analyze_failed" to sm_attachAnalyzeFailed,
                    "chat_attach_store_failed" to sm_attachStoreFailed,
                    "chat_dispatch_attached_one" to sm_dispatchAttachedOne,
                    "chat_dispatch_attached_many" to sm_dispatchAttachedMany,
                    "chat_choice_edit_empty_hint" to sm_choiceEditEmptyHint,
                )
            }

            // Sheet side-effect handler — mirrors ChatRoute logic exactly.
            // Audio recorder for the inline dock — declared BEFORE the SideEffect collector
            // so the RequestRecordAudioPermission handler below can call .start() directly
            // (mirrors ChatRoute). onResult/onError feed OnVoiceRecordingStopped back to the VM.
            val sheetAudioRecorder = rememberAudioRecorderLauncher(
                onResult = { result ->
                    chatViewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(
                        recordingPath = result?.filePath,
                        mimeType = result?.mimeType ?: "audio/m4a",
                    ))
                },
                onError = { chatViewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(recordingPath = null)) },
            )

            // rememberUpdatedState, NOT the map directly: LaunchedEffect captures its lambda ONCE
            // (key = chatViewModel), and Compose Resources resolve asynchronously — on the first
            // frame every stringResource is still "". A directly-captured map freezes those empty
            // values for the lifetime of the collector, so every reply rendered as a blank bubble
            // while the strings were long since loaded. Same stale-closure trap as the wasmJs
            // FilePicker callbacks (project memory: filepicker-rememberupdatedstate-closure-trap).
            val currentSheetMessages by rememberUpdatedState(sheetMessages)
            LaunchedEffect(chatViewModel) {
                chatViewModel.sideEffect.collect { effect ->
                    when (effect) {
                        is ChatScreenSideEffect.ShowSnackbar -> {
                            val text = currentSheetMessages[effect.messageKey] ?: effect.messageKey
                            snackbarHostState.showSnackbar(text)
                        }
                        is ChatScreenSideEffect.ShowAssistantMessage -> {
                            val template = currentSheetMessages[effect.messageKey] ?: effect.messageKey
                            val resolved = applyFormatArgsLocal(template, effect.args)
                            chatViewModel.sendIntent(
                                ChatScreenIntent.AppendAssistantMessage(
                                    text = resolved,
                                    linkedChecklistId = effect.linkedChecklistId,
                                    askAiForText = effect.askAiForText,
                                    paywallCtaCredits = effect.paywallCtaCredits,
                                    retryText = effect.retryText,
                                    routedLayer = effect.routedLayer,
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
                        is ChatScreenSideEffect.NavigateToPaywall -> {
                            // Forward the effect's own tag — the old hardcoded "chat_sheet_credits"
                            // described the credits chip and would mislabel every out-of-credits
                            // CTA tap as an unprompted chip tap.
                            navigator.navigateToPaywall(source = effect.source)
                        }
                        ChatScreenSideEffect.RequestRecordAudioPermission -> {
                            // Mic tapped on a bottom bar (MainScreen / ChecklistDetail) opens the
                            // dock and sends OnVoiceRecordingStarted; the VM emits this side-effect.
                            // AudioRecorderLauncher handles the permission request internally —
                            // starting it here requests permission if needed; on denial onError
                            // fires → OnVoiceRecordingStopped(null) → cancelled snackbar.
                            sheetAudioRecorder.start()
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

            // Auto-focus on expand is now owned by GistiExpandableDockContent (it focuses this
            // requester when the dock settles to Expanded — it has the exact drag state). App only
            // creates the shared requester and hands it to the input row.
            val chatInputFocusRequester = remember { FocusRequester() }
            // Separate requester for the FULL overlay's own input node (the dock input still exists
            // beneath the opaque overlay — a shared requester would let the two ChatInputRows fight
            // over focus). The overlay clears focus on open/close instead of auto-raising the keyboard.
            val chatFullInputFocusRequester = remember { FocusRequester() }

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
            // sheetAudioRecorder is declared above the SideEffect collector (needed by the
            // RequestRecordAudioPermission handler) — do not re-declare it here.
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

            // ── v2 shell computations (parallel to selectedDestination / showShell above) ─────
            // Written as SIBLINGS of the three control-arm blocks rather than as branches inside
            // them: the control-arm blocks stay textually identical to their pre-experiment form,
            // which is the cheapest possible proof that the baseline did not move. Everything below
            // is inert while navVariant == CONTROL (showV2Shell is false and nothing reads the rest).

            // True while ANY v2 tab route is anywhere in the stack — mirrors showShell's .any {} so the
            // shell stays mounted (and the tab state alive) under a pushed detail screen.
            val v2AnyTabInStack = navigator.backStack.any { key ->
                key is AppNavRoute.Inbox || key is AppNavRoute.Calendar ||
                key is AppNavRoute.Main || key is AppNavRoute.Overview
            }

            // findLast, not last: on Expanded two-pane a ChecklistDetail sits on top while Main still
            // renders in the list pane, and the bar must keep pointing at the tab underneath.
            val v2SelectedTab = remember(navigator.backStack.toList()) {
                val topTab = navigator.backStack.findLast { key ->
                    key is AppNavRoute.Inbox || key is AppNavRoute.Calendar ||
                    key is AppNavRoute.Main || key is AppNavRoute.Overview
                }
                when (topTab) {
                    is AppNavRoute.Calendar -> V2Destination.Calendar
                    is AppNavRoute.Main -> V2Destination.Projects
                    is AppNavRoute.Overview -> V2Destination.Overview
                    else -> V2Destination.Inbox
                }
            }

            // Chrome visibility is decided by the TOP entry (unlike showV2Shell): the bar and the FAB
            // must not float over ChecklistDetail / AiChat / Settings pushed on top of a tab.
            val v2BarVisible = navigator.backStack.lastOrNull().let { top ->
                top is AppNavRoute.Inbox || top is AppNavRoute.Calendar ||
                top is AppNavRoute.Main || top is AppNavRoute.Overview
            }

            val showV2Shell = navVariant == NavVariant.V2 && v2AnyTabInStack

            // Only Compact has a bottom bar + FAB to clear; the rail and the permanent drawer sit
            // beside the content, so a non-zero inset there would just be dead space.
            //
            // The window-size read is nested INSIDE the v2 branch on purpose. App.kt read no
            // window/configuration state at all before this experiment, and rememberAppWindowSizeClass()
            // is not free: on Android it reads LocalConfiguration.current, so hoisting it would make
            // this whole composable recompose on every configuration change; on wasmJs it registers a
            // resize listener, so every browser resize would recompose the entire App body
            // (sceneStrategy, entryProvider, every lambda) — a measurable behaviour change in the
            // BASELINE arm, which must stay untouched. Conditional @Composable calls are perfectly
            // legal: the runtime inserts and removes the group. The arm is latched at the first
            // mounted shell, so the branch cannot oscillate either.
            // ONE value for every Compact v2 tab: the FAB band. The shell renders its bar outside the
            // content slot AND consumes WindowInsets.navigationBars while the bar is visible, so a
            // hosted screen never has to reserve either the bar or the system strip. An earlier
            // second constant that also reserved the bar height left a blank ~88dp band on Projects.
            val v2IsCompact =
                navVariant == NavVariant.V2 &&
                    rememberAppWindowSizeClass() == AppWindowSizeClass.Compact
            val v2FabBandPadding = if (v2IsCompact) V2ShellMetrics.FabBandPadding else 0.dp

            // Re-root the stack at Inbox once per process, the first time a tab route appears.
            // The control arm never executes this (guarded on navVariant).
            //
            // Plain `remember`, deliberately NOT rememberSaveable: the flag guards the back stack of
            // the AppNavigator SINGLETON, which is re-created (and re-seeded with Splash) on process
            // death. A saved `true` would outlive the stack it describes and skip the rewrite on the
            // next cold start, landing the user on Projects instead of Inbox. Plain remember resets
            // in lockstep with the navigator. A recomposition-only reset (Activity recreate, where
            // the navigator DOES survive) is harmless — the `none { Inbox }` guard makes a re-run a
            // no-op.
            var v2RootApplied by remember { mutableStateOf(false) }
            LaunchedEffect(navVariant, v2AnyTabInStack) {
                if (navVariant == NavVariant.V2 && v2AnyTabInStack && !v2RootApplied) {
                    v2RootApplied = true
                    if (navigator.backStack.none { it is AppNavRoute.Inbox }) {
                        val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                        // Never clear() then add(): NavDisplay requires a non-empty stack at ALL
                        // times, not just at first composition (the same reason
                        // AppNavigatorImpl.replaceStack sets [0] first and only then trims).
                        if (mainIdx == 0) {
                            navigator.backStack[0] = AppNavRoute.Inbox
                        } else {
                            navigator.backStack.add(0, AppNavRoute.Inbox)
                        }
                    }
                }
            }

            val v2OnNavigate: (String) -> Unit = { dest ->
                // POP to the Inbox root, then push the tab — never a bare push. Every top-level nav
                // helper in this app is pushLaunchSingleTop, so a bottom bar wired to them would grow
                // the stack on each tap and Android BACK would walk the tab history instead of
                // returning home. This is the Main branch's pop-to-existing idiom generalised to all
                // four tabs, and it keeps each tab exactly one entry deep.
                val inboxIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Inbox }
                if (inboxIdx < 0) {
                    // The v2 root was displaced — navigateToChecklistDetail(clearBackStack = true)
                    // (gallery deep link, weekly-checklist create) rebuilds the stack around Main via
                    // popToMainThenPush. Re-root in place rather than trimming to a foreign [0], which
                    // would leave "Inbox" selected while Main is on screen.
                    navigator.backStack[0] = AppNavRoute.Inbox
                    while (navigator.backStack.size > 1) {
                        navigator.backStack.removeAt(navigator.backStack.size - 1)
                    }
                } else {
                    while (navigator.backStack.size > inboxIdx + 1) {
                        navigator.backStack.removeAt(navigator.backStack.size - 1)
                    }
                }
                when (dest) {
                    V2Destination.Inbox -> Unit   // the root itself — popping above was the whole job
                    V2Destination.Calendar -> navigator.backStack.add(AppNavRoute.Calendar)
                    V2Destination.Projects -> navigator.backStack.add(AppNavRoute.Main)
                    V2Destination.Overview -> navigator.backStack.add(AppNavRoute.Overview)
                }
                analyticsTracker.event(
                    AnalyticsEvents.Nav.TAB_SELECTED,
                    mapOf(AnalyticsParams.TAB to dest),
                )
            }

            // Arm-exposure denominator. Fired ONCE per process in BOTH arms — a v2-only emit would
            // leave the arms incomparable (no control baseline to divide by).
            //
            // ensureResolved() is awaited HERE rather than reading navVariant directly, because
            // navVariant seeds from the non-suspending currentArm() (CONTROL until resolution lands).
            // Stamping the denominator from that seed would file a v2 user under variant="control"
            // and then latch shellEventSent, so the record could never be corrected — inflating
            // control and deflating v2, exactly the bias this event exists to rule out. The call
            // short-circuits on the arm SplashViewModel already resolved, so it costs nothing.
            //
            // This is also where the arm is LATCHED: from the first mounted shell onwards the value
            // is frozen for the process, so no later resolution can swap the shell under the user.
            var shellEventSent by remember { mutableStateOf(false) }
            LaunchedEffect(showShell, showV2Shell) {
                if (!shellEventSent && (showShell || showV2Shell)) {
                    val arm = navResolver.ensureResolved()
                    navVariant = arm
                    armLatched = true
                    shellEventSent = true
                    // Three values, not two. A user Remote Config has not assigned an arm to renders
                    // the CONTROL shell as a fail-safe but is NOT in the experiment — they carry no
                    // nav_arm user property by design. Filing them under "control" would pad the
                    // baseline's exposure denominator with non-participants (the rc-activation gap is
                    // ~35% of new users here), deflating every rate the treatment is measured against.
                    analyticsTracker.event(
                        AnalyticsEvents.Nav.SHELL_SHOWN,
                        mapOf(
                            AnalyticsParams.VARIANT to when {
                                !navResolver.isArmAssigned() -> "unassigned"
                                arm == NavVariant.V2 -> "v2"
                                else -> "control"
                            }
                        ),
                    )
                }
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
                    DrawerDestination.Mcp -> {
                        // Push as a detail screen (back-arrow return). No dedicated AppNavigator
                        // method — mutate the exposed backStack directly with a launchSingleTop
                        // guard, avoiding an interface change + its test-fakes ripple.
                        if (navigator.backStack.lastOrNull() != AppNavRoute.Mcp) {
                            navigator.backStack.add(AppNavRoute.Mcp)
                        }
                    }
                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                    DrawerDestination.Settings -> navigator.navigateToSettings()
                }
            }

            // ── Expandable in-place chat dock content (Approach A — morph, NOT slide-on-top) ──
            // The dock lives PER-SCREEN inside each screen's GistiGlassChatDock (so the Haze backdrop
            // blur + per-screen chips + two-pane scoping all work). App.kt only supplies this content
            // slot — it captures the singleton ChatViewModel and all global wiring (recorder, pickers,
            // focusRequester) declared above. The screen passes its own (expanded, onExpand, onCollapse)
            // so a tablet two-pane never shares a draggable state across panes (it's just a Boolean).
            val resolvedContextLabel = chatSheetContextLabel?.let { name ->
                chatDockAskAboutFmt.replace("%1\$s", name)
            }
            val lastAssistantMessage = remember(chatUiState.messages) {
                chatUiState.messages.lastOrNull { it.role == ChatRole.Assistant }
            }
            // hasLastAnswer drives the expanded answer-frame vs empty-greeting switch (order mirrors
            // the lastAnswerContent when-branches: pending choice / typing / last assistant bubble).
            val hasLastAnswer = chatUiState.pendingChoice != null ||
                chatUiState.isProcessing ||
                lastAssistantMessage != null

            val chatDockContent: @Composable (AnchoredDraggableState<DockAnchor>, String, Dp, @Composable () -> Unit, ChatDockItemCreateOverride?, () -> Unit) -> Unit =
                { dockState, peekPlaceholder, dockAvailableDp, chips, itemCreateOverride, onOpenFull ->
                    GistiExpandableDockContent(
                        state = dockState,
                        // Item-create mode hides the chat answer/greeting frame (it shows the item-create
                        // chips in the empty-state slot instead) and keeps the dock pinned open for rapid
                        // multi-add. Default (null override) → unchanged AI-chat behaviour.
                        hasLastAnswer = if (itemCreateOverride != null) false else hasLastAnswer,
                        // Answer cap height from the host (status bar → keyboard top); Unspecified = no kb.
                        dockAvailableDp = dockAvailableDp,
                        // ↗ (and drag-up over-scroll at Expanded) → open the in-place FULL overlay
                        // (the per-screen third "floor"), NOT a navigation to the full chat route.
                        onExpandFull = onOpenFull,
                        onHelpClick = { chatPanelHelpSheetOpen = true },
                        contextLabel = resolvedContextLabel,
                        // Chips hosted INSIDE the morph. Chat: the contextual peek chips (fade as the dock
                        // expands). Item-create: the reminder/property chips render in this SAME row (not in
                        // the answer frame) and are PINNED (chipsPinned below) so they stay visible with the
                        // keyboard up AND the create⇄chat peek is the same height → Back swaps the chip
                        // content in place with no shrink/grow.
                        chipsContent = if (itemCreateOverride != null) {
                            { itemCreateOverride.chips() }
                        } else {
                            chips
                        },
                        chipsPinned = itemCreateOverride != null,
                        // Focused by the morph when the dock settles to Expanded (raise the keyboard).
                        inputFocusRequester = chatInputFocusRequester,
                        // On blur (keyboard dismissed) the dock collapses to Peek only when blank.
                        inputBlank = chatUiState.inputText.isBlank(),
                        // Keep the dock open while the answer frame has content (in-flight turn /
                        // pending choice / last answer). Sending disables+blurs the input (the old
                        // auto-collapse trigger); without this the dock slammed shut to Peek mid-turn
                        // and hid the ChatTypingIndicator + answer. Grabber drag-down still collapses.
                        keepExpanded = if (itemCreateOverride != null) true else hasLastAnswer,
                        // A pending choice block (prompt + chips + escape) is taller than a one-line
                        // answer; raise the frame cap so its escape/cancel chip isn't clipped.
                        // D2 adds typed object rows inside the bubble (item + list + time), so a
                        // question that carries them needs more room again. Safe: GistiInlineChatPanel
                        // clamps this against the real dock space, so a keyboard-up dock just scrolls.
                        answerMaxHeight = when {
                            chatUiState.pendingChoice?.hasObjectRows == true -> 440.dp
                            chatUiState.pendingChoice != null -> 360.dp
                            else -> 210.dp
                        },
                        // Item-create shows only a short chip row — wrap it (min 0) so there's no empty
                        // gap between the chips and the pinned input; chat keeps the 125dp comfortable
                        // body. INSTANT (not animated): animating this min height sweeps panelFullPx every
                        // frame during the open animation → updateAnchors churns → the AnchoredDraggable
                        // resettles to Peek → the dock collapses → item-create exits (the "morph plays then
                        // snaps back to chat" bug). The dock's own expand animation already masks the jump.
                        answerMinHeight = if (itemCreateOverride != null) 0.dp else 125.dp,
                        lastAnswerContent = {
                            // Fixed-height frame: scroll a long answer inside instead of growing the
                            // dock. Priority mirrors ChatContent so the SAME confirm cards render here.
                            //
                            // The anchor depends on WHAT the frame holds:
                            //  - A QUESTION (non-blank prompt) → anchor TOP. Its object rows and the
                            //    question itself sit above the chips, so bottom-anchoring scrolls the
                            //    subject out of the cap and leaves bare chips — "cancel WHAT?", which
                            //    is the exact defect the object rows exist to fix.
                            //  - Everything else (a plain answer, or the D1 post-action offer whose
                            //    prompt is blank and whose result bubble renders inside this frame)
                            //    → anchor BOTTOM: the newest content and the input are what matter.
                            // Keyed on maxValue so it re-anchors as content grows (new message /
                            // streaming) and on first show / expand.
                            val answerScroll = rememberScrollState()
                            val anchorTop = chatUiState.pendingChoice?.choice?.prompt?.isNotBlank() == true
                            LaunchedEffect(answerScroll.maxValue, anchorTop) {
                                if (anchorTop) {
                                    answerScroll.animateScrollTo(0)
                                } else {
                                    answerScroll.animateScrollTo(answerScroll.maxValue)
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(answerScroll)
                                    .padding(
                                        horizontal = AppDimens.ScreenPaddingHorizontal,
                                        vertical = AppDimens.SpacingMd,
                                    ),
                            ) {
                                when {
                                    chatUiState.pendingChoice != null -> {
                                        // A BLANK prompt means the outcome was already said in its own
                                        // assistant message (D1 post-action chips: Undo / move-to-list).
                                        // The full-screen chat shows both — message in the list, chips
                                        // under it — but this peek frame renders ONE branch, so the chips
                                        // arrived context-free: "Undo" WHAT? Render the message above them.
                                        val choicePrompt = chatUiState.pendingChoice!!.choice.prompt
                                        if (choicePrompt.isBlank() && lastAssistantMessage != null) {
                                            ChatMessageBubble(
                                                message = lastAssistantMessage,
                                                onFeedbackClick = { msg ->
                                                    chatViewModel.sendIntent(ChatScreenIntent.OnFeedbackOpen(msg))
                                                },
                                                onThumbUpClick = { msg ->
                                                    chatViewModel.sendIntent(ChatScreenIntent.OnThumbUpClick(msg))
                                                },
                                                onOpenChecklist = lastAssistantMessage.linkedChecklistId?.let { id ->
                                                    {
                                                        chatSheetOpen = false
                                                        navigator.navigateToChecklistDetail(id)
                                                    }
                                                },
                                                onRetry = lastAssistantMessage.retryText?.let { text ->
                                                    { chatViewModel.sendIntent(ChatScreenIntent.OnRetryClick(text)) }
                                                },
                                                showSenderLabel = true,
                                            )
                                            Spacer(Modifier.height(AppDimens.SpacingSm))
                                        }
                                        AiChoiceResponse(
                                            pending = chatUiState.pendingChoice!!,
                                            onSelect = { id -> chatViewModel.sendIntent(ChatScreenIntent.OnChoiceSelected(id)) },
                                            onEditChange = { chatViewModel.sendIntent(ChatScreenIntent.OnChoiceEditChange(it)) },
                                            onEditConfirm = { chatViewModel.sendIntent(ChatScreenIntent.OnChoiceEditConfirmed) },
                                            // The dock: fewer preview rows fit under the frame cap.
                                            compact = true,
                                            onMemoryToggle = { enabled ->
                                                chatViewModel.sendIntent(ChatScreenIntent.OnChoiceMemoryToggle(enabled))
                                            },
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
                                            onPaywallCta = lastAssistantMessage.paywallCtaCredits?.let {
                                                { chatViewModel.sendIntent(ChatScreenIntent.OnPaywallCtaClick) }
                                            },
                                            onOpenChecklist = lastAssistantMessage.linkedChecklistId?.let { id ->
                                                {
                                                    chatSheetOpen = false
                                                    navigator.navigateToChecklistDetail(id)
                                                }
                                            },
                                            onRetry = lastAssistantMessage.retryText?.let { text ->
                                                { chatViewModel.sendIntent(ChatScreenIntent.OnRetryClick(text)) }
                                            },
                                            showSenderLabel = true,
                                        )
                                    }
                                }
                            }
                        },
                        emptyStateContent = {
                            // Chat: the greeting. Item-create: EMPTY — the reminder/property chips moved to
                            // the pinned peek-chip row (chipsContent) so the create⇄chat peek is the SAME
                            // height (a chip row is always present) and Back swaps the chips in place with no
                            // shrink/grow. The answer frame just stays empty (height 0 via answerMinHeight=0)
                            // in item-create.
                            if (itemCreateOverride == null) {
                                // Localized greeting + a prompt-chip row whose taps PREFILL the dock
                                // composer (OnPrefillInput) — never send. The peek chips fade on
                                // expand, so there is no double row.
                                ChatEmptyState(
                                    compact = true,
                                    onPrefill = { chatViewModel.sendIntent(ChatScreenIntent.OnPrefillInput(it)) },
                                    modifier = Modifier.padding(vertical = AppDimens.SpacingMd),
                                )
                            }
                        },
                        inputContent = { onInputFocusChanged ->
                            // ── Unified input node (chat ⇄ item-create) ──────────────────────────────
                            // ONE ChatInputRow node spans BOTH modes (mode-derived props) so the field
                            // is the SAME composable across the morph: when the dock switches into
                            // item-create the row's help/attach buttons animate OUT in place (their
                            // AnimatedVisibility runs only because the node persists) — a smooth morph of
                            // the chat input INTO the create input, not a hard swap of two separate nodes.
                            // A single shared focusRequester also stays on ONE field (no two-field focus
                            // tug-of-war that would drop the keyboard mid-transition).
                            // The input is rendered unless a CHAT choice block is pending (then chips are
                            // the only interaction); item-create never has a pending choice.
                            val ic = itemCreateOverride
                            // Force-focus when ENTERING item-create from an ALREADY-Expanded chat dock:
                            // the dock's targetValue doesn't change, so its targetValue-driven auto-focus
                            // never fires and the keyboard would drop on the prop change. Keyed on the
                            // mode transition so it runs once per entry (and never on exit).
                            LaunchedEffect(ic != null) {
                                if (ic != null) runCatching { chatInputFocusRequester.requestFocus() }
                            }
                            // A pending QUESTION hides the input (chips are the only interaction).
                            // A post-action offer (Undo / move) is not a question and has no escape
                            // chip — hiding the input there trapped the dock: the offer stayed up and
                            // only Back got out, collapsing the chat. Keep typing enabled for it.
                            if (ic != null ||
                                chatUiState.pendingChoice == null ||
                                chatUiState.pendingChoice!!.isPostAction
                            ) {
                              Column {
                                // Item-create only: pending-attachment preview strip ABOVE the input.
                                // In chat mode (ic == null) the strip lambda is absent → nothing renders,
                                // and the Column wraps a single fillMaxWidth child (layout-neutral).
                                if (ic != null) ic.attachmentStrip()
                                ChatInputRow(
                                    text = ic?.text ?: chatUiState.inputText,
                                    onTextChange = ic?.onTextChange
                                        ?: { chatViewModel.sendIntent(ChatScreenIntent.OnInputChange(it)) },
                                    onSend = ic?.onSend
                                        ?: { chatViewModel.sendIntent(ChatScreenIntent.OnSendClick) },
                                    // Item-create opens ITS picker (files staged on the new item); chat
                                    // opens the chat attachment sheet.
                                    onAttachFileClick = ic?.onAttachClick ?: { chatSheetAttachmentSheet = true },
                                    // BUG #1 FIX: send OnVoiceRecordingStarted (flips isRecording +
                                    // emits RequestRecordAudioPermission) like the full ChatScreen does.
                                    // The OLD inline panel only called sheetAudioRecorder.start() with NO
                                    // intent, so isRecording never flipped and the press-hold mic was a
                                    // no-op in the dock. Now the SAME press-hold works in the peek.
                                    // (In item-create the mic/attach are hidden and Send replaces them, so
                                    // these chat handlers are simply never reached there.)
                                    onVoiceRecordingStarted = {
                                        chatViewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStarted)
                                        sheetAudioRecorder.start()
                                    },
                                    onVoiceRecordingStopped = { sheetAudioRecorder.stop() },
                                    onVoiceRecordingCancelled = { sheetAudioRecorder.cancel() },
                                    onHelpClick = { chatPanelHelpSheetOpen = true },
                                    // Item-create now supports attachments: drive the placeholder hint
                                    // from the staged-files flag; chat keeps its own pending-attachment flag.
                                    hasAttachments = if (ic != null) ic.hasAttachments else chatUiState.pendingAttachments.isNotEmpty(),
                                    isEnabled = ic != null || !chatUiState.isProcessing,
                                    canSend = ic?.canSend ?: chatUiState.canSend,
                                    isRecording = ic == null && chatUiState.isRecording,
                                    isTranscribing = ic == null && chatUiState.isTranscribing,
                                    onDragCancelChanged = { chatSheetDragCancel = it },
                                    focusRequester = chatInputFocusRequester,
                                    // Report focus to the morph: focus → expand + lock the dock open
                                    // while the keyboard is up; blur → release (collapse if blank).
                                    onFocusChanged = onInputFocusChanged,
                                    // Contextual peek placeholder — "I want to…" (item-create) /
                                    // "Ask Gisti…" / "Ask about <name>…" (chat) — supplied per-screen.
                                    placeholderOverride = peekPlaceholder,
                                    // Drives the in-row help/attach morph: true → stripped Send-only row.
                                    simpleSendOnly = ic != null,
                                    // Item-create keeps the attach button visible (help + mic stay hidden)
                                    // so files can be staged on the new item.
                                    attachVisibleInSimpleMode = ic != null,
                                )
                              }
                            }
                        },
                        recordingOverlay = {
                            ChatRecordingOverlay(
                                isRecording = chatUiState.isRecording,
                                durationMs = chatSheetRecordingMs,
                                isDragCancel = chatSheetDragCancel,
                            )
                        },
                    )
                }

            // ── FULL chat overlay content (the expanded dock's third "floor") ──────────────────────
            // Rendered by each screen ABOVE its dock (so it covers the top bar) with a per-screen
            // DockFullExpandState. Same singleton ChatViewModel wiring as the dock, but chat-only (no
            // item-create) and with its OWN input focus requester. The full scrollable history reuses
            // ChatMessageList (extracted from ChatScreen) so it matches the full-screen chat exactly.
            val chatFullContent: @Composable (DockFullExpandState, Int) -> Unit = { fullState, dockHeightPx ->
                val fullListState = rememberLazyListState()
                val fullFocusManager = LocalFocusManager.current
                val fullTotalItemCount = chatUiState.messages.size +
                    (if (chatUiState.pendingChoice != null) 1 else 0) +
                    (if (chatUiState.isProcessing && chatUiState.pendingChoice == null) 1 else 0)
                // Clear focus on open AND close so the keyboard never lingers over a hidden input node
                // (the dock input beneath, or the full input after collapse). User taps to type.
                LaunchedEffect(fullState.isOpen) { fullFocusManager.clearFocus() }
                GistiFullChatOverlay(
                    state = fullState,
                    dockStartHeightPx = dockHeightPx,
                    onCollapse = { scope.launch { fullState.close() } },
                    historyContent = {
                        // ChatBody (not ChatMessageList directly) so the FULL overlay shows the SAME
                        // localized empty-state (greeting + suggestion cards that prefill the composer)
                        // as the full-screen chat while the conversation is empty.
                        ChatBody(
                            state = chatUiState,
                            onIntent = { chatViewModel.sendIntent(it) },
                            listState = fullListState,
                            showTodayDivider = false,
                            totalItemCount = fullTotalItemCount,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    inputContent = {
                        // Hidden while a pending choice block is shown (chips are the only interaction),
                        // mirroring the full ChatScreen.
                        if (chatUiState.pendingChoice == null) {
                            ChatInputRow(
                                text = chatUiState.inputText,
                                onTextChange = { chatViewModel.sendIntent(ChatScreenIntent.OnInputChange(it)) },
                                onSend = { chatViewModel.sendIntent(ChatScreenIntent.OnSendClick) },
                                onAttachFileClick = { chatSheetAttachmentSheet = true },
                                onVoiceRecordingStarted = {
                                    chatViewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStarted)
                                    sheetAudioRecorder.start()
                                },
                                onVoiceRecordingStopped = { sheetAudioRecorder.stop() },
                                onVoiceRecordingCancelled = { sheetAudioRecorder.cancel() },
                                onHelpClick = { chatPanelHelpSheetOpen = true },
                                hasAttachments = chatUiState.pendingAttachments.isNotEmpty(),
                                isEnabled = !chatUiState.isProcessing,
                                canSend = chatUiState.canSend,
                                isRecording = chatUiState.isRecording,
                                isTranscribing = chatUiState.isTranscribing,
                                onDragCancelChanged = { chatSheetDragCancel = it },
                                focusRequester = chatFullInputFocusRequester,
                            )
                        }
                    },
                    recordingOverlay = {
                        ChatRecordingOverlay(
                            isRecording = chatUiState.isRecording,
                            durationMs = chatSheetRecordingMs,
                            isDragCancel = chatSheetDragCancel,
                        )
                    },
                )
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

                    entry<AppNavRoute.WelcomeOnboarding> {
                        WelcomeOnboardingScreen()
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
                                // Continuous-drag dock content (MainScreen owns its own drag state).
                                // v2 removes the bottom chat dock everywhere — gated HERE at the call
                                // site rather than at the chatDockContent declaration, so the diff is
                                // two lines and the control arm's slot is provably unchanged. Both
                                // slots are nullable and every dock-dependent branch inside MainScreen
                                // already null-degrades (dockShown / showDock / contentBottomPadding),
                                // so passing null needs no structural edit to the screen.
                                chatDockContent = if (navVariant == NavVariant.V2) null else chatDockContent,
                                // FULL overlay content (MainScreen owns its own per-screen full state).
                                chatFullContent = if (navVariant == NavVariant.V2) null else chatFullContent,
                                // When MainScreen's dock opens/closes → seed the home (null) chat context
                                // + fire the open analytics (chatSheetOpen mirror drives those effects).
                                onChatExpandedChanged = { expandedNow ->
                                    if (expandedNow) onOpenChatSheet(null, null) else chatSheetOpen = false
                                },
                                chatInputBlank = chatUiState.inputText.isBlank(),
                                routeCollapseSignal = routeCollapseSignal,
                                // Each prompt chip drives its own chat flow (photo/pdf picker,
                                // link/remind prefill, plan-day prefill+send) via the singleton
                                // ChatViewModel + inline dock. See onQuickAction above.
                                onQuickAction = onQuickAction,
                                // v2 only: the dock that used to host these six chips is gone, so the
                                // chips move to their own row and the host must ALSO navigate — plain
                                // onQuickAction just prefills the singleton chat ViewModel and relies
                                // on a dock being on screen, so without the navigate every chip would
                                // be a silent no-op. null in control leaves that arm's tree untouched.
                                onInlineQuickAction = if (navVariant == NavVariant.V2) {
                                    { action ->
                                        onQuickAction(action)
                                        analyticsTracker.event(
                                            AnalyticsEvents.Nav.CHAT_FAB_TAPPED,
                                            mapOf(AnalyticsParams.SOURCE to "home_chip"),
                                        )
                                        navigator.navigateToAiChat()
                                    }
                                } else {
                                    null
                                },
                                // Top-bar "+" and the leading "New list" prompt chip both
                                // route to the manual create screen (CreateChecklistScreen).
                                // From there the user can still pick a template via the
                                // "Choose from template" button. Creation moved to the top of
                                // the screen; the bottom is a clean chat dock.
                                onCreateFromTemplatesClick = { navigator.navigateToCreateChecklistScreen() },
                                // New-user activation hero (empty MainScreen, flag ON). Typed text +
                                // template chips drive the AI create flow via the inline dock.
                                activationEnabled = activationBundleEnabled,
                                onActivationGenerate = onActivationGenerate,
                                onActivationChipTapped = onActivationChipTapped,
                                // v2 only (0.dp in control): MainScreen runs contentExtendsBehindNavBar,
                                // so without this the last card slides under the bottom bar and the FAB.
                                extraBottomPadding = v2FabBandPadding,
                                // MainScreen unconditionally SWALLOWS Android BACK whenever the drawer
                                // is closed and the dock collapsed — which in v2 is always true. Left
                                // on, "BACK returns to Inbox" would silently do nothing on the Projects
                                // tab. Control keeps the swallow (true), so its behaviour is unchanged.
                                swallowRootBack = navVariant == NavVariant.CONTROL,
                            )
                        }
                    }

                    entry<AppNavRoute.CreateChecklistRoute.CreateChecklist> { route ->
                        CreateChecklistScreen(
                            editChecklistId = route.editChecklistId,
                            templateId = route.templateId,
                            initialText = route.initialText,
                        )
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
                        AnalyzeScreen(
                            checklistId = route.checklistId,
                            fillDefault = route.fillDefault,
                            initialText = route.initialText,
                            autoAnalyze = route.autoAnalyze,
                        )
                    }

                    entry<AppNavRoute.AddToChecklistPicker> { route ->
                        AddToChecklistPickerScreen(text = route.text, purpose = route.purpose)
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
                            // Folder drill-down level (null = checklist root). Each folder open
                            // pushes a new ChecklistDetail entry carrying this; forwarded into the
                            // screen (and its keyed ViewModel) here.
                            currentFolderId = route.currentFolderId,
                            // Called by the screen when its dock opens (seeds context + name + the
                            // mirror for analytics/banner) / closes. The name comes from the screen's
                            // own state (App only knows route.checklistId here).
                            onOpenChatSheet = { checklistId, checklistName ->
                                onOpenChatSheet(checklistId, checklistName)
                            },
                            onChatCollapse = { chatSheetOpen = false },
                            // v2 removes the dock here too (DECISION 2). This also removes the dock's
                            // ChatDockItemCreateOverride fast-add path — replaced by the inline
                            // "+ Add task" row below, NOT dropped: losing fast item entry would be a
                            // regression, not a simplification.
                            chatDockContent = if (navVariant == NavVariant.V2) null else chatDockContent,
                            chatFullContent = if (navVariant == NavVariant.V2) null else chatFullContent,
                            chatInputBlank = chatUiState.inputText.isBlank(),
                            routeCollapseSignal = routeCollapseSignal,
                            onChecklistQuickAction = { checklistId, checklistName, action ->
                                onChecklistQuickAction(checklistId, checklistName, action)
                            },
                            // v2 only: the Todoist-style inline add row, plus zeroing the item-create
                            // scrim (state.itemCreateMode drives it independently of the dock, so
                            // without this the screen would dim with no dock to justify it).
                            useInlineAddRow = navVariant == NavVariant.V2,
                            // The shell's chat FAB is hidden on detail screens (barVisible = false), so
                            // v2 restores chat access with one top-bar action. null in control = zero
                            // extra actions rendered, i.e. the control top bar is untouched.
                            onOpenChat = if (navVariant == NavVariant.V2) {
                                {
                                    // Instrumented with a distinct source: without this, chat opens
                                    // from a project were the ONE chat entry point in v2 with no
                                    // event at all (the shell FAB emits this, control emits
                                    // ai_chat_opened(source="dock")), leaving the experiment's
                                    // headline question — does the chat lose usage once it loses
                                    // focus — unanswerable for detail screens.
                                    analyticsTracker.event(
                                        AnalyticsEvents.Nav.CHAT_FAB_TAPPED,
                                        mapOf(AnalyticsParams.SOURCE to "detail_toolbar"),
                                    )
                                    navigator.navigateToAiChat()
                                }
                            } else {
                                null
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
                        PaywallRoute(sourceOverride = route.source, forceVariant = route.forceVariant)
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

                    entry<AppNavRoute.Mcp> {
                        McpScreen(onBack = { navigator.onBack() })
                    }

                    entry<AppNavRoute.Today>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        TodayRoute(
                            drawerState = drawerState,
                            // v2 reaches Today from the Overview tab as a pushed screen: the bottom bar
                            // and FAB hide (Today is not a tab) and the shell passes drawerState = null,
                            // so no hamburger renders either. On wasmJs PlatformBackHandler is a no-op
                            // and Nav 3 has no browser-history integration, which left the user with no
                            // way off the screen at all. The arm gate is REQUIRED, not defensive: in
                            // control, Medium/Expanded also pass drawerState = null (rail / permanent
                            // drawer), so an unconditional onBack would add a back arrow to the control
                            // arm's tablet top bar.
                            onBack = if (navVariant == NavVariant.V2) {
                                { navigator.onBack() }
                            } else {
                                null
                            },
                            onCreateChecklistClick = { navigator.navigateToCreateChecklistScreen() },
                        )
                    }

                    entry<AppNavRoute.Calendar>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        CalendarRoute(
                            drawerState = drawerState,
                            onCreateChecklistClick = { navigator.navigateToCreateChecklistScreen() },
                            // 0.dp in control — Calendar's inner lists keep their current insets there.
                            contentBottomPadding = v2FabBandPadding,
                        )
                    }

                    // ── v2 tab destinations ──────────────────────────────────────────────────
                    // Registered UNCONDITIONALLY, never behind `if (navVariant == V2)`. entryProvider
                    // is rebuilt on every renderNav recomposition, and a rememberSaveable'd back stack
                    // can outlive a process death that lands on the other arm — a route with no
                    // matching entry<> hard-crashes NavDisplay. Registering them is free: in the
                    // control arm neither route is ever pushed.

                    entry<AppNavRoute.Inbox>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        InboxRoute(
                            contentBottomPadding = v2FabBandPadding,
                            // Swallow BACK only while the Inbox IS the top of the stack. On Expanded
                            // this entry is a listPane that stays composed beside a pushed
                            // ChecklistDetail, and a handler registered later than NavDisplay's wins —
                            // so an always-on handler makes BACK dead instead of dismissing the detail
                            // pane. backStack is a SnapshotStateList, so this re-evaluates on push/pop.
                            swallowRootBack = navigator.backStack.lastOrNull() is AppNavRoute.Inbox,
                        )
                    }

                    entry<AppNavRoute.Overview> {
                        OverviewScreen(
                            contentBottomPadding = v2FabBandPadding,
                            // Rows that point at a v2 TAB go through the tab router; everything else
                            // (Today / AiChat / Mcp / UpdateFeed / Settings) keeps the v1 router, so
                            // those behave identically in both arms.
                            //
                            // shellOnNavigate cannot serve the tabs here. Its Main branch only POPS
                            // to an existing AppNavRoute.Main and never pushes — and in v2 the stack
                            // while Overview is on screen is always [Inbox, Overview], so Main is
                            // absent and "Home" did nothing at all: no navigation, no message, no
                            // log. Its Calendar branch has the opposite flaw: pushLaunchSingleTop
                            // produced [Inbox, Overview, Calendar], so BACK returned to Overview
                            // instead of Inbox and a stale Overview entry lingered underneath —
                            // reaching Calendar from the bar and from here left different stacks.
                            // v2OnNavigate is pop-to-root-then-push, keeping every tab one entry deep.
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> v2OnNavigate(V2Destination.Projects)
                                    DrawerDestination.Calendar -> v2OnNavigate(V2Destination.Calendar)
                                    else -> shellOnNavigate(dest)
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                            isGoogleLinked = userData.isGoogleLinked,
                            googleEmail = userData.googleEmail,
                            googleDisplayName = userData.googleDisplayName,
                            onSignInClick = handleSignIn,
                            onSignOutClick = handleSignOut,
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
                            // Source comes from ChatRoute (the effect's tag, or the credits-chip
                            // constant) — never re-labelled here.
                            onNavigateToPaywall = { source ->
                                navigator.navigateToPaywall(source = source)
                            },
                        )
                    }
                }, // end entryProvider
            ) // end NavDisplay
            } // end renderNav lambda

            // Shell mount. The v2 branch is checked FIRST and the control branch below is a literal
            // copy of the pre-experiment `if (showShell) { … }` body — no reordering, no added
            // parameters — so `git diff` shows the control arm's chrome is byte-identical.
            when {
                // Hold a bare background for the frame(s) before the arm is known, rather than
                // mounting a shell we may have to swap.
                //
                // The three branches below are three DIFFERENT call sites, so moving between them
                // disposes and rebuilds the whole renderNav / NavDisplay subtree — a fresh
                // SaveableStateHolder, i.e. the user loses scroll position and in-progress edits. The
                // latch alone does not prevent that: navVariant seeds from the non-suspending
                // currentArm() (CONTROL until resolution lands) and only freezes once a shell has
                // mounted, so on a path that skips Splash's await — a deep link straight into a tab, a
                // process restart into a saved back stack — frame 1 would mount the control shell and
                // the resolver would then flip it. SplashViewModel resolves before navigating, so the
                // ordinary launch never reaches this branch at all.
                !armResolved && (showShell || showV2Shell) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }

                showV2Shell -> V2NavigationShell(
                    selectedTab = v2SelectedTab,
                    onNavigate = v2OnNavigate,
                    onOpenChat = {
                        // Tagged "fab" so it stays distinguishable from the detail screen's top-bar
                        // action, which emits the same event with source="detail_toolbar". Two very
                        // different surfaces; collapsing them would hide where chat access actually
                        // survived the dock removal.
                        analyticsTracker.event(
                            AnalyticsEvents.Nav.CHAT_FAB_TAPPED,
                            mapOf(AnalyticsParams.SOURCE to "fab"),
                        )
                        navigator.navigateToAiChat()
                    },
                    onOpenSettings = { navigator.navigateToSettings() },
                    onOpenUpdates = { navigator.navigateToUpdateFeed() },
                    barVisible = v2BarVisible,
                    content = renderNav,
                )

                showShell -> AdaptiveNavigationShell(
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
                    // Disable the drawer's left-edge swipe-to-open while the chat dock is expanded —
                    // that edge gesture was eating the keyboard-dismiss / dock-collapse drags in the
                    // expanded chat. chatSheetOpen mirrors the dock's Expanded state (onChatExpandedChanged).
                    // The hamburger button still opens the drawer; only the edge-swipe is suppressed.
                    drawerGesturesEnabled = !chatSheetOpen,
                    content = renderNav,
                )

                else -> renderNav(null)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )

            // The expandable chat dock is now rendered PER-SCREEN (inside MainScreen /
            // ChecklistDetailScreen's GistiGlassChatDock) via the `chatDockContent` slot built
            // above — NOT as an app-level slide-on-top overlay. This is Approach A (in-place morph):
            // one surface that expands in place, preserving the Haze backdrop blur. The old
            // GistiInlineChatPanel(AnimatedVisibility) overlay was removed here.
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

            // ── Chat feedback sheet (thumb-down) — hosted at App level ────────────
            // Bug #2: tapping 👎 on an assistant bubble in the COLLAPSED dock sets
            // chatUiState.feedbackTarget, but only the full ChatScreen hosted the
            // ChatFeedbackSheet — so in the dock nothing opened. Hosting it here (a
            // sibling of the other App-level chat sheets) makes "Leave Feedback" work
            // from the dock too, regardless of collapsed/expanded. The full ChatScreen
            // keeps its own host (independent render site) — both read the same singleton
            // ViewModel state, and only one chat surface is on screen at a time, so there
            // is no double-sheet. Mirrors ChatScreen's previousUserQuestion lookup.
            chatUiState.feedbackTarget?.let { target ->
                val previousUserQuestion = remember(chatUiState.messages, target.id) {
                    val idx = chatUiState.messages.indexOfFirst { it.id == target.id }
                    chatUiState.messages
                        .take(idx.coerceAtLeast(0))
                        .lastOrNull { it.role == ChatRole.User }
                        ?.content
                }
                ChatFeedbackSheet(
                    target = target,
                    previousUserQuestion = previousUserQuestion,
                    feedbackText = chatUiState.feedbackText,
                    isSubmitting = chatUiState.isSubmittingFeedback,
                    onTextChange = { chatViewModel.sendIntent(ChatScreenIntent.OnFeedbackTextChange(it)) },
                    onSubmit = { chatViewModel.sendIntent(ChatScreenIntent.OnFeedbackSubmit) },
                    onDismiss = { chatViewModel.sendIntent(ChatScreenIntent.OnFeedbackDismiss) },
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

            // ── Activation reminder opt-in (RC flag activation_bundle_v1) ─────────
            // The ActivationCoordinator (driven by the AI create paths — the chat tool-call
            // dispatcher AND the Analyze result-preview VM for the hero flow) emits the id of the
            // new user's FIRST AI checklist. We show a one-time soft-ask; on enable→grant we
            // schedule a default reminder for this checklist; the coordinator records the outcome
            // and marks it shown so it never reappears. Singleton coordinator — same instance the
            // create paths emit on.
            val activationCoordinator: ActivationCoordinator = koinInject()
            val checklistRepository: ChecklistRepository = koinInject()
            val reminderScheduler: ChecklistReminderScheduler = koinInject()
            var activationReminderChecklistId by rememberSaveable { mutableStateOf<Long?>(null) }
            LaunchedEffect(Unit) {
                activationCoordinator.reminderOptInRequests.collect { checklistId ->
                    activationReminderChecklistId = checklistId
                }
            }
            activationReminderChecklistId?.let { reminderChecklistId ->
                ActivationReminderSheet(
                    onEnableGranted = {
                        val triggerAt = nextActivationReminderTrigger()
                        scope.launch {
                            runCatching {
                                checklistRepository.setReminder(reminderChecklistId, triggerAt)
                                reminderScheduler.scheduleReminder(reminderChecklistId, triggerAt)
                            }.onFailure { e ->
                                logger.error("Activation", "schedule activation reminder failed: ${e.message}", e)
                            }
                            activationCoordinator.reportReminderOptInOutcome(granted = true)
                        }
                        activationReminderChecklistId = null
                    },
                    onSkip = {
                        scope.launch { activationCoordinator.reportReminderOptInOutcome(granted = false) }
                        activationReminderChecklistId = null
                    },
                    onDismiss = {
                        // Dismiss (scrim/back) counts as skip — never re-ask.
                        scope.launch { activationCoordinator.reportReminderOptInOutcome(granted = false) }
                        activationReminderChecklistId = null
                    },
                )
            }

            // ── Widget promo (retention) ─────────────────────────────────────────
            // Promotes the home-screen widget a DISTINCT beat after the user's SECOND checklist so it
            // never stacks with the post-first-checklist reminder opt-in (ActivationReminderSheet) or
            // its notification-permission ask. Evaluated once per app-open: if widgets are supported,
            // the user already has >= 2 checklists (so they are past the first-checklist moment — the
            // reminder opt-in already had its turn in a prior session), and the device show-once flag
            // is unset, we mark it shown and surface the promo. The render guard ALSO requires the
            // reminder sheet to be absent — a hard guarantee the two never co-render in one frame.
            // The flag is device-global (the widget is a per-device surface, not per-account), stored
            // via AppDatastore — same boolean-key pattern as ActivationPrefsRepository.
            val appDatastore: AppDatastore = koinInject()
            val widgetPromoAnalytics: AnalyticsTracker = koinInject()
            var showWidgetPromo by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!isWidgetSupported()) return@LaunchedEffect
                if (appDatastore.observeBoolean(WIDGET_PROMO_SHOWN_KEY, false).first()) return@LaunchedEffect
                // `.projects`, not `.checklists`: the v2 arm auto-creates a system Inbox, which would
                // otherwise push every user over WIDGET_PROMO_MIN_CHECKLISTS one real checklist early
                // — a retention-prompt timing difference between the arms, not a cosmetic one.
                val checklistCount = checklistRepository.projects.first().size
                if (checklistCount < WIDGET_PROMO_MIN_CHECKLISTS) return@LaunchedEffect
                // Never overlap the activation reminder soft-ask (which also drives the notif ask).
                if (activationReminderChecklistId != null) return@LaunchedEffect
                appDatastore.saveBoolean(WIDGET_PROMO_SHOWN_KEY, true)
                widgetPromoAnalytics.event(AnalyticsEvents.Widget.PROMO_SHOWN)
                showWidgetPromo = true
            }
            if (showWidgetPromo && activationReminderChecklistId == null) {
                WidgetPromoSheet(
                    onAddWidget = {
                        scope.launch { widgetPromoAnalytics.event(AnalyticsEvents.Widget.PROMO_ACCEPTED) }
                        showWidgetPromo = false
                        // Hand off to the established widget-instruction flow (no programmatic pin API).
                        showWidgetInstruction = true
                    },
                    onSkip = {
                        scope.launch { widgetPromoAnalytics.event(AnalyticsEvents.Widget.PROMO_DISMISSED) }
                        showWidgetPromo = false
                    },
                    onDismiss = {
                        scope.launch { widgetPromoAnalytics.event(AnalyticsEvents.Widget.PROMO_DISMISSED) }
                        showWidgetPromo = false
                    },
                )
            }

            // In-App Review launcher — side-effect composable, no UI
            InAppReviewLauncher(
                shouldLaunch = csatState.shouldLaunchReview,
                onComplete = { csatViewModel.sendIntent(CsatIntent.ReviewComplete) },
            )

            // In-App Update launcher — side-effect composable, no UI. Android-only (no-op on
            // web/iOS): checks Google Play on cold start + resume and shows the restart snackbar
            // through the shared snackbarHostState when a flexible update has downloaded.
            AppUpdateLauncher(snackbarHostState = snackbarHostState)
            } // AppLocaleEnvironment
        } // AppTheme
        } // CompositionLocalProvider(LocalEmojiFont)
    }
}

/** Device-global show-once flag for the retention widget promo sheet (see [App]). */
private const val WIDGET_PROMO_SHOWN_KEY = "widget_promo_shown"

/**
 * Minimum checklist count before the widget promo may appear. `>= 2` places it a distinct beat after
 * the post-first-checklist activation reminder, so the two retention prompts never stack.
 */
private const val WIDGET_PROMO_MIN_CHECKLISTS = 2
