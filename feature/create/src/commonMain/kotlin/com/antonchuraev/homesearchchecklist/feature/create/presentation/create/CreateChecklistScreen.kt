package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AddItemInputField
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.InlineAddItemRow
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.formatReminderDateTime
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.resolveRepeatSummaryLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val LOG_TAG = "CreateProject"

/**
 * Minimum height of a tappable list row (template row, task row).
 *
 * Local to the file rather than an `AppDimens` token because the design system has no row-height
 * scale yet: `TextFieldHeight` and `TopBarHeight` are also 56dp but mean different things, and
 * reusing one of them would tie this row to an unrelated component's future change. Mirrors
 * `InboxTaskRowMinHeight` in `InboxScreen.kt`.
 */
private val ListRowMinHeight = 56.dp

/** Minimum width of the primary action once it stops being full-bleed (Medium / Expanded). */
private val CreateActionMinWidth = 200.dp

/** Duration of the "More options" disclosure — MD3 standard for an in-place utility transition. */
private const val DisclosureDurationMs = 300

/**
 * @param useProjectForm v2 nav arm: render the redesigned "New project" form.
 *
 * Default `false` renders the pre-redesign screen unchanged — the CONTROL arm of the live
 * `nav_variant` experiment reaches this same route, and a redesigned form on both arms would move
 * the experiment's baseline while it is being measured. The flag is read FIRST, before any window
 * size / `WindowInsets` lookup: hoisting an inset read into the screen body invalidates the whole
 * screen on every keyboard frame in BOTH arms, which this project has already been bitten by.
 */
@Composable
fun CreateChecklistScreen(
    editChecklistId: Long? = null,
    templateId: Int? = null,
    initialText: String? = null,
    // NO DEFAULT ON PURPOSE. A `= false` here is fail-soft: the one production call site that
    // forgets it ships the classic form to the v2 arm silently — no crash, no failing test, since
    // every test passes the flag explicitly and therefore verifies the reader, never the writer.
    // Requiring it makes the compiler the check.
    useProjectForm: Boolean,
    viewModel: CreateChecklistViewModel = koinViewModel(
        key = "create_checklist_${editChecklistId}_${initialText?.hashCode()}"
    ) { parametersOf(editChecklistId, initialText, useProjectForm) }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.CREATE_CHECKLIST) }

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    if (!useProjectForm) {
        LegacyCreateChecklistContent(
            state = screenState,
            templateId = templateId,
            onIntent = viewModel::sendIntent,
        )
        return
    }

    val logger: AppLogger = koinInject()
    CreateChecklistContent(
        state = screenState,
        logger = logger,
        onIntent = viewModel::sendIntent,
    )
}

// ── v1 "Classic view" arm ───────────────────────────────────────────────────────────────────────

/**
 * The screen exactly as it shipped before the v2 redesign, kept as the CONTROL arm's rendering.
 *
 * Do not "improve" anything in here: every visible difference from `master` is a difference the
 * running `nav_variant` A/B would report as an effect of the navigation change. New behaviour goes
 * into [CreateChecklistContent] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyCreateChecklistContent(
    state: CreateChecklistState,
    templateId: Int?,
    onIntent: (CreateChecklistIntent) -> Unit,
) {
    val title = if (state.isEditMode) {
        stringResource(Res.string.checklist_edit_title)
    } else {
        stringResource(Res.string.create_title)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        title = title,
        onBackButtonClick = { onIntent(CreateChecklistIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.ScreenPaddingHorizontal)
                    .padding(bottom = AppDimens.SpacingLg)
                    .navigationBarsPadding()
            ) {
                val isLocked = !state.isEditMode && !state.canCreateChecklist
                AppButton(
                    text = if (isLocked) {
                        stringResource(Res.string.unlock_more_with_premium)
                    } else {
                        stringResource(Res.string.save)
                    },
                    icon = if (isLocked) Icons.Outlined.Lock else null,
                    onClick = { onIntent(CreateChecklistIntent.OnSaveClick) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.adaptiveContentWidth(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingLg,
                bottom = AppDimens.SpacingLg
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            // Name section
            item(key = "name_section") {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                    Text(
                        text = stringResource(Res.string.create_name_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    AppTextField(
                        value = state.name,
                        onValueChange = { onIntent(CreateChecklistIntent.OnNameChange(it)) },
                        placeholder = stringResource(Res.string.create_name_placeholder),
                        isError = state.nameError != null,
                        errorMessage = state.nameError,
                        showClearButton = true
                    )

                    // "Choose from template" is offered only when creating a brand-new
                    // checklist from scratch (not in edit mode and not already seeded from a
                    // template). Templates are now reachable only from here and "Create Weekly".
                    val showChooseTemplate = !state.isEditMode && templateId == null
                    if (showChooseTemplate) {
                        AppButtonSecondary(
                            text = stringResource(Res.string.create_choose_template),
                            onClick = { onIntent(CreateChecklistIntent.OnChooseTemplateClick) },
                            icon = Icons.Outlined.GridView,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Items section header and input
            item(key = "items_header") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    modifier = Modifier.padding(top = AppDimens.SpacingMd)
                ) {
                    Text(
                        text = stringResource(Res.string.create_items_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Inline input field for adding items
                    AddItemInputField(
                        text = state.newItemText,
                        onTextChange = { onIntent(CreateChecklistIntent.OnNewItemTextChange(it)) },
                        onAdd = { onIntent(CreateChecklistIntent.OnAddItemFromInput) }
                    )
                }
            }

            // Items list (new items appear at top)
            itemsIndexed(
                items = state.items,
                key = { _, item -> item.id }
            ) { _, item ->
                val isEditing = state.editingItemId == item.id
                if (isEditing) {
                    InlineItemEditCard(
                        text = state.editingItemText,
                        onTextChange = { onIntent(CreateChecklistIntent.OnItemEditTextChange(it)) },
                        onConfirm = { onIntent(CreateChecklistIntent.OnConfirmItemEdit) },
                        onFocusLost = { onIntent(CreateChecklistIntent.OnConfirmItemEdit) },
                        confirmContentDescription = stringResource(Res.string.create_confirm_item_edit),
                        modifier = Modifier.animateItem()
                    )
                } else {
                    AppCard(modifier = Modifier.animateItem()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            // Trailing action group: Edit + Delete
                            Row(horizontalArrangement = Arrangement.End) {
                                IconButton(
                                    onClick = { onIntent(CreateChecklistIntent.OnStartItemEdit(item.id)) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(Res.string.create_edit_item),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onIntent(CreateChecklistIntent.OnDeleteItem(item)) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(Res.string.delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── v2 "New project" arm ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateChecklistContent(
    state: CreateChecklistState,
    logger: AppLogger,
    onIntent: (CreateChecklistIntent) -> Unit,
) {
    val isCompact = rememberAppWindowSizeClass() == AppWindowSizeClass.Compact
    // `null` = untouched; wide windows have vertical room to spare, so nothing is worth hiding.
    val moreOptionsExpanded = state.moreOptionsExpanded ?: !isCompact
    val isLocked = !state.isEditMode && !state.canCreateChecklist

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nameFocusRequester = remember { FocusRequester() }
    val taskFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Autofocus is one-shot AND conditional: arriving with a name already filled in (template,
    // shared text, edit) means the keyboard would cover the very content the user came for.
    val shouldAutofocusName = !state.isEditMode && !state.nameFocusConsumed && state.name.isBlank()
    LaunchedEffect(shouldAutofocusName) {
        if (!shouldAutofocusName) return@LaunchedEffect
        // Deferred one frame: a focus request issued while the Nav3 enter transition is still
        // running lands on a node that is not attached yet and is dropped silently.
        withFrameNanos { }
        runCatching { nameFocusRequester.requestFocus() }
            .onFailure { logger.warning(LOG_TAG, "name autofocus rejected: ${it.message}") }
        onIntent(CreateChecklistIntent.OnNameFocusConsumed)
    }

    // A rejected submit must bring the field back into view — an error string on a field that has
    // scrolled off the top of the form is indistinguishable from nothing happening. Both halves are
    // conditional: scrolling a form that is already at the top is a jitter, and forcing the keyboard
    // up when the field could not take focus leaves the caret nowhere.
    LaunchedEffect(state.nameErrorFocusSignal) {
        if (state.nameErrorFocusSignal == 0) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(0)
        }
        val focused = runCatching { nameFocusRequester.requestFocus() }
            .onFailure { logger.warning(LOG_TAG, "name error refocus rejected: ${it.message}") }
            .isSuccess
        if (focused) keyboard?.show()
    }

    val title = if (state.isEditMode) {
        stringResource(Res.string.checklist_edit_title)
    } else {
        stringResource(Res.string.create_project_title)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        title = title,
        startAlignedTitle = true,
        onBackButtonClick = { onIntent(CreateChecklistIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            CreateActionBar(
                isCompact = isCompact,
                isLocked = isLocked,
                isEditMode = state.isEditMode,
                isSubmitting = state.isSubmitting,
                onIntent = onIntent,
            )
        }
    ) {
        LazyColumn(
            state = listState,
            // Order matters: fillMaxSize pins minWidth == maxWidth, so the width cap only bites
            // once wrapContentWidth has released the minimum.
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .adaptiveContentWidth(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingLg,
                // Breathing room under the last row so the inline input is not pinned against the
                // action bar when bring-into-view scrolls it up. NOT imePadding(): the keyboard is
                // already accounted for by AppScaffold's bottomBar slot, and adding it here would
                // subtract the keyboard twice.
                bottom = AppDimens.SpacingXl,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "limit_banner") {
                // Animated, not inserted: `canCreateChecklist` arrives from a Flow after the first
                // frame, and a 110dp block appearing instantly shifts the form under the finger.
                val limit = state.maxChecklists
                AnimatedVisibility(
                    visible = isLocked && limit != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        LimitReachedBanner(
                            // Non-null whenever the banner is visible: both values come from the
                            // same limits emission.
                            maxChecklists = limit ?: 0,
                            onUpgradeClick = { onIntent(CreateChecklistIntent.OnLimitBannerUpgradeClick) },
                        )
                        Spacer(Modifier.height(AppDimens.SpacingLg))
                    }
                }
            }

            item(key = "name") {
                val error = state.nameError
                AppTextField(
                    value = state.name,
                    onValueChange = { onIntent(CreateChecklistIntent.OnNameChange(it)) },
                    placeholder = stringResource(Res.string.create_project_name_placeholder),
                    singleLine = true,
                    showClearButton = true,
                    enabled = !state.isSubmitting,
                    // Driven by the flag, not by the text: a rejected submit whose copy failed to
                    // resolve must still highlight the field rather than look like a dead tap.
                    isError = state.nameInvalid,
                    errorMessage = error,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        // Weekly projects have no task section, so there is nothing for Next to move
                        // to; offering it would be a key that visibly does nothing.
                        imeAction = if (state.weeklyMode) ImeAction.Done else ImeAction.Next,
                    ),
                    // Explicit target: the default traversal would hand focus to the next focusable
                    // node, which is the clickable template row, not the task input.
                    keyboardActions = KeyboardActions(
                        onDone = { keyboard?.hide() },
                        onNext = {
                            scope.launch {
                                // The inline row is the LAST item of the list and only holds its
                                // FocusRequester while composed — off-screen, requestFocus throws.
                                val last = listState.layoutInfo.totalItemsCount - 1
                                if (last >= 0) runCatching { listState.scrollToItem(last) }
                                runCatching { taskFocusRequester.requestFocus() }
                                    .onFailure { logger.warning(LOG_TAG, "task focus rejected: ${it.message}") }
                            }
                        }
                    ),
                    modifier = Modifier
                        .focusRequester(nameFocusRequester)
                        .semantics { if (error != null) error(error) },
                )
            }

            if (!state.isEditMode) {
                item(key = "template_row") {
                    Spacer(Modifier.height(AppDimens.SpacingSm))
                    ChooseTemplateRow(
                        label = stringResource(Res.string.create_choose_template),
                        onClick = { onIntent(CreateChecklistIntent.OnChooseTemplateClick) },
                    )
                }
            }

            item(key = "settings") {
                ProjectSettingsSection(
                    state = state,
                    moreOptionsExpanded = moreOptionsExpanded,
                    onIntent = onIntent,
                )
            }

            // An EXISTING weekly list is edited per weekday on the detail screen, so this form has
            // nothing true to say about its tasks — the "filled in after you create them" hint
            // would be a lie in front of a list that already has them.
            if (!state.weeklyMode || !state.isEditMode) {
                item(key = "tasks_header") {
                    Spacer(Modifier.height(AppDimens.SpacingXl))
                    SectionTitle(stringResource(Res.string.create_tasks_section))
                    Spacer(Modifier.height(AppDimens.SpacingSm))
                    when {
                        state.weeklyMode -> SectionHint(stringResource(Res.string.create_weekly_tasks_hint))
                        state.items.isEmpty() -> SectionHint(stringResource(Res.string.create_tasks_empty_hint))
                    }
                    if (state.weeklyMode || state.items.isEmpty()) {
                        Spacer(Modifier.height(AppDimens.SpacingSm))
                    }
                }
            }

            // A Weekly project is created empty and planned per weekday, so offering a task list
            // here would silently discard everything typed into it.
            if (!state.weeklyMode) {
                items(items = state.items, key = { it.id }) { item ->
                    if (state.editingItemId == item.id) {
                        InlineItemEditCard(
                            text = state.editingItemText,
                            onTextChange = { onIntent(CreateChecklistIntent.OnItemEditTextChange(it)) },
                            onConfirm = { onIntent(CreateChecklistIntent.OnConfirmItemEdit) },
                            onFocusLost = { onIntent(CreateChecklistIntent.OnConfirmItemEdit) },
                            confirmContentDescription = stringResource(Res.string.create_confirm_item_edit),
                            modifier = Modifier.animateItem(),
                        )
                    } else {
                        DraftItemRow(
                            item = item,
                            onEditClick = { onIntent(CreateChecklistIntent.OnStartItemEdit(item.id)) },
                            onDeleteClick = { onIntent(CreateChecklistIntent.OnDeleteItem(item)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                item(key = "inline_add") {
                    InlineAddItemRow(
                        text = state.newItemText,
                        canSend = state.newItemText.isNotBlank(),
                        focusRequester = taskFocusRequester,
                        onTextChange = { onIntent(CreateChecklistIntent.OnNewItemTextChange(it)) },
                        onSubmit = { onIntent(CreateChecklistIntent.OnAddItemFromInput) },
                        onFocusChanged = {},
                        enabled = !state.isSubmitting,
                        // No attachments before the project exists: they belong to a fill item, and
                        // no fill is created until addChecklist() returns.
                        onAttachClick = null,
                    )
                }
            }
        }
    }

    if (state.weeklySwitchConfirmOpen) {
        AlertDialog(
            onDismissRequest = { onIntent(CreateChecklistIntent.OnWeeklySwitchDismiss) },
            title = { Text(stringResource(Res.string.create_weekly_switch_title)) },
            text = { Text(stringResource(Res.string.create_weekly_switch_message, state.items.size)) },
            confirmButton = {
                TextButton(onClick = { onIntent(CreateChecklistIntent.OnWeeklySwitchConfirm) }) {
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(CreateChecklistIntent.OnWeeklySwitchDismiss) }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    ReminderUi(state = state, onIntent = onIntent)
}

// ── Reminder ────────────────────────────────────────────────────────────────────────────────────

/**
 * The shared reminder sheet + its date/time picker, driven entirely by staged state.
 *
 * Same component and same callback surface as the checklist-detail and onboarding flows — a second
 * reminder UI would be a second set of presets, a second repeat editor and a second set of bugs.
 * What differs is only where the result goes: nothing is persisted or scheduled here, because the
 * project has no id yet (see `CreateChecklistViewModel.applyStagedReminder`).
 */
@Composable
private fun ReminderUi(
    state: CreateChecklistState,
    onIntent: (CreateChecklistIntent) -> Unit,
) {
    if (state.reminderSheetOpen) {
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = state.activeReminderTab,
                currentReminder = state.reminderAt,
                currentRepeatRule = state.repeatRule,
                // Read by the sheet as a VISIBILITY FLAG only (`ReminderSheetState` KDoc): the card's
                // text comes from `currentRepeatRule` via `resolveRepeatSummaryLabel`, so passing a
                // caller-built string here would only re-introduce the English-only phrasing.
                repeatRuleSummary = if (state.repeatRule != null) "" else null,
                pendingRepeatConfig = state.pendingRepeatConfig,
                showEndConditionPicker = state.showEndConditionPicker,
                isLocked = state.reminderSheetLocked,
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(CreateChecklistIntent.OnReminderTabSelected(it)) },
                onPresetSelected = { onIntent(CreateChecklistIntent.OnReminderPresetSelected(it)) },
                onCustomDateRequested = { onIntent(CreateChecklistIntent.OnCustomDateRequested) },
                onRemoveReminder = { onIntent(CreateChecklistIntent.OnRemoveReminder) },
                onRepeatTypeSelected = { onIntent(CreateChecklistIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(CreateChecklistIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(CreateChecklistIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(CreateChecklistIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(CreateChecklistIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(CreateChecklistIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(CreateChecklistIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(CreateChecklistIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(CreateChecklistIntent.OnDismissEndConditionPicker) },
                onSaveRepeat = { onIntent(CreateChecklistIntent.OnSaveRepeat) },
                onRemoveRepeat = { onIntent(CreateChecklistIntent.OnRemoveRepeat) },
                onDismiss = { onIntent(CreateChecklistIntent.OnDismissReminderUI) },
                onUpgradeClick = { onIntent(CreateChecklistIntent.OnReminderUpgradeClick) },
            ),
        )
    }

    if (state.showCustomPicker) {
        ReminderDateTimePicker(
            selectedDateMillis = state.customPickerDateMillis,
            minDateMillis = state.customPickerMinDateMillis,
            initialHour = state.customPickerInitialHour,
            isTimeInPast = state.isCustomTimeInPast,
            onDateSelected = { onIntent(CreateChecklistIntent.OnCustomDateSelected(it)) },
            onTimeChanged = { h, m -> onIntent(CreateChecklistIntent.OnCustomTimeChanged(h, m)) },
            onTimeSelected = { h, m -> onIntent(CreateChecklistIntent.OnCustomTimeSelected(h, m)) },
            onDismiss = { onIntent(CreateChecklistIntent.OnDismissReminderUI) },
        )
    }
}

/** "Every 2 weeks" / "Jan 5, 09:00" / "Not set" — whichever the staged configuration amounts to. */
@Composable
private fun reminderRowValue(state: CreateChecklistState): String {
    val repeat = state.repeatRule
    if (repeat != null) return resolveRepeatSummaryLabel(repeat)
    val at = state.reminderAt
    if (at != null) {
        return formatReminderDateTime(
            Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.currentSystemDefault())
        )
    }
    return stringResource(Res.string.create_setting_reminder_none)
}

// ── Bottom action bar ───────────────────────────────────────────────────────────────────────────

/**
 * Lives in [AppScaffold]'s `bottomBar`, the only slot that pads for `ime ∪ navigationBars`: the
 * keyboard is up from the first frame here, and a button inside the scrolling content would sit
 * under it.
 *
 * Deliberately ENABLED with a blank name. A disabled button gives no reason and does nothing when
 * tapped, which reads as a frozen screen; tapping instead produces the error + scroll + refocus.
 */
@Composable
private fun CreateActionBar(
    isCompact: Boolean,
    isLocked: Boolean,
    isEditMode: Boolean,
    isSubmitting: Boolean,
    onIntent: (CreateChecklistIntent) -> Unit,
) {
    val label = when {
        isLocked -> stringResource(Res.string.unlock_more_with_premium)
        isEditMode -> stringResource(Res.string.save)
        else -> stringResource(Res.string.create_submit)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .adaptiveContentWidth()
            .padding(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = AppDimens.SpacingLg,
            )
    ) {
        if (isCompact) {
            AppButton(
                text = label,
                icon = if (isLocked) Icons.Outlined.Lock else null,
                loading = isSubmitting,
                onClick = { onIntent(CreateChecklistIntent.OnSaveClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // MD3 form convention on wide windows: the action pair sits at the trailing edge
            // instead of stretching a single button across 720dp.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButtonText(
                    text = stringResource(Res.string.cancel),
                    onClick = { onIntent(CreateChecklistIntent.OnBackClick) },
                )
                AppButton(
                    text = label,
                    icon = if (isLocked) Icons.Outlined.Lock else null,
                    loading = isSubmitting,
                    onClick = { onIntent(CreateChecklistIntent.OnSaveClick) },
                    modifier = Modifier.widthIn(min = CreateActionMinWidth),
                )
            }
        }
    }
}

// ── Blocks ──────────────────────────────────────────────────────────────────────────────────────

/**
 * States the plan ceiling BEFORE the form is filled in, instead of after "Create" is tapped.
 *
 * `primaryContainer`, not `errorContainer`: reaching a plan limit is not a mistake the user made.
 * The form stays fully usable behind it — the draft outlives the trip to the paywall.
 */
@Composable
private fun LimitReachedBanner(
    maxChecklists: Int,
    onUpgradeClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            // Announced when it arrives: the value comes from a Flow, so the banner appears after
            // the screen has already been read out.
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
                Text(
                    text = stringResource(Res.string.create_limit_reached_title, maxChecklists),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(Res.string.create_limit_reached_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            AppButtonText(
                text = stringResource(Res.string.unlock_more_with_premium),
                onClick = onUpgradeClick,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * Tonal affordance, not a second call to action.
 *
 * An outlined full-width button (what v1 used) reads at the same weight as the primary CTA — which
 * is exactly why the template gallery felt like a gate rather than an option.
 */
@Composable
private fun ChooseTemplateRow(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowMinHeight),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingMd,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}

@Composable
private fun ProjectSettingsSection(
    state: CreateChecklistState,
    moreOptionsExpanded: Boolean,
    onIntent: (CreateChecklistIntent) -> Unit,
) {
    Column(
        // Keeps TalkBack from interleaving these rows with the task list below them.
        modifier = Modifier.semantics { isTraversalGroup = true },
    ) {
        Spacer(Modifier.height(AppDimens.SpacingXl))
        SectionTitle(stringResource(Res.string.create_section_settings))
        Spacer(Modifier.height(AppDimens.SpacingSm))

        // Create-mode only, both rows. In edit mode the reminder already exists on the entity and is
        // owned by the detail screen (which can schedule against a real id), and the limits Flow is
        // not observed at all — a Weekly switch there would convert a standard list, discarding its
        // tasks, while stepping around the free weekly quota. The loaded `viewMode` is still written
        // back on save, so an existing Weekly list stays Weekly.
        if (!state.isEditMode) {
            SettingRow(
                icon = Icons.Outlined.Notifications,
                label = stringResource(Res.string.create_setting_reminder),
                supportingText = reminderRowValue(state),
                onClick = { onIntent(CreateChecklistIntent.OnReminderClick) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
            HorizontalDivider(
                thickness = AppDimens.DividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingRow(
                icon = Icons.Outlined.CalendarViewWeek,
                label = stringResource(Res.string.create_setting_weekly),
                supportingText = stringResource(Res.string.create_setting_weekly_description),
                onClick = {
                    if (state.canCreateWeekly) {
                        onIntent(CreateChecklistIntent.OnWeeklyToggled(!state.weeklyMode))
                    } else {
                        onIntent(CreateChecklistIntent.OnWeeklyLockClick)
                    }
                },
            ) {
                if (state.canCreateWeekly) {
                    // onCheckedChange = null: the whole row is the click target, and a switch that
                    // also handled the tap would toggle twice.
                    AppSwitch(checked = state.weeklyMode, onCheckedChange = null)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(Res.string.unlock_more_with_premium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }
            HorizontalDivider(
                thickness = AppDimens.DividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        MoreOptionsToggle(
            expanded = moreOptionsExpanded,
            onClick = { onIntent(CreateChecklistIntent.OnToggleMoreOptions(moreOptionsExpanded)) },
        )

        AnimatedVisibility(
            visible = moreOptionsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // The gap lives INSIDE the animated subtree: an external spacedBy would survive the
            // exit animation and leave a jumping hole where the group used to be.
            Column(modifier = Modifier.padding(top = AppDimens.SpacingSm)) {
                SettingRow(
                    icon = Icons.Outlined.Folder,
                    label = stringResource(Res.string.folders_toggle),
                    supportingText = if (state.weeklyMode) {
                        stringResource(Res.string.folders_toggle_weekly_hint)
                    } else {
                        null
                    },
                    enabled = !state.weeklyMode,
                    onClick = { onIntent(CreateChecklistIntent.OnFoldersToggled(!state.foldersEnabled)) },
                ) {
                    AppSwitch(
                        checked = state.foldersEnabled,
                        onCheckedChange = null,
                        enabled = !state.weeklyMode,
                    )
                }
                HorizontalDivider(
                    thickness = AppDimens.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingRow(
                    icon = Icons.Outlined.CheckCircle,
                    label = stringResource(Res.string.separate_completed),
                    onClick = {
                        onIntent(CreateChecklistIntent.OnSeparateCompletedToggled(!state.separateCompleted))
                    },
                ) {
                    AppSwitch(checked = state.separateCompleted, onCheckedChange = null)
                }
                HorizontalDivider(
                    thickness = AppDimens.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingRow(
                    icon = Icons.Outlined.RemoveDone,
                    label = stringResource(Res.string.auto_delete_completed),
                    onClick = {
                        onIntent(CreateChecklistIntent.OnAutoDeleteToggled(!state.autoDeleteCompleted))
                    },
                ) {
                    AppSwitch(checked = state.autoDeleteCompleted, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun MoreOptionsToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DisclosureDurationMs, easing = FastOutSlowInEasing),
        label = "more_options_chevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) {
                stringResource(Res.string.create_hide_options)
            } else {
                stringResource(Res.string.create_more_options)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(AppDimens.SpacingXs))
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            // The label already says which way this goes; a second announcement is noise.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(AppDimens.IconSizeMd)
                .rotate(rotation),
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            modifier = Modifier.size(AppDimens.IconSizeMd),
        )
        Spacer(Modifier.size(AppDimens.SpacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                // No Ellipsis on purpose: truncating the NAME of a setting is worse than wrapping
                // it, and long locales (hi, ru) run 30-50% longer than English.
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
        Spacer(Modifier.size(AppDimens.SpacingSm))
        trailing()
    }
}

@Composable
private fun DraftItemRow(
    item: ChecklistItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(Res.string.create_edit_item)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowMinHeight)
                // onClickLabel rather than an overriding contentDescription: TalkBack then reads the
                // task text and announces "double tap to edit item", instead of replacing the text
                // with the word "Edit item".
                .clickable(onClickLabel = editLabel, onClick = onEditClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = AppDimens.SpacingSm),
            )
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.delete_item),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }
        HorizontalDivider(
            thickness = AppDimens.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InlineItemEditCard(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onFocusLost: () -> Unit,
    confirmContentDescription: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Guard against the initial isFocused=false fire that happens before LaunchedEffect
    // requests focus — without it onFocusLost fires on mount and the field disappears.
    var hasBeenFocused by remember { mutableStateOf(false) }

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = false,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasBeenFocused = true
                        } else if (hasBeenFocused) {
                            onFocusLost()
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onConfirm()
                        keyboard?.hide()
                    }
                )
            )

            IconButton(
                onClick = {
                    onConfirm()
                    keyboard?.hide()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = confirmContentDescription,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}
