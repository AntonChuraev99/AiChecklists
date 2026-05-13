package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.main_create_checklist
import aichecklists.core.designsystem.generated.resources.today_all_done_description
import aichecklists.core.designsystem.generated.resources.today_all_done_title
import aichecklists.core.designsystem.generated.resources.today_empty_state_description
import aichecklists.core.designsystem.generated.resources.today_empty_state_title
import aichecklists.core.designsystem.generated.resources.today_no_checklists_description
import aichecklists.core.designsystem.generated.resources.today_open_menu
import aichecklists.core.designsystem.generated.resources.today_section_past_due
import aichecklists.core.designsystem.generated.resources.today_section_today
import aichecklists.core.designsystem.generated.resources.today_title
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// TodayScreen — "My Day" view
//
// Displays all reminders (checklist-level + item-level) that fire today,
// grouped into "Past due" (reminderAt < now, within today) and "Today"
// (reminderAt >= now, within today) sections via stickyHeader.
//
// This file is UI-only. ViewModel + data wiring is handled by @android-expert.
// State is modelled here with stub data classes so the composable is
// testable and previewable without Koin/Room dependencies.
//
// Design decisions:
//  - ListItem-style rows (no AppCard per item) — cleaner, TickTick-like.
//    Cards add visual weight; a flat list with dividers is more appropriate
//    for a dense reminder-view. AppCard is reserved for checklist tiles on
//    the Lists tab where hierarchy needs more prominence.
//  - stickyHeader for sections — standard LazyColumn pattern.
//  - "Past due" section uses errorContainer/onErrorContainer for soft accent
//    without being alarming. MD3 error role is for validation; error-container
//    (lighter) is the right choice for a "gentle warning" state.
//  - Empty state reuses the design-system EmptyState component as required.
//  - Loading state uses CircularProgressIndicator (full-screen centered) —
//    consistent with existing screens (MainScreen.kt:132).
//  - Accessibility: each reminder row has semantics role Button + merged
//    contentDescription combining time + item + checklist name.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Data models (stub — ViewModel will supply real instances)
// ---------------------------------------------------------------------------

/**
 * A single reminder entry shown in TodayScreen.
 *
 * @param id Stable unique key for `items(key = …)`.
 * @param itemName The name of the checklist item (or null if checklist-level reminder).
 * @param checklistName Parent checklist title — always shown as context.
 * @param checklistId Used for navigation to ChecklistDetail.
 * @param fillId Used for navigation to FillDetail (may be null for checklist reminders).
 * @param timeLabel Human-readable time string, e.g. "14:30" or "09:00".
 * @param isPastDue True if reminderAt < nowMillis within today.
 * @param isRecurring True if this is a recurring reminder (visual indicator only).
 */
data class TodayReminderItem(
    val id: String,
    val itemName: String?,
    val checklistName: String,
    val checklistId: Long,
    val fillId: Long?,
    val timeLabel: String,
    val isPastDue: Boolean,
    val isRecurring: Boolean = false,
)

/**
 * Screen state for TodayScreen.
 * ViewModel emits one of these states; UI renders accordingly.
 */
sealed interface TodayScreenState : State {
    data object Loading : TodayScreenState
    data object Empty : TodayScreenState

    /** All reminders done for today (checked or completed). */
    data object AllDone : TodayScreenState

    /** No checklists created yet — show onboarding-promo state. */
    data object NoChecklists : TodayScreenState

    data class Success(
        val dateLabel: String,          // e.g. "Tuesday, May 6"
        val pastDue: List<TodayReminderItem>,
        val today: List<TodayReminderItem>,
    ) : TodayScreenState
}

// ---------------------------------------------------------------------------
// TodayScreen — root composable
// ---------------------------------------------------------------------------

/**
 * "Today" (My Day) screen — shows all reminders scheduled for today.
 *
 * @param state Current screen state (Loading / Empty / AllDone / NoChecklists / Success).
 * @param drawerState Lifted [DrawerState] from App.kt — hamburger opens it.
 * @param onReminderClick Navigation callback. Receives checklistId and optional fillId.
 *        - If fillId != null → navigate to FillDetail(fillId)
 *        - If fillId == null → navigate to ChecklistDetail(checklistId)
 * @param onCreateChecklistClick Called from NoChecklists empty state CTA.
 * @param modifier Optional modifier for the root scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayScreenState,
    drawerState: DrawerState,
    onReminderClick: (checklistId: Long, fillId: Long?) -> Unit,
    onCreateChecklistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = stringResource(Res.string.today_title),
        navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(Res.string.today_open_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        TodayBody(
            state = state,
            onReminderClick = onReminderClick,
            onCreateChecklistClick = onCreateChecklistClick,
        )
    }
}

/**
 * Renders the Today screen body (without the [AppScaffold] wrapper).
 *
 * Used directly inside the Calendar tab host so the tab area gets the Today
 * agenda content without nesting a second top bar. The standalone
 * [TodayScreen] composable wraps this body in [AppScaffold].
 */
@Composable
fun TodayBody(
    state: TodayScreenState,
    onReminderClick: (checklistId: Long, fillId: Long?) -> Unit,
    onCreateChecklistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            TodayScreenState.Loading -> TodayLoadingContent()

            TodayScreenState.Empty -> EmptyState(
                icon = Icons.Outlined.WbSunny,
                title = stringResource(Res.string.today_empty_state_title),
                description = stringResource(Res.string.today_empty_state_description),
            )

            TodayScreenState.AllDone -> EmptyState(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(Res.string.today_all_done_title),
                description = stringResource(Res.string.today_all_done_description),
            )

            TodayScreenState.NoChecklists -> EmptyState(
                icon = Icons.Outlined.WbSunny,
                title = stringResource(Res.string.today_empty_state_title),
                description = stringResource(Res.string.today_no_checklists_description),
                action = {
                    com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton(
                        text = stringResource(Res.string.main_create_checklist),
                        onClick = onCreateChecklistClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            )

            is TodayScreenState.Success -> TodaySuccessContent(
                state = state,
                onReminderClick = onReminderClick,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Loading state — 3 skeleton rows
// ---------------------------------------------------------------------------

@Composable
private fun TodayLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------------
// Success state — date header + LazyColumn with sticky section headers
// ---------------------------------------------------------------------------

@Composable
private fun TodaySuccessContent(
    state: TodayScreenState.Success,
    onReminderClick: (checklistId: Long, fillId: Long?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppDimens.SpacingXl),
    ) {
        // ---- Date header ----
        item(key = "date_header") {
            TodayDateHeader(dateLabel = state.dateLabel)
        }

        // ---- "Past due" section ----
        if (state.pastDue.isNotEmpty()) {
            stickyHeader(key = "header_past_due") {
                TodaySectionHeader(
                    title = stringResource(Res.string.today_section_past_due),
                    isPastDue = true,
                )
            }
            items(
                items = state.pastDue,
                key = { it.id },
            ) { reminder ->
                TodayReminderRow(
                    item = reminder,
                    onClick = { onReminderClick(reminder.checklistId, reminder.fillId) },
                )
            }
        }

        // ---- "Today" section ----
        if (state.today.isNotEmpty()) {
            stickyHeader(key = "header_today") {
                TodaySectionHeader(
                    title = stringResource(Res.string.today_section_today),
                    isPastDue = false,
                )
            }
            items(
                items = state.today,
                key = { it.id },
            ) { reminder ->
                TodayReminderRow(
                    item = reminder,
                    onClick = { onReminderClick(reminder.checklistId, reminder.fillId) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Date header — e.g. "Tuesday, May 6"
// ---------------------------------------------------------------------------

@Composable
private fun TodayDateHeader(
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingMd),
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Section header — sticky, colored differently for "Past due"
// ---------------------------------------------------------------------------

@Composable
private fun TodaySectionHeader(
    title: String,
    isPastDue: Boolean,
    modifier: Modifier = Modifier,
) {
    // Sticky headers need a background to cover scrolled content.
    val bgColor = MaterialTheme.colorScheme.background
    val labelColor = if (isPastDue) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        if (isPastDue) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Reminder row — ListItem-style (no AppCard — flat list is more appropriate)
// ---------------------------------------------------------------------------

/**
 * A single reminder row in the Today list.
 *
 * Layout (horizontal):
 *   [icon 24dp] [spacer 16dp] [column: itemName + checklistName / timeLabel] [spacer] [recurring badge?]
 *
 * Touch target: entire row is clickable (min 56dp height via padding).
 * Accessibility: merged semantics — screen reader reads "14:30 — Buy milk in Groceries".
 */
@Composable
private fun TodayReminderRow(
    item: TodayReminderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compose full contentDescription for screen reader
    val rowDescription = buildString {
        append(item.timeLabel)
        append(" — ")
        if (item.itemName != null) {
            append(item.itemName)
            append(" in ")
        }
        append(item.checklistName)
        if (item.isRecurring) append(", recurring")
        if (item.isPastDue) append(", past due")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingMd,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon: clock for regular, bell for recurring
            val iconTint = if (item.isPastDue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            Icon(
                imageVector = if (item.isRecurring) Icons.Outlined.Notifications else Icons.Outlined.Alarm,
                contentDescription = null, // merged via parent semantics
                tint = iconTint,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )

            Spacer(modifier = Modifier.width(AppDimens.SpacingLg))

            // Text block — takes all remaining width
            Column(modifier = Modifier.weight(1f)) {
                // Item name (primary line) OR checklist name when reminder is checklist-level
                val primaryText = item.itemName ?: item.checklistName
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isPastDue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Supporting line: context (parent checklist) + time
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Show parent checklist only for item-level reminders
                    if (item.itemName != null) {
                        Text(
                            text = item.checklistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = item.timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Divider between rows (not after last item — handled by LazyColumn gap)
        HorizontalDivider(
            modifier = Modifier.padding(start = AppDimens.ScreenPaddingHorizontal + AppDimens.IconSizeMd + AppDimens.SpacingLg),
            thickness = AppDimens.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Note: Compose Previews for TodayScreen live in androidMain.
// commonMain does not support @Preview without the multiplatform preview
// plugin. Add previews in:
//   feature/home/src/androidMain/.../today/TodayScreenPreviews.kt
// ---------------------------------------------------------------------------
