package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.weekly_add_for_day
import aichecklists.core.designsystem.generated.resources.weekly_overdue_section_title
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistItemCard
import org.jetbrains.compose.resources.stringResource

/**
 * Content pane for "My Week" (weekly) checklist mode.
 *
 * Renders a [LazyColumn] with:
 * - Checklist title at the top
 * - Sticky "Overdue" section with unchecked items from past weekdays (if any)
 * - 7 sticky [WeekdayHeader] sections in rolling order starting from today
 * - [ChecklistItemCard] rows per item (reuses existing composable from ChecklistDetailScreen)
 * - [WeeklyAddItemRow] at the bottom of EVERY day section (always visible)
 * - Auto-scrolls to today's section on first composition
 * - Long-press on item triggers [onItemLongPress] with the item id
 *
 * @param state checked & loaded Content state from ChecklistDetailViewModel
 * @param todayWeekday ISO weekday 1=Monday..7=Sunday, typically from kotlinx.datetime
 * @param onIntent main intent dispatcher wired to ChecklistDetailViewModel
 * @param onAddItemToDay callback for adding a new item to a specific day
 * @param onItemCheckedChange callback for toggling an item's checked state
 * @param onItemLongPress callback when user long-presses an item (opens MoveToDayBottomSheet)
 * @param onItemTap callback when user taps the right 70% of an item card (opens ItemDetailsSheet)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WeeklyChecklistDetailContent(
    state: ChecklistDetailState.Content,
    todayWeekday: Int,
    onIntent: (ChecklistDetailIntent) -> Unit,
    onAddItemToDay: (weekday: Int, text: String) -> Unit,
    onItemCheckedChange: (itemId: String, checked: Boolean) -> Unit,
    onItemLongPress: (itemId: String) -> Unit,
    onItemTap: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.defaultFill?.items.orEmpty()
    val overdueItems = remember(items, todayWeekday) { getOverdueItems(items, todayWeekday) }

    // Fixed ISO order Mon→Sun. Today is highlighted in place; we don't rotate
    // the list, so users always see Monday at top and Sunday at bottom.
    val weekOrder = ISO_WEEK_DAYS

    val addTextByDay = rememberSaveable(saver = addTextMapSaver()) {
        mutableStateMapOf<Int, String>().apply { weekOrder.forEach { put(it, "") } }
    }

    val focusRequesters = remember {
        weekOrder.associateWith { FocusRequester() }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to today's day header on first composition.
    // LazyColumn item order: [title] [overdue header + N overdue items] [for each weekday: header + items + add row].
    // We sum the lazy slots up to (but excluding) today's header so the user lands on it.
    LaunchedEffect(Unit) {
        val titleSlots = 1
        val overdueSlots = if (overdueItems.isEmpty()) 0 else (1 + overdueItems.size)
        val daysBeforeToday = weekOrder.takeWhile { it != todayWeekday }
        val daysBeforeSlots = daysBeforeToday.sumOf { day ->
            val cards = items.count { it.weekday == day && !isOverdue(it, todayWeekday) }
            2 + cards // header + cards + add row
        }
        listState.scrollToItem(titleSlots + overdueSlots + daysBeforeSlots)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppDimens.SpacingXxl),
    ) {
        item(key = "title") {
            Text(
                text = state.checklist.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppDimens.ScreenPaddingHorizontal,
                        vertical = AppDimens.SpacingMd,
                    ),
            )
        }

        // Overdue section: sticky header + items with weekday tags
        if (overdueItems.isNotEmpty()) {
            stickyHeader(key = "overdue_header") {
                OverdueHeader()
            }

            itemsIndexed(
                items = overdueItems,
                key = { _, item -> "overdue_item_${item.id}" },
            ) { _, item ->
                ChecklistItemCard(
                    item = item,
                    isDragging = false,
                    isEditMode = false,
                    wiggleAngle = 0f,
                    onCheckedChange = { checked: Boolean -> onItemCheckedChange(item.id, checked) },
                    onItemTap = { onItemTap(item.id) },
                    onLongClick = { onItemLongPress(item.id) },
                    modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                )

                // Weekday tag for overdue items — local copy so smart-cast survives
                // across the public-API module boundary (item.weekday is Int?).
                val itemWeekday = item.weekday
                if (itemWeekday != null) {
                    Text(
                        text = stringResource(weekdayNameKey(itemWeekday)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(
                                start = AppDimens.ScreenPaddingHorizontal + AppDimens.SpacingMd,
                                end = AppDimens.ScreenPaddingHorizontal,
                                bottom = AppDimens.SpacingXs,
                            )
                    )
                }

                Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
            }
        }

        weekOrder.forEach { weekday ->
            val isToday = weekday == todayWeekday
            // Show every item pinned to this weekday EXCEPT those already
            // surfaced in the Overdue section above (avoid double-render).
            // Checked items stay visible (rendered struck through by ChecklistItemCard).
            val dayItems = items.filter { it.weekday == weekday && !isOverdue(it, todayWeekday) }
            val focusRequester = focusRequesters[weekday] ?: FocusRequester()

            stickyHeader(key = "header_$weekday") {
                WeekdayHeader(
                    weekday = weekday,
                    isToday = isToday,
                    // If the inline input already has text — treat "+" as submit
                    // (commit the pending text). Empty input — focus the field
                    // so the user can start typing.
                    onAddClick = {
                        val pending = (addTextByDay[weekday] ?: "").trim()
                        if (pending.isNotEmpty()) {
                            onAddItemToDay(weekday, pending)
                            addTextByDay[weekday] = ""
                        } else {
                            focusRequester.requestFocus()
                        }
                    },
                )
            }

            if (dayItems.isNotEmpty()) {
                itemsIndexed(
                    items = dayItems,
                    key = { _, item -> "item_${item.id}" },
                ) { _, item ->
                    ChecklistItemCard(
                        item = item,
                        isDragging = false,
                        isEditMode = false,
                        wiggleAngle = 0f,
                        onCheckedChange = { checked: Boolean -> onItemCheckedChange(item.id, checked) },
                        onItemTap = { onItemTap(item.id) },
                        onLongClick = { onItemLongPress(item.id) },
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                    )

                    Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                }
            }

            item(key = "add_$weekday") {
                val placeholder = stringResource(
                    Res.string.weekly_add_for_day,
                    stringResource(weekdayNameKey(weekday)),
                )

                WeeklyAddItemRow(
                    text = addTextByDay[weekday] ?: "",
                    onTextChange = { newText -> addTextByDay[weekday] = newText },
                    onSubmit = {
                        val text = (addTextByDay[weekday] ?: "").trim()
                        if (text.isNotEmpty()) {
                            onAddItemToDay(weekday, text)
                            addTextByDay[weekday] = ""
                        }
                    },
                    placeholder = placeholder,
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                        .padding(
                            top = AppDimens.SpacingXs,
                            bottom = AppDimens.SpacingMd,
                        ),
                )
            }
        }
    }
}

/**
 * Sticky header for overdue items section.
 * Shown at the top of the list when there are unchecked items from past weekdays.
 */
@Composable
private fun OverdueHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.SpacingXs,
                top = AppDimens.SpacingLg,
                bottom = AppDimens.SpacingXs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.weekly_overdue_section_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lightweight input row for adding an item to a specific weekday.
 *
 * Differs from the shared [com.antonchuraev.homesearchchecklist.desingsystem.components.AddItemInputField]:
 * - No trailing "+" submit button (relies on IME Done action and the per-day header "+" for affordance)
 * - Subdued outline color (outlineVariant with alpha) for less visual noise — 7 such rows stack on screen
 * - Exposes a [FocusRequester] so the header "+" can focus this field on tap
 *
 * Submission flow: user types → presses IME Done (or taps another field) → onSubmit fires.
 */
@Composable
private fun WeeklyAddItemRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val subduedBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = subduedBorder,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Suppress("UNCHECKED_CAST")
private fun addTextMapSaver() = androidx.compose.runtime.saveable.Saver<
        androidx.compose.runtime.snapshots.SnapshotStateMap<Int, String>,
        List<Any>>(
    save = { map ->
        map.flatMap { (k, v) -> listOf(k, v) }
    },
    restore = { list ->
        val map = mutableStateMapOf<Int, String>()
        var i = 0
        while (i < list.size - 1) {
            map[list[i] as Int] = list[i + 1] as String
            i += 2
        }
        map
    },
)
