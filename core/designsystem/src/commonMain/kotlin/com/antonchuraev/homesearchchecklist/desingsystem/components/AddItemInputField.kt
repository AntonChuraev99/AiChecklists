package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_item
import aichecklists.core.designsystem.generated.resources.add_item_placeholder
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Inline input for adding items. Wraps [AppTextField] in multiline mode so long item text
 * wraps to several lines instead of scrolling horizontally. IME Done still submits.
 *
 * ## The field is FILLED, and the "+" lives inside it
 * Both halves of that sentence answer one measurement taken on the recorded dock frames: the field's
 * interior read `222,220,214` — byte-identical to the dock behind it, contrast **1.00 : 1** — while
 * the optional date chips one row above were filled white (1.37 : 1). The primary target on the
 * surface was the only element with no body, and the commit action beside it was, in the empty
 * state, the quietest thing in the row. A user reading weight as importance was being told the
 * opposite of the truth.
 *
 * The fill is [AppChatColors.raised], the SAME token the chips take, and it is a token rather than a
 * colour because it resolves against the plane it is drawn on: `bottomChromeRaised` inside the
 * capture dock (ΔL\* +12.2 light / +5.9 dark off the chrome), `card` on the four page-plane call
 * sites (analyze preview, template preview, create-checklist, weekly detail). A literal picked from
 * the dock frame would have been correct in the dock and wrong on all four of the others.
 *
 * The "+" moved from a 56dp sibling square into the field's trailing slot, the arrangement
 * [com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBar] already uses for
 * its mic: one container, one edge, the action attached to the thing it acts on. The row's height is
 * unchanged — the button is 48dp inside a field whose minimum height is 56dp, so nothing grows, and
 * the field gains the 64dp the sibling square and its gap used to take.
 *
 * ⚠️ The background shape is `MaterialTheme.shapes.small` because that is the shape [AppTextField]
 * gives its `OutlinedTextField`. The two have to agree or the fill corners cut inside the ring;
 * changing one means changing the other.
 *
 * @param leadingPreview Optional slot rendered ABOVE the input row. Intended for
 *   [TokenChipPreview] when the Smart Add parser recognises a date/time/repeat phrase.
 *   Animated with [AnimatedVisibility] so the chip appears/disappears smoothly without
 *   causing IME jumps. Pass null (default) for the standard single-row layout.
 *
 *   Position rationale (Option A — chip above): a chip below the input disappears behind
 *   the soft keyboard on small screens, giving the user no visual confirmation that a
 *   reminder will be set. A chip above the field is always visible, regardless of IME state.
 *
 * @param highlightRange character range of [text] to tint — the phrase Smart-Add recognised. Passed
 *   straight through to [AppTextField]; this component neither parses nor validates it, and the host
 *   that produces it owns proving the indices address THIS string (see
 *   `TaskDraft.smartAddHighlightRange`). Null = no highlight, the state every non-dock call site is
 *   in.
 *
 * @param focusRequester attaches to the TEXT FIELD itself, so a caller that reveals this row on
 *   demand (the v2 Inbox capture dock) can raise the keyboard with it. It has to be threaded in
 *   rather than applied to `modifier` by the caller: a requester on the wrapping Column moves focus
 *   to the column, not into the field, and the keyboard never appears. Null (default) = the four
 *   existing call sites are untouched.
 */
@Composable
fun AddItemInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    placeholder: String = stringResource(Res.string.add_item_placeholder),
    modifier: Modifier = Modifier,
    leadingPreview: (@Composable () -> Unit)? = null,
    highlightRange: IntRange? = null,
    focusRequester: FocusRequester? = null,
) {
    val isTextNotBlank = text.isNotBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        // Chip preview slot — animated so appear/disappear is smooth.
        // Spacing lives INSIDE AnimatedVisibility (bottom padding on the slot wrapper)
        // to avoid Arrangement.spacedBy gap snapping on exit (shrinkVertically pitfall).
        AnimatedVisibility(
            visible = leadingPreview != null,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Box(
                modifier = Modifier.padding(bottom = AppDimens.SpacingSm),
            ) {
                leadingPreview?.invoke()
            }
        }

        AppTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = placeholder,
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                // Drawn BEHIND the OutlinedTextField, whose own container is transparent — this is
                // the fill the field never had. Same shape as the ring it sits under (see the KDoc).
                .background(AppChatColors.raised(), MaterialTheme.shapes.small)
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                ),
            highlightRange = highlightRange,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (isTextNotBlank) onAdd() }
            ),
            trailingIcon = {
                // ── Two boxes, two different jobs ────────────────────────────────────────────
                // The TOUCH target is the transparent 48dp Surface; the VISIBLE button is the 40dp
                // circle inside it. They are separate because a `Modifier.size` on a clickable
                // Surface IS the touch target — there is no `minimumInteractiveComponentSize`
                // underneath to rescue a 40dp one the way there is inside an `IconButton` — while a
                // 48dp circle drawn at the trailing edge of a 56dp field collides with the ring it
                // sits in: recorded at 360dp, its right edge landed on the outline at x=343 with no
                // gap at all, and it filled the field's height to within 4dp.
                //
                // 40dp visible matches the mic in `AskGistiBar`, which is the arrangement this row
                // was asked to copy. The 4dp end padding is on top of the 4dp the 48→40 inset
                // already gives, because `OutlinedTextField` places its trailing slot flush against
                // the container edge and contributes none of its own.
                Box(modifier = Modifier.padding(end = AppDimens.SpacingXs)) {
                    Surface(
                        onClick = onAdd,
                        enabled = isTextNotBlank,
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isTextNotBlank) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(Res.string.add_item),
                                    tint = if (isTextNotBlank) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}
