package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBar
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiQuickAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDefaultPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.done
import aichecklists.core.designsystem.generated.resources.google_sign_in_failed
import aichecklists.core.designsystem.generated.resources.google_sign_in_required
import aichecklists.core.designsystem.generated.resources.google_sign_in_success
import aichecklists.core.designsystem.generated.resources.main_ask_gisti_mic
import aichecklists.core.designsystem.generated.resources.main_ask_gisti_placeholder
import aichecklists.core.designsystem.generated.resources.main_create_checklist_action
import aichecklists.core.designsystem.generated.resources.main_menu
import aichecklists.core.designsystem.generated.resources.main_prompt_new_list
import aichecklists.core.designsystem.generated.resources.main_prompt_photo
import aichecklists.core.designsystem.generated.resources.main_prompt_remind
import aichecklists.core.designsystem.generated.resources.main_prompt_link
import aichecklists.core.designsystem.generated.resources.main_prompt_plan_day
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main home screen. Drawer lives above this composable in App.kt via
 * ModalNavigationDrawer + AppNavigationDrawerContent. This screen only
 * owns the hamburger affordance and forwards open() to the lifted drawerState.
 *
 * `isEditMode` and `onEditModeChange` are also lifted to the caller so the
 * parent ModalNavigationDrawer can disable swipe gestures while the user is
 * dragging checklist items (gesturesEnabled = !isEditMode).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    drawerState: DrawerState?,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    viewModel: MainScreenViewModel = koinViewModel(),
    /** When true, the bottom "Create Checklist" bar is suppressed.
     *  Set to true when the caller (App.kt) provides a FAB instead. */
    hideBottomBar: Boolean = false,
    /** Navigate to the AI Chat screen. Wired in App.kt NavHost. */
    onNavigateToAiChat: (() -> Unit)? = null,
    /**
     * Handles a prompt-chip [GistiQuickAction] (Photo / Remind / Link / Plan day / PDF).
     * App.kt maps each action to its own chat flow (attachment picker, input prefill, or
     * prefill+send) via the singleton ChatViewModel + inline dock. When null, the chips fall
     * back to plainly opening the chat (legacy [MainScreenIntent.OnAiChatClick]).
     */
    onQuickAction: ((GistiQuickAction) -> Unit)? = null,
    /**
     * Mic tap in the AskGisti bar: App.kt opens the dock and starts voice recording.
     * When null, the mic falls back to plainly opening the chat.
     */
    onMicClick: (() -> Unit)? = null,
    /** Navigate to the Templates screen (manual checklist creation entry).
     *  When non-null, a "+" action appears in the top bar and a "New list" chip
     *  is prepended to the prompt chips. Wired in App.kt NavHost. */
    onCreateFromTemplatesClick: (() -> Unit)? = null,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("main") }

    val screenState: MainScreenState by viewModel.screenState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    val showHamburgerHint = (screenState as? MainScreenState.Success)?.showHamburgerHint ?: false
    val hamburgerScale = remember { Animatable(1f) }

    val snackbarHostState = remember { SnackbarHostState() }

    val msgSignInSuccess = stringResource(Res.string.google_sign_in_success)
    val msgSignInFailed = stringResource(Res.string.google_sign_in_failed)
    val msgSignInRequired = stringResource(Res.string.google_sign_in_required)

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is MainScreenSideEffect.ShowSnackbar -> {
                    val text = when (effect.messageKey) {
                        "google_sign_in_success" -> msgSignInSuccess
                        "google_sign_in_failed" -> msgSignInFailed
                        "google_sign_in_required" -> msgSignInRequired
                        else -> effect.messageKey
                    }
                    snackbarHostState.showSnackbar(text)
                }
                MainScreenSideEffect.NavigateToAiChat -> {
                    onNavigateToAiChat?.invoke()
                }
            }
        }
    }

    LaunchedEffect(showHamburgerHint, drawerState) {
        if (showHamburgerHint && drawerState != null) {
            while (true) {
                hamburgerScale.animateTo(1.20f, tween(400, easing = FastOutSlowInEasing))
                hamburgerScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            }
        } else {
            hamburgerScale.snapTo(1f)
        }
    }

    LaunchedEffect(drawerState?.isOpen) {
        if (drawerState?.isOpen == true && showHamburgerHint) {
            viewModel.sendIntent(MainScreenIntent.OnHamburgerHintCompleted)
        }
    }

    AppScaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        title = "",
        navigationIcon = if (!isEditMode && drawerState != null) {
            {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(Res.string.main_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer {
                            scaleX = hamburgerScale.value
                            scaleY = hamburgerScale.value
                        },
                    )
                }
            }
        } else null,
        actions = {
            // "Done" button exits edit/reorder mode — only meaningful on Compact
            // where LazyColumn + reorderable is active. Hidden on Medium/Expanded
            // (grid path) since drag-drop is not supported on LazyVerticalGrid.
            if (isEditMode && isCompact) {
                TextButton(onClick = { onEditModeChange(false) }) {
                    Text(
                        text = stringResource(Res.string.done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                if (screenState is MainScreenState.Success) {
                    val state = screenState as MainScreenState.Success
                    // Top-bar actions, laid out left → right (MD3 action ordering):
                    // [+ New checklist]  [credits chip]. The "+" is a parallel manual-create
                    // entry to the "New list" prompt chip; both route to Templates.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    ) {
                        if (onCreateFromTemplatesClick != null) {
                            IconButton(onClick = onCreateFromTemplatesClick) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(Res.string.main_create_checklist_action),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AppCreditsChip(
                            credits = state.aiCredits,
                            isPremium = state.subscriptionStatus.isActive,
                            onClick = { viewModel.sendIntent(MainScreenIntent.OnCreditsClick) },
                        )
                    }
                }
            }
        },
        bottomBar = {
            // When the caller provides a FAB (hideBottomBar = true), suppress this bar
            // to avoid double bottom UI on the Lists tab.
            if (!hideBottomBar && !isEditMode && screenState is MainScreenState.Success) {
                // Bottom area is now a clean chat dock: prompt-starter chips + the
                // persistent "Ask Gisti" command bar. Manual checklist creation moved to
                // the top bar "+" and the leading "New list" prompt chip (both →
                // onCreateFromTemplatesClick). Upsell at the free limit lives on the
                // in-list Premium banner + paywall, not a bottom button.
                //
                // Outer column owns ONLY the bottom + navigation-bar insets.
                // Horizontal padding is applied per-child below, so the prompt chips can
                // run edge-to-edge (LazyRow contentPadding) while the bar stays inset.
                if (onNavigateToAiChat != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = AppDimens.SpacingLg)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    ) {
                        // Edge-to-edge: NO outer horizontal padding here. The LazyRow insets
                        // its content via contentPadding (= ScreenPaddingHorizontal by default),
                        // so the last chip ("🔔 Remind me…") bleeds past the screen edge as a
                        // scroll affordance instead of being clipped at the padding boundary.
                        GistiPromptChips(
                            chips = gistiDefaultPromptChips(
                                photoLabel = stringResource(Res.string.main_prompt_photo),
                                remindLabel = stringResource(Res.string.main_prompt_remind),
                                linkLabel = stringResource(Res.string.main_prompt_link),
                                planDayLabel = stringResource(Res.string.main_prompt_plan_day),
                            ),
                            // Each action drives its own chat flow (App.kt). Fall back to plainly
                            // opening the chat when the host didn't wire onQuickAction.
                            onChipClick = { action ->
                                if (onQuickAction != null) {
                                    onQuickAction(action)
                                } else {
                                    viewModel.sendIntent(MainScreenIntent.OnAiChatClick)
                                }
                            },
                            // Leading "➕ New list" chip → Templates (manual create), distinct
                            // from the chat-routing chips above. Shown only when wired.
                            onNewListClick = onCreateFromTemplatesClick,
                            newListLabel = stringResource(Res.string.main_prompt_new_list),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AskGistiBar(
                            placeholder = stringResource(Res.string.main_ask_gisti_placeholder),
                            onClick = { viewModel.sendIntent(MainScreenIntent.OnAiChatClick) },
                            // Mic opens the dock and starts recording (App.kt). Fall back to
                            // plainly opening the chat when not wired.
                            onMicClick = {
                                if (onMicClick != null) {
                                    onMicClick()
                                } else {
                                    viewModel.sendIntent(MainScreenIntent.OnAiChatClick)
                                }
                            },
                            micContentDescription = stringResource(Res.string.main_ask_gisti_mic),
                            modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                        )
                    }
                }
            }
        }
    ) {
        when (val state = screenState) {
            MainScreenState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is MainScreenState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreenContent(
                        screenState = state,
                        isEditMode = isEditMode,
                        onChecklistClick = { checklistWithProgress ->
                            viewModel.sendIntent(MainScreenIntent.OnChecklistClick(checklistWithProgress))
                        },
                        onAddChecklistClick = {
                            viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick)
                        },
                        onAiAnalyzeClick = {
                            viewModel.sendIntent(MainScreenIntent.OnAiAnalyzeClick)
                        },
                        onPremiumBannerClick = {
                            viewModel.sendIntent(MainScreenIntent.OnPremiumBannerClick)
                        },
                        onEnterEditMode = { onEditModeChange(true) },
                        onExitEditMode = { onEditModeChange(false) },
                        onReorderChecklists = { orderedIds ->
                            viewModel.sendIntent(MainScreenIntent.OnReorderChecklists(orderedIds))
                        },
                        onSignInClick = {
                            viewModel.sendIntent(MainScreenIntent.OnSignInClick)
                        },
                    )
                }
            }
        }
    }
}
