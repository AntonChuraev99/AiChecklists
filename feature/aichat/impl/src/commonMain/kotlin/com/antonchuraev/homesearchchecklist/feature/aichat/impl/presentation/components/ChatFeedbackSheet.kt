package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_feedback_answer_label
import aichecklists.core.designsystem.generated.resources.chat_feedback_cancel
import aichecklists.core.designsystem.generated.resources.chat_feedback_placeholder
import aichecklists.core.designsystem.generated.resources.chat_feedback_question_label
import aichecklists.core.designsystem.generated.resources.chat_feedback_submit
import aichecklists.core.designsystem.generated.resources.chat_feedback_subtitle
import aichecklists.core.designsystem.generated.resources.chat_feedback_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import org.jetbrains.compose.resources.stringResource

/**
 * Feedback bottom sheet for an assistant reply.
 *
 * Layout (top → bottom):
 * 1. Title + subtitle
 * 2. Read-only question preview (previous user message, maxLines=2)
 * 3. Read-only AI answer preview (maxLines=3)
 * 4. OutlinedTextField — multi-line free-form feedback
 * 5. Row: Cancel (AppButtonText) + Submit (AppButton, disabled when blank / submitting)
 *
 * Uses [LazyColumn] instead of Column+verticalScroll to integrate correctly with
 * [ModalBottomSheet] nested scroll — avoids the scroll-jitter anti-pattern.
 *
 * @param target                The assistant [ChatMessage] being reviewed.
 * @param previousUserQuestion  The preceding user message content, or null if not found.
 * @param feedbackText          Current text in the feedback input field.
 * @param isSubmitting          True while submit is in-flight (disables Submit button).
 * @param onTextChange          Called on every keystroke in the feedback field.
 * @param onSubmit              Called when the user taps Submit.
 * @param onDismiss             Called when the sheet should close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFeedbackSheet(
    target: ChatMessage,
    previousUserQuestion: String?,
    feedbackText: String,
    isSubmitting: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        // LazyColumn: integrates correctly with ModalBottomSheet NestedScrollConnection
        // (avoids Column+verticalScroll jitter — see CLAUDE.md §5 BottomSheets).
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {

            // ── Title ─────────────────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(Res.string.chat_feedback_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.chat_feedback_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            }

            // ── Question preview ──────────────────────────────────────────────
            if (!previousUserQuestion.isNullOrBlank()) {
                item {
                    PreviewCard(
                        label = stringResource(Res.string.chat_feedback_question_label),
                        content = previousUserQuestion,
                        maxLines = 2,
                    )
                }
            }

            // ── AI answer preview ─────────────────────────────────────────────
            item {
                PreviewCard(
                    label = stringResource(Res.string.chat_feedback_answer_label),
                    content = target.content,
                    maxLines = 3,
                )
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
            }

            // ── Feedback input ────────────────────────────────────────────────
            item {
                AppTextField(
                    value = feedbackText,
                    onValueChange = onTextChange,
                    placeholder = stringResource(Res.string.chat_feedback_placeholder),
                    singleLine = false,
                    maxLines = 6,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            }

            // ── Action row ────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppButtonText(
                        text = stringResource(Res.string.chat_feedback_cancel),
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                    )
                    AppButton(
                        text = stringResource(Res.string.chat_feedback_submit),
                        onClick = onSubmit,
                        enabled = !isSubmitting && feedbackText.isNotBlank(),
                    )
                }
            }

            // Bottom spacing so action row isn't flush with the navigation bar
            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
            }
        }
    }

    // Auto-focus the feedback field when the sheet appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

// ---------------------------------------------------------------------------
// Internal composables
// ---------------------------------------------------------------------------

/**
 * Read-only card showing a labelled preview of the question or answer.
 */
@Composable
private fun PreviewCard(
    label: String,
    content: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
