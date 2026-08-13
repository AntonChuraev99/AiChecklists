package com.antonchuraev.homesearchchecklist.desingsystem.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.plan_nudge_subtitle
import aichecklists.core.designsystem.generated.resources.plan_nudge_title
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.SparkleTile
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import org.jetbrains.compose.resources.stringResource

/** Sizing and the motion contract for [AppPlanNudge]. */
object AppPlanNudgeDefaults {

    /** Minimum row height. A minimum, never a fixed height — ru/hi and large font scales need room. */
    val MinHeight: Dp = 56.dp

    /** The AI mark. Small enough to sit inside the row's rhythm rather than head it. */
    val TileSize: Dp = 24.dp

    /** Corner radius of [TileSize]'s tile, keeping the proportion `SparkleTile` uses at 28dp. */
    val TileCorner: Dp = 8.dp

    /** Trailing chevron. */
    val ChevronSize: Dp = 24.dp

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
 * ## Why there is no number on it
 * The obvious design — "7 tasks without a date" — was rejected. A debt counter grows faster than a
 * person clears it, so within a week it becomes a standing reproach that they learn not to see, and
 * it works directly against the thing being measured: whoever watches that number climb on every
 * capture captures less. The acceptance criterion for this whole vertical is *"the share of tasks
 * with a reminder goes up **and** the number of tasks created does not go down"*, and a counter
 * trades the second half away for the first.
 *
 * So the nudge says what is on offer, never how far behind you are. If the figure is genuinely
 * needed for the business, its place is the Overview screen, where a summary is the genre and it
 * does not greet the user on every capture.
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
 * The whole row is one button ([Surface] with `onClick` merges its children), so a screen reader
 * announces "Plan your day with Gisti, One task at a time, button" and the chevron stays decorative.
 * The click also lives on the `Surface` rather than on a `Modifier.clickable` passed in from
 * outside, which is what keeps the ripple clipped to the 16dp corners.
 *
 * @param onClick Opens the daily review.
 * @param modifier Optional external modifier.
 * @param title Overridable for a variant of the invitation; defaults to the canonical copy.
 * @param subtitle As [title].
 */
@Composable
fun AppPlanNudge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.plan_nudge_title),
    subtitle: String = stringResource(Res.string.plan_nudge_subtitle),
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = AppSurface.recessed(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = AppPlanNudgeDefaults.MinHeight),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingMd,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            SparkleTile(
                size = AppPlanNudgeDefaults.TileSize,
                cornerRadius = AppPlanNudgeDefaults.TileCorner,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, // decorative — the row itself is the button
                modifier = Modifier.size(AppPlanNudgeDefaults.ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
