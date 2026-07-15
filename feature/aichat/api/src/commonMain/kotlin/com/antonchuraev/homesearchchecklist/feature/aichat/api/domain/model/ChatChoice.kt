package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * A "Claude-style" assistant turn that asks the user to choose between a small set of
 * actions: a localized [prompt] plus 2-4 positive [options] rendered as pill chips, and
 * an optional [escape] chip ("Cancel" / "Something else").
 *
 * Replaces the old confirm cards (write-intent preview, agent plan, ambiguous-match text):
 * instead of Apply/Cancel/Reject buttons the assistant shows tappable choices, and a tap
 * executes immediately. Strings are resolved in the presentation layer (ViewModel) before
 * the model is built — the domain never touches Compose Resources.
 */
data class ChatChoice(
    /** The assistant's question / prompt, already localized. */
    val prompt: String,
    /** 2-4 positive choices presented as pill chips. */
    val options: List<ChoiceOption>,
    /** Optional escape chip — a safe "Cancel" / "Something else" off-ramp. */
    val escape: ChoiceOption? = null,
    /**
     * The typed OBJECT of the pending action, rendered as icon rows INSIDE the prompt bubble
     * (D2). Empty = no object rows (the agent batch keeps using [PendingChoice.batchItems]).
     *
     * Replaces D1's single flat preview line: a question like "Set a reminder?" needs to show
     * the item, the list AND the time — three entities a one-slot prompt could never fit.
     */
    val objectRows: List<ChoiceObjectRow> = emptyList(),
)

/**
 * One selectable choice chip.
 *
 * @param id     Stable identifier used by the UI to report the selected chip back to the
 *               ViewModel ([ChatChoice] options must have unique ids).
 * @param label  Localized chip text.
 * @param meta   Optional disambiguating suffix rendered dimmed after a "•" separator
 *               ("12" → "Shopping • 12"). Deliberately NOT part of [label]: after a tap the
 *               label collapses into a user-style sent pill, and "Shopping • 12" reads broken
 *               as something the user said. Bare value by design (a number, not "12 items") —
 *               the full form lives in the chip's contentDescription.
 * @param role   Visual + semantic role (drives chip color + icon).
 * @param action What happens when the chip is tapped.
 */
data class ChoiceOption(
    val id: String,
    val label: String,
    val meta: String? = null,
    val role: ChoiceRole = ChoiceRole.Default,
    val action: ChoiceAction,
)

/**
 * One typed line of the action's object, shown inside the prompt bubble above the chips.
 *
 * Typed rather than pre-formatted so the renderer — not the ViewModel — owns icon + emphasis,
 * and so a missing entity is expressed by an ABSENT row instead of an empty string (D1 lesson:
 * a contract carried in a blank string is a contract nobody can check).
 *
 * @param kind               What this row is (drives the leading icon).
 * @param emphasis           How loud the row is (drives typography + color).
 * @param value              The localized, display-ready text.
 * @param contentDescription Full a11y phrase, already localized in the ViewModel ("Item: Milk").
 *                           Required: the bubble merges its descendants, so without a per-row
 *                           description a screen reader reads "Set a reminder? Buy butter
 *                           Shopping Monday 20 July" — every role lost.
 */
data class ChoiceObjectRow(
    val value: String,
    val kind: RowKind,
    val emphasis: RowEmphasis,
    val contentDescription: String,
)

/**
 * What a [ChoiceObjectRow] represents. Maps to a leading icon in the renderer.
 *
 * - [Item]        the checklist item being acted on.
 * - [Destination] the checklist the action lands in.
 * - [Time]        when a reminder fires.
 * - [File]        an attachment (name, optionally "• size").
 * - [Name]        the name of a checklist about to be created.
 * - [Preview]     one proposed item of a to-be-created list (bullet, no vector icon).
 * - [Count]       an aggregate ("5 reminders").
 * - [DateRange]   a from–to span for mass reminder moves.
 * - [Overflow]    the "…and N more" tail after the preview cap.
 */
enum class RowKind { Item, Destination, Time, File, Name, Preview, Count, DateRange, Overflow }

/**
 * How loud a [ChoiceObjectRow] is.
 *
 * - [Primary] titleSmall / onSurface — the object itself.
 * - [Detail]  bodyMedium / onSurfaceVariant — supporting context (list, preview, count).
 * - [Accent]  bodyMedium / onSurface — deliberately full-strength for TIME: a silent 3 a.m.
 *             alarm is the surprise this block exists to prevent, so it never sits at
 *             supporting emphasis.
 * - [Danger]  titleSmall / error + trash icon — the target of an irreversible action.
 */
enum class RowEmphasis { Primary, Detail, Accent, Danger }

/**
 * Visual role of a choice chip. Maps to colorScheme tokens in [AiChoiceChip]:
 * - [Primary]     primary / onPrimary — the recommended action, max one per block.
 * - [Default]     primaryContainer / onPrimaryContainer — a secondary positive option.
 * - [Destructive] error / onError + trash icon — irreversible action.
 * - [Escape]      transparent + outlineVariant border — safe off-ramp ("Cancel").
 * - [Add]         surfaceContainer + dashed outline + leading "+" — additive option.
 */
enum class ChoiceRole { Primary, Default, Destructive, Escape, Add }

/** What tapping a [ChoiceOption] does. */
sealed interface ChoiceAction {
    /** Dispatch a single concrete [ToolCall] (the old single-tool preview "Apply"). */
    data class Execute(val toolCall: ToolCall) : ChoiceAction

    /** Approve the whole agent batch — resumes the suspended agent loop (the old "Apply all"). */
    data object ExecuteAll : ChoiceAction

    /**
     * Re-classify / escalate the original [text] to the next pipeline layer (the old
     * "I meant something else" Reject). Carries the original user input so the ViewModel
     * can reproduce the source-layer escalation.
     */
    data class FreeForm(val text: String) : ChoiceAction

    /**
     * Send [text] as a fresh agent turn (forceAgent) — used by AI-generated answer options
     * ([AgentStepResult.Options]). Unlike [FreeForm] this does NOT re-classify: the tapped
     * label is the user's next message straight to the reasoning agent.
     */
    data class SendMessage(val text: String) : ChoiceAction

    /** Open the inline edit field so the user can fix the captured payload before executing. */
    data object Edit : ChoiceAction

    /** Cancel the pending choice with a visible response (the old "Cancel"). */
    data object Dismiss : ChoiceAction

    /**
     * Roll back an already-applied reversible mutation ([UndoHandle]). Shown as a chip AFTER the
     * action ran — the D1 "ceremony proportional to reversibility" path replaces the pre-hoc
     * "Add milk to Shopping?" question for add/complete.
     */
    data class Undo(val handle: UndoHandle) : ChoiceAction

    /**
     * Open the list picker for a just-added item: replaces the chips with one chip per candidate
     * list ([MoveTo]) plus a Cancel escape.
     */
    data class MoveToList(val handle: UndoHandle.AddedItem) : ChoiceAction

    /**
     * Move the just-added item to [targetName] (a chip from the [MoveToList] picker).
     * Add-then-remove; see [ToolCallDispatcher.moveAddedItem].
     */
    data class MoveTo(val handle: UndoHandle.AddedItem, val targetName: String) : ChoiceAction
}
