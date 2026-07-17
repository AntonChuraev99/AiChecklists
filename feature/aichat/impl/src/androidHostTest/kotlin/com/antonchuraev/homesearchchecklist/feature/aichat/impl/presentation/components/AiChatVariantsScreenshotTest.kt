package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.AgentPlanItem
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.PendingChoice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi (golden) screenshot backbone for the full AI-chat answer/UI catalog.
 *
 * Every distinct chat "special case" is fed as a plain data class into the REAL stateless
 * production composable ([ChatMessageBubble] / [AiChoiceResponse] / [ChatTypingIndicator] /
 * [ChatPricingRow]) — no ViewModel, no routing, no network. Each golden becomes one figure of a
 * standing AI-chat audit; a missing variant here is a missing audit case, so this file's coverage
 * of the render surface (not any single assertion) is the point.
 *
 * Families:
 *  - **Flat** ([ChatMessageBubble]) — welcome / answer / user+cost / open-checklist / ask-AI /
 *    become-pro / error / attachment. One golden each (light), per the audit brief.
 *  - **Interactive** ([AiChoiceResponse]) — the choice block in each of its shapes. Light + dark
 *    each, so contrast + wrapping are reviewable in both themes.
 *  - **Direct** — typing indicator, pricing band. One golden each.
 *
 * Record goldens:
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest
 * Verify (CI):
 *   ./gradlew :feature:aichat:impl:verifyRoborazziAndroidHostTest
 * Golden PNGs land in:
 *   feature/aichat/impl/src/androidHostTest/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiChatVariantsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // =========================================================================
    // Flat family — ChatMessageBubble. One golden each (light).
    // =========================================================================

    @Test
    fun variant_empty_welcome() = capture {
        // showSenderLabel = false: the static greeting carries no avatar/label (welcome case).
        ChatMessageBubble(message = welcomeMessage, showSenderLabel = false)
    }

    @Test
    fun variant_plain_answer() = capture {
        // Markdown (bold + bullets + inline code) + the Copy/ThumbUp/ThumbDown action row.
        ChatMessageBubble(
            message = plainAnswerMessage,
            onFeedbackClick = {},
            onThumbUpClick = {},
            showSenderLabel = true,
        )
    }

    @Test
    fun variant_user_cost_badge() = capture {
        // User (sent) bubble + InlineCostBadge (costCredits = 1).
        ChatMessageBubble(message = userCostMessage)
    }

    @Test
    fun variant_success_open_checklist() = capture {
        // Success reply carrying linkedChecklistId → the "Open checklist" deeplink button.
        ChatMessageBubble(
            message = successMessage,
            onFeedbackClick = {},
            onThumbUpClick = {},
            onOpenChecklist = {},
            showSenderLabel = true,
        )
    }

    @Test
    fun variant_unknown_ask_ai() = capture {
        // Unknown-intent dead-end (askAiForText set) → the "Ask AI" Layer-3 escalation button.
        ChatMessageBubble(
            message = unknownMessage,
            onFeedbackClick = {},
            onThumbUpClick = {},
            onAskAiFallback = {},
            showSenderLabel = true,
        )
    }

    @Test
    fun variant_out_of_credits_become_pro() = capture {
        // Out-of-credits reply (paywallCtaCredits set) → the "Become Pro" CTA row.
        ChatMessageBubble(
            message = outOfCreditsMessage,
            onFeedbackClick = {},
            onThumbUpClick = {},
            onPaywallCta = {},
            showSenderLabel = true,
        )
    }

    @Test
    fun variant_generic_error() = capture {
        // F1: connectivity-aware error copy (no longer "reaching the AI") + a real Retry chip
        // (retryText set on the message → onRetry wired).
        ChatMessageBubble(
            message = errorMessage,
            onFeedbackClick = {},
            onThumbUpClick = {},
            onRetry = {},
            showSenderLabel = true,
        )
    }

    @Test
    fun variant_user_attachment_pdf() = capture {
        // User message + icon-based attachment thumbnails (PDF + text — no bitmap needed).
        ChatMessageBubble(message = userAttachmentMessage)
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun variant_user_attachment_photo() = capture {
        // F2: an image/* attachment renders its thumbnail through Coil's AsyncImage, which
        // decodes nothing under Robolectric (no real image engine) → a blank tile. Coil's
        // preview handler paints a deterministic solid ColorImage instead, but ONLY when
        // LocalInspectionMode is on (coil3.compose.internal.previewHandler gates on it), so we
        // provide both. The golden therefore shows a real (non-blank) photo tile next to the
        // icon-based PDF/text cases, closing the attachment-render coverage.
        // Caveat: this proves the image *branch* lays out and fills correctly; it does NOT
        // exercise Coil's real decode path (that needs an instrumented test with a bitmap).
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler {
                ColorImage(PHOTO_THUMBNAIL_ARGB)
            },
        ) {
            ChatMessageBubble(message = userImageAttachmentMessage)
        }
    }

    // =========================================================================
    // Interactive family — AiChoiceResponse. Light + dark each.
    // =========================================================================

    @Test fun variant_question_card_delete_light() = capture { Choice(false, deleteChoice()) }
    @Test fun variant_question_card_delete_dark() = capture { Choice(true, deleteChoice()) }

    @Test fun variant_question_card_reminder_light() = capture { Choice(false, reminderChoice()) }
    @Test fun variant_question_card_reminder_dark() = capture { Choice(true, reminderChoice()) }

    @Test fun variant_question_card_create_checklist_light() = capture { Choice(false, createChecklistChoice()) }
    @Test fun variant_question_card_create_checklist_dark() = capture { Choice(true, createChecklistChoice()) }

    @Test fun variant_which_list_memory_toggle_light() = capture { Choice(false, whichListMemoryChoice()) }
    @Test fun variant_which_list_memory_toggle_dark() = capture { Choice(true, whichListMemoryChoice()) }

    @Test fun variant_agent_batch_light() = capture { Choice(false, agentBatchChoice()) }
    @Test fun variant_agent_batch_dark() = capture { Choice(true, agentBatchChoice()) }

    // FIX #5 — the dock (compact = true) must render EVERY plan step, never truncate. The batch has
    // 4 steps; this golden proves all four are visible in the compact surface (unlike the
    // create-checklist preview, which caps at 2 in the dock).
    @Test fun variant_agent_batch_compact_light() = capture { Choice(false, agentBatchChoice(), compact = true) }

    @Test fun variant_quick_replies_light() = capture { Choice(false, quickRepliesChoice()) }
    @Test fun variant_quick_replies_dark() = capture { Choice(true, quickRepliesChoice()) }

    @Test fun variant_post_action_undo_move_light() = capture { Choice(false, postActionChoice()) }
    @Test fun variant_post_action_undo_move_dark() = capture { Choice(true, postActionChoice()) }

    @Test fun variant_move_target_picker_light() = capture { Choice(false, moveTargetChoice()) }
    @Test fun variant_move_target_picker_dark() = capture { Choice(true, moveTargetChoice()) }

    // Inline edit field auto-focuses → a blinking text cursor is an ongoing animation. Pin the
    // clock so the golden is a deterministic frame instead of an arbitrary cursor phase.
    @Test fun variant_inline_edit_field_light() = capture(pinMs = PIN_MS) { Choice(false, inlineEditChoice()) }
    @Test fun variant_inline_edit_field_dark() = capture(pinMs = PIN_MS) { Choice(true, inlineEditChoice()) }

    // The tapped chip shows an indefinite CircularProgressIndicator (infinite animation). Pin the
    // clock so record/verify agree on one frame and the run does not wait on a never-idle clock.
    @Test fun variant_chip_executing_spinner_light() = capture(pinMs = PIN_MS) { Choice(false, executingChoice()) }
    @Test fun variant_chip_executing_spinner_dark() = capture(pinMs = PIN_MS) { Choice(true, executingChoice()) }

    // =========================================================================
    // Direct components. One golden each.
    // =========================================================================

    // Three pulsing dots = infinite animation → pin the clock (same reason as the spinner).
    @Test
    fun variant_typing_indicator() = capture(pinMs = PIN_MS) {
        Frame(darkTheme = false) { ChatTypingIndicator() }
    }

    @Test
    fun variant_pricing_row() = capture {
        Frame(darkTheme = false) { ChatPricingRow(onWhyClick = {}) }
    }

    // =========================================================================
    // Capture harness
    // =========================================================================

    /**
     * Sets [content], optionally pins the animation clock at [pinMs] (for blocks that carry an
     * ongoing animation — a text cursor, a spinner, the typing dots — which would otherwise make
     * the golden non-deterministic or keep the clock from ever going idle), then captures the root.
     */
    private fun capture(pinMs: Long? = null, content: @Composable () -> Unit) {
        if (pinMs != null) composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { content() }
        if (pinMs != null) composeTestRule.mainClock.advanceTimeBy(pinMs)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Renders an [AiChoiceResponse] inside the frame. [compact] = true mimics the inline dock. */
    @Composable
    private fun Choice(darkTheme: Boolean, pending: PendingChoice, compact: Boolean = false) {
        Frame(darkTheme = darkTheme) {
            AiChoiceResponse(
                pending = pending,
                onSelect = {},
                onEditChange = {},
                onEditConfirm = {},
                compact = compact,
            )
        }
    }

    private companion object {
        /** > the 400ms AiChoiceResponse enter animation, so it is complete and only the ongoing
         *  (cursor / spinner / dots) animation is pinned at a fixed, reproducible phase. */
        const val PIN_MS = 700L

        /** Deterministic solid fill for the image-attachment thumbnail golden (a blue tile,
         *  ARGB 0xFF3F7CEC), painted by Coil's preview handler instead of a real decode. */
        const val PHOTO_THUMBNAIL_ARGB: Int = 0xFF3F7CEC.toInt()
    }
}

// =============================================================================
// Frame — explicit darkTheme (Robolectric controls it, not isSystemInDarkTheme), phone-dock
// width so wrapping is realistic. Mirrors the AiChoiceResponseScreenshotTest harness.
// =============================================================================

@Composable
private fun Frame(darkTheme: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = darkTheme) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            content()
        }
    }
}

// =============================================================================
// Flat-family messages (theme-independent data)
// =============================================================================

private val welcomeMessage = ChatMessage(
    id = "welcome",
    role = ChatRole.Assistant,
    timestamp = 0L,
    content = "Hi! I'm your AI assistant. Ask me to add items, create checklists, " +
        "or set reminders — just type naturally.",
)

private val plainAnswerMessage = ChatMessage(
    id = "answer",
    role = ChatRole.Assistant,
    timestamp = 0L,
    content = "Here's what I can do:\n\n" +
        "- **Add** items to any list\n" +
        "- **Create** a checklist from a photo or PDF\n" +
        "- Set `reminders` in natural language\n\n" +
        "Just tell me what you need.",
)

private val userCostMessage = ChatMessage(
    id = "user_cost",
    role = ChatRole.User,
    timestamp = 0L,
    content = "add milk to shopping",
    costCredits = 1,
)

private val successMessage = ChatMessage(
    id = "success",
    role = ChatRole.Assistant,
    timestamp = 0L,
    content = "Done! I added **milk** to your Shopping list.",
    linkedChecklistId = 42L,
)

private val unknownMessage = ChatMessage(
    id = "unknown",
    role = ChatRole.Assistant,
    timestamp = 0L,
    content = "I'm not sure how to help with that yet.",
    askAiForText = "what should I pack for a weekend in the mountains",
)

private val outOfCreditsMessage = ChatMessage(
    id = "out_of_credits",
    role = ChatRole.Assistant,
    timestamp = 0L,
    content = "You're out of AI credits for today. Upgrade to Premium for many more every day.",
    paywallCtaCredits = 300,
)

private val errorMessage = ChatMessage(
    id = "error",
    role = ChatRole.Assistant,
    timestamp = 0L,
    // Connectivity-aware copy (offline case) — no longer blames the AI. retryText drives the
    // Retry chip in the bubble footer.
    content = "You appear to be offline. Check your connection and try again.",
    retryText = "add milk to shopping",
)

private val userAttachmentMessage = ChatMessage(
    id = "attachment",
    role = ChatRole.User,
    timestamp = 0L,
    content = "Make a checklist from this",
    attachments = listOf(
        ChatAttachment(
            sourcePath = "doc://recipe",
            mimeType = "application/pdf",
            fileName = "Recipe.pdf",
            sizeBytes = 20_480L,
        ),
        ChatAttachment(
            sourcePath = "doc://notes",
            mimeType = "text/plain",
            fileName = "notes.txt",
            sizeBytes = 512L,
        ),
    ),
)

private val userImageAttachmentMessage = ChatMessage(
    id = "attachment_image",
    role = ChatRole.User,
    timestamp = 0L,
    content = "Make a checklist from this photo",
    attachments = listOf(
        ChatAttachment(
            sourcePath = "photo://kitchen",
            mimeType = "image/jpeg",
            fileName = "kitchen.jpg",
            sizeBytes = 128_000L,
        ),
    ),
)

// =============================================================================
// Interactive-family choice factories (faithful to the shapes the ChatViewModel builds)
// =============================================================================

private fun escapeCancel() = ChoiceOption(
    id = "escape",
    label = "Cancel",
    role = ChoiceRole.Escape,
    action = ChoiceAction.Dismiss,
)

/** Variant 9 — delete confirmation: item (danger) + list rows, Destructive chip, no primary. */
private fun deleteChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "Delete this item?",
        objectRows = listOf(
            ChoiceObjectRow("Buy milk", RowKind.Item, RowEmphasis.Danger, "Item to delete: Buy milk"),
            ChoiceObjectRow("Shopping", RowKind.Destination, RowEmphasis.Detail, "From list: Shopping"),
        ),
        options = listOf(
            ChoiceOption(
                id = "delete",
                label = "Delete",
                role = ChoiceRole.Destructive,
                action = ChoiceAction.Execute(
                    ToolCall.DeleteItem(checklistHint = "Shopping", itemText = "Buy milk"),
                ),
            ),
        ),
        escape = escapeCancel(),
    ),
)

/** Variant 10 — set reminder: item + list + accent Time rows, confirm/edit/cancel. */
private fun reminderChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "Set a reminder?",
        objectRows = listOf(
            ChoiceObjectRow("Take medicine", RowKind.Item, RowEmphasis.Primary, "Item: Take medicine"),
            ChoiceObjectRow("Health", RowKind.Destination, RowEmphasis.Detail, "List: Health"),
            ChoiceObjectRow("Tomorrow, 8:00 AM", RowKind.Time, RowEmphasis.Accent, "Reminder time: Tomorrow, 8:00 AM"),
        ),
        options = listOf(
            ChoiceOption(
                id = "confirm",
                label = "Set reminder",
                role = ChoiceRole.Primary,
                action = ChoiceAction.Execute(
                    ToolCall.SetItemReminder(checklistHint = "Health", itemText = "Take medicine", at = 0L),
                ),
            ),
            ChoiceOption(id = "edit", label = "Edit", role = ChoiceRole.Default, action = ChoiceAction.Edit),
        ),
        escape = escapeCancel(),
    ),
)

/** Variant 11 — create checklist: name + 8 preview rows (caps at 6 + "…and 2 more"), primary chip. */
private fun createChecklistChoice(): PendingChoice {
    val items = listOf(
        "Passport", "Plane tickets", "Hotel booking", "Euros",
        "Power adapter", "Camera", "Sunscreen", "Guidebook",
    )
    return PendingChoice(
        choice = ChatChoice(
            prompt = "Create this checklist?",
            objectRows = buildList {
                add(ChoiceObjectRow("Paris Trip", RowKind.Name, RowEmphasis.Primary, "New list name: Paris Trip"))
                items.forEach { add(ChoiceObjectRow(it, RowKind.Preview, RowEmphasis.Detail, it)) }
            },
            options = listOf(
                ChoiceOption(
                    id = "create",
                    label = "Create ${items.size} items",
                    role = ChoiceRole.Primary,
                    action = ChoiceAction.Execute(
                        ToolCall.CreateChecklist(name = "Paris Trip", initialItems = items),
                    ),
                ),
            ),
            escape = escapeCancel(),
        ),
    )
}

/**
 * Variant 12 — which-list disambiguation with per-chip meta + "Remember my choice" toggle.
 *
 * The object row ("eggs") is mandatory: the picker must always say WHAT is being added, or the user
 * picks a destination for an unnamed thing. The real product (ChatViewModel.showWhichListChoice)
 * always builds it via buildObjectRows(sourceToolCall) — this golden now reflects that.
 */
private fun whichListMemoryChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "Which list?",
        objectRows = listOf(
            ChoiceObjectRow("eggs", RowKind.Item, RowEmphasis.Primary, "Item: eggs"),
        ),
        options = listOf(
            whichListChip("c0", "Shopping", "12 • 3 Jul", 1L),
            whichListChip("c1", "Groceries", "8", 2L),
            whichListChip("c2", "Weekend", "3", 3L),
        ),
        escape = escapeCancel(),
    ),
    showMemoryToggle = true,
)

private fun whichListChip(id: String, name: String, meta: String, checklistId: Long) = ChoiceOption(
    id = id,
    label = name,
    meta = meta,
    role = ChoiceRole.Default,
    action = ChoiceAction.Execute(
        ToolCall.AddItem(checklistHint = name, itemText = "eggs", checklistId = checklistId),
    ),
)

/** Variant 13 — agent batch: numbered plan inside the bubble (one destructive line), "Do it all". */
private fun agentBatchChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "Here's my plan — apply all?",
        options = listOf(
            ChoiceOption(id = "all", label = "Do it all", role = ChoiceRole.Primary, action = ChoiceAction.ExecuteAll),
        ),
        escape = escapeCancel(),
    ),
    batchItems = listOf(
        AgentPlanItem("Add «milk» to Shopping"),
        AgentPlanItem("Add «eggs» to Shopping"),
        AgentPlanItem("Delete «expired yogurt» from Fridge", isDestructive = true),
        AgentPlanItem("Set a reminder for «call the plumber»"),
    ),
)

/** Variant 14 — AI answer options: blank prompt (question is its own bubble), SendMessage chips. */
private fun quickRepliesChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "",
        options = listOf(
            ChoiceOption("o0", "Yes, add them all", role = ChoiceRole.Default, action = ChoiceAction.SendMessage("Yes, add them all")),
            ChoiceOption("o1", "Show me the list first", role = ChoiceRole.Default, action = ChoiceAction.SendMessage("Show me the list first")),
            ChoiceOption("o2", "No, thanks", role = ChoiceRole.Default, action = ChoiceAction.SendMessage("No, thanks")),
        ),
    ),
)

private fun addedItemHandle() = UndoHandle.AddedItem(
    checklistId = 1L,
    checklistName = "Shopping",
    fillId = 10L,
    fillItemId = "fill-1",
    templateItemId = "tmpl-1",
    itemText = "milk",
)

/** Variant 15 — post-action offer (blank prompt): Undo (escape-styled) + Move to another list. */
private fun postActionChoice(): PendingChoice {
    val handle = addedItemHandle()
    return PendingChoice(
        choice = ChatChoice(
            prompt = "",
            options = listOf(
                ChoiceOption("undo", "Undo", role = ChoiceRole.Escape, action = ChoiceAction.Undo(handle)),
                ChoiceOption("move", "Move to another list", role = ChoiceRole.Default, action = ChoiceAction.MoveToList(handle)),
            ),
        ),
    )
}

/** Variant 16 — move-target picker: one destination chip per candidate + Cancel. */
private fun moveTargetChoice(): PendingChoice {
    val handle = addedItemHandle()
    return PendingChoice(
        choice = ChatChoice(
            prompt = "Which list?",
            options = listOf(
                ChoiceOption("m0", "Groceries", role = ChoiceRole.Default, action = ChoiceAction.MoveTo(handle, "Groceries")),
                ChoiceOption("m1", "Weekend", role = ChoiceRole.Default, action = ChoiceAction.MoveTo(handle, "Weekend")),
                ChoiceOption("m2", "Work", role = ChoiceRole.Default, action = ChoiceAction.MoveTo(handle, "Work")),
            ),
            escape = escapeCancel(),
        ),
    )
}

/** Variant 17 — inline edit: editText non-null → OutlinedTextField + primary chip relabels "Save". */
private fun inlineEditChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "Add to Shopping?",
        objectRows = listOf(
            ChoiceObjectRow("milk", RowKind.Item, RowEmphasis.Primary, "Item: milk"),
            ChoiceObjectRow("Shopping", RowKind.Destination, RowEmphasis.Detail, "List: Shopping"),
        ),
        options = listOf(
            ChoiceOption(
                id = "add",
                label = "Add",
                role = ChoiceRole.Primary,
                action = ChoiceAction.Execute(ToolCall.AddItem(checklistHint = "Shopping", itemText = "milk")),
            ),
        ),
        escape = escapeCancel(),
    ),
    editText = "milk",
)

/** Variant 18 — chip mid-execution: executingId set → tapped chip shows a spinner, block dimmed. */
private fun executingChoice() = PendingChoice(
    choice = ChatChoice(
        prompt = "Which list?",
        options = listOf(
            ChoiceOption(
                id = "s0",
                label = "Shopping",
                role = ChoiceRole.Default,
                action = ChoiceAction.Execute(ToolCall.AddItem(checklistHint = "Shopping", itemText = "milk", checklistId = 1L)),
            ),
            ChoiceOption(
                id = "s1",
                label = "Groceries",
                role = ChoiceRole.Default,
                action = ChoiceAction.Execute(ToolCall.AddItem(checklistHint = "Groceries", itemText = "milk", checklistId = 2L)),
            ),
        ),
        escape = escapeCancel(),
    ),
    executingId = "s0",
    executingLabel = "Adding…",
)
