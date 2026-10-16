package com.antonchuraev.homesearchchecklist.desingsystem.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.plan_nudge_subtitle
import aichecklists.core.designsystem.generated.resources.plan_nudge_title
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import org.jetbrains.compose.resources.stringResource

/** Sizing and the motion contract for [AppPlanNudge]. */
object AppPlanNudgeDefaults {

    /** Minimum row height. A minimum, never a fixed height — ru/hi and large font scales need room. */
    val MinHeight: Dp = 56.dp

    /**
     * How the nudge appears: a fade and **nothing else**.
     *
     * It does not fly in, because it is not an event — it is simply there once there is something to
     * plan. A slide would announce it, and an invitation that announces itself becomes a demand.
     * Exposed as a constant so every call site inherits the same contract instead of reinventing it
     * with a `slideInVertically` attached.
     */
    val Enter: EnterTransition = fadeIn(AppMotion.effectsDefault)

    /** Mirror of [Enter]. */
    val Exit: ExitTransition = fadeOut(AppMotion.effectsDefault)
}

/**
 * An invitation to sit down and plan the day with Gisti.
 *
 * Since redesign B (2026-10-16) this is a thin preset over [AppAiInviteRow] — the one anatomy for
 * every AI invite row — with the canonical copy and the [AiInviteStyle.Recessed] style by default.
 * Inside a grouped Inbox section the host passes [AiInviteStyle.GroupRow] and applies the segment.
 *
 * ## Why there is no number on it
 * The obvious design — "7 tasks without a date" — was rejected. A debt counter grows faster than a
 * person clears it, so within a week it becomes a standing reproach that they learn not to see, and
 * it works directly against the thing being measured: whoever watches that number climb on every
 * capture captures less. The acceptance criterion for this whole vertical is *"the share of tasks
 * with a reminder goes up **and** the number of tasks created does not go down"*, and a counter
 * trades the second half away for the first.
 *
 * So the nudge says what is on offer, never how far behind you are. (The Inbox SECTION headers carry
 * counts since the owner's 2026-10-16 decision for redesign B; that decision covers the headers, not
 * this nudge.)
 *
 * ## Placement
 * At the **tail** of the undated section, never at the top. At the top it reads as a heading, and a
 * heading phrased like this reads as an accusation.
 *
 * ## Motion
 * Wrap in `AnimatedVisibility` and use the tokens, so no call site invents its own entrance:
 * ```kotlin
 * AnimatedVisibility(
 *     visible = showNudge,
 *     enter = AppPlanNudgeDefaults.Enter,
 *     exit = AppPlanNudgeDefaults.Exit,
 * ) { AppPlanNudge(onClick = openDailyReview) }
 * ```
 *
 * ## Accessibility
 * The whole row is one button (the [AppAiInviteRow] `Surface` merges its children), so a screen
 * reader announces "Plan your day with Gisti, One task at a time, button" and the chevron stays
 * decorative.
 *
 * @param onClick Opens the daily review.
 * @param modifier Optional external modifier.
 * @param title Overridable for a variant of the invitation; defaults to the canonical copy.
 * @param subtitle As [title].
 * @param style Container style; [AiInviteStyle.GroupRow] when the host draws a `groupSegment`.
 */
@Composable
fun AppPlanNudge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.plan_nudge_title),
    subtitle: String = stringResource(Res.string.plan_nudge_subtitle),
    style: AiInviteStyle = AiInviteStyle.Recessed,
) {
    AppAiInviteRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        style = style,
        modifier = modifier.heightIn(min = AppPlanNudgeDefaults.MinHeight),
    )
}
