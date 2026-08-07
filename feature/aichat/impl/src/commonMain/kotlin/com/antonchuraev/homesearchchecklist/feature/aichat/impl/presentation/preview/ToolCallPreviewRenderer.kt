package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_preview_attach_to
import aichecklists.core.designsystem.generated.resources.chat_preview_clear_completed
import aichecklists.core.designsystem.generated.resources.chat_preview_create_from_file
import aichecklists.core.designsystem.generated.resources.chat_preview_files_count
import aichecklists.core.designsystem.generated.resources.chat_preview_in_list
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Converts a [ToolCall] into the human-readable object line shown to the user — the "what"
 * of a proposed or applied action ("• Milk → Shopping").
 *
 * Since D1 this is the ONLY place that renders the object: the choice question itself is
 * argument-less ("Add to a list?") and the object is rendered here, into the batch-item list of
 * the bubble. Consequently every string here is user-facing on ~11 screens and MUST come from
 * Compose Resources — only the bullet and arrow stay literal (punctuation, not language).
 *
 * `suspend` for the same reason: `getString` suspends. Both call sites (the agent batch and the
 * single-action choice) are already in coroutines.
 */
interface ToolCallPreviewRenderer {
    suspend fun render(toolCall: ToolCall): String
}

internal class ToolCallPreviewRendererImpl(
    private val dateFormatter: ChatDateFormatter,
) : ToolCallPreviewRenderer {

    override suspend fun render(toolCall: ToolCall): String = when (toolCall) {
        is ToolCall.AddItem -> buildString {
            append(bullet(toolCall.itemText))
            toolCall.checklistHint?.let { append(" $ARROW $it") }
        }

        is ToolCall.DeleteItem -> bullet(toolCall.itemText) + inListSuffix(toolCall.checklistHint)

        is ToolCall.CompleteItem -> bullet(toolCall.itemText) + inListSuffix(toolCall.checklistHint)

        is ToolCall.CreateChecklist -> buildString {
            append(toolCall.name)
            if (toolCall.initialItems.isNotEmpty()) {
                append("\n")
                append(toolCall.initialItems.joinToString("\n") { bullet(it) })
            }
        }

        is ToolCall.SetItemReminder -> buildString {
            append(bullet(toolCall.itemText))
            append(inListSuffix(toolCall.checklistHint))
            append(" $ARROW ${dateFormatter.formatDateTime(toolCall.at)}")
        }

        is ToolCall.MoveAllReminders ->
            "${dateFormatter.formatDay(toolCall.fromDayStartMs)} $ARROW " +
                dateFormatter.formatDay(toolCall.toDayStartMs)

        // FindItemsQuery renders inline (no preview card), this is a safety fallback
        is ToolCall.FindItemsQuery -> ""

        is ToolCall.CreateChecklistFromAttachment ->
            previewString(
                Res.string.chat_preview_create_from_file,
                fileLabel(toolCall.attachments.size, toolCall.attachments.firstOrNull()?.fileName),
            )

        is ToolCall.AttachToItem ->
            previewString(
                Res.string.chat_preview_attach_to,
                fileLabel(toolCall.attachments.size, toolCall.attachments.firstOrNull()?.fileName),
                toolCall.itemText,
            ) + inListSuffix(toolCall.checklistHint)

        is ToolCall.AddItems -> buildString {
            append(toolCall.itemTexts.joinToString("\n") { bullet(it) })
            toolCall.checklistHint?.let { append("\n$ARROW $it") }
        }

        // ReadChecklist is agent-only read operation — never shown as a preview card
        is ToolCall.ReadChecklist -> ""

        is ToolCall.RenameChecklist -> "${toolCall.checklistHint} $ARROW ${toolCall.newName}"

        // Agent batch plan line: "• Call the bank – Errands". The DESTINATION is what the user is
        // approving, so it is what the arrow points at; the source is the screen they are on.
        is ToolCall.MoveItem ->
            bullet(toolCall.itemText) + " $ARROW ${toolCall.toChecklistHint}"

        // Agent batch plan line: "Clear completed items (in Shopping)".
        is ToolCall.ClearCompleted ->
            previewString(Res.string.chat_preview_clear_completed) + inListSuffix(toolCall.checklistHint)
    }

    private fun bullet(text: String): String = "$BULLET $text"

    /** " (in Shopping)" / " (в Покупки)" — empty when the command names no list. */
    private suspend fun inListSuffix(hint: String?): String =
        if (hint == null) "" else " " + previewString(Res.string.chat_preview_in_list, hint)

    /** One file → its name; several → a localized "N files". */
    private suspend fun fileLabel(count: Int, firstFileName: String?): String =
        if (count == 1 && firstFileName != null) {
            firstFileName
        } else {
            previewString(Res.string.chat_preview_files_count, count.toString())
        }

    /**
     * Resolves a preview string, degrading to the bare arguments if the resource cannot be
     * resolved — never throwing.
     *
     * This line is the OBJECT of a confirmation question: if it blows up, the exception unwinds
     * into handleSend's catch-all and the user gets a generic error instead of their action —
     * i.e. exactly the class of bug D1 exists to kill (a question the user cannot answer). Losing
     * the punctuation ("• Milk Shopping") is a bad day; losing the turn is a broken feature.
     *
     * It also keeps the renderer usable from the plain unit-test host, where Compose Resources
     * throw "Resources.getSystem not mocked" — same environment caveat that [choiceString] in
     * ChatViewModel documents. Locale-correct output is asserted on the Android host test, where
     * resources actually resolve.
     */
    private suspend fun previewString(res: StringResource, vararg args: String): String =
        runCatching { getString(res, *args) }.getOrElse { args.joinToString(" ") }

    private companion object {
        const val BULLET = "•"

        /**
         * En dash, NOT "→" (U+2192): Skiko on wasmJs has no CSS-style font fallback, and the arrow
         * is covered by neither the emoji font nor any web system font — it renders as tofu (an
         * empty square) while Android's Roboto fallback hides the problem. Verified on :9090
         * 2026-07-15: the reminder preview showed "• купить масло ⯀ Пятница, 17 июля в 09:00".
         * U+2013 is covered (the chat header's "≈ 0–3 кредита" renders fine on the same canvas).
         * Project precedent: review-rules wasm-bugs/premium-price-arrow-tofu-2026-05-22.
         */
        const val ARROW = "–"
    }
}
