package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatDockItemCreateOverride
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.rememberDockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiQuickAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDefaultPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.done
import aichecklists.core.designsystem.generated.resources.google_sign_in_failed
import aichecklists.core.designsystem.generated.resources.google_sign_in_required
import aichecklists.core.designsystem.generated.resources.google_sign_in_success
import aichecklists.core.designsystem.generated.resources.sign_in_no_account
import aichecklists.core.designsystem.generated.resources.sign_in_cancelled
import aichecklists.core.designsystem.generated.resources.sign_in_unavailable
import aichecklists.core.designsystem.generated.resources.sign_in_network
import aichecklists.core.designsystem.generated.resources.main_ask_gisti_placeholder
import aichecklists.core.designsystem.generated.resources.main_create_checklist_action
import aichecklists.core.designsystem.generated.resources.main_create_with_ai_action
import aichecklists.core.designsystem.generated.resources.main_error_description
import aichecklists.core.designsystem.generated.resources.main_error_retry
import aichecklists.core.designsystem.generated.resources.main_error_title
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
    /**
     * App-level continuous-drag chat dock content rendered INSIDE this screen's [GistiGlassChatDock]
     * (so the Haze backdrop blur is preserved). Invoked as
     * `chatDockContent(dockState, peekPlaceholder, chips)` — the screen owns the per-screen
     * [AnchoredDraggableState] (no shared draggable across two-pane panes). When null, dock hidden.
     */
    chatDockContent: (@Composable (AnchoredDraggableState<DockAnchor>, String, Dp, @Composable () -> Unit, ChatDockItemCreateOverride?, () -> Unit) -> Unit)? = null,
    /**
     * App-level FULL chat overlay content (the expanded dock's third "floor"). Rendered ABOVE the whole
     * screen (covers the top bar) with this screen's own [DockFullExpandState] + the measured live dock
     * height. When null the full overlay is unavailable. Per-screen — never shared across two-pane panes.
     */
    chatFullContent: (@Composable (DockFullExpandState, Int) -> Unit)? = null,
    /** True when the chat input is blank — drives BACK (collapse only when blank; else text holds open). */
    chatInputBlank: Boolean = true,
    /** Called when this screen's dock settles to / away from Expanded (App seeds context + analytics). */
    onChatExpandedChanged: (Boolean) -> Unit = {},
    /** Bumped by App.kt on every route change → animate the dock back to its Peek (auto-collapse). */
    routeCollapseSignal: Int = 0,
    /**
     * Handles a prompt-chip [GistiQuickAction] (Create with AI / Photo / Remind / Link / Plan day / PDF).
     * App.kt maps each action to its own chat flow (attachment picker, input prefill, or
     * prefill+send) via the singleton ChatViewModel + inline dock. When null, the chips fall
     * back to plainly opening the chat (legacy [MainScreenIntent.OnAiChatClick]).
     */
    onQuickAction: ((GistiQuickAction) -> Unit)? = null,
    /**
     * v2 only — handler for the DOCK-FREE quick-action chip row rendered directly above the list.
     *
     * The six AI entry points (New list / Create with AI / Photo / Remind / Link / Plan day) live
     * INSIDE the chat dock's morph (see [chatDockContent] below). v2 passes `chatDockContent = null`,
     * which would silently delete every one-tap AI-create shortcut from the app's flagship surface
     * and leave only the manual top-bar "+". The product decision was to drop the DOCK, not the
     * entry points, so the identical chip set is re-surfaced here as a plain row.
     *
     * Non-null → the row is rendered and every chip reports its [GistiQuickAction] here.
     * null (the default) → nothing is rendered and the control arm's node tree is untouched — there
     * the same chips keep living inside the dock.
     *
     * Deliberately a SEPARATE callback from [onQuickAction]: [onQuickAction] only prefills the
     * singleton chat ViewModel and relies on a dock being on screen to show the result. With no dock
     * the host MUST also navigate to the chat route, or the tap is a silent no-op.
     */
    onInlineQuickAction: ((GistiQuickAction) -> Unit)? = null,
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
    /**
     * Extra bottom inset the HOST reserves below this screen (v2 shell: bottom bar + chat FAB).
     *
     * Required because [contentExtendsBehindNavBar] is true, so content runs to the physical bottom:
     * without it the last checklist card slides under the v2 NavigationBar and FAB. Folded into the
     * existing `contentBottomPadding` expression rather than replacing it, so the control arm's
     * dock/navbar math is untouched.
     *
     * Defaults to 0.dp — the control arm passes nothing and renders exactly as before.
     */
    extraBottomPadding: Dp = 0.dp,
    /**
     * Whether BACK at the home root is swallowed.
     *
     * v1 (true, the default): BACK on Home does nothing, so the user is never kicked out to the
     * launcher. v2 (false): Home is the *Projects tab*, not the root — BACK must fall through to
     * NavDisplay so it pops back to the Inbox. Leaving this true in v2 would make BACK silently
     * dead on three of the four tabs.
     */
    swallowRootBack: Boolean = true,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.MAIN) }

    val screenState: MainScreenState by viewModel.screenState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Per-screen drag state for the continuous chat dock (NOT shared across two-pane panes).
    val dockState = remember { AnchoredDraggableState(initialValue = DockAnchor.Peek) }
    // Per-screen FULL overlay state (the dock's third "floor"). Separate from dockState — Full is NOT a
    // third dock anchor (that would re-measure the dock's reveal panel and break keyboard-up Expanded).
    val fullState = rememberDockFullExpandState()
    // Live Expanded height of the dock (the full overlay's collapsed start height, so it grows out of
    // the dock seamlessly). Measured continuously — does NOT drive the list's bottom padding (that stays
    // frozen at the Peek height via dockHeightPx below).
    var dockExpandedHeightPx by remember { mutableStateOf(0) }
    // Discrete "is the dock open" — targetValue flips at the drag midpoint, so reading it here
    // recomposes only on that flip (NOT per pixel; the pixel-level reveal is offset-driven in layout).
    val dockExpanded by remember { derivedStateOf { dockState.targetValue == DockAnchor.Expanded } }
    val focusManager = LocalFocusManager.current
    // Tell App.kt when the dock opens/closes (it seeds the chat context + fires the open analytics).
    LaunchedEffect(dockExpanded) { onChatExpandedChanged(dockExpanded) }
    // Auto-collapse on any route change (App bumps the signal). animateTo needs anchors → NaN-guard.
    LaunchedEffect(routeCollapseSignal) {
        if (routeCollapseSignal > 0 && !dockState.offset.isNaN()) dockState.animateTo(DockAnchor.Peek)
    }

    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    val showHamburgerHint = (screenState as? MainScreenState.Success)?.showHamburgerHint ?: false
    val hamburgerScale = remember { Animatable(1f) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Measured chat-dock height (already includes the dock's navbar inset). Hoisted ABOVE AppScaffold
    // so the snackbarHost slot can lift snackbars/toasts to sit just above the dock instead of under
    // it. Written by the dock's onSizeChanged; read by the list's bottom contentPadding (dockHeight)
    // AND the snackbar host.
    var dockHeightPx by remember { mutableStateOf(0) }

    val msgSignInSuccess = stringResource(Res.string.google_sign_in_success)
    val msgSignInFailed = stringResource(Res.string.google_sign_in_failed)
    val msgSignInRequired = stringResource(Res.string.google_sign_in_required)
    val msgSignInNoAccount = stringResource(Res.string.sign_in_no_account)
    val msgSignInCancelled = stringResource(Res.string.sign_in_cancelled)
    val msgSignInUnavailable = stringResource(Res.string.sign_in_unavailable)
    val msgSignInNetwork = stringResource(Res.string.sign_in_network)

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is MainScreenSideEffect.ShowSnackbar -> {
                    val text = when (effect.messageKey) {
                        "google_sign_in_success" -> msgSignInSuccess
                        "google_sign_in_failed" -> msgSignInFailed
                        "google_sign_in_required" -> msgSignInRequired
                        "sign_in_no_account" -> msgSignInNoAccount
                        "sign_in_cancelled" -> msgSignInCancelled
                        "sign_in_unavailable" -> msgSignInUnavailable
                        "sign_in_network" -> msgSignInNetwork
                        else -> effect.messageKey
                    }
                    snackbarHostState.showSnackbar(text)
                }
                MainScreenSideEffect.NavigateToAiChat -> {
                    // "Open AI chat" now animates the in-place dock open (it is always present as peek).
                    if (!dockState.offset.isNaN()) dockState.animateTo(DockAnchor.Expanded)
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

    // BACK on the home root must NEVER fall through to the Activity — at the root the NavDisplay
    // back handler is disabled (nothing to pop), so an unhandled BACK reaches ComponentActivity and
    // finish()es the app, dropping the user to the launcher. The three handlers below are mutually
    // exclusive by state (only one is ever enabled), so their registration order is irrelevant.
    //
    // 1) Drawer open → close the drawer (Compact only; drawerState is null on Medium/Expanded).
    PlatformBackHandler(enabled = drawerState?.isOpen == true) {
        scope.launch { drawerState?.close() }
    }
    // 2) True root (drawer closed, dock collapsed) → swallow. Product decision: BACK on the home
    //    screen does nothing so the user is never kicked out of the app (they leave via Home).
    //    v2 passes swallowRootBack = false: there this screen is the *Projects tab*, not the root,
    //    and BACK must reach NavDisplay so it pops back to the Inbox. The condition is otherwise
    //    unchanged, so the control arm registers exactly the handler it always did.
    PlatformBackHandler(enabled = swallowRootBack && drawerState?.isOpen != true && !dockExpanded) {
        // Intentionally no-op.
    }
    // 3) Dock expanded: hide the keyboard and ALWAYS collapse to peek on BACK. A draft is NOT lost — the
    //    text lives in the chat input, which stays visible at Peek. Once collapsed handler (2) takes over
    //    and swallows further BACKs.
    PlatformBackHandler(enabled = dockExpanded) {
        focusManager.clearFocus()
        if (!dockState.offset.isNaN()) {
            scope.launch { dockState.animateTo(DockAnchor.Peek) }
        }
    }
    // 4) FULL overlay open → BACK collapses it back onto the expanded dock. Registered AFTER (3) so it
    //    wins while both are enabled (full-open implies dock-expanded). The dock stays Expanded beneath
    //    (keepExpanded when there is content), so collapsing Full reveals the expanded chat again.
    PlatformBackHandler(enabled = fullState.isOpen) {
        scope.launch { fullState.close() }
    }

    LaunchedEffect(drawerState?.isOpen) {
        if (drawerState?.isOpen == true && showHamburgerHint) {
            viewModel.sendIntent(MainScreenIntent.OnHamburgerHintCompleted)
        }
    }

    AppScaffold(
        snackbarHost = {
            // Lift the snackbar to sit ABOVE the floating chat dock (the measured dockHeightPx already
            // includes the dock's navbar inset). When the dock is hidden fall back to navigationBarsPadding.
            val dockShown = chatDockContent != null && !hideBottomBar && !isEditMode
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = if (dockShown) {
                    Modifier.padding(
                        bottom = with(LocalDensity.current) { dockHeightPx.toDp() } + AppDimens.SpacingSm
                    )
                } else {
                    Modifier.navigationBarsPadding()
                },
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
            MainScreenState.Error -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(Res.string.main_error_title),
                    description = stringResource(Res.string.main_error_description),
                    action = {
                        AppButton(
                            text = stringResource(Res.string.main_error_retry),
                            onClick = { viewModel.sendIntent(MainScreenIntent.OnRetry) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
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
                val dockHeight = with(density) { dockHeightPx.toDp() }
                // Same visibility as the old bottomBar (we are already inside the Success branch).
                val showDock = chatDockContent != null && !hideBottomBar && !isEditMode
                // Content runs edge-to-edge behind the navbar (contentExtendsBehindNavBar). When the
                // dock is shown, dockHeight already includes the navbar inset (the dock carries its own
                // navigationBarsPadding), so the list clears both. When hidden (edit mode), add the
                // navbar inset explicitly so the last row isn't swallowed by the navigation bar.
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                // extraBottomPadding is ADDED to the existing expression, never replaces a branch:
                // the dock/navbar math above is control-arm behaviour and must not move. It is 0.dp
                // in v1, so both branches evaluate exactly as before.
                val contentBottomPadding = if (showDock) {
                    dockHeight + AppDimens.SpacingXxl + extraBottomPadding
                } else {
                    navBottom + AppDimens.SpacingXxl + extraBottomPadding
                }
                // FIX B: the answer cap (status bar → keyboard top), computed HERE at the host where
                // WindowInsets.ime is reliable — a deep read inside the dock returns 0 once the host
                // applies imePadding. Unspecified when the keyboard is down (use the design cap).
                val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val statusTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val containerHDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
                val dockAvailableDp = if (imeBottomDp > navBottom + 8.dp) {
                    (containerHDp - imeBottomDp - statusTopDp).coerceAtLeast(0.dp)
                } else {
                    Dp.Unspecified
                }

                // The home list/grid. Hoisted into a local composable lambda ONLY so the v2 chip row
                // can wrap it in a Column without adding layout nodes to the control arm: below, the
                // control branch still invokes it as the single, direct child of the hazeSource Box,
                // so the control arm's rendered tree is unchanged.
                val homeListContent: @Composable () -> Unit = {
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
                        // v2 (onInlineQuickAction != null): the dock is gone, so the SAME six chips it
                        // hosted get their own row above the list — losing them would delete every
                        // one-tap AI-create shortcut from the flagship surface. null → the control arm
                        // renders the list alone, as it always did.
                        //
                        // The BRANCH KEY IS THE ARM ONLY — never `&& !isEditMode`. The arm is latched
                        // for the process, so this choice is fixed at first composition; folding edit
                        // mode into it would move homeListContent() between two composition groups on
                        // every Done/edit toggle, disposing it and discarding the rememberLazyListState
                        // inside MainScreenContent — the list would visibly jump to the top. Edit mode
                        // is gated on the CHIPS instead (reorder mode owns the screen; its only action
                        // is "Done", which is why the dock hid too).
                        if (onInlineQuickAction != null) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (!isEditMode) {
                                    GistiPromptChips(
                                        chips = gistiDefaultPromptChips(
                                            createAiLabel = stringResource(Res.string.main_create_with_ai_action),
                                            photoLabel = stringResource(Res.string.main_prompt_photo),
                                            remindLabel = stringResource(Res.string.main_prompt_remind),
                                            linkLabel = stringResource(Res.string.main_prompt_link),
                                            planDayLabel = stringResource(Res.string.main_prompt_plan_day),
                                        ),
                                        onChipClick = onInlineQuickAction,
                                        // Leading "➕ New list" chip → Templates (manual create), same as
                                        // in the dock. Null when the host wires no manual-create route.
                                        onNewListClick = onCreateFromTemplatesClick,
                                        newListLabel = stringResource(Res.string.main_prompt_new_list),
                                        // No horizontal padding here on purpose — GistiPromptChips owns it
                                        // as LazyRow contentPadding so chips bleed past the screen edge
                                        // while scrolling (stacking padding here kills that signal).
                                        modifier = Modifier.padding(top = AppDimens.SpacingSm),
                                    )
                                }
                                // Single weighted child → takes all remaining height, so the list keeps
                                // measuring exactly as it does without the row above it.
                                Box(modifier = Modifier.weight(1f)) { homeListContent() }
                            }
                        } else {
                            homeListContent()
                        }
                    }

                    // Nav-bar grey: paint the system navigation-bar zone with the dock's own flat-grey
                    // token so the gesture/nav strip matches the dock instead of letting the white page
                    // show through underneath it. The dock sits navbar-padded ABOVE this strip (its host
                    // modifier owns ime ∪ navigationBars), so the strip can't live inside the dock — it is
                    // a sibling filling exactly the navbar inset at the screen bottom. Only while the dock
                    // is shown; behind the keyboard (harmless) when it is up. gistiDockColor() is the
                    // single source of the dock background, so the strip and the dock read as one surface.
                    if (showDock) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                .background(gistiDockColor()),
                        )
                    }

                    // Floating dock — a DIRECT child of the anchor Box. FIX B: `.imePadding()` lifts the
                    // WHOLE dock above the keyboard at the HOST (where the ime inset is NOT consumed —
                    // the deep windowInsetsPadding(ime) inside the dock read ≈0 on the phone). The host
                    // is the single bottom-inset owner: imePadding (keyboard) + navigationBarsPadding
                    // (the collapsed peek still clears the navbar). FIX D: the dock is a flat grey now.
                    if (showDock && chatDockContent != null) {
                        GistiGlassChatDock(
                            hazeState = hazeState,
                            bottomPadding = AppDimens.SpacingSm,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .imePadding()
                                .navigationBarsPadding()
                                // Freeze the measured dock height at the PEEK size so the list's bottom
                                // contentPadding stays put — the expanded panel grows UPWARD over the
                                // list (no repad / jump). targetValue flips to Expanded as soon as a
                                // drag-up begins, so growth is not measured.
                                .onSizeChanged {
                                    if (dockState.targetValue == DockAnchor.Peek) dockHeightPx = it.height
                                    // Track the LIVE dock height too (used only as the full overlay's
                                    // start height — it must NOT freeze at Peek, or Full would grow from
                                    // the wrong origin). Does not affect the list's bottom padding.
                                    dockExpandedHeightPx = it.height
                                },
                            // The morphing chat content. Home peek placeholder = "Ask Gisti…"; chips are
                            // hosted INSIDE the morph (it fades + collapses them as the dock expands).
                            pillContent = {
                                chatDockContent(
                                    dockState,
                                    stringResource(Res.string.main_ask_gisti_placeholder),
                                    dockAvailableDp,
                                    {
                                        GistiPromptChips(
                                            chips = gistiDefaultPromptChips(
                                                createAiLabel = stringResource(Res.string.main_create_with_ai_action),
                                                photoLabel = stringResource(Res.string.main_prompt_photo),
                                                remindLabel = stringResource(Res.string.main_prompt_remind),
                                                linkLabel = stringResource(Res.string.main_prompt_link),
                                                planDayLabel = stringResource(Res.string.main_prompt_plan_day),
                                            ),
                                            // Tapping a chip animates the dock open AND drives its chat flow.
                                            onChipClick = { action ->
                                                scope.launch {
                                                    if (!dockState.offset.isNaN()) dockState.animateTo(DockAnchor.Expanded)
                                                }
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
                                    // MainScreen never enters item-create mode → no override.
                                    null,
                                    // ↗ / drag-up over Expanded → open the FULL overlay.
                                    { scope.launch { fullState.open() } },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    // FULL chat overlay — sibling of AppScaffold so it covers the top bar (statusBarsPadding inside).
    // Opaque; drawn above the dock. Renders nothing until opened (its own reveal gate).
    chatFullContent?.invoke(fullState, dockExpandedHeightPx)
}
