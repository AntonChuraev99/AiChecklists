package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBar
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiQuickAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDefaultPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.done
import aichecklists.core.designsystem.generated.resources.google_sign_in_failed
import aichecklists.core.designsystem.generated.resources.google_sign_in_required
import aichecklists.core.designsystem.generated.resources.google_sign_in_success
import aichecklists.core.designsystem.generated.resources.main_ask_gisti_mic
import aichecklists.core.designsystem.generated.resources.main_ask_gisti_placeholder
import aichecklists.core.designsystem.generated.resources.main_create_checklist_action
import aichecklists.core.designsystem.generated.resources.main_create_with_ai_action
import aichecklists.core.designsystem.generated.resources.main_menu
import aichecklists.core.designsystem.generated.resources.main_prompt_new_list
import aichecklists.core.designsystem.generated.resources.main_prompt_photo
import aichecklists.core.designsystem.generated.resources.main_prompt_remind
import aichecklists.core.designsystem.generated.resources.main_prompt_link
import aichecklists.core.designsystem.generated.resources.main_prompt_plan_day
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
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
     * Handles a prompt-chip [GistiQuickAction] (Create with AI / Photo / Remind / Link / Plan day / PDF).
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
    /**
     * New-user activation bundle (RC flag `activation_bundle_v1`). When true AND the checklist
     * list is empty, the AI first-run hero replaces the plain empty state. App.kt resolves the
     * flag and passes it here.
     */
    activationEnabled: Boolean = false,
    /** Hero typed input → App.kt opens the inline dock and sends the AI create prompt. */
    onActivationGenerate: ((String) -> Unit)? = null,
    /** Hero template chip tapped → (chipKey for analytics, resolved prompt). App.kt fires the
     *  CHIP_TAPPED event then sends the prompt down the AI create path. */
    onActivationChipTapped: ((String, String) -> Unit)? = null,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.MAIN) }

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
        // No bottomBar slot: the AI-chat dock is now a floating glassmorphism overlay rendered
        // inside the content Box (Success branch) so its backdrop can blur the scrolling list.
        // An opaque Scaffold bottomBar can never show a backdrop blur.
        //
        // Content extends edge-to-edge to the physical bottom (behind the navbar) so the dock's
        // blurred backdrop covers the navbar zone (no un-blurred strip) and the bar sits a clean
        // navbar + 8dp from the edge — not 2×navbar (the old double-inset: Scaffold reserved the
        // navbar AND the dock added its own navigationBarsPadding).
        contentExtendsBehindNavBar = true,
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
                // Floating glassmorphism chat-dock overlay (mirrors ChecklistDetailScreen). The dock
                // is NOT a Scaffold bottomBar so its backdrop can be a live blur of the scrolling
                // list: the list is marked hazeSource, the dock (sibling, BottomCenter) samples it via
                // hazeEffect. dockHeight is measured so the list gets exactly enough bottom
                // contentPadding to scroll clear of the dock (single owner of the bottom inset — the
                // dock carries its own navigationBarsPadding, the list never does).
                val hazeState = rememberHazeState()
                val density = LocalDensity.current
                var dockHeightPx by remember { mutableStateOf(0) }
                val dockHeight = with(density) { dockHeightPx.toDp() }
                // Same visibility as the old bottomBar (we are already inside the Success branch).
                val showDock = onNavigateToAiChat != null && !hideBottomBar && !isEditMode
                // Content runs edge-to-edge behind the navbar (contentExtendsBehindNavBar). When the
                // dock is shown, dockHeight already includes the navbar inset (the dock carries its own
                // navigationBarsPadding), so the list clears both. When hidden (edit mode), add the
                // navbar inset explicitly so the last row isn't swallowed by the navigation bar.
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val contentBottomPadding = if (showDock) {
                    dockHeight + AppDimens.SpacingLg
                } else {
                    navBottom + AppDimens.SpacingXxl
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // hazeSource on the CONTAINER (not the LazyColumn items — Haze #865 only blurs
                    // centre cells when the source is on items). The opaque background gives the blur
                    // something to sample over the near-white surface.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .hazeSource(hazeState),
                    ) {
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
                            onDismissSyncBanner = {
                                viewModel.sendIntent(MainScreenIntent.OnDismissSyncBanner)
                            },
                            activationEnabled = activationEnabled,
                            onActivationGenerate = { prompt -> onActivationGenerate?.invoke(prompt) },
                            onActivationChipTapped = { key, prompt -> onActivationChipTapped?.invoke(key, prompt) },
                            contentBottomPadding = contentBottomPadding,
                        )
                    }

                    // Floating dock — a DIRECT child of the anchor Box (the hazeEffect node must sit
                    // directly over the hazeSource; wrapping it in AnimatedVisibility would put the
                    // effect in a separate graphicsLayer and stop Haze sampling the backdrop).
                    if (showDock) {
                        GistiGlassChatDock(
                            hazeState = hazeState,
                            // 8dp gap above the navbar inset. Content extends behind the navbar (see
                            // AppScaffold contentExtendsBehindNavBar) so there is no second navbar inset
                            // from the Scaffold — the bar sits a clean navbar + 8dp from the screen edge.
                            bottomPadding = AppDimens.SpacingSm,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .onSizeChanged { dockHeightPx = it.height },
                            // Prompt-starter chips above the bar — edge-to-edge (own contentPadding,
                            // last chip bleeds past the screen edge as a scroll affordance).
                            chipsContent = {
                                GistiPromptChips(
                                    chips = gistiDefaultPromptChips(
                                        createAiLabel = stringResource(Res.string.main_create_with_ai_action),
                                        photoLabel = stringResource(Res.string.main_prompt_photo),
                                        remindLabel = stringResource(Res.string.main_prompt_remind),
                                        linkLabel = stringResource(Res.string.main_prompt_link),
                                        planDayLabel = stringResource(Res.string.main_prompt_plan_day),
                                    ),
                                    // Each action drives its own chat flow (App.kt). Fall back to
                                    // plainly opening the chat when the host didn't wire onQuickAction.
                                    onChipClick = { action ->
                                        if (onQuickAction != null) {
                                            onQuickAction(action)
                                        } else {
                                            viewModel.sendIntent(MainScreenIntent.OnAiChatClick)
                                        }
                                    },
                                    // Leading "➕ New list" chip → Templates (manual create).
                                    onNewListClick = onCreateFromTemplatesClick,
                                    newListLabel = stringResource(Res.string.main_prompt_new_list),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            // Persistent "Ask Gisti" command bar — inset horizontally (chips stay edge-to-edge).
                            pillContent = {
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
