package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_item
import aichecklists.core.designsystem.generated.resources.add_item_placeholder
import aichecklists.core.designsystem.generated.resources.item_create_chip_important
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
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
 *
 * @param trailingToggle an optional toggle drawn in the SAME trailing slot, immediately before the
 *   "+", [AppDimens.SpacingXs] apart — in practice [ImportantStarToggle] in both capture docks.
 *
 *   A slot rather than a typed `important` / `onImportantToggle` pair because the property being
 *   toggled belongs to a feature-layer draft, and because the arrangement is what this component
 *   owns: one container, one edge, both actions attached to the thing they act on. Null (default)
 *   leaves the four page-plane call sites exactly as they were.
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
    trailingToggle: (@Composable () -> Unit)? = null,
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
                // The toggle rides in the SAME slot as the "+", before it, so the field keeps ONE
                // trailing edge instead of growing a second control outside its ring. `Row` rather
                // than two slots because `OutlinedTextField` has only this one.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailingToggle != null) {
                        trailingToggle()
                        Spacer(Modifier.width(AppDimens.SpacingXs))
                    }
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
                        modifier = Modifier.size(TrailingActionTarget),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(TrailingActionVisual)
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
                }
            },
        )
    }
}

/**
 * The "mark this task as important" toggle that rides in [AddItemInputField]'s trailing slot.
 *
 * ## Why it is here and not a chip in the due rail
 * It was a `GistiSelectableChipItem` pinned to the right of the due rail, filled `primary` when on,
 * and the owner rejected both halves on 2026-09-03 ("не нравится кнопка добавить в избранное, она
 * очень плохо выглядит и находится в плохом месте"). Three things were wrong at once and this
 * placement answers all three:
 *
 *  - **Blue.** A `primary` fill 40dp from the `primary` submit button reads as a second primary
 *    action, and as a *selected chip* in a row whose chips are answers to "when". Gold is this app's
 *    own priority colour ([GistiColors.star], already the star on every task row), so ON is now the
 *    same signal here and in the list.
 *  - **Place.** Importance is a property of the task being composed, so it belongs beside the action
 *    that commits it — not in the list of answers to "when", where it also folded away with the
 *    offers whenever the planner was open and was therefore unreachable in that state.
 *  - **Width.** Pinned outside the rail's scroll it cost ~56dp permanently, clipping the offers
 *    mid-word at 360dp ("Wee…"). The rail gets that width back.
 *
 * Built with the exact construction the "+" beside it uses: a transparent
 * [TrailingActionTarget] `Surface(onClick)` around a [TrailingActionVisual] visual, so the touch
 * target clears 48dp while the drawn control stays clear of the field's ring.
 *
 * Colour is never the only channel — the glyph itself changes silhouette (outlined ☆ → filled ★).
 * The accessible name is the EXISTING `item_create_chip_important` string, and the state travels as
 * `toggleableState` rather than being folded into the description, so a screen reader announces one
 * name and one state instead of two names for one control.
 */
@Composable
fun ImportantStarToggle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.item_create_chip_important)
    val toggleState = if (selected) ToggleableState.On else ToggleableState.Off

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .size(TrailingActionTarget)
            .semantics {
                contentDescription = description
                toggleableState = toggleState
                // `Checkbox`, not `Switch`: M3 reserves Switch for a control that turns a MODE on and
                // off, while this one marks a property of the thing being composed — the same reading
                // `Modifier.toggleable` gives an icon toggle by default. Without a role TalkBack
                // announces a name and a state but not what KIND of control it is, so "double-tap to
                // toggle" never gets said. Set here rather than left to `Surface(onClick)`, which
                // would announce it as a plain Button and contradict the state beside it.
                role = Role.Checkbox
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(TrailingActionVisual)
                    .background(
                        // A tint, not a fill: at full strength gold on a white field is louder than
                        // the submit button it sits beside, and this control is a qualifier rather
                        // than the commit. 0.18 keeps the disc visible on both the light field
                        // (`#FFFFFF`) and the dark one (`#26282E`).
                        color = if (selected) {
                            GistiColors.star.copy(alpha = ImportantOnContainerAlpha)
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    // The name lives on the Surface above, which merges its descendants; a second
                    // description here would replace it rather than add to it.
                    contentDescription = null,
                    tint = if (selected) {
                        GistiColors.star
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(ImportantGlyphSize),
                )
            }
        }
    }
}

/** Touch target of a control in the input field's trailing slot — the 48dp floor, never less. */
private val TrailingActionTarget = 48.dp

/**
 * The DRAWN size of that control, inside the [TrailingActionTarget] touch square.
 *
 * 40dp matches the mic in `AskGistiBar`, and the 8dp of inset is what keeps a trailing circle off
 * the field's own ring: recorded at 360dp, a 48dp circle landed on the outline at x=343 with no gap.
 */
private val TrailingActionVisual = 40.dp

/** Glyph inside [ImportantStarToggle]: 22dp, one step up from the 20dp default, on a 40dp disc. */
private val ImportantGlyphSize = 22.dp

/** Strength of the gold disc behind an ON [ImportantStarToggle]. */
private const val ImportantOnContainerAlpha = 0.18f
