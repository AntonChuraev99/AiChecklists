package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannelSaveResult
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramFailureReason
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramReminderLink
import com.antonchuraev.homesearchchecklist.core.common.api.webReminderTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ReminderDelivery"

/**
 * The ONE delivery-channel controller behind every reminder sheet (spec §5.2): six hosts share this
 * app-scoped instance, so a save started in one sheet shows as "saving" in the next sheet opened.
 *
 * - **Latest tap wins:** while a request is in flight a new tap only replaces the pending target;
 *   the running loop sends it when the in-flight call returns ([TelegramReminderLink] sends calls in
 *   order, so the last one is what the server keeps).
 * - **Same value → no request:** a tap on the option already shown (confirmed, or the one being
 *   saved) is a radio that already shows the answer.
 * - **Failure → revert:** the pending value is dropped, the last confirmed value shows again, and
 *   `failed` is raised until the next tap or the next sheet open ([onSheetShown]).
 * - **Outlives the sheet:** [scope] is an application scope (pitfall 2), so closing the sheet or
 *   clearing the host ViewModel never cancels a save. A failure that lands with no sheet open has
 *   no UI — it is logged and reported, the server value wins on the next status refresh.
 *
 * @param web the web build (two radio rows, `webReminderTarget` mapping).
 */
class ReminderDeliveryChannelController(
    private val link: TelegramReminderLink,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    private val web: Boolean,
) {
    private data class Local(
        val saving: ReminderChannel? = null,
        val failed: Boolean = false,
        val confirmedOverride: ReminderChannel? = null,
    )

    private val local = MutableStateFlow(Local())

    private val knowledge: StateFlow<TelegramLinkKnowledge> = link.telegramLink
        // A fresh cache value is the truth again: drop the "just confirmed" bridge.
        .onEach { local.update { it.copy(confirmedOverride = null) } }
        .catch { e ->
            logger.error(TAG, "telegramLink failed, hiding the row: ${e.message}", e)
            emit(TelegramLinkKnowledge.Unknown)
        }
        .stateIn(scope, SharingStarted.Eagerly, TelegramLinkKnowledge.Unknown)

    /** What every sheet's [ReminderSheetState.delivery] shows. */
    val delivery: StateFlow<ReminderDeliveryRowUi?> = combine(knowledge, local) { k, l ->
        reminderDeliveryRowUi(k, l.saving, l.failed, l.confirmedOverride, web)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        reminderDeliveryRowUi(TelegramLinkKnowledge.Unknown, null, false, null, web),
    )

    private var saveJob: Job? = null

    /** Android radio tap. Ignored when no Choice row is shown (there is no radio to tap then). */
    fun onChannelClick(target: ReminderChannel) {
        val confirmed = confirmedOrNull() ?: return
        val state = local.value
        if ((state.saving ?: confirmed) == target) {
            // Radio already shows it: no request, but any tap on a radio clears the error (§3.4).
            if (state.failed) local.value = state.copy(failed = false)
            return
        }
        local.value = state.copy(saving = target, failed = false)
        if (saveJob?.isActive == true) return
        saveJob = scope.launch { runSaveLoop(target) }
    }

    /** Web radio tap: `on` = "Send to Telegram"; mapped with the screen's [webReminderTarget]. */
    fun onWebClick(on: Boolean) {
        val confirmed = confirmedOrNull() ?: return
        val shown = local.value.saving ?: confirmed
        // null = the tapped row is already selected — radio semantics, no request.
        val target = webReminderTarget(shown, on) ?: return
        onChannelClick(target)
    }

    /** A reminder sheet opened: an old failure line does not follow the user into a new sheet. */
    fun onSheetShown() {
        local.update { if (it.failed) it.copy(failed = false) else it }
    }

    /** `telegram_reminder_row_tapped` for the header (expand / collapse) and the Connect row. */
    fun onRowTapped(action: String, surface: String) {
        val state = when (knowledge.value) {
            is TelegramLinkKnowledge.Linked -> AnalyticsEvents.Telegram.STATE_LINKED
            TelegramLinkKnowledge.NotLinked -> AnalyticsEvents.Telegram.STATE_NOT_LINKED
            TelegramLinkKnowledge.SignedOut -> AnalyticsEvents.Telegram.STATE_SIGNED_OUT
            TelegramLinkKnowledge.Unknown -> return // no row is rendered in this state
        }
        analytics.event(
            AnalyticsEvents.Telegram.REMINDER_ROW_TAPPED,
            mapOf(
                AnalyticsEvents.Telegram.PARAM_STATE to state,
                AnalyticsParams.ACTION to action,
                AnalyticsParams.SURFACE to surface,
            ),
        )
    }

    private fun confirmedOrNull(): ReminderChannel? {
        val linked = knowledge.value as? TelegramLinkKnowledge.Linked ?: return null
        return local.value.confirmedOverride ?: linked.reminderChannel
    }

    private suspend fun runSaveLoop(first: ReminderChannel) {
        var sending = first
        while (true) {
            val result = try {
                link.setReminderChannel(sending)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "setReminderChannel threw: ${e.message}", e)
                ReminderChannelSaveResult.Failed(TelegramFailureReason.Server)
            }
            when (result) {
                is ReminderChannelSaveResult.Saved -> {
                    val saved = result.channel
                    analytics.event(
                        AnalyticsEvents.Telegram.REMINDER_CHANNEL_SET,
                        mapOf(
                            AnalyticsEvents.Telegram.PARAM_CHANNEL to saved.wire,
                            AnalyticsParams.SOURCE to AnalyticsEvents.Telegram.SOURCE_REMINDER_SHEET,
                        ),
                    )
                    val latest = local.value.saving
                    // Compared with what was SENT, not with the answer: a server that stores a
                    // different value than requested must not make the loop resend forever.
                    if (latest == null || latest == sending || latest == saved) {
                        local.update { it.copy(saving = null, confirmedOverride = saved) }
                        return
                    }
                    // Another option was tapped while this one was in flight → send that one too.
                    local.update { it.copy(confirmedOverride = saved) }
                    sending = latest
                }
                is ReminderChannelSaveResult.Failed -> {
                    // The channel of the request that failed, not a tap still pending behind it.
                    logger.warning(TAG, "reminder channel save failed: ${result.reason} (attempted ${sending.wire})")
                    analytics.event(
                        AnalyticsEvents.Telegram.REMINDER_CHANNEL_SET_FAILED,
                        mapOf(
                            AnalyticsEvents.Telegram.PARAM_CHANNEL to sending.wire,
                            AnalyticsParams.SOURCE to AnalyticsEvents.Telegram.SOURCE_REMINDER_SHEET,
                            AnalyticsParams.REASON to result.reason.analyticsValue(),
                        ),
                    )
                    local.update { it.copy(saving = null, failed = true) }
                    return
                }
            }
        }
    }
}

/** Wire value of `reason` in `tg_reminder_channel_set_failed`. */
fun TelegramFailureReason.analyticsValue(): String = when (this) {
    TelegramFailureReason.Network -> "network"
    TelegramFailureReason.Server -> "server"
    TelegramFailureReason.Account -> "account"
}
