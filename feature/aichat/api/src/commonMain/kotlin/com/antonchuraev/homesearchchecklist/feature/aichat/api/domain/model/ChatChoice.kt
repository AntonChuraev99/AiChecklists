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
)

/**
 * One selectable choice chip.
 *
 * @param id     Stable identifier used by the UI to report the selected chip back to the
 *               ViewModel ([ChatChoice] options must have unique ids).
 * @param label  Localized chip text.
 * @param role   Visual + semantic role (drives chip color + icon).
 * @param action What happens when the chip is tapped.
 */
data class ChoiceOption(
    val id: String,
    val label: String,
    val role: ChoiceRole = ChoiceRole.Default,
    val action: ChoiceAction,
)

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
}
