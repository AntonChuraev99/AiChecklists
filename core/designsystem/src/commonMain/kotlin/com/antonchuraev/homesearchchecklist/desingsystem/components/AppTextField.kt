package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    showClearButton: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /**
     * Character range of [value] to tint with a `primaryContainer` background — today: the phrase
     * Smart-Add recognised in a capture-dock task ("call mum **tomorrow**").
     *
     * A range rather than a ready [VisualTransformation] so this component keeps ONE way of styling
     * itself: a caller handing in its own transformation could change the offset mapping, and a
     * text field whose caret lands somewhere other than where the user tapped is a defect no golden
     * would catch. The mapping here is [OffsetMapping.Identity] by construction — the transform adds
     * a span and never a character.
     *
     * Background only. Not bold, not a different text colour: the field's job is to show what was
     * typed, and re-weighting a fragment of the user's own sentence reads as the app editing it.
     *
     * ⚠️ Indices address THIS string. Whoever produces them owns proving that — the parser reports
     * offsets into a whitespace-normalised copy of the input, so the capture hosts re-check the
     * substring before passing anything in (`TaskDraft.smartAddHighlightRange`). Null (the default)
     * is `VisualTransformation.None`, i.e. every existing call site renders byte-for-byte as before.
     */
    highlightRange: IntRange? = null,
) {
    val effectiveTrailingIcon: @Composable (() -> Unit)? = when {
        showClearButton && value.isNotEmpty() -> {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        trailingIcon != null -> trailingIcon
        else -> null
    }

    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val visualTransformation = remember(highlightRange, highlightColor) {
        spanBackgroundTransformation(highlightRange, highlightColor)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // A single-line field's placeholder must never wrap: a long hint would otherwise grow
                    // the decoration box to two lines (a too-tall field). Honor singleLine here; multi-line
                    // fields keep unbounded placeholder wrapping.
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = effectiveTrailingIcon,
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else null,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Paints [background] behind [range] and changes nothing else about the text.
 *
 * Extracted from [AppTextField] so the mapping can be asserted without rendering a frame: a
 * screenshot shows that SOMETHING is tinted, while this returns the exact span offsets, and "the
 * highlight is one character off" is invisible in a 360px PNG and obvious in an assertion.
 *
 * Every out-of-contract input degrades to [VisualTransformation.None] rather than throwing or
 * guessing — a null range, an inverted range, an empty range, a range past the end of the string.
 * The field is on the app's capture path: a highlight that does not appear costs a decoration,
 * whereas an exception inside a `VisualTransformation` takes the whole text field down with it, and
 * a clamped-to-fit range paints the wrong word with full confidence.
 */
internal fun spanBackgroundTransformation(
    range: IntRange?,
    background: Color,
): VisualTransformation {
    if (range == null || range.isEmpty() || range.first < 0) return VisualTransformation.None
    return VisualTransformation { text ->
        val start = range.first
        val end = range.last + 1
        val styled = if (end > text.length) {
            AnnotatedString(text.text)
        } else {
            AnnotatedString(
                text = text.text,
                spanStyles = listOf(
                    AnnotatedString.Range(SpanStyle(background = background), start, end),
                ),
            )
        }
        TransformedText(styled, OffsetMapping.Identity)
    }
}
