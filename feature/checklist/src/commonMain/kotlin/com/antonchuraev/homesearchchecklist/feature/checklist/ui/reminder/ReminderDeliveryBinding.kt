package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents

/**
 * What App.kt provides to every reminder sheet: the shared [controller] and the app-level
 * navigation to the Telegram screen ([openTelegram] pushes the route with
 * `source = reminder_sheet`).
 */
class ReminderDeliveryHost(
    val controller: ReminderDeliveryChannelController,
    val openTelegram: () -> Unit,
)

/**
 * Null outside the app (previews, screenshot tests, any host App.kt does not wrap): the sheet then
 * shows no delivery row, pixel-identical to the sheet before the feature.
 */
val LocalReminderDeliveryHost = staticCompositionLocalOf<ReminderDeliveryHost?> { null }

/** The row's state + callbacks for one sheet. [NONE] = no row. */
data class ReminderDeliveryBinding(
    val row: ReminderDeliveryRowUi?,
    val callbacks: ReminderDeliveryCallbacks,
) {
    companion object {
        val NONE = ReminderDeliveryBinding(row = null, callbacks = ReminderDeliveryCallbacks())
    }
}

/**
 * Binds one reminder sheet to the shared delivery controller. Call it INSIDE the branch that shows
 * the sheet: entering composition is the "sheet opened" signal that clears a stale failure line.
 *
 * @param surface `AnalyticsEvents.Telegram.SURFACE_*` of the host.
 */
@Composable
fun rememberReminderDelivery(surface: String): ReminderDeliveryBinding {
    val host = LocalReminderDeliveryHost.current ?: return ReminderDeliveryBinding.NONE
    val row by host.controller.delivery.collectAsState()
    LaunchedEffect(host) { host.controller.onSheetShown() }
    val callbacks = remember(host, surface) {
        val controller = host.controller
        ReminderDeliveryCallbacks(
            onChannelClick = controller::onChannelClick,
            onWebClick = controller::onWebClick,
            onConnectClick = {
                controller.onRowTapped(AnalyticsEvents.Telegram.ACTION_CONNECT, surface)
                host.openTelegram()
            },
            onExpandedChange = { expanded ->
                controller.onRowTapped(
                    if (expanded) AnalyticsEvents.Telegram.ACTION_EXPAND else AnalyticsEvents.Telegram.ACTION_COLLAPSE,
                    surface,
                )
            },
        )
    }
    return ReminderDeliveryBinding(row, callbacks)
}

/** `state.withDelivery(binding)` at a host's `ReminderSheet(` call. */
fun ReminderSheetState.withDelivery(binding: ReminderDeliveryBinding): ReminderSheetState =
    if (binding.row == delivery) this else copy(delivery = binding.row)

fun ReminderSheetCallbacks.withDelivery(binding: ReminderDeliveryBinding): ReminderSheetCallbacks =
    copy(delivery = binding.callbacks)
