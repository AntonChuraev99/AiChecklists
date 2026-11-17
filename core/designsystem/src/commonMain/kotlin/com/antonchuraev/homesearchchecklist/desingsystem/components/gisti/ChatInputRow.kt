package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_attach_file_action
import aichecklists.core.designsystem.generated.resources.chat_credits_label
import aichecklists.core.designsystem.generated.resources.chat_features_help_action
import aichecklists.core.designsystem.generated.resources.chat_input_placeholder
import aichecklists.core.designsystem.generated.resources.chat_input_placeholder_with_attachment
import aichecklists.core.designsystem.generated.resources.chat_input_transcribing_placeholder
import aichecklists.core.designsystem.generated.resources.chat_pricing_caption_action
import aichecklists.core.designsystem.generated.resources.nav_ask_pill_call_description
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom input row of the AI chat — the chat screen, the docks, the full-screen overlay and the
 * onboarding Talk screen (DESIGN_SPEC onboarding-steps-2026-11-17 §3.2: moved here from
 * `feature:aichat:impl` so onboarding reuses the chat's own composer instead of a thinner twin).
 *
 * Layout: `[Help | Credits | —] · [TextField weight=1f] · [GistiAttachButton "+"]? · [GistiCallButton?] · [Mic OR Send]`
 *
 * The trailing Mic ⇄ Send control, its press-and-hold gesture and its TalkBack action are
 * [GistiMicSendButton] in its [MicSendStyle.Bare] look (shared with the onboarding Write composer,
 * which uses [MicSendStyle.Disc]).
 *
 * @param text                      Current input text.
 * @param onTextChange              Called on every keystroke.
 * @param onSend                    Called when Send button or IME Send action fires.
 * @param onAttachFileClick         Called when the attach "+" button is tapped.
 * @param onVoiceRecordingStarted   Called when the user presses-and-holds the mic.
 * @param onVoiceRecordingStopped   Called when the user releases the mic without cancel.
 * @param onVoiceRecordingCancelled Called when the user releases after drag-up > 80dp.
 * @param isEnabled                 When false, all interactive elements are disabled.
 * @param canSend                   True when text is non-blank OR pendingAttachments is non-empty.
 * @param isRecording               True while voice recording is in progress.
 * @param isTranscribing            True while audio is being sent to the transcription Cloud Function.
 *                                  Replaces the mic/send button with a progress indicator and
 *                                  the field hint with «Recognizing speech…» (full-strength colour,
 *                                  polite live region). Input field is read-only in this state.
 * @param onDragCancelChanged       Reports drag-up cancel state to parent for overlay label.
 * @param focusRequester            When non-null, attached to the center [TextField] so a
 *                                  caller can programmatically request focus (e.g. auto-focus
 *                                  the input when the inline chat dock expands). The caller
 *                                  owns the [androidx.compose.runtime.LaunchedEffect] that calls
 *                                  `focusRequester.requestFocus()`; this component only wires
 *                                  the modifier.
 */
@Composable
fun ChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFileClick: () -> Unit,
    onVoiceRecordingStarted: () -> Unit,
    onVoiceRecordingStopped: () -> Unit,
    onVoiceRecordingCancelled: () -> Unit,
    onHelpClick: () -> Unit = {},
    hasAttachments: Boolean = false,
    isEnabled: Boolean = true,
    canSend: Boolean = false,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onDragCancelChanged: ((Boolean) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /**
     * Called whenever the text field's focus changes (`true` = gained, `false` = lost). Used by the
     * in-place expandable dock: focus → expand + lock the dock open while the keyboard is up; blur →
     * release the lock (collapse if the input is blank). No-op elsewhere (full ChatScreen passes null).
     */
    onFocusChanged: ((Boolean) -> Unit)? = null,
    /**
     * When non-null, replaces the default "Message" placeholder. Used by the in-place dock peek to
     * show a contextual hint ("Ask Gisti…" on home, "Ask about <name>…" inside a checklist). null →
     * the resource-driven default (the full ChatScreen is unchanged). The pending-attachment hint
     * still takes precedence when attachments are queued.
     */
    placeholderOverride: String? = null,
    /**
     * When true the row renders in a stripped-down "send only" mode used by the checklist-detail
     * item-create dock: the leading help button and the trailing attach button are hidden, and the
     * trailing control is ALWAYS a Send button (never the press-and-hold mic) — disabled while the
     * input is blank ([canSend] == false) so a blank add is impossible. Voice/attach are chat-only
     * features that don't apply when the dock is creating a checklist item. Defaults to false so the
     * full ChatScreen + AI-chat dock are unchanged.
     */
    simpleSendOnly: Boolean = false,
    /**
     * In [simpleSendOnly] (item-create) mode the trailing attach button is hidden by default. Set
     * true to KEEP it visible so the item-create dock can attach files to the new item. The leading
     * help button and the press-and-hold mic stay hidden regardless. No-op when [simpleSendOnly] is
     * false (the full AI-chat row already shows attach). Defaults to false.
     */
    attachVisibleInSimpleMode: Boolean = false,
    /**
     * What sits in the leading slot. [ChatInputLeading.Help] (default) = the "?" features-help button
     * wired to [onHelpClick] — the dock hosts keep it. [ChatInputLeading.Credits] = the redesign-B
     * credits chip (full ChatScreen): balance in a pill that opens the pricing sheet.
     * [ChatInputLeading.None] = nothing, the field starts at a small inset (onboarding Talk). All hide
     * while [simpleSendOnly] or [isTranscribing], with the same width-collapse morph.
     */
    leading: ChatInputLeading = ChatInputLeading.Help,
    /**
     * Starts a voice call — the SAME entry as the home Ask pill's call button ([GistiCallButton]),
     * placed INSIDE this row between attach and mic (never as a row of its own: the dock measures
     * `inputContent` and an extra row overflows the panel with the keyboard up). Null draws no button
     * (platform without calls, `voice_call_enabled` off). Shown/greyed per [chatInputCallSlot].
     */
    onCallClick: (() -> Unit)? = null,
    /**
     * False hides the attach "+" in the chat mode too. The onboarding Talk screen passes false: a "+"
     * there would be a third meaning for files next to the Attach branch. Defaults to true, so every
     * chat host is unchanged.
     */
    attachVisible: Boolean = true,
    /**
     * False drops the mic: the trailing control is a Send that is disabled while there is nothing to
     * send (web without Google, where every dictation would fail on credits). Defaults to true.
     */
    micVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Pre-resolve strings in Composable scope (spec §10 rule 6)
    val attachFileLabel = stringResource(Res.string.chat_attach_file_action)
    val helpLabel = stringResource(Res.string.chat_features_help_action)
    val callLabel = stringResource(Res.string.nav_ask_pill_call_description)
    val defaultPlaceholder = stringResource(Res.string.chat_input_placeholder)
    val attachmentPlaceholder = stringResource(Res.string.chat_input_placeholder_with_attachment)
    val transcribingPlaceholder = stringResource(Res.string.chat_input_transcribing_placeholder)
    // Transcription wins over every other hint: the cold-start `transcribe_audio` call can take tens
    // of seconds, and a field that still says "Message Gisti" next to a lone spinner reads as frozen.
    // The mic is only offered while the input is blank (isMic = !canSend), so the field is empty for
    // the whole transcription and this hint is always what the user sees.
    val placeholder = when {
        isTranscribing -> transcribingPlaceholder
        hasAttachments -> attachmentPlaceholder
        placeholderOverride != null -> placeholderOverride
        else -> defaultPlaceholder
    }

    // The host exposes the input as a plain String, but we render with a TextFieldValue so we
    // control the caret. Default String-valued TextField collapses the caret to position 0 when
    // the value changes programmatically — so prompt-chip prefill ("Remind me to ") and
    // clear-after-send would land the caret at the START. We adopt external string changes with
    // the caret at the END. Plain typing round-trips the same string back in, so the
    // LaunchedEffect is a no-op mid-edit and never fights the user's cursor.
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    LaunchedEffect(text) {
        if (text != textFieldValue.text) {
            textFieldValue = TextFieldValue(text, TextRange(text.length))
        }
    }

    // UI-local: drives the focus ring on the pill (the host callback below is unchanged).
    var isFieldFocused by remember { mutableStateOf(false) }

    val showLeading = !simpleSendOnly && !isTranscribing && leading != ChatInputLeading.None

    // Outer column applies the surface padding (M3 chat bar — `padding: 8px 16px 12px`).
    // Inner pill is `AppChatColors.raised()` + a 1dp `controlOutline()` — see the Surface below for
    // why both are plane-relative. (It used to be a fixed `surfaceContainerLowest` + `outlineVariant`,
    // itself a fix for an earlier `surfaceContainerHigh` grey tonal fill — the "страшный серый" the
    // user reported. `raised()` keeps that white-in-light result and repairs the dark dock.)
    // 28dp radius, all 4 controls live inside; minHeight 56dp lets the pill grow when text wraps.
    // NOTE: this component does NOT apply imePadding / navigationBars itself. The bottom inset
    // (keyboard + system-nav) is owned by the HOST so it can be applied exactly once:
    //  - full ChatScreen: root Column already has .imePadding() + Scaffold navbar via scaffoldPadding;
    //  - expandable dock: the HOST (GistiGlassChatDock's call site) applies imePadding +
    //    navigationBarsPadding, lifting the whole dock as one unit;
    //  - full-screen overlay (GistiFullChatOverlay): its input Column applies ime.union(navigationBars).
    // Applying it here too would double the inset in the full chat (Scaffold already insets navbar).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingSm,
                bottom = AppDimens.SpacingMd,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            // Plane-relative. This same row renders on the page (full ChatScreen) and inside the
            // bottom chrome (chat dock, capture dock, full-screen overlay); `surfaceContainerLowest`
            // is a step UP from the light page but ΔL* −6.2 DOWN from the dark chrome, i.e. a hole
            // in the dock rather than a pill on it. `outline`, not `outlineVariant`, because this is
            // a tappable target. See AppChatColors.
            color = AppChatColors.raised(),
            // B pass 2 §2.1: 2dp primary ring while the field has focus — the same rule as the
            // capture dock's composer card — 1dp control outline otherwise. The 28dp radius on a
            // 56dp minimum IS the pill at one line and stays 28 as the field grows.
            border = if (isFieldFocused) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, AppChatColors.controlOutline())
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
        // [1] Leading help button (Telegram-style — replaces emoji slot). In item-create mode it
        // animates OUT (width collapse + fade) so the chat→create dock change is a smooth in-place
        // morph instead of a hard node swap; a matching inset Spacer animates IN so the field keeps
        // a small left gap once the button is gone. (Both wrappers live in the SAME ChatInputRow node
        // across the mode flip — that node persistence is what lets these transitions actually run.)
        // While transcribing, help and attach are disabled anyway; they step aside (same width-collapse
        // morph as item-create mode) so the «Recognizing speech…» status gets the room it needs —
        // at 320dp × fontScale 1.5 it otherwise truncated to «Recogni…».
        AnimatedVisibility(
            visible = showLeading,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            when (leading) {
                // Not gated on isEnabled (= "not processing"): reading the pricing sheet while an
                // answer is on its way conflicts with nothing, and a greyed balance read as an error.
                is ChatInputLeading.Credits -> CreditsLeadingChip(
                    balance = leading.balance,
                    onClick = leading.onClick,
                    enabled = !isRecording && !isTranscribing,
                )
                ChatInputLeading.Help -> IconButton(
                    onClick = onHelpClick,
                    enabled = isEnabled && !isRecording && !isTranscribing,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = helpLabel,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                        // Disabled while transcribing too — tinting it like an idle control is what made
                        // the row read as "ready for input" while the recording was still uploading.
                        tint = if (isEnabled && !isRecording && !isTranscribing)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
                // Never visible (showLeading is false for None); here only to keep `when` exhaustive.
                ChatInputLeading.None -> Unit
            }
        }
        AnimatedVisibility(
            visible = !showLeading,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            // Keep a small leading inset so the text field doesn't butt against the pill edge.
            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
        }

        // [2] Center text field — borderless Telegram-style.
        // Indicator + container colors are transparent so the field blends into the row;
        // placeholder swaps when attachments are pending.
        TextField(
            value = textFieldValue,
            onValueChange = if (isRecording || isTranscribing) { _ -> } else { newValue ->
                textFieldValue = newValue
                if (newValue.text != text) onTextChange(newValue.text)
            },
            placeholder = {
                // Up to TWO lines (B pass 2 §2.1, iteration-10 open item): at 320dp the RU hint
                // "Сообщение Gisti" beside the credits chip truncated to "Сооб…" on one line — a
                // wrapped hint reads, a clipped one does not. The pill grows (heightIn, never a fixed
                // height). Ellipsis only past the second line; typed text still grows to 4 lines.
                // While transcribing the hint is a STATUS, not an invitation to type: full-strength
                // onSurface (the idle hint is the weaker onSurfaceVariant) and a polite live region so
                // TalkBack announces it once instead of leaving a silent disabled field.
                // The status may wrap to a second line at large font scales — a cut-off
                // «Recogni…» is not a status; the idle hint gets the same two lines (see above).
                ChatInputPlaceholder(
                    text = placeholder,
                    color = if (isTranscribing) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = if (isTranscribing) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                )
            },
            singleLine = false,
            maxLines = 4,
            enabled = isEnabled && !isRecording && !isTranscribing,
            modifier = Modifier
                .weight(1f)
                .let { base -> focusRequester?.let { base.focusRequester(it) } ?: base }
                .onFocusChanged {
                    isFieldFocused = it.isFocused
                    onFocusChanged?.invoke(it.isFocused)
                },
            // Item-create (simpleSendOnly): Enter/Done adds the item (parity with the removed inline
            // field) and the first letter auto-capitalizes (Sentences). AI-chat path is unchanged.
            keyboardOptions = KeyboardOptions(
                capitalization = if (simpleSendOnly) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                imeAction = when {
                    simpleSendOnly -> ImeAction.Done
                    canSend -> ImeAction.Send
                    else -> ImeAction.Default
                },
            ),
            keyboardActions = if (simpleSendOnly) {
                KeyboardActions(onDone = { if (canSend) onSend() })
            } else {
                KeyboardActions.Default
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )

        // [3] Attach file button (moved to trailing side, next to Mic/Send — Telegram pattern).
        // Normally animates out in item-create mode, but the item-create dock re-enables it via
        // [attachVisibleInSimpleMode] so files can be attached to the new item (help + mic still hide).
        AnimatedVisibility(
            visible = attachVisible && (!simpleSendOnly || attachVisibleInSimpleMode) && !isTranscribing,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            // The home Ask pill's "+" itself, not a look-alike (owner, layout-debug 2026-11-16:
            // "переиспользуй + с экрана главного, кнопки вложений должны везде совпадать"): same
            // 36dp recessed disc, same glyph, same 48dp target. Disabled while recording or
            // transcribing too — tinting it like an idle control is what made the row read as
            // "ready for input" while the recording was still uploading.
            GistiAttachButton(
                onClick = onAttachFileClick,
                contentDescription = attachFileLabel,
                enabled = isEnabled && !isRecording && !isTranscribing,
            )
        }

        // [3b] Voice call — the home pill's call disc itself. Same morph as attach; see chatInputCallSlot
        // for when it hides vs greys out.
        val callSlot = chatInputCallSlot(
            hasCallEntry = onCallClick != null,
            simpleSendOnly = simpleSendOnly,
            isTranscribing = isTranscribing,
            canSend = canSend,
            isEnabled = isEnabled,
            isRecording = isRecording,
        )
        AnimatedVisibility(
            visible = callSlot != ChatInputCallSlot.Hidden,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            GistiCallButton(
                onClick = { onCallClick?.invoke() },
                contentDescription = callLabel,
                enabled = callSlot == ChatInputCallSlot.Enabled,
            )
        }

        // [4] Trailing Mic / Send / Transcribing indicator — the shared control, bare-glyph look.
        // Item-create mode (and a row without the mic) never shows the mic: the trailing control is
        // always Send, disabled while blank.
        GistiMicSendButton(
            canSend = canSend,
            onSend = onSend,
            onVoiceRecordingStarted = onVoiceRecordingStarted,
            onVoiceRecordingStopped = onVoiceRecordingStopped,
            onVoiceRecordingCancelled = onVoiceRecordingCancelled,
            enabled = isEnabled,
            isRecording = isRecording,
            isTranscribing = isTranscribing,
            onDragCancelChanged = onDragCancelChanged,
            sendOnly = simpleSendOnly || !micVisible,
            style = MicSendStyle.Bare,
        )
        }  // end Row
        }  // end Surface
    }  // end Column
}

/**
 * The field hint: up to TWO lines, but only ever broken BETWEEN words.
 *
 * At 320dp × fontScale 1.5 the RU word «Сообщение» alone is wider than the slot left between the
 * credits chip and attach / mic, so a plain `maxLines = 2` broke it mid-word («Сообщ / ение …») —
 * worse than the old one-line «Сооб…». When every word fits the slot the hint wraps on words (2
 * lines); when one does not, it falls back to a single ellipsised line. Measured against the slot's
 * own constraints, so the decision follows the real width, font scale and locale.
 */
@Composable
private fun ChatInputPlaceholder(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        val wrapsOnWords = remember(text, style, maxWidthPx, bounded) {
            !bounded || text.split(' ').filter { it.isNotEmpty() }.all { word ->
                measurer.measure(text = word, style = style, maxLines = 1, softWrap = false).size.width <= maxWidthPx
            }
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (wrapsOnWords) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the voice-call slot of [ChatInputRow] draws. */
enum class ChatInputCallSlot { Hidden, Enabled, Disabled }

/**
 * Call-button rule of [ChatInputRow]:
 * - **Hidden** — no call entry wired; item-create mode (the row is a Send-only add field); while
 *   transcribing (attach steps aside for the «Recognizing speech…» status too); and while the user has
 *   something to send — the call sits beside the MIC, and the mic itself has become Send.
 * - **Disabled** (greyed disc, no click) — recording, or a reply is being processed: starting a call
 *   would drop the recording / race the answer.
 * - **Enabled** otherwise.
 *
 * Public only so `feature:aichat`'s existing unit test keeps covering it after the move to the design
 * system; no other caller.
 */
fun chatInputCallSlot(
    hasCallEntry: Boolean,
    simpleSendOnly: Boolean,
    isTranscribing: Boolean,
    canSend: Boolean,
    isEnabled: Boolean,
    isRecording: Boolean,
): ChatInputCallSlot = when {
    !hasCallEntry || simpleSendOnly || isTranscribing -> ChatInputCallSlot.Hidden
    canSend && !isRecording -> ChatInputCallSlot.Hidden
    !isEnabled || isRecording -> ChatInputCallSlot.Disabled
    else -> ChatInputCallSlot.Enabled
}

/** Content of [ChatInputRow]'s leading slot (redesign B §2.10). */
sealed interface ChatInputLeading {
    /** The "?" features-help button (today's default; the dock hosts keep it). */
    data object Help : ChatInputLeading

    /** The credits chip: [balance] in a pill; [onClick] opens the pricing sheet. */
    data class Credits(val balance: Int, val onClick: () -> Unit) : ChatInputLeading

    /** Nothing: the field starts at a small inset (onboarding Talk — no help, no wallet yet). */
    data object None : ChatInputLeading
}

/**
 * Credits chip in the input's leading slot: a 32dp `primaryContainer` pill (sparkle 16dp in
 * `primary` + the balance in `labelLarge` Bold `onPrimaryContainer`) inside a 48dp target. Grows with
 * the text at large font scales (`heightIn`, never a fixed height).
 *
 * Semantics: ONE button whose name is the full `chat_credits_label` ("80 credits left · ≈ 0–3") and
 * whose click label is `chat_pricing_caption_action` ("Why?") — the bare digit is not announced.
 */
@Composable
private fun CreditsLeadingChip(
    balance: Int,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val description = stringResource(Res.string.chat_credits_label, balance)
    val clickLabel = stringResource(Res.string.chat_pricing_caption_action)
    val contentAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                onClickLabel = clickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = balance.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = contentAlpha),
                maxLines = 1,
            )
        }
    }
}

/**
 * Legacy overload for existing call sites that don't pass attachment/voice params.
 * Delegates to the full signature with no-op handlers.
 */
@Composable
fun ChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ChatInputRow(
        text = text,
        onTextChange = onTextChange,
        onSend = onSend,
        onAttachFileClick = {},
        onVoiceRecordingStarted = {},
        onVoiceRecordingStopped = {},
        onVoiceRecordingCancelled = {},
        isEnabled = isEnabled,
        canSend = isEnabled && text.isNotBlank(),
        isRecording = false,
        modifier = modifier,
    )
}
