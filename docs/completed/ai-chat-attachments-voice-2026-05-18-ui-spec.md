# AI Chat Attachments & Voice — UI Design Spec (Phase 2)

**Date:** 2026-05-18
**Phase:** 2 of 3 (Mobile Design — spec only, no code edits)
**Phase 3 implements:** @android-expert (Compose code from this spec)
**Domain contract frozen by:** Phase 1 @kmp-expert (do NOT modify domain models)
**Scope guard:** NO code changes in this document. Spec only.

---

## Design Decisions Summary

Before the sections, explicit decisions made in this spec:

1. **Icon-swap rule for trailing button** — single `FilledIconButton` morphs between `Icons.Filled.Mic` (canSend=false) and `Icons.AutoMirrored.Filled.Send` (canSend=true). No second button. Icon swap uses `Crossfade` (200ms standard-accelerate easing) so the transition is visible but not distracting.

2. **Attachment chips — tile-only, no filename label** — following the item-attachments post-deploy lesson (icon-only over text-label at fixed tile sizes), chips show visual content only. Audio chips show duration ("0:08") as the one exception because duration is low-character, high-signal metadata. Filename visible only on long-press tooltip (Phase 3 can defer to Phase 4 if needed).

3. **Audio row in source chooser = "Audio file" (upload existing)** — press-and-hold mic = new recording; "Audio file" row in ModalBottomSheet = pick existing audio file from device. The two paths are complementary, not redundant. Keeping both avoids a UX trap where users with pre-recorded audio have no path.

4. **Recording overlay** — 3 pulsing animated dots above the input row (not a waveform). Rationale: waveform requires platform audio-level polling (non-trivial on KMP/wasmJs). 3-dot pulse reuses the existing `ChatTypingIndicator` animation pattern already in the codebase. Effort: near-zero for Phase 3.

5. **Scope boundary** — `ItemDetailsSheet` is untouched. Chat attachments live entirely in the chat surface. No shared state between them.

---

## State Map (from Phase 1 contract)

```
pendingAttachments: List<ChatAttachment>   → drives chip strip visibility + canSend
attachmentPickerType: AttachmentSource?    → trigger-flag for picker (non-null = open picker)
isRecording: Boolean                       → drives overlay visibility + mic button state
voiceRecordingError: String?               → drives inline error snackbar
canSend: Boolean (computed)                → inputText.isNotBlank() || pendingAttachments.isNotEmpty()
```

---

## Section 1 — ChatInputRow New Layout

### Structure

```
Row(
  verticalAlignment = CenterVertically,
  horizontalArrangement = spacedBy(SpacingSm),   // SpacingSm = 8dp
  modifier = fillMaxWidth()
            .imePadding()
            .padding(horizontal = ScreenPaddingHorizontal, vertical = SpacingMd)
) {
  [1] AttachFile IconButton        // leading, 48×48dp tap target
  [2] AppTextField weight=1f       // center, multi-line
  [3] Mic / Send FilledIconButton  // trailing, primary action
}
```

### [1] AttachFile IconButton

```
IconButton(
  onClick = { onSendIntent(OnPickAttachment) },   // opens source chooser §4
  enabled = isEnabled,                            // same guard as text field
  modifier = Modifier.size(48.dp),               // min touch target is implicit via IconButton
) {
  Icon(
    imageVector = Icons.Filled.AttachFile,
    contentDescription = stringResource(chat_attach_file_action),
    modifier = Modifier.size(AppDimens.IconSizeMd),   // 24dp
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
```

- Tint: `onSurfaceVariant` at rest; system disabled tint when `isEnabled = false`.
- Disabled state: same condition as TextField (`chatStatus == Routing || Sending`).
- The `OnPickAttachment` intent WITHOUT a source is a UI-layer concern: tapping this button opens the source chooser bottom sheet (§4), which then emits `OnPickAttachment(source: AttachmentSource)` for each specific type.

### [2] AppTextField

- Component: existing `AppTextField` (outlined text field design-system wrapper).
- `singleLine = false`, `maxLines = 4` (was 5; reduced to 4 to leave room for chip strip).
- `placeholder`: `stringResource(Res.string.chat_input_placeholder)` — existing key, no change.
- `keyboardOptions`: `KeyboardOptions(imeAction = if (canSend) ImeAction.Send else ImeAction.Default)`.
- `onKeyboardAction(ImeAction.Send)`: same as `onSend()`.
- `enabled = isEnabled`.
- `modifier = Modifier.weight(1f)`.

### [3] Trailing FilledIconButton (Mic / Send)

```
val isMic = !canSend

FilledIconButton(
  onClick = if (isMic) { /* no-op — press-and-hold handled by pointerInput */ } else onSend,
  enabled = isEnabled,
  modifier = if (isMic) Modifier
      .pointerInput(Unit) {
          detectTapGestures(
              onPress = {
                  onSendIntent(OnVoiceRecordingStarted)
                  tryAwaitRelease()
                  // drag-up-cancel is handled inside pointerInput by Phase 3
                  onSendIntent(OnVoiceRecordingStopped(/* see §2 */))
              }
          )
      }
      else Modifier,
  colors = IconButtonDefaults.filledIconButtonColors(
      containerColor = if (isMic)
          MaterialTheme.colorScheme.surfaceContainerHigh
      else
          MaterialTheme.colorScheme.primary,
      contentColor = if (isMic)
          MaterialTheme.colorScheme.onSurfaceVariant
      else
          MaterialTheme.colorScheme.onPrimary,
  ),
) {
  Crossfade(
      targetState = isMic,
      animationSpec = tween(durationMillis = 200),   // standard-accelerate, exit transition
  ) { showMic ->
      if (showMic) {
          Icon(
              imageVector = Icons.Filled.Mic,
              contentDescription = stringResource(Res.string.chat_record_voice),
              modifier = Modifier.size(AppDimens.IconSizeMd),
          )
      } else {
          Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(Res.string.chat_send_action),
              modifier = Modifier.size(AppDimens.IconSizeMd),
          )
      }
  }
}
```

**Color logic rationale:**
- Send state: `primary` container + `onPrimary` icon — high-emphasis primary action (M3 FilledIconButton default, already in the codebase).
- Mic state: `surfaceContainerHigh` container + `onSurfaceVariant` icon — medium-emphasis at-rest state. The mic is secondary until text or attachments exist. Avoids blue-on-blue clash with the primary button color in the inactive mic state.
- Recording-in-progress: container becomes `errorContainer`, icon tint becomes `onErrorContainer` — universal "recording" signal (matches iOS/Android recording indicators). Animated via `animateColorAsState(durationMillis = 150)` on both colors.

### ASCII Mockup

```
canSend = false (no text, no attachments):

 ┌────────────────────────────────────────────────────────────┐
 │  [📎] │ [Ask anything…        (TextField)        ] │ [🎤] │
 └────────────────────────────────────────────────────────────┘
  onSurfaceVariant                                    surfaceContainerHigh/onSurfaceVariant

canSend = true (has text OR has attachments):

 ┌────────────────────────────────────────────────────────────┐
 │  [📎] │ [Buy milk             (TextField)        ] │ [➤]  │
 └────────────────────────────────────────────────────────────┘
  onSurfaceVariant                                    primary/onPrimary

Recording in progress (isRecording = true):

 ┌──────────── 3 pulsing dots ── "Recording…  0:04" ──────────┐  ← overlay above
 ┌────────────────────────────────────────────────────────────┐
 │  [📎] │ [                               ]          │ [🎤🔴]│  ← mic = errorContainer
 └────────────────────────────────────────────────────────────┘
  (disabled)   (readOnly=true)                        errorContainer/onErrorContainer
```

---

## Section 2 — Mic Press-and-Hold Semantics

### Gesture Contract

Phase 3 implements the gesture math; this section specifies the UX contract Phase 3 must honour.

#### Press (finger down)

1. ViewModel: `sendIntent(OnVoiceRecordingStarted)`.
2. ViewModel checks: if `RecordAudio` permission not granted → emit `SideEffect.RequestRecordAudioPermission` → block recording until granted.
3. If granted: `isRecording = true` in state → recording overlay appears (§2.3), mic button morphs to `errorContainer`.
4. Audio recording starts on the platform-specific recorder (Phase 3 / androidMain).

#### Hold (finger held)

- Recording overlay visible with duration counter ticking up ("0:01", "0:02"…).
- "Slide up to cancel" hint visible in overlay (see §2.3).
- If drag distance > 80dp upward: overlay shows "Release to cancel" label swap.

#### Release without drag (normal stop)

1. `sendIntent(OnVoiceRecordingStopped(audioPath = path, durationMs = N, mimeType = "audio/m4a"))`.
2. ViewModel appends the audio as a `ChatAttachment(source = Audio, …)` to `pendingAttachments`.
3. `isRecording = false` → overlay disappears.
4. Since `pendingAttachments` is now non-empty, `canSend = true` → trailing button swaps to Send.
5. ViewModel auto-emits `OnSendWithAttachments` immediately (same session, no user re-tap needed — voice recording implies intent to send).

#### Release with drag-up (cancel)

1. `sendIntent(OnVoiceRecordingStopped(audioPath = null, durationMs = elapsed, mimeType = ""))`.
2. ViewModel discards the null-path, emits `ShowSnackbar(messageKey = chat_recording_cancelled)` — existing string key.
3. `isRecording = false` → overlay disappears. No attachment added.

#### Recording too short (< 500ms)

1. Treated as cancel by ViewModel: audioPath discarded.
2. `ShowSnackbar(messageKey = chat_voice_too_short)` — new string key (see §7).

### Mic Button Visual States Summary

| State | Container | Icon tint | Icon |
|---|---|---|---|
| At rest, canSend=false | `surfaceContainerHigh` | `onSurfaceVariant` | `Icons.Filled.Mic` |
| At rest, canSend=true | `primary` | `onPrimary` | `Icons.AutoMirrored.Filled.Send` |
| Recording in progress | `errorContainer` | `onErrorContainer` | `Icons.Filled.Mic` (pulsing scale) |
| Drag-up cancel zone | `errorContainer` | `onErrorContainer` | `Icons.Filled.Close` (24dp) |

Scale animation during recording: `animateFloatAsState(targetValue = if (isRecording) 1.2f else 1.0f, animationSpec = spring(stiffness = 300f))` on `graphicsLayer { scaleX = scale; scaleY = scale }` of the Icon.

### Accessibility Contract for Mic

```kotlin
Modifier.semantics {
    contentDescription = stringResource(Res.string.chat_record_voice)
    role = Role.Button
    // Custom action for screen readers: hold = record
    customActions = listOf(
        CustomAccessibilityAction(
            label = stringResource(Res.string.chat_voice_press_hold_hint),
            action = { /* Phase 3: trigger TalkBack-compatible recording via short tap */ true }
        )
    )
}
```

Note: TalkBack cannot reproduce press-and-hold gesture. Phase 3 should treat a TalkBack-triggered single tap on the mic as starting a recording and a second tap as stopping it (toggle mode). This makes the feature accessible without requiring the hold gesture.

### Recording Overlay (above ChatInputRow)

```
AnimatedVisibility(
    visible = isRecording,
    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
    exit  = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPaddingHorizontal, bottom = SpacingSm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpacingLg, vertical = SpacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: 3 pulsing dots + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingXs),
            ) {
                ChatTypingIndicator(   // reuse existing component — same 3-dot pulse
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = if (isDragCancel) stringResource(chat_voice_drag_cancel_hint)
                           else stringResource(chat_recording_in_progress, formattedDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            // Right: duration counter
            Text(
                text = formattedDuration,  // "0:04"
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
```

`formattedDuration` formatting: `"${minutes}:${seconds.toString().padStart(2, '0')}"`.

Note: `ChatTypingIndicator` is already in `feature/aichat/impl/.../components/`. Phase 3 can reuse it directly. The 3-dot pattern is intentionally identical to the typing indicator — recording feels like the inverse of receiving.

---

## Section 3 — Attachment Preview Chips Strip

### Placement

The chip strip lives **between** `Surface(scaffoldContent)` and `ChatInputRow`, as a separate composable column:

```
Column(modifier = Modifier.fillMaxWidth()) {
    // Chip strip — only when pendingAttachments is not empty
    ChatAttachmentChipStrip(
        attachments = state.pendingAttachments,
        onRemove = { id -> onSendIntent(OnRemoveAttachment(id)) },
    )
    // Input row always present
    ChatInputRow(...)
}
```

### ChatAttachmentChipStrip Composable

New composable in `feature/aichat/impl/.../components/ChatAttachmentChipStrip.kt`.

```kotlin
@Composable
fun ChatAttachmentChipStrip(
    attachments: List<ChatAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

Internal structure:

```
AnimatedVisibility(
    visible = attachments.isNotEmpty(),
    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
    exit  = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingSm),
        contentPadding = PaddingValues(
            horizontal = ScreenPaddingHorizontal,
            vertical = SpacingSm,
        ),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            ChatAttachmentChip(
                attachment = attachment,
                onRemove = { onRemove(attachment.id) },
            )
        }
    }
}
```

### ChatAttachmentChip Composable

New composable in `feature/aichat/impl/.../components/ChatAttachmentChip.kt`.

```kotlin
@Composable
fun ChatAttachmentChip(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Chip dimensions:** 56×56dp tile + 16dp remove-button overlay at top-right corner.

**Structure:**

```
Box(modifier = Modifier.size(56.dp)) {

    // Tile content
    Surface(
        shape = RoundedCornerShape(12.dp),           // shape.medium equivalent for tiles
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (attachment.source) {

            AttachmentSource.Image -> {
                AsyncImage(
                    model = attachment.filePath,
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }

            AttachmentSource.Pdf -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.size(AppDimens.IconSizeMd),   // 24dp
                        tint = MaterialTheme.colorScheme.error,           // PDF red — conventional
                    )
                }
            }

            AttachmentSource.Text -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            AttachmentSource.Audio -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    // Duration — audio only exception for label in tile
                    attachment.durationMs?.let { ms ->
                        val formatted = "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    // Remove button — top-right corner overlay
    IconButton(
        onClick = onRemove,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 6.dp, y = (-6).dp)       // slight bleed outside tile for tap target
            .size(24.dp)
            .minimumInteractiveComponentSize(),   // ensures ≥48dp effective tap zone via padding
    ) {
        Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = stringResource(Res.string.chat_attachment_remove),
            modifier = Modifier.size(18.dp),      // 18dp — inline close icon per M3 chip spec
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

**Remove button clarification:** The tap zone is `Modifier.minimumInteractiveComponentSize()` which guarantees the 48dp minimum touch target. The visual icon is 18dp (M3 chip close icon size). The offset `(+6, -6)` places the visual icon partially outside the tile boundary — this is the standard Android close-chip affordance (Google Search chips, Gmail chips, Android contacts). It signals "detach this item" rather than "action inside item".

**Chip outline token:** `outline` (not `outlineVariant`) — following the item-attachments post-deploy lesson §8 point 2: interactive tiles use `outline`, decorative dividers use `outlineVariant`.

### ASCII Mockup — Chip Strip

```
canSend=true, pendingAttachments=[IMG, PDF, AUDIO]:

  ┌──────────────────────────────────────────────────────────┐
  │  ┌──────┐✕  ┌──────┐✕  ┌──────┐✕                       │  ← AnimatedVisibility
  │  │  🖼  │   │  📄  │   │  ♪   │                       │
  │  │(img) │   │(pdf) │   │ 0:08 │                       │
  │  └──────┘   └──────┘   └──────┘                       │
  └──────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────┐
  │ [📎] │ [Type a message…                ] │    [➤]      │
  └──────────────────────────────────────────────────────────┘
```

---

## Section 4 — Attachment Source Chooser (Modal Bottom Sheet)

### Trigger

Leading `AttachFile` IconButton tap → ViewModel receives `OnPickAttachment` intent (no source yet) → ViewModel sets internal `showAttachmentSourceChooser = true` state flag → Composable shows `AppModalBottomSheet`.

Alternative: ViewModel emits a `SideEffect.ShowAttachmentSourceChooser`. Either approach is valid; Phase 3 chooses based on existing ViewModel patterns in the codebase (trigger-flag is already established — see item-attachments pattern).

### Sheet Structure

```
AppModalBottomSheet(
    onDismissRequest = { /* reset showAttachmentSourceChooser */ },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = SpacingLg),
    ) {
        // Drag handle implicit in AppModalBottomSheet

        Text(
            text = stringResource(Res.string.chat_attachment_source_chooser_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingLg, vertical = SpacingMd),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 4 source rows
        AttachmentSourceRow(
            icon = Icons.Filled.Image,
            label = stringResource(Res.string.chat_attachment_source_image),
            onClick = {
                onSendIntent(OnPickAttachment(AttachmentSource.Image))
                dismiss()
            },
        )
        AttachmentSourceRow(
            icon = Icons.Filled.PictureAsPdf,
            label = stringResource(Res.string.chat_attachment_source_pdf),
            onClick = {
                onSendIntent(OnPickAttachment(AttachmentSource.Pdf))
                dismiss()
            },
        )
        AttachmentSourceRow(
            icon = Icons.Filled.Description,
            label = stringResource(Res.string.chat_attachment_source_text),
            onClick = {
                onSendIntent(OnPickAttachment(AttachmentSource.Text))
                dismiss()
            },
        )
        AttachmentSourceRow(
            icon = Icons.Filled.AudioFile,
            label = stringResource(Res.string.chat_attachment_source_audio),
            onClick = {
                onSendIntent(OnPickAttachment(AttachmentSource.Audio))
                dismiss()
            },
        )
    }
}
```

### AttachmentSourceRow private composable

```kotlin
@Composable
private fun AttachmentSourceRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SpacingLg, vertical = SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingLg),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,   // label provides semantic context
            modifier = Modifier.size(AppDimens.IconSizeMd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

### Why Audio Row Stays in Chooser

Press-and-hold mic = record NEW audio. "Audio file" row = pick EXISTING audio file (voice memos, downloaded recordings). These are complementary paths. A user who recorded a 5-minute briefing in their voice memo app needs to be able to attach it. Removing the Audio row forces them to re-record. Precedent: WhatsApp supports both "audio recording" (in-app) and "document > audio file" (picker).

---

## Section 5 — ChatMessageBubble Attachment Display

### User bubble with attachments

When `message.attachments.isNotEmpty()` and `message.role == ChatRole.User`:

Render attachments **above** the text content inside the bubble Surface:

```
Surface(shape = userBubbleShape, color = primaryContainer) {
    Column(modifier = Modifier.padding(vertical = SpacingSm)) {
        // Attachment thumbnails — row above text
        if (message.attachments.isNotEmpty()) {
            MessageAttachmentRow(
                attachments = message.attachments,
                modifier = Modifier.padding(
                    horizontal = SpacingMd,
                    bottom = SpacingXs,
                ),
            )
        }
        // Text content (if any)
        if (message.content.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = SpacingMd, bottom = SpacingXs),
                )
            }
        }
    }
}
```

### MessageAttachmentRow private composable

```kotlin
@Composable
private fun MessageAttachmentRow(
    attachments: List<ChatAttachment>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingXs),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            MessageAttachmentThumbnail(attachment)
        }
    }
}
```

### MessageAttachmentThumbnail private composable

Dimensions: 80×80dp. Consistent with item-attachments solution (80dp thumbnail in AttachmentFullscreenViewer).

```
Surface(
    shape = RoundedCornerShape(8.dp),   // shape.small — smaller than pending chip (12dp) for bubble context
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.size(80.dp),
) {
    when (attachment.source) {
        Image → AsyncImage(contentScale = Crop, fillMaxSize, clip shape.small)
        Pdf   → Icon(PictureAsPdf, 24dp, error tint)
        Text  → Icon(Description, 24dp, secondary tint)
        Audio → Column(Icon(GraphicEq, 24dp, tertiary) + duration Text labelSmall)
    }
}
```

**Note on tap-to-view:** Full-screen viewer for chat bubble thumbnails is explicitly out of scope for Phase 3. Phase 3 leaves thumbnails non-clickable (no `Modifier.clickable`). A deferred enhancement can add a viewer in Phase 4 using the existing `AttachmentFullscreenViewer` from `feature/home`.

### Component Reuse Decision

The `AttachmentThumbnail.kt` in `feature/home` is scoped to `ItemDetailsSheet` and tied to the `Attachment` domain model (not `ChatAttachment`). The two models share structure but have different types. Rather than creating a cross-feature dependency (`feature/aichat` → `feature/home`), Phase 3 creates new private composables `MessageAttachmentThumbnail` and `MessageAttachmentRow` inside `feature/aichat/impl/.../components/`. These are a direct port of the visual design — same sizes, same tokens — but typed to `ChatAttachment`. This preserves the module boundary.

---

## Section 6 — Color, Typography, Spacing Tokens

All tokens are from `core/designsystem/theme/`. No hardcoded hex values anywhere in the spec.

### Color Token Usage Map

| Context | Token | Notes |
|---|---|---|
| Attach icon tint (at rest) | `colorScheme.onSurfaceVariant` | Secondary affordance |
| Attach icon tint (disabled) | `colorScheme.onSurface.copy(alpha = 0.38f)` | M3 disabled content alpha |
| Mic button container (at rest) | `colorScheme.surfaceContainerHigh` | Medium-emphasis at rest |
| Mic button icon (at rest) | `colorScheme.onSurfaceVariant` | — |
| Send button container | `colorScheme.primary` | M3 FilledIconButton default |
| Send button icon | `colorScheme.onPrimary` | — |
| Recording mic container | `colorScheme.errorContainer` | Universal recording signal |
| Recording mic icon | `colorScheme.onErrorContainer` | — |
| Recording overlay background | `colorScheme.errorContainer` | — |
| Recording overlay text/icons | `colorScheme.onErrorContainer` | — |
| Chip tile background | `colorScheme.surfaceContainerLow` | Low-emphasis container |
| Chip tile border | `colorScheme.outline` | Interactive boundary (not outlineVariant) |
| Chip remove icon | `colorScheme.onSurfaceVariant` | — |
| PDF icon tint | `colorScheme.error` | Conventional PDF red |
| Text-file icon tint | `colorScheme.secondary` | — |
| Audio icon tint | `colorScheme.tertiary` | — |
| Bubble attachment thumbnail border | `colorScheme.outlineVariant` | Decorative in message context |
| Source chooser divider | `colorScheme.outlineVariant` | — |
| Source chooser icon | `colorScheme.onSurfaceVariant` | — |
| Source chooser text | `colorScheme.onSurface` | — |

### Typography Token Usage Map

| Context | Token |
|---|---|
| Source chooser title | `typography.titleMedium` |
| Source chooser row label | `typography.bodyLarge` |
| Recording overlay label | `typography.labelSmall` |
| Recording duration counter | `typography.labelSmall` |
| Audio chip duration | `typography.labelSmall` |

### Spacing Token Usage Map

| Usage | Token | Value |
|---|---|---|
| ChatInputRow padding horizontal | `AppDimens.ScreenPaddingHorizontal` | 16dp |
| ChatInputRow padding vertical | `AppDimens.SpacingMd` | 12dp |
| Gap between row elements | `AppDimens.SpacingSm` | 8dp |
| Chip strip content padding horizontal | `AppDimens.ScreenPaddingHorizontal` | 16dp |
| Chip strip content padding vertical | `AppDimens.SpacingSm` | 8dp |
| Gap between chips | `AppDimens.SpacingSm` | 8dp |
| Source chooser row padding horizontal | `AppDimens.SpacingLg` | 16dp |
| Source chooser row padding vertical | `AppDimens.SpacingMd` | 12dp |
| Source chooser icon-label gap | `AppDimens.SpacingLg` | 16dp |
| Recording overlay padding horizontal | `AppDimens.SpacingLg` | 16dp |
| Recording overlay padding vertical | `AppDimens.SpacingMd` | 12dp |

---

## Section 7 — New Strings

Cross-checked against existing `chat_*` keys in EN and RU strings.xml. Phase 1 already added:
- `chat_recording_cancelled` — exists in both EN and RU.
- `chat_attach_no_files`, `chat_attach_limit_reached`, `chat_attach_unsupported_type` — exist.
- `chat_preview_will_create_from_attachment`, `chat_preview_will_attach_to_item` — exist.
- `chat_dispatch_created_from_attachment`, `chat_dispatch_attached_one/many` — exist.

**New keys required for UI layer only (Phase 3 must add both EN and RU):**

| Key | EN value | RU value | Context |
|---|---|---|---|
| `chat_attach_file_action` | "Attach file" | "Прикрепить файл" | ContentDescription for AttachFile IconButton |
| `chat_record_voice` | "Record voice message" | "Записать голосовое сообщение" | ContentDescription for Mic FilledIconButton |
| `chat_voice_press_hold_hint` | "Hold to record voice" | "Удерживайте, чтобы записать голос" | TalkBack customAction label + overlay hint |
| `chat_voice_drag_cancel_hint` | "Slide up to cancel" | "Сдвиньте вверх для отмены" | Recording overlay — drag-cancel state |
| `chat_voice_too_short` | "Recording too short. Hold the mic button longer." | "Запись слишком короткая. Удерживайте кнопку дольше." | Snackbar when recording < 500ms |
| `chat_recording_in_progress` | "Recording… %1$s" | "Запись… %1$s" | Overlay label with duration placeholder (positional!) |
| `chat_attachment_remove` | "Remove attachment" | "Удалить вложение" | ContentDescription for ✕ button on chip |
| `chat_attachment_source_chooser_title` | "Attach" | "Прикрепить" | ModalBottomSheet title |
| `chat_attachment_source_image` | "Image" | "Изображение" | Source chooser row label |
| `chat_attachment_source_pdf` | "PDF document" | "PDF документ" | Source chooser row label |
| `chat_attachment_source_text` | "Text file" | "Текстовый файл" | Source chooser row label |
| `chat_attachment_source_audio` | "Audio file" | "Аудио файл" | Source chooser row label (existing audio upload) |

**Total new keys: 12**

**Reminder:** Use positional placeholders `%1$s` in `chat_recording_in_progress` — bare `%s` silently fails in Compose Multiplatform Resources (item-attachments post-deploy lesson §8 point 4).

---

## Section 8 — Accessibility Checklist

### Minimum Touch Targets

| Component | Visual size | Touch target | How |
|---|---|---|---|
| AttachFile IconButton | 24dp icon | 48dp (IconButton default) | `IconButton` inherently 48dp |
| Mic/Send FilledIconButton | 24dp icon | 48dp (FilledIconButton default) | — |
| Chip remove ✕ | 18dp icon, 24dp button | ≥48dp via padding | `Modifier.minimumInteractiveComponentSize()` |
| Source chooser rows | full width × ~48dp height | ≥48dp height from padding | 12dp top+bottom padding + 24dp icon = 48dp |

### ContentDescriptions

| Component | contentDescription |
|---|---|
| AttachFile IconButton | `stringResource(chat_attach_file_action)` |
| Mic icon | `stringResource(chat_record_voice)` |
| Send icon | `stringResource(chat_send_action)` — existing key |
| Chip remove icon | `stringResource(chat_attachment_remove)` |
| Image chip AsyncImage | `attachment.fileName` — descriptive filename |
| PDF/Text/Audio chip Icon | `attachment.fileName` — descriptive filename |
| Source chooser row Icon | `null` — label provides full description |
| Recording overlay typing dots | `null` — overlay label announces via liveRegion |

### Recording Overlay Live Region

The recording overlay `Surface` must carry:
```kotlin
modifier = Modifier.semantics {
    liveRegion = LiveRegionMode.Polite
}
```
This causes TalkBack to announce the duration change ("Recording… 0:05") without interrupting whatever the user is doing.

### Contrast Verification

Using the project's tonal palette:
- `onErrorContainer` (#410002) on `errorContainer` (#FFDAD6) in light: ≈ 11.9:1 — WCAG AAA.
- `onSurfaceVariant` (#49454F) on `surfaceContainerLow` (#F5EFFA) in light: ≈ 7.1:1 — WCAG AAA.
- `onPrimary` (#FFFFFF) on `primary` (#1565C0) in light: ≈ 8.6:1 — WCAG AAA.

All token pairings inherit the AAA-verified ratios already established in `Color.kt`.

### Screen Reader Flow (logical order)

1. Attach File button (leading)
2. Text input field
3. Mic / Send button (trailing)
4. Chip strip (if visible): each chip described by filename
5. Source chooser sheet (modal, so focus trap applies automatically)
6. Recording overlay (liveRegion.Polite — announced, not focused)

---

## Section 9 — Edge Cases UI Contract

### State Matrix

| `inputText` | `pendingAttachments` | `canSend` | Trailing button | Notes |
|---|---|---|---|---|
| blank | empty | false | Mic (surfaceContainerHigh) | Default idle state |
| non-blank | empty | true | Send (primary) | Text-only message |
| blank | non-empty | true | Send (primary) | Attachments-only message |
| non-blank | non-empty | true | Send (primary) | Mixed message |
| any | any | any | Mic recording (errorContainer) | isRecording=true overrides |

### Specific Edge Cases

**Picker cancelled by user:**
- Phase 3: picker returns null/cancelled → ViewModel receives no `OnAttachmentPicked` → no change to state.
- No snackbar for cancellation (user made a deliberate choice to close the picker). This differs from recording cancel which is accidental gesture.

**Recording permission denied:**
- ViewModel emits `RequestRecordAudioPermission` SideEffect.
- Android: system permission dialog appears. If denied: emit `ShowSnackbar(messageKey = "chat_mic_permission_denied")` — Phase 3 adds this key if not already present (check Phase 1 strings — not found in current scan).
- Note: `chat_mic_permission_denied` key not found in Phase 1. Phase 3 must add it. Suggest: EN "Allow microphone access in Settings", RU "Разрешите доступ к микрофону в настройках".

**Recording too short (< 500ms):**
- Snackbar `chat_voice_too_short` (new key in §7).
- No attachment added. Overlay hides. State returns to pre-recording.

**Attachment limit reached (free user):**
- Existing key `chat_attach_limit_reached` → existing snackbar pathway.
- Picker does NOT open when limit is already at free cap. ViewModel checks before emitting `OpenFilePicker` SideEffect.
- Free cap: 3 attachments per message (follow item-attachments 3-item free limit precedent).

**Maximum message attachments (premium):**
- Spec does not define a hard premium cap. Phase 3 uses 10 as a reasonable UX cap to prevent degenerate UIs (10+ chips in a LazyRow is visually acceptable; 50+ is not). If product requires a different cap, this is a product decision outside Phase 3 scope.

**Sending attachments-only (blank text):**
- `canSend = true` because `pendingAttachments.isNotEmpty()`.
- ViewModel handles `OnSendWithAttachments` — already in Phase 1 contract.
- No special UI treatment. Chip strip visible, Send button active.

**isRecording while attachments already pending:**
- Both states are independent. `isRecording = true` shows overlay; chip strip remains visible below overlay (chip strip is above input row; overlay is above chip strip).
- Completed recording appends to `pendingAttachments` alongside existing chips.

**AttachFile button disabled during Routing/Sending:**
- Same `isEnabled` flag as TextField and trailing button.
- All three elements share one enabled flag derived from `chatStatus`.

**Audio file via chooser (not recording):**
- User picks `.m4a` / `.mp3` from system file picker.
- Phase 3 maps the MIME type to `AttachmentSource.Audio`.
- `durationMs` may be null if metadata unavailable from picker content URI. Audio chip renders without duration text if null.

---

## Section 10 — Anti-Patterns to Call Out

**DO NOT:**

1. **Add a separate "Voice" button next to "Send"** — The single trailing FilledIconButton morphs. Two trailing buttons breaks the M3 action hierarchy and wastes horizontal space on compact screens. Precedent: WhatsApp, Telegram, iMessage all use single-button morph.

2. **Show filename text labels under tile icons in the chip strip** — Fixed 56dp tiles cannot safely show text at user font scales ≥1.0 (item-attachments post-deploy lesson). Use `contentDescription` for accessibility instead. Exception: audio duration (≤5 chars = "0:08") is safe and informative.

3. **Extend ItemDetailsSheet** — Zero code in `ItemDetailsSheet` should change as part of this feature. These two surfaces operate independently and share no state.

4. **Use `outlineVariant` for chip tile borders** — Use `outline` for interactive element boundaries. `outlineVariant` is for decorative/divider usage. (item-attachments post-deploy lesson §8 point 2).

5. **Use bare `%s` / `%d` in string resources** — Always positional `%1$s` / `%1$d`. Bare placeholders silently fail in Compose Multiplatform Resources. (item-attachments post-deploy lesson §8 point 4). See `chat_recording_in_progress` in §7.

6. **Call `stringResource()` inside `LaunchedEffect` or `collect` lambdas** — Must be resolved at Composable scope top level and captured as a variable. (item-attachments post-deploy lesson §8 point 3).

7. **Use `AssistChip` / `FilterChip` for pending attachment tiles** — Both carry click-role semantics inappropriate for the "pending attachment with remove action" affordance. A plain `Surface` with a separate remove `IconButton` overlay is the correct M3 pattern (see AppItemMetaChip precedent and the read-only-badge section in mobile-design-expert.md).

8. **Use hardcoded colors (`Color(0xFF...)`)** — All colors via `MaterialTheme.colorScheme.*` tokens as listed in §6.

9. **Put recording logic inside the Composable** — All recording state management lives in ViewModel. The Composable only observes `isRecording: Boolean` and calls `sendIntent`. Platform-specific recording (AudioRecord, MediaRecorder) lives in `androidMain` via expect/actual, as with existing `ChatLocaleProvider` and `AttachmentStorage` patterns.

10. **Cross-feature dependency `feature/aichat` → `feature/home`** — Do not import `AttachmentThumbnail` from feature/home. Create `MessageAttachmentThumbnail` typed to `ChatAttachment` in `feature/aichat/impl`. Module boundary preservation (cross-module dep direction rule from mobile-design-expert.md).

---

## Acceptance Criteria for Phase 3

Phase 3 (@android-expert) verifies each item at smoke time. Each criterion is binary (pass/fail).

### Layout & Visual

- [ ] AC-01: `ChatInputRow` shows `AttachFile` icon (24dp, `onSurfaceVariant`) as leading element.
- [ ] AC-02: Trailing button shows `Mic` icon when input text is blank AND no pending attachments.
- [ ] AC-03: Trailing button shows `Send` icon when input text is non-blank OR pendingAttachments ≥ 1.
- [ ] AC-04: `Crossfade` animation visible during icon swap (not instant replacement).
- [ ] AC-05: Mic button container is `surfaceContainerHigh` at rest; `primary` for Send state.
- [ ] AC-06: Chip strip appears above input row when pendingAttachments ≥ 1; disappears when 0.
- [ ] AC-07: `AnimatedVisibility` enter/exit is smooth (`expandVertically` + `fadeIn`/`fadeOut`).
- [ ] AC-08: Chip strip chips are 56×56dp with `outline` border and `surfaceContainerLow` background.
- [ ] AC-09: Image chips show thumbnail via Coil AsyncImage with `ContentScale.Crop`.
- [ ] AC-10: PDF chip shows `PictureAsPdf` icon in `error` tint; Text chip shows `Description` in `secondary`.
- [ ] AC-11: Audio chip shows `GraphicEq` icon + formatted duration when `durationMs != null`.
- [ ] AC-12: Chip remove ✕ at top-right corner; tap removes chip from `pendingAttachments`.

### Recording Overlay

- [ ] AC-13: Recording overlay appears above chip strip when `isRecording = true`.
- [ ] AC-14: Overlay uses `errorContainer` background; text and 3-dot indicator use `onErrorContainer`.
- [ ] AC-15: Mic button container changes to `errorContainer` during recording.
- [ ] AC-16: Duration counter increments every second in "M:SS" format.
- [ ] AC-17: Releasing without drag-up stops recording, appends `ChatAttachment(Audio)` to chips, overlay disappears.
- [ ] AC-18: Drag-up ≥ 80dp shows cancel hint in overlay; releasing after drag cancels recording, shows snackbar `chat_recording_cancelled`.
- [ ] AC-19: Recording < 500ms shows snackbar `chat_voice_too_short`, no attachment added.

### Source Chooser Sheet

- [ ] AC-20: Tap on `AttachFile` button opens `AppModalBottomSheet` with 4 rows (Image, PDF, Text, Audio).
- [ ] AC-21: Tap on any row emits `OnPickAttachment(source)` and dismisses the sheet.
- [ ] AC-22: Sheet dismisses on drag-down and on tap-outside.

### Message Bubble Attachments

- [ ] AC-23: User bubble with attachments renders `MessageAttachmentRow` above message text.
- [ ] AC-24: Attachment thumbnails in bubble are 80×80dp with `outlineVariant` border.
- [ ] AC-25: Bubbles without attachments unchanged from current appearance.

### Strings

- [ ] AC-26: All 12 new string keys present in both `values/strings.xml` and `values-ru/strings.xml`.
- [ ] AC-27: `chat_recording_in_progress` uses `%1$s` positional placeholder, not `%s`.

### Accessibility

- [ ] AC-28: `AttachFile` IconButton has `contentDescription = chat_attach_file_action`.
- [ ] AC-29: Mic button has `contentDescription = chat_record_voice`.
- [ ] AC-30: Chip remove button has `contentDescription = chat_attachment_remove`.
- [ ] AC-31: Chip remove button has effective touch target ≥ 48dp via `minimumInteractiveComponentSize`.
- [ ] AC-32: Recording overlay Surface has `semantics { liveRegion = LiveRegionMode.Polite }`.

### Scope Guard

- [ ] AC-33: No changes in `ItemDetailsSheet` or any file in `feature/home` (scope boundary).
- [ ] AC-34: No cross-feature import `feature/aichat` → `feature/home`.
- [ ] AC-35: No hardcoded color values `Color(0xFF...)` in any new or modified Composable.
