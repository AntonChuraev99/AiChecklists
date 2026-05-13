package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_empty_cta
import aichecklists.core.designsystem.generated.resources.calendar_empty_description
import aichecklists.core.designsystem.generated.resources.calendar_empty_title
import aichecklists.core.designsystem.generated.resources.calendar_error_retry
import aichecklists.core.designsystem.generated.resources.calendar_error_title
import aichecklists.core.designsystem.generated.resources.calendar_grid_week_label
import aichecklists.core.designsystem.generated.resources.calendar_teaser_cta
import aichecklists.core.designsystem.generated.resources.calendar_teaser_description
import aichecklists.core.designsystem.generated.resources.calendar_teaser_dismiss
import aichecklists.core.designsystem.generated.resources.calendar_teaser_title
import aichecklists.core.designsystem.generated.resources.calendar_title
import aichecklists.core.designsystem.generated.resources.today_open_menu
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// CalendarScreen — Agenda + Week-grid view
//
// Structure:
//   CalendarScreen (scaffold shell + state routing)
//   └── CalendarContent (top banner + LazyColumn)
//       ├── PremiumTeaserChip  — free, chip not dismissed
//       ├── WeekGridContent    — premium
//       └── AgendaItems        — DateHeader + ReminderRow via LazyColumn
//   CalendarEmptyState — no reminders in window
//   CalendarErrorState  — repository error + retry
//   CalendarLoadingContent — initial load spinner
//
// Design decisions:
//  - Flat LazyColumn with stickyHeader for DateHeaders (matches TodayScreen).
//  - ReminderRow: flat ListItem-style, no AppCard per row (same rationale as Today).
//  - WeekGridContent: 7 equal weight(1f) cells, scroll-to-DateHeader on tap via
//    LazyListState.animateScrollToItem (UI-only side-effect, no ViewModel intent).
//  - PremiumTeaserChip: AppCard container + AppButtonText CTA + Close IconButton.
//  - Past-due DateHeader uses error color + Alarm icon (soft warning, not alarming).
//  - Accessibility: each ReminderRow has merged semantics (role=Button + full desc).
// ---------------------------------------------------------------------------

/**
 * Stateless Calendar screen composable.
 *
 * Receives [state] and [onIntent] from [CalendarRoute] — no ViewModel access here.
 * Renders [AppScaffold] with a hamburger icon that opens [drawerState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarState,
    drawerState: DrawerState,
    onIntent: (CalendarIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = stringResource(Res.string.calendar_title),
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
        when (state) {
            CalendarState.Loading -> CalendarLoadingContent()
            is CalendarState.Content -> CalendarContent(state = state, onIntent = onIntent)
            is CalendarState.Empty -> CalendarEmptyState(state = state, onIntent = onIntent)
            is CalendarState.Error -> CalendarErrorState(state = state, onIntent = onIntent)
        }
    }
}

// ---------------------------------------------------------------------------
// Loading
// ---------------------------------------------------------------------------

@Composable
private fun CalendarLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------------
// Content — top banner (teaser / grid) + agenda LazyColumn
// ---------------------------------------------------------------------------

@Composable
private fun CalendarContent(
    state: CalendarState.Content,
    onIntent: (CalendarIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Build a flat index map: epochDay → LazyColumn item index (for grid cell scroll).
    // Headers are stickyHeader items; we need the position of the matching sticky item.
    // We do a single pass here at composition time (cheap for ~35 agenda items max).
    val headerIndexMap: Map<Long, Int> = buildHeaderIndexMap(state.agenda)

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Top banner: teaser chip OR week grid ----
        when {
            state.isPremium -> WeekGridContent(
                agenda = state.agenda,
                onCellClick = { epochDay ->
                    val index = headerIndexMap[epochDay] ?: return@WeekGridContent
                    scope.launch { listState.animateScrollToItem(index) }
                },
            )
            !state.isChipDismissed -> PremiumTeaserChip(onIntent = onIntent)
            else -> Unit
        }

        // ---- Agenda list ----
        AgendaListContent(
            agenda = state.agenda,
            listState = listState,
            onReminderClick = { info -> onIntent(CalendarIntent.OnReminderClick(info)) },
        )
    }
}

// ---------------------------------------------------------------------------
// AgendaListContent — LazyColumn with stickyHeader + items
// ---------------------------------------------------------------------------

@Composable
private fun AgendaListContent(
    agenda: List<AgendaItem>,
    listState: LazyListState,
    onReminderClick: (TodayReminderInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppDimens.SpacingXl),
    ) {
        // Render each AgendaItem — DateHeaders as stickyHeader, ReminderRows as items.
        // We iterate the flat list manually to mix stickyHeader + item calls.
        // Group consecutive same-type items: DateHeader → stickyHeader, ReminderRow → item.
        agenda.forEach { agendaItem ->
            when (agendaItem) {
                is AgendaItem.DateHeader -> {
                    stickyHeader(key = "header:${agendaItem.epochDay}") {
                        CalendarDateHeader(header = agendaItem)
                    }
                }
                is AgendaItem.ReminderRow -> {
                    item(key = "reminder:${agendaItem.info.reminderAt}:${agendaItem.info.checklistId}") {
                        CalendarReminderRow(
                            info = agendaItem.info,
                            onClick = { onReminderClick(agendaItem.info) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds a map of epochDay → approximate flat index in the LazyColumn for scroll targeting.
 * Only DateHeader items are tracked (stickyHeader index in the combined list).
 */
private fun buildHeaderIndexMap(agenda: List<AgendaItem>): Map<Long, Int> {
    val map = mutableMapOf<Long, Int>()
    agenda.forEachIndexed { index, item ->
        if (item is AgendaItem.DateHeader) {
            map[item.epochDay] = index
        }
    }
    return map
}

// ---------------------------------------------------------------------------
// DateHeader — sticky section separator
// ---------------------------------------------------------------------------

@Composable
private fun CalendarDateHeader(
    header: AgendaItem.DateHeader,
    modifier: Modifier = Modifier,
) {
    val bgColor = MaterialTheme.colorScheme.background
    val labelColor = if (header.isPastDue) {
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
        if (header.isPastDue) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
        Text(
            text = header.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// ReminderRow — flat ListItem-style row (no AppCard, matches TodayScreen)
// ---------------------------------------------------------------------------

@Composable
private fun CalendarReminderRow(
    info: TodayReminderInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isItemLevel = info is TodayReminderInfo.ItemLevel
    val primaryText = when (info) {
        is TodayReminderInfo.ItemLevel -> info.itemText
        is TodayReminderInfo.ChecklistLevel -> info.checklistName
    }
    val timeLabel = formatReminderTime(info.reminderAt)

    val rowDescription = buildString {
        append(timeLabel)
        append(" — ")
        if (isItemLevel) {
            append(primaryText)
            append(" in ")
        }
        append(info.checklistName)
        if (info.isRecurring) append(", recurring")
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
            // Leading icon: recurring → bell, one-shot → alarm clock
            Icon(
                imageVector = if (info.isRecurring) Icons.Outlined.Notifications else Icons.Outlined.Alarm,
                contentDescription = null, // merged via parent semantics
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )

            Spacer(modifier = Modifier.width(AppDimens.SpacingLg))

            // Text block — primary + supporting
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Show parent checklist name only for per-item reminders
                    if (isItemLevel) {
                        Text(
                            text = info.checklistName,
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
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(
                start = AppDimens.ScreenPaddingHorizontal + AppDimens.IconSizeMd + AppDimens.SpacingLg,
            ),
            thickness = AppDimens.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Formats epoch millis to HH:mm string, KMP-safe (no java.text.SimpleDateFormat).
 */
private fun formatReminderTime(reminderAt: Long): String {
    val instant = Instant.fromEpochMilliseconds(reminderAt)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val h = local.hour.toString().padStart(2, '0')
    val m = local.minute.toString().padStart(2, '0')
    return "$h:$m"
}

// ---------------------------------------------------------------------------
// PremiumTeaserChip — free users, chip not dismissed (U4)
// ---------------------------------------------------------------------------

@Composable
private fun PremiumTeaserChip(
    onIntent: (CalendarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.padding(
            horizontal = AppDimens.SpacingLg,
            vertical = AppDimens.SpacingMd,
        ),
        contentPadding = PaddingValues(
            horizontal = AppDimens.SpacingMd,
            vertical = AppDimens.SpacingSm,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: calendar-week icon + title + description
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarViewWeek,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
                Column {
                    Text(
                        text = stringResource(Res.string.calendar_teaser_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.calendar_teaser_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Right: CTA button + dismiss icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButtonText(
                    text = stringResource(Res.string.calendar_teaser_cta),
                    onClick = { onIntent(CalendarIntent.OnUpgradeToGridClick) },
                )
                IconButton(onClick = { onIntent(CalendarIntent.OnDismissTeaser) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.calendar_teaser_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// WeekGridContent — premium users — Mon-Sun 7-cell row (U4)
// ---------------------------------------------------------------------------

@Composable
private fun WeekGridContent(
    agenda: List<AgendaItem>,
    onCellClick: (epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compute current week Monday (today's weekday offset back to Monday).
    val today = Instant.fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val mondayOffset = today.dayOfWeek.ordinal // Mon=0 … Sun=6 in kotlinx.datetime
    val monday = today.minus(mondayOffset, DateTimeUnit.DAY)
    val sunday = monday.plus(6, DateTimeUnit.DAY)

    // Collect dates that have at least one reminder.
    val datesWithReminders: Set<Long> = agenda
        .filterIsInstance<AgendaItem.ReminderRow>()
        .map { row ->
            Instant.fromEpochMilliseconds(row.info.reminderAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
        }
        .toSet()

    // Week label: e.g. "May 12 — May 18"
    val weekLabel = buildString {
        append(formatShortDate(monday))
        append(" — ")
        append(formatShortDate(sunday))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        Text(
            text = stringResource(Res.string.calendar_grid_week_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = weekLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            for (dayOffset in 0..6) {
                val cellDate = monday.plus(dayOffset, DateTimeUnit.DAY)
                val cellEpochDay = cellDate.toEpochDays().toLong()
                val isToday = cellDate == today
                val hasReminder = cellEpochDay in datesWithReminders

                WeekGridCell(
                    date = cellDate,
                    isToday = isToday,
                    hasReminder = hasReminder,
                    onClick = { onCellClick(cellEpochDay) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = AppDimens.DividerThickness,
    )
}

@Composable
private fun WeekGridCell(
    date: LocalDate,
    isToday: Boolean,
    hasReminder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Weekday letter: Mon → "M", Tue → "T", etc.
    val weekdayLetter = date.dayOfWeek.name.first().toString()

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AppDimens.SpacingSm))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = weekdayLetter,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        // Dot indicator — only when reminders exist on this date
        if (hasReminder) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        } else {
            // Reserve space so cells without dot have consistent height
            Spacer(modifier = Modifier.height(7.dp))
        }
    }
}

/** Formats a LocalDate to "Mon D" short form, e.g. "May 12". KMP-safe, no locale. */
private fun formatShortDate(date: LocalDate): String {
    val monthAbbrev = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$monthAbbrev ${date.dayOfMonth}"
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun CalendarEmptyState(
    state: CalendarState.Empty,
    onIntent: (CalendarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.CalendarMonth,
        title = stringResource(Res.string.calendar_empty_title),
        description = stringResource(Res.string.calendar_empty_description),
        modifier = modifier,
        action = {
            AppButton(
                text = stringResource(Res.string.calendar_empty_cta),
                onClick = { onIntent(CalendarIntent.OnCreateChecklistClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ---------------------------------------------------------------------------
// Error state
// ---------------------------------------------------------------------------

@Composable
private fun CalendarErrorState(
    state: CalendarState.Error,
    onIntent: (CalendarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(Res.string.calendar_error_title),
        description = state.message,
        modifier = modifier,
        action = {
            AppButton(
                text = stringResource(Res.string.calendar_error_retry),
                onClick = { onIntent(CalendarIntent.OnRetry) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ---------------------------------------------------------------------------
// Note: Compose Previews for CalendarScreen live in androidMain.
// Add previews in:
//   feature/home/src/androidMain/.../calendar/CalendarScreenPreviews.kt
// ---------------------------------------------------------------------------
