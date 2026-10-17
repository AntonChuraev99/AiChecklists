package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Redesign B, pass 2: the header type tile of a write-intent choice block comes from
 * [choiceKind]. Every variant is pinned, so a remap (or a new variant landing on the wrong tile)
 * shows up here rather than as a wrong icon nobody screenshots.
 */
class ChoiceKindMapperTest {

    private val file = ChatAttachment(
        sourcePath = "/tmp/a.pdf",
        mimeType = "application/pdf",
        fileName = "a.pdf",
        sizeBytes = 0L,
    )

    @Test
    fun everyToolCallVariant_mapsToItsTileOrToNoTile() {
        val expected: List<Pair<ToolCall, ChoiceKind?>> = listOf(
            ToolCall.AddItem(checklistHint = "A", itemText = "milk") to ChoiceKind.AddItems,
            ToolCall.AddItems(checklistHint = "A", itemTexts = listOf("a", "b")) to ChoiceKind.AddItems,
            ToolCall.CreateChecklist(name = "Trip", initialItems = emptyList()) to ChoiceKind.CreateChecklist,
            ToolCall.CreateChecklistFromAttachment(attachments = listOf(file)) to ChoiceKind.CreateChecklist,
            ToolCall.SetItemReminder(checklistHint = "A", itemText = "milk", at = 1L) to ChoiceKind.Reminder,
            ToolCall.MoveAllReminders(fromDayStartMs = 1L, fromDayEndMs = 2L, toDayStartMs = 3L) to ChoiceKind.Reminder,
            ToolCall.DeleteItem(checklistHint = "A", itemText = "milk") to ChoiceKind.Delete,
            ToolCall.ClearCompleted(checklistHint = "A") to ChoiceKind.Delete,
            ToolCall.MoveItem(itemText = "milk", toChecklistHint = "B") to ChoiceKind.Move,
            // No tile in the design for these — the header is the prompt alone.
            ToolCall.CompleteItem(checklistHint = "A", itemText = "milk") to null,
            ToolCall.AttachToItem(checklistHint = "A", itemText = "milk", attachments = listOf(file)) to null,
            ToolCall.RenameChecklist(checklistHint = "A", newName = "B") to null,
            // Read-only variants never reach a choice block.
            ToolCall.FindItemsQuery(query = "milk") to null,
            ToolCall.ReadChecklist(name = "A") to null,
        )

        expected.forEach { (call, kind) ->
            assertEquals(kind, call.choiceKind(), "wrong tile kind for ${call::class.simpleName}")
        }
    }
}
