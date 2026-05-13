package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_empty_cta
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.today_title
import aichecklists.core.designsystem.generated.resources.calendar_empty_description
import aichecklists.core.designsystem.generated.resources.calendar_empty_title
import aichecklists.core.designsystem.generated.resources.calendar_error_retry
import aichecklists.core.designsystem.generated.resources.calendar_error_title
import aichecklists.core.designsystem.generated.resources.calendar_grid_week_label
import aichecklists.core.designsystem.generated.resources.calendar_next_week
import aichecklists.core.designsystem.generated.resources.calendar_prev_week
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
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayBody
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
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
    todayState: TodayScreenState,
    calendarState: CalendarState,
    drawerState: DrawerState,
    onTodayReminderClick: (checklistId: Long, fillId: Long?) -> Unit,
    onTodayCreateChecklistClick: () -> Unit,
    onCalendarIntent: (CalendarIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

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
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(Res.string.today_title)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(Res.string.calendar_nav_label)) },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> TodayBody(
                        state = todayState,
                        onReminderClick = onTodayReminderClick,
                        onCreateChecklistClick = onTodayCreateChecklistClick,
                    )
                    1 -> CalendarTabBody(
                        state = calendarState,
                        onIntent = onCalendarIntent,
                    )
                }
            }
        }
    }
}

/** Calendar tab body (was the body of the standalone CalendarScreen). */
@Composable
private fun CalendarTabBody(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
) {
    when (state) {
        CalendarState.Loading -> CalendarLoadingContent()
        is CalendarState.Content -> CalendarContent(state = state, onIntent = onIntent)
        CalendarState.Empty -> CalendarEmptyState(onIntent = onIntent)
        is CalendarState.Error -> CalendarErrorState(state = state, onIntent = onIntent)
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

    // Briefly highlight the DateHeader matching a tapped week-grid cell.
    // Works regardless of whether LazyColumn can actually scroll — if agenda
    // fits the viewport, scroll is a no-op but the highlight still gives
    // unambiguous "yes, I targeted this date" feedback.
    var tappedEpochDay by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(tappedEpochDay) {
        if (tappedEpochDay != null) {
            kotlinx.coroutines.delay(900)
            tappedEpochDay = null
        }
    }

    // First-open auto-scroll: jump to today's DateHeader so the user lands on
    // "now" rather than on -7 days of past history. Gated by rememberSaveable
    // so manual scrolling past this point is preserved on tab switch / config
    // change (we don't snap back to today every time CalendarContent recomposes).
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(headerIndexMap, didInitialScroll) {
        if (didInitialScroll) return@LaunchedEffect
        if (headerIndexMap.isEmpty()) return@LaunchedEffect
        val todayDate = Instant.fromEpochMilliseconds(currentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val todayEpochDay = todayDate.toEpochDays().toLong()
        val index = headerIndexMap[todayEpochDay] ?: return@LaunchedEffect
        listState.scrollToItem(index)
        didInitialScroll = true
    }

    // Derive the epochDay of the agenda's currently-visible "leading" DateHeader.
    // Walking backward from firstVisibleItemIndex finds the section the user is
    // currently looking at. WeekGridContent uses this to (a) align its week to
    // the visible date and (b) underline the matching day cell.
    val firstVisibleEpochDay by remember(state.agenda) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            var i = firstVisible.coerceAtMost(state.agenda.lastIndex)
            while (i >= 0) {
                val item = state.agenda.getOrNull(i)
                if (item is AgendaItem.DateHeader && item.epochDay != Long.MIN_VALUE) {
                    return@derivedStateOf item.epochDay
                }
                i--
            }
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Top banner: week grid (free for everyone — monetization TBD) ----
        WeekGridContent(
            agenda = state.agenda,
            currentVisibleEpochDay = firstVisibleEpochDay,
            onCellClick = { epochDay ->
                tappedEpochDay = epochDay
                headerIndexMap[epochDay]?.let { index ->
                    scope.launch { listState.animateScrollToItem(index) }
                }
            },
            onPrevWeekClick = {
                val anchor = firstVisibleEpochDay
                if (anchor != null) {
                    val target = weekMondayEpochDay(anchor) - 7
                    val index = headerIndexMap[target]
                        ?: headerIndexMap.entries
                            .filter { it.key < anchor }
                            .maxByOrNull { it.key }
                            ?.value
                    if (index != null) {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                }
            },
            onNextWeekClick = {
                val anchor = firstVisibleEpochDay
                if (anchor != null) {
                    val target = weekMondayEpochDay(anchor) + 7
                    val index = headerIndexMap[target]
                        ?: headerIndexMap.entries
                            .filter { it.key > anchor }
                            .minByOrNull { it.key }
                            ?.value
                    if (index != null) {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                }
            },
        )

        // ---- Agenda list ----
        AgendaListContent(
            agenda = state.agenda,
            listState = listState,
            highlightedEpochDay = tappedEpochDay,
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
    highlightedEpochDay: Long?,
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
                        CalendarDateHeader(
                            header = agendaItem,
                            isHighlighted = agendaItem.epochDay == highlightedEpochDay,
                        )
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
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val targetBg = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 250),
        label = "calendar_header_highlight",
    )
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
// WeekGridContent — Mon-Sun 7-cell row above agenda
// ---------------------------------------------------------------------------

@Composable
private fun WeekGridContent(
    agenda: List<AgendaItem>,
    currentVisibleEpochDay: Long?,
    onCellClick: (epochDay: Long) -> Unit,
    onPrevWeekClick: () -> Unit,
    onNextWeekClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Anchor the displayed week on the agenda's currently-visible date.
    // Falls back to "today" when nothing is visible yet (initial composition).
    val today = Instant.fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val anchorDate = currentVisibleEpochDay?.let {
        kotlinx.datetime.LocalDate.fromEpochDays(it.toInt())
    } ?: today
    val mondayOffset = anchorDate.dayOfWeek.ordinal // Mon=0 … Sun=6
    val monday = anchorDate.minus(mondayOffset, DateTimeUnit.DAY)
    val sunday = monday.plus(6, DateTimeUnit.DAY)

    // Collect dates that have at least one reminder.
    val datesWithReminders: Set<Long> = agenda
        .filterIsInstance<AgendaItem.ReminderRow>()
        .map { row ->
            Instant.fromEpochMilliseconds(row.info.reminderAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
        }
        .toSet()

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevWeekClick,
                modifier = Modifier.size(AppDimens.IconSizeMd + AppDimens.SpacingMd),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(Res.string.calendar_prev_week),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onNextWeekClick,
                modifier = Modifier.size(AppDimens.IconSizeMd + AppDimens.SpacingMd),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(Res.string.calendar_next_week),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            for (dayOffset in 0..6) {
                val cellDate = monday.plus(dayOffset, DateTimeUnit.DAY)
                val cellEpochDay = cellDate.toEpochDays().toLong()
                val isToday = cellDate == today
                val isPast = cellDate < today
                val hasReminder = cellEpochDay in datesWithReminders
                val isCurrentScroll = cellEpochDay == currentVisibleEpochDay

                WeekGridCell(
                    date = cellDate,
                    isToday = isToday,
                    isPast = isPast,
                    hasReminder = hasReminder,
                    isCurrentScroll = isCurrentScroll,
                    onClick = { onCellClick(cellEpochDay) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Scroll-position indicator: a single primary-colored bar that slides
        // horizontally under the day cell whose date matches the agenda's
        // currently-visible DateHeader. M3 tab-indicator pattern with a tween
        // slide animation between positions.
        Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
        val currentIdx = (0..6).firstOrNull { dayOffset ->
            monday.plus(dayOffset, DateTimeUnit.DAY).toEpochDays().toLong() ==
                currentVisibleEpochDay
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            val totalSpacing = AppDimens.SpacingXs * 6
            val cellW = (this.maxWidth - totalSpacing) / 7
            val indicatorW = cellW * 0.6f
            val centerOffset = (cellW - indicatorW) / 2
            val targetX = if (currentIdx != null) {
                (cellW + AppDimens.SpacingXs) * currentIdx + centerOffset
            } else {
                0.dp
            }
            val animX by animateDpAsState(
                targetValue = targetX,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "calendar_indicator_x",
            )
            if (currentIdx != null) {
                Box(
                    modifier = Modifier
                        .offset(x = animX)
                        .width(indicatorW)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = AppDimens.DividerThickness,
    )
}

/** Compute the epoch-day of the Monday of the week containing [anchorEpochDay]. */
private fun weekMondayEpochDay(anchorEpochDay: Long): Long {
    val date = LocalDate.fromEpochDays(anchorEpochDay.toInt())
    val mondayOffset = date.dayOfWeek.ordinal // Mon=0 … Sun=6
    return date.minus(mondayOffset, DateTimeUnit.DAY).toEpochDays().toLong()
}

@Composable
private fun WeekGridCell(
    date: LocalDate,
    isToday: Boolean,
    isPast: Boolean,
    hasReminder: Boolean,
    isCurrentScroll: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tappable on today, past-with-history, or future-with-reminders.
    val isInteractive = hasReminder || isToday
    val bgColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    // Past days are dimmed (history) regardless of whether they have content.
    // Future days without reminders are also dimmed (nothing scheduled).
    // Today stays full-opacity (primaryContainer signals it).
    val textColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        !isInteractive -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Weekday letter: Mon → "M", Tue → "T", etc.
    val weekdayLetter = date.dayOfWeek.name.first().toString()

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AppDimens.SpacingSm))
            .background(bgColor)
            .then(if (isInteractive) Modifier.clickable(onClick = onClick) else Modifier)
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
        Spacer(modifier = Modifier.height(4.dp))
        // Bottom slot: dot for dates with reminders; fixed-size spacer otherwise.
        // (Current-scroll indicator lives as a text underline on the number above.)
        when {
            hasReminder -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isPast) 0.45f else 1f,
                        ),
                        CircleShape,
                    ),
            )
            else -> Spacer(modifier = Modifier.height(6.dp))
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
