package com.antonchuraev.homesearchchecklist.desingsystem.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.due_chip_a11y
import aichecklists.core.designsystem.generated.resources.due_chip_clear_a11y
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSchedule
import org.jetbrains.compose.resources.stringResource

/**
 * What the recognised date looks like while the draft is still being typed.
 *
 * `dismissed` is deliberately absent: a dismissed chip is not a chip in a third colour, it is a chip
 * that is no longer composed. The call site stops passing it and the exit fade belongs to the
 * `AnimatedVisibility` around it.
 */
enum class TokenChipState {

    /** The parser found a date. Accented, because it is a claim the app is making, not a label. */
    Recognized,

    /** The user confirmed or edited it. Switches to the schedule palette the task row will use. */
    Confirmed,
}

/** Sizing for [TokenChipPreview]. */
object TokenChipPreviewDefaults {

    /**
     * Minimum chip height, and the reason it is a minimum.
     *
     * The chip lives in `AddItemInputField`'s `leadingPreview` slot, which animates in with
     * `expandVertically` directly above the keyboard. If the chip's *height* changed while that
     * expansion ran, the whole input row would jump, which reads as a rendering bug rather than as
     * motion. Only its width may change — see the `animateContentSize` in the implementation.
     */
    val MinHeight: Dp = 32.dp

    /** The clear glyph itself. */
    val ClearIconSize: Dp = 18.dp

    /**
     * Touch target of the clear affordance.
     *
     * ⚠️ Below the 48dp minimum, knowingly. The chip it sits inside is 32dp tall, and
     * `minimumInteractiveComponentSize()` on the trailing slot is a *layout* minimum, not a touch
     * slop: it would inflate the chip itself to 48dp and reintroduce the height jump
     * [MinHeight] exists to prevent. Dismissing is also fully recoverable — the word stays in the
     * text, so a mis-tap costs one tap to undo — and the same action is reachable from the reminder
     * sheet at full size. This is the one place the trade-off is written down; call sites must not
     * restate it.
     */
    val ClearTouchSize: Dp = 24.dp
}

/**
 * The chip that shows a date the Smart Add parser found in the text the user is typing, and lets
 * them accept, change, or drop it without leaving the input.
 *
 * Uses [InputChip] (M3 chip taxonomy): a token *the user entered*, carrying a first-class trailing
 * slot for removing it. It used to be an `AssistChip` back when it was purely decorative — assist
 * means "the system offers an action", which stopped being true once the chip became the primary way
 * to correct a misparse.
 *
 * ## The clear button removes the date, not the word
 * 🔴 Tapping the cross clears the parsed reminder and **leaves the text alone**. Someone who typed
 * "tomorrow buy bread" and dropped the date still meant to write "tomorrow" — deleting it would take
 * away what they typed, which is a far worse failure than an unwanted reminder. That is why
 * [onDismiss] is a separate callback rather than "edit the draft text": the call site owns the draft
 * and must only clear the date field.
 *
 * ## Interactivity is opt-in
 * With [onClick] `null` the chip renders exactly as before and is not focusable or clickable — no
 * ripple that leads nowhere. It never renders greyed out: the disabled colours are pinned to the
 * enabled ones, so "not yet wired up" and "unavailable" do not look the same.
 *
 * ## Accessibility
 * Two nodes, not one. The chip is a button that announces the reminder and says it can be changed;
 * the cross is its own button labelled "Remove reminder". A single merged node would leave a screen
 * reader user able to open the picker but not to clear the date.
 *
 * @param label Fully resolved, localized label. Feature-layer callers produce it (`resolveChipLabel`
 *   and friends) — keeping label resolution out of here preserves the
 *   `core:designsystem` → `feature:checklist` dependency direction, and stops English being
 *   hardcoded into the design system.
 * @param modifier Optional external modifier.
 * @param isRepeat Show the repeat glyph instead of the bell: a recurring schedule is the dominant
 *   information when there is one.
 * @param state Recognised vs confirmed. See [TokenChipState].
 * @param onClick Opens the time picker. `null` leaves the chip inert.
 * @param onDismiss Clears the date. `null` hides the cross entirely.
 */
@Composable
fun TokenChipPreview(
    label: String,
    modifier: Modifier = Modifier,
    isRepeat: Boolean = false,
    state: TokenChipState = TokenChipState.Recognized,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val confirmed = state == TokenChipState.Confirmed
    val container = if (confirmed) {
        GistiSchedule.activeContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (confirmed) {
        GistiSchedule.activeContent
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val chipDescription = stringResource(Res.string.due_chip_a11y, label)
    val clearDescription = stringResource(Res.string.due_chip_clear_a11y)

    InputChip(
        selected = confirmed,
        onClick = onClick ?: {},
        enabled = onClick != null,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (isRepeat) Icons.Outlined.Repeat else Icons.Outlined.Notifications,
                contentDescription = null, // decorative — the chip's own description carries it
            )
        },
        trailingIcon = onDismiss?.let { dismiss ->
            {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(TokenChipPreviewDefaults.ClearTouchSize)
                        .semantics {
                            role = Role.Button
                            contentDescription = clearDescription
                        }
                        .clickable(onClick = dismiss),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null, // the Box above owns the semantics
                        modifier = Modifier.size(TokenChipPreviewDefaults.ClearIconSize),
                    )
                }
            }
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = container,
            labelColor = content,
            leadingIconColor = content,
            trailingIconColor = content,
            // Pinned to the enabled colours on purpose: `enabled = false` here means "no tap target
            // wired up", not "unavailable to you", and must not read as greyed out.
            disabledContainerColor = container,
            disabledLabelColor = content,
            disabledLeadingIconColor = content,
            disabledTrailingIconColor = content,
            selectedContainerColor = container,
            selectedLabelColor = content,
            selectedLeadingIconColor = content,
            selectedTrailingIconColor = content,
        ),
        elevation = null,
        border = null,
        modifier = modifier
            .heightIn(min = TokenChipPreviewDefaults.MinHeight)
            // Width only. The height is pinned by heightIn above, so this animates the one dimension
            // that is allowed to move while the slot around it expands.
            .animateContentSize(AppMotion.spatialFastAs())
            .semantics {
                role = Role.Button
                if (onClick != null) contentDescription = chipDescription
            },
    )
}
