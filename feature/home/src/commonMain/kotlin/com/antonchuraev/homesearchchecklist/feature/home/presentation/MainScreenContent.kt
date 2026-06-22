package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.SyncAccountBanner
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.desingsystem.components.PremiumBanner
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChecklistListCard
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Minimum column width for checklist cards in grid mode (Medium/Expanded). */
private val ChecklistGridMinColumnSize = 320.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreenContent(
    screenState: MainScreenState.Success,
    isEditMode: Boolean,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onAddChecklistClick: () -> Unit,
    onAiAnalyzeClick: () -> Unit,
    onPremiumBannerClick: () -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onReorderChecklists: (List<Long>) -> Unit = {},
    onSignInClick: () -> Unit = {},
    onDismissSyncBanner: () -> Unit = {},
    // Activation bundle (RC flag activation_bundle_v1): when true AND the list is empty, the empty
    // state is replaced by the AI first-run hero (prompt + template chips). When false the plain
    // EmptyState is shown (legacy behavior). Wired from App.kt.
    activationEnabled: Boolean = false,
    // Typed hero input → send as an AI create prompt (App.kt opens the dock + prefills/sends).
    onActivationGenerate: (String) -> Unit = {},
    // Hero template chip tapped → (chipKey for analytics, resolved prompt to send).
    onActivationChipTapped: (String, String) -> Unit = { _, _ -> },
    // Bottom contentPadding for the list/grid so the last item scrolls clear of the floating
    // chat-dock overlay (the host measures the dock height and passes it here). Defaults to the
    // plain bottom spacing when no dock is shown.
    contentBottomPadding: Dp = AppDimens.SpacingXxl,
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    if (isCompact) {
        MainScreenContentLazyColumn(
            screenState = screenState,
            isEditMode = isEditMode,
            onChecklistClick = onChecklistClick,
            onAddChecklistClick = onAddChecklistClick,
            onPremiumBannerClick = onPremiumBannerClick,
            onEnterEditMode = onEnterEditMode,
            onReorderChecklists = onReorderChecklists,
            onSignInClick = onSignInClick,
            onDismissSyncBanner = onDismissSyncBanner,
            activationEnabled = activationEnabled,
            onActivationGenerate = onActivationGenerate,
            onActivationChipTapped = onActivationChipTapped,
            contentBottomPadding = contentBottomPadding,
        )
    } else {
        MainScreenContentLazyGrid(
            screenState = screenState,
            onChecklistClick = onChecklistClick,
            onAddChecklistClick = onAddChecklistClick,
            onPremiumBannerClick = onPremiumBannerClick,
            activationEnabled = activationEnabled,
            onActivationGenerate = onActivationGenerate,
            onActivationChipTapped = onActivationChipTapped,
            contentBottomPadding = contentBottomPadding,
        )
    }
}

/**
 * Compact (phone) path — reorderable LazyColumn with drag-drop.
 * sh.calvin.reorderable only supports LazyColumn/LazyRow, so this path
 * is kept as-is for Compact screens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreenContentLazyColumn(
    screenState: MainScreenState.Success,
    isEditMode: Boolean,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onAddChecklistClick: () -> Unit,
    onPremiumBannerClick: () -> Unit,
    onEnterEditMode: () -> Unit,
    onReorderChecklists: (List<Long>) -> Unit,
    onSignInClick: () -> Unit = {},
    onDismissSyncBanner: () -> Unit = {},
    activationEnabled: Boolean = false,
    onActivationGenerate: (String) -> Unit = {},
    onActivationChipTapped: (String, String) -> Unit = { _, _ -> },
    contentBottomPadding: Dp = AppDimens.SpacingXxl,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Local mutable list for optimistic reorder
    var localList by remember(screenState.checklists) {
        mutableStateOf(screenState.checklists)
    }

    val lazyListState = rememberLazyListState()
    // Header items before the checklist rows. Their count drives the reorder index offset.
    // Gisti redesign: the loud premium banner is shown only to active subscribers (status);
    // free users get a calm upgrade hint below the list instead. A "My lists" section header
    // always precedes the cards when the list is non-empty.
    // The loud "Premium Member" status banner was removed — premium is now shown as a
    // "PRO" prefix on the credits chip (top bar). Free users still get the upgrade banner below.
    // The sync banner occupies the first index only when shown (showSyncBanner gates it on
    // not-linked + >1 checklist + not dismissed), so the reorder offset must track that exact
    // condition — using isGoogleLinked here would mis-offset drag indices once the banner is
    // dismissed or suppressed for new users.
    val headerItemCount = (if (screenState.showSyncBanner) 1 else 0) +
        1 // "My lists" section header

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Offset by headerItemCount because banner items occupy the first indices
        val fromIndex = from.index - headerItemCount
        val toIndex = to.index - headerItemCount
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localList.size && toIndex < localList.size) {
            localList = localList.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Wiggle animation for edit mode
    val wiggleTransition = rememberInfiniteTransition(label = "wiggle")
    val wiggleAngle by wiggleTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleAngle"
    )

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingLg,
            bottom = contentBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        if (screenState.showSyncBanner) {
            item(key = "sync_banner") {
                SyncAccountBanner(
                    onSignInClick = onSignInClick,
                    onDismiss = onDismissSyncBanner,
                )
            }
        }

        if (localList.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (activationEnabled) {
                        ActivationHero(
                            onGenerate = onActivationGenerate,
                            onChipTapped = onActivationChipTapped,
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Checklist,
                            title = stringResource(Res.string.main_empty_title),
                            description = stringResource(Res.string.main_empty_description),
                            action = {
                                AppButton(
                                    text = stringResource(Res.string.main_empty_cta),
                                    onClick = onAddChecklistClick,
                                )
                            }
                        )
                    }
                }
            }
        } else {
            item(key = "my_lists_header") {
                MyListsHeader(count = localList.size)
            }
            items(
                items = localList,
                key = { it.checklist.id }
            ) { checklistWithProgress ->
                ReorderableItem(
                    state = reorderableState,
                    key = checklistWithProgress.checklist.id,
                    enabled = isEditMode
                ) { isDragging ->
                    val isDark = LocalIsDarkTheme.current

                    // Drag/wiggle stay on the OUTER modifier (not clipped — shadow + drag
                    // transform survive). Tap/long-press-to-edit move INSIDE the card via
                    // onClick/onLongClick so the ripple is clipped to the rounded corners.
                    // In edit mode the card is not tappable (the drag handle owns the gesture).
                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (isEditMode && !isDragging) {
                                rotationZ = wiggleAngle
                            }
                        }
                        .then(
                            if (isEditMode) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onReorderChecklists(localList.map { it.checklist.id })
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )

                    ChecklistCard(
                        checklistWithProgress = checklistWithProgress,
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        modifier = cardModifier,
                        onClick = if (isEditMode) {
                            null
                        } else {
                            { onChecklistClick(checklistWithProgress) }
                        },
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnterEditMode()
                        },
                    )
                }
            }

            // Free users get the bright gradient upsell banner below the list (replaces
            // the earlier quiet CalmUpgradeHint string per the 2026-06-01 redesign).
            // Hidden for premium users. verticalArrangement spacedBy already separates it
            // from the last card, so no extra top padding is needed.
            val limits = screenState.userLimits
            if (limits != null && !limits.isPremium) {
                item(key = "upgrade_banner") {
                    PremiumBanner(
                        isActive = false,
                        formattedExpirationDate = null,
                        onUpgradeClick = onPremiumBannerClick,
                        onSubscriptionClick = onPremiumBannerClick,
                    )
                }
            }
        }
    }
}

/**
 * Medium/Expanded (tablet, desktop) path — non-reorderable LazyVerticalGrid.
 *
 * GridCells.Adaptive(minSize = 320.dp) distributes columns automatically:
 * - Medium (~600dp content area after rail): 1–2 columns
 * - Expanded (~840dp+ content area after permanent drawer): 2–3 columns
 *
 * Edit Mode ("Done" button / long-press drag) is hidden on this path because
 * sh.calvin.reorderable does not support LazyVerticalGrid.
 *
 * PremiumBanner uses span = maxLineSpan to occupy the full row width.
 * EmptyState uses span = maxLineSpan to stay centered across all columns.
 */
@Composable
private fun MainScreenContentLazyGrid(
    screenState: MainScreenState.Success,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onAddChecklistClick: () -> Unit,
    onPremiumBannerClick: () -> Unit,
    activationEnabled: Boolean = false,
    onActivationGenerate: (String) -> Unit = {},
    onActivationChipTapped: (String, String) -> Unit = { _, _ -> },
    contentBottomPadding: Dp = AppDimens.SpacingXxl,
) {
    if (screenState.checklists.isEmpty()) {
        // Empty state — center it, no grid needed
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (activationEnabled) {
                ActivationHero(
                    onGenerate = onActivationGenerate,
                    onChipTapped = onActivationChipTapped,
                )
            } else {
                EmptyState(
                    icon = Icons.Outlined.Checklist,
                    title = stringResource(Res.string.main_empty_title),
                    description = stringResource(Res.string.main_empty_description),
                    action = {
                        AppButton(
                            text = stringResource(Res.string.main_empty_cta),
                            onClick = onAddChecklistClick,
                        )
                    }
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = ChecklistGridMinColumnSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingLg,
            bottom = contentBottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        item(
            key = "my_lists_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            MyListsHeader(count = screenState.checklists.size)
        }

        items(
            items = screenState.checklists,
            key = { it.checklist.id },
        ) { checklistWithProgress ->
            ChecklistCard(
                checklistWithProgress = checklistWithProgress,
                isEditMode = false,
                isDragging = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChecklistClick(checklistWithProgress) },
            )
        }

        val limits = screenState.userLimits
        if (limits != null && !limits.isPremium) {
            item(
                key = "upgrade_banner",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                PremiumBanner(
                    isActive = false,
                    formattedExpirationDate = null,
                    onUpgradeClick = onPremiumBannerClick,
                    onSubscriptionClick = onPremiumBannerClick,
                )
            }
        }
    }
}

/**
 * Section header for the "My lists" group — uppercase caps label + count, per the
 * Gisti variant-D design (gisti-screens.jsx RxHome "My lists" header).
 */
@Composable
private fun MyListsHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.main_my_lists).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * Home list card — delegates to the design-system [ChecklistListCard], mapping the domain
 * [ChecklistWithProgress] to primitives (the design-system module must not depend on the
 * feature/checklist domain model). The avatar tint + monogram are derived from the
 * checklist id (the model has no emoji/color field), so this is a pure UI concern.
 *
 * Tap / long-press are passed via [onClick]/[onLongClick] and handled INSIDE the card (ripple
 * clipped to the rounded corners). Drag/wiggle modifiers (graphicsLayer, longPressDraggableHandle)
 * stay on [modifier] so the elevation shadow + drag transform are not clipped. In edit mode the
 * caller passes onClick = null (the drag handle owns the gesture). [isEditMode] / [isDragging]
 * remain in the signature for call-site compatibility; they are no longer read here.
 */
@Composable
private fun ChecklistCard(
    checklistWithProgress: ChecklistWithProgress,
    @Suppress("UNUSED_PARAMETER") isEditMode: Boolean,
    @Suppress("UNUSED_PARAMETER") isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    ChecklistListCard(
        name = checklistWithProgress.checklist.name,
        checkedItems = checklistWithProgress.checkedItems,
        totalItems = checklistWithProgress.totalItems,
        seed = checklistWithProgress.checklist.id,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/**
 * A single hero template chip (chipKey + localized prompt + label). The chipKey is stable for
 * analytics; the prompt is the resolved create-instruction sent to the AI; the label is the
 * short user-facing text.
 */
private data class ActivationChip(
    val key: String,
    val label: String,
    val prompt: String,
)

/**
 * New-user AI first-run hero — replaces the plain [EmptyState] on the empty MainScreen when the
 * `activation_bundle_v1` bundle is ON. Shows a short prompt, a free-text input ("Describe anything
 * — I'll build a checklist"), a primary "Build my checklist" action, and a curated row of template
 * chips. Typed text and chips both flow up to the host (App.kt), which routes to the Analyze screen
 * with autoAnalyze = true so the AI GENERATES the checklist items and the user lands on the result
 * preview (AI-generated items → edit → Create) — the flagship "turn anything into a checklist" flow.
 *
 * Fires [AnalyticsEvents.Activation.FIRST_RUN_SHOWN] once on first composition (this branch only
 * renders under the treatment arm). Chip taps are reported by the host via [onChipTapped].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivationHero(
    onGenerate: (String) -> Unit,
    onChipTapped: (String, String) -> Unit,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) {
        analyticsTracker.event(AnalyticsEvents.Activation.FIRST_RUN_SHOWN)
    }

    // Each chip / typed topic sends "Create a checklist for <topic>" as the analysis input; the
    // host routes it to the Analyze screen (autoAnalyze) where generate_checklist turns it into
    // AI-generated items shown in a preview the user confirms. The create-prefix is resolved once;
    // labels per chip are localized.
    val createPrefix = stringResource(Res.string.main_create_with_ai_prefill)
    val chips = listOf(
        ActivationChip("trip", stringResource(Res.string.activation_chip_trip), createPrefix + stringResource(Res.string.activation_chip_trip)),
        ActivationChip("groceries", stringResource(Res.string.activation_chip_groceries), createPrefix + stringResource(Res.string.activation_chip_groceries)),
        ActivationChip("morning", stringResource(Res.string.activation_chip_morning), createPrefix + stringResource(Res.string.activation_chip_morning)),
        ActivationChip("work", stringResource(Res.string.activation_chip_work), createPrefix + stringResource(Res.string.activation_chip_work)),
        ActivationChip("workout", stringResource(Res.string.activation_chip_workout), createPrefix + stringResource(Res.string.activation_chip_workout)),
        ActivationChip("party", stringResource(Res.string.activation_chip_party), createPrefix + stringResource(Res.string.activation_chip_party)),
    )

    var input by remember { mutableStateOf("") }
    val canSend = input.isNotBlank()
    val submit: () -> Unit = {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            onGenerate(createPrefix + trimmed)
            input = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.activation_hero_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.activation_hero_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        AppTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = stringResource(Res.string.activation_hero_input_placeholder),
            singleLine = false,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        AppButton(
            text = stringResource(Res.string.activation_hero_send),
            onClick = submit,
            enabled = canSend,
            icon = Icons.Outlined.AutoAwesome,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            chips.forEach { chip ->
                ActivationHeroChip(
                    label = chip.label,
                    onClick = { onChipTapped(chip.key, chip.prompt) },
                )
            }
        }
    }
}

/**
 * Hero template chip — mirrors the design-system [com.antonchuraev.homesearchchecklist.desingsystem.components.gisti]
 * prompt-chip look (full-pill Surface, surfaceContainerLowest + outlineVariant hairline, ripple
 * clipped to the pill). Local to the hero because its semantics (create-with-topic) differ from the
 * GistiQuickAction chip set.
 */
@Composable
private fun ActivationHeroChip(
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(38.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}
