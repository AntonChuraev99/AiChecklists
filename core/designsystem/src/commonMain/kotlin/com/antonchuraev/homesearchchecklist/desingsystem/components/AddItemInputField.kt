package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_item
import aichecklists.core.designsystem.generated.resources.add_item_placeholder
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Inline input for adding items. Wraps [AppTextField] in multiline mode so long item text
 * wraps to several lines instead of scrolling horizontally. IME Done still submits.
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            AppTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = placeholder,
                singleLine = false,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (isTextNotBlank) onAdd() }
                ),
            )

            Surface(
                onClick = onAdd,
                enabled = isTextNotBlank,
                shape = RoundedCornerShape(12.dp),
                color = if (isTextNotBlank) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                // size(56.dp) — was height(56.dp).aspectRatio(1f), which is behaviourally IDENTICAL
                // here: height() is the outer modifier, so aspectRatio receives min=max=56 and its
                // maxWidth candidate fails isSatisfiedBy, leaving 56x56 whatever the row's width.
                // Swapped only because one modifier expressing one square is honest about intent.
                //
                // ⚠️ Do NOT read this as the fix for the v2-Inbox symptom (send button rendered as a
                // screen-wide square that swallowed the tab row). That was diagnosed here first and the
                // diagnosis was WRONG — the real cause was the row living in a content Column with a
                // manual imePadding(), i.e. an unbounded-height parent; it went away when the row moved
                // into AppScaffold's bottomBar slot (see InboxScreen.PinnedQuickAddRow). Recorded
                // because this component has 4 call sites and a false root cause would send the next
                // session hunting the wrong modifier.
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.add_item),
                        tint = if (isTextNotBlank) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
