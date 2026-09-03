---
title: "AI Chat M3 Design Polish and Mic Gesture Fixes"
date: 2026-05-19
type: feature
modules: [feature/aichat, feature/analyze, core/designsystem]
keywords: [aichat, m3-design, telegram-input-pill, mic-gesture-pointerinput, modern-photo-picker-picvisualm, negative-margin-offset, day-divider-timezone, snackbar-messagekey-resolver, message-actions-4-row, material3-colors-tokens]
project: gisti-checklists
---

# AI Chat M3 Design Polish + Mic Gesture Fixes

## Проблема / Контекст

AI Chat feature completed Phase A–C (core intents, classifiers, full multi-turn chat). Remaining scope: design system alignment (Material 3 tokens, visual consistency with SettingsScreen from 2026-05-18), modern API adoption (PickVisualMedia for Android 13+), and critical gesture bugs preventing reliable voice recording.

**Three blocking issues:**
1. Mic press-and-hold gesture unreliable: pointerInput key cascade causes mid-gesture cancellation before release event.
2. Negative margin CSS pattern (`margin: -6px`) from design.html doesn't translate to Compose `Modifier.padding(start = -6dp)` — crashes at runtime with `IllegalArgumentException`.
3. File pickers outdated: still using legacy `Intent.ACTION_OPEN_DOCUMENT` instead of modern `PickVisualMedia` API (Android 13+, Play Services backport 11+).

**Design gap:** ChatScreen toolbar, credit banner, message bubbles, and input row use inconsistent spacing/tokens compared to M3 spec in AI Chat M3.html.

## Решение

### 1. Telegram-Style Input Row (ChatInputRow.kt redesign)

```kotlin
// BEFORE: Row with multiple trailing IconButtons
Row(Modifier.fillMaxWidth()) {
    TextField(...)
    IconButton(onClick = onAttach) { Icon(Icons.Outlined.Attachment) }
    IconButton(onClick = onVoiceSend) { Icon(Icons.Filled.Mic) }  // ❌ Double buttons, no morphing
}

// AFTER: Surface pill with Crossfade morphing
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(AppDimens.SpacingMd)
        .height(56.dp),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = AppDimens.SpacingMd)
            .fillMaxHeight(),
        verticalAlignment = CenterVertically,
    ) {
        // Placeholder changes based on attachmentCount
        TextField(
            value = input,
            onValueChange = { ... },
            placeholder = { Text(placeholderText) },  // "Message" / "Add more or send..."
            modifier = Modifier
                .weight(1f)
                .background(Color.Transparent),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Crossfade(targetState = canSend, label = "mic_send_morph") { canSend ->
            if (canSend) {
                IconButton(onClick = { onSendMessage() }) {
                    Icon(Icons.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(modifier = Modifier.pointerInput(Unit) { /* gesture handler */ }) {
                    Icon(Icons.Filled.Mic, null)
                }
            }
        }
    }
}
```

**Why:** Matches Telegram/WhatsApp affordance (single trailing button morphs mic↔send). No double buttons eating touch targets. Pill styling (Surface 28dp radius, 56dp height) brands input as distinct widget.

### 2. Mic Gesture Stability (ChatInputRow + ChatRecordingOverlay)

**Root cause:** pointerInput key recreation during gesture interrupts awaitEachGesture lambda.

```kotlin
// ❌ BAD: isRecording in keys causes pointerInput() to recreate mid-gesture
Box(modifier = Modifier.pointerInput(isRecording) {
    awaitEachGesture {
        val down = awaitFirstDown()
        // On isRecording flip (mid-gesture), this whole block recreates, cancel event lost
        ...
    }
})

// ✅ GOOD: stable key (Unit) + read state via rememberUpdatedState
val isRecordingLatest by rememberUpdatedState(isRecording)
Box(modifier = Modifier.pointerInput(Unit) {  // stable key
    awaitEachGesture {
        val down = awaitFirstDown()
        startRecording()
        val up = awaitPointerEvent(PointerEventPass.Main)
        if (isRecordingLatest) stopRecording()  // read state safely
    }
})
```

**Pattern:** Always use `pointerInput(Unit)` for stable lifetime. For state reads inside gesture lambda, use `rememberUpdatedState()` wrapper.

### 3. Negative Margin Translation (ChatMessageBubble)

```kotlin
// ❌ BAD: Padding throws IllegalArgumentException on negative
Modifier.padding(start = (-6).dp)  // Runtime crash

// ✅ GOOD: offset() for visual margin adjustment
Modifier.offset(x = (-6).dp)  // Safe, translates to transform: translateX
```

**CSS→Compose rule:** `margin: negative` → `Modifier.offset()`, NOT `padding()`. Padding enforces non-negative constraint at runtime.

### 4. Modern Photo Picker API (FilePicker.android.kt split)

```kotlin
// BEFORE: Single intent launcher
val docLauncher = rememberLauncherForActivityResult(OpenDocument()) { ... }

// AFTER: Split by API level + picker type
val photoLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
    // Android 13+ native picker, returns session-scoped URI, no READ_MEDIA_IMAGES permission needed
    ...
}

val docLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
    // Fallback for AUDIO, PDF, TEXT, LINK (no PickAudio for <API 33)
    ...
}

// Usage
when (source) {
    FilePickerType.IMAGE -> photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    FilePickerType.AUDIO -> docLauncher.launch(arrayOf("audio/*"))  // Falls back to OpenDocument
    ...
}
```

**Why:** PickVisualMedia (Android 13+) provides native system picker, better UX than web-intent. Play Services backport (API 11+) enables on older devices via Cronet compat. Separate launchers avoid launcher-state duplication.

### 5. M3 Design System Alignment

#### ChatHeader — TopAppBar Small (left-aligned)
```kotlin
// BEFORE: CenterAlignedTopAppBar (centered title)
CenterAlignedTopAppBar(
    title = { Text("AI Chat") },
    actions = { ... }
)

// AFTER: TopAppBar Small (left-aligned per M3)
TopAppBar(
    title = { Text("") },  // Empty title slot (design spec)
    navigationIcon = { },  // No back (drawer only)
    actions = { IconButton(...) },  // Settings icon, right-aligned
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    ),
)
```

#### ChatPricingRow — Pill Design
```kotlin
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AppDimens.SpacingMd),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.icon_sparkle),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = creditsRemaining.toString(),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onHelpClick,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Outlined.HelpOutline, null, modifier = Modifier.size(20.dp))
        }
    }
}
```

**Design tokens:** surfaceContainerLow (pill background), primary (sparkle), onSurfaceVariant (help icon), 12dp radius, asymmetric padding (16/8/10/8).

#### ChatMessage Bubbles
- **Corners:** 20.dp (was 16.dp)
- **Max width:** 320.dp / 340.dp (was 280.dp)
- **Typography:** bodyLarge (was bodyMedium)
- **Padding:** 16.dp horizontally, 10/12.dp vertically
- **AI sender label:** new composable with 24.dp avatar circle + "AI" text
- **Actions row:** 4 actions (Copy, ThumbUp, ThumbDown, + placeholder for future refresh)

#### Day Divider
```kotlin
// Shown only when last message is from today (calendar boundary)
fun showTodayDivider(): Boolean {
    val now = Clock.System.now()
    val lastMessageInstant = lastMessage?.createdAt ?: return false
    val nowLocalDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val lastLocalDate = lastMessageInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return nowLocalDate == lastLocalDate
}

// In ChatScreen composable scope (NOT inside LazyListScope item{} or items{})
val showDivider = remember { showTodayDivider() }
if (showDivider) {
    ChatDayDivider()  // "Today" label, Divider
}
```

**Why:** TimeZone calculation must happen in @Composable scope where `remember` is valid. Cannot call inside `items {}` lambda (LazyListScope, not Composable scope).

### 6. Snackbar MessageKey Resolver (triple-add rule)

When adding new `SideEffect.ShowSnackbar(messageKey)` from ViewModel:

1. **Add to EN strings.xml:**
   ```xml
   <string name="chat_thumb_up_thanks">Thanks for your feedback!</string>
   ```

2. **Add to RU strings.xml:**
   ```xml
   <string name="chat_thumb_up_thanks">Спасибо за отзыв!</string>
   ```

3. **Add to ChatRoute.kt resolver (3 places):**
   ```kotlin
   // Place 1: stringResource() pre-resolve for type safety
   SideEffect.ShowSnackbar(Res.string.chat_thumb_up_thanks) { ... }

   // Place 2: remember keys list
   val messageKeys = remember {
       setOf(
           Res.string.chat_thumb_up_thanks,
           ...
       )
   }

   // Place 3: mapOf resolver in LaunchedEffect
   mapOf(
       Res.string.chat_thumb_up_thanks to stringResource(Res.string.chat_thumb_up_thanks),
       ...
   )
   ```

**Why:** Without triple-add, raw string key appears in snackbar UI (e.g., `"chat_thumb_up_thanks"` instead of `"Thanks for your feedback!"`). One point of entry (VM) requires three points of exit (EN, RU, resolver mapping).

### 7. AiChatFeaturesHelpSheet (new composable)

```kotlin
@Composable
fun AiChatFeaturesHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(Res.string.chat_features_help_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(AppDimens.SpacingMd))

            // 8–10 capability examples
            listOf(
                Res.string.chat_features_example_create_checklist to Res.string.chat_features_example_create_checklist_desc,
                Res.string.chat_features_example_add_item to Res.string.chat_features_example_add_item_desc,
                Res.string.chat_features_example_complete_item to Res.string.chat_features_example_complete_item_desc,
                Res.string.chat_features_example_remind to Res.string.chat_features_example_remind_desc,
                Res.string.chat_features_example_find to Res.string.chat_features_example_find_desc,
                Res.string.chat_features_example_priority to Res.string.chat_features_example_priority_desc,
                Res.string.chat_features_example_attach to Res.string.chat_features_example_attach_desc,
                Res.string.chat_features_example_feedback to Res.string.chat_features_example_feedback_desc,
            ).forEach { (example, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.SpacingSm),
                ) {
                    Text(
                        text = stringResource(example),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

## Почему именно так

### Telegram-style pill
- **Affordance:** Users expect single morphing button (mic→send) from messaging apps. Double trailing buttons violate this pattern and confuse touch targets.
- **Brand consistency:** Pill shape matches Android 15 Material You default (settingsActivityReveal, appbars on modern devices).

### pointerInput(Unit) + rememberUpdatedState
- **Stability:** Keys passed to `pointerInput` should never change during gesture lifetime. Recreation of pointerInput breaks awaitEachGesture loop.
- **State safety:** Reading Compose state inside gesture lambda requires `rememberUpdatedState` to decouple state updates from gesture handler lifetime.
- **Generalizability:** Pattern applies to all drag, swipe, press-and-hold, or multi-touch gestures in Compose.

### offset() vs padding() for negative margins
- **Design semantics:** CSS `margin` is visual space outside bounds; Compose offset translates without affecting layout. Padding is layout space inside bounds and enforces non-negative.
- **Runtime safety:** Padding with negative values throws `IllegalArgumentException` at composition time only when used in actual modifier chain (not type-safe at compile time).

### PickVisualMedia split
- **UX:** Native Android 13+ photo picker is faster and more discoverable than intent fallback. Users familiar with native behavior.
- **API coverage:** PickAudio API doesn't exist for <API 33; splitting launchers avoids conditional logic per use-site, centralizes in FilePicker.
- **Permissions:** PickVisualMedia returns session-scoped URI, no `READ_MEDIA_IMAGES` permission required (implicit via file picker).

### M3 design token alignment
- **Consistency:** Using surfaceContainerLow, surfaceContainerHigh, and primary tokens ensures light/dark theme support without hardcoded colors.
- **Scalability:** Pill-based design (input row, credit banner, future alerts) is consistent and scales to feature expansion.
- **Accessibility:** Material 3 tokens ensure WCAG contrast ratios and readable typography across device sizes.

### Day divider TimeZone calculation
- **Correctness:** User's local calendar date depends on TimeZone. Using Clock.System.now() with toLocalDateTime(TimeZone.currentSystemDefault()) handles DST and regional offsets.
- **Scope:** Calculation must happen in @Composable scope (not LazyListScope item{} lambda) because `remember` is a Composable function.

### Triple-add snackbar rule
- **Completeness:** ViewModel emits intent (Layer 1), UI observes and resolves key (Layer 2 XML), AppRoute routes via LaunchedEffect (Layer 3 mapping). Missing any step leaves raw key in UI.
- **Maintainability:** Future refactors (e.g., change messageKey to parameter-based) need all three to be touched together.

## Примеры

### Before/After: Mic Gesture Bug
```
BEFORE (2026-05-19 early):
1. User presses mic icon
2. isRecording = true
3. pointerInput(isRecording) recreates due to key change
4. awaitEachGesture loop breaks
5. Release event never reaches handler
6. Recording never stops

AFTER:
1. User presses mic icon
2. isRecording = true (state changes, but pointerInput key is stable Unit)
3. pointerInput(Unit) stays alive
4. awaitEachGesture continues, awaits release
5. Release triggers stopRecording() via rememberUpdatedState(isRecording)
6. Recording stops cleanly ✓
```

### Before/After: ChatMessage Bubble Design
```
BEFORE: 16dp radius, 280dp max width, bodyMedium, 8/12dp padding
- Looks small and cramped on large screens
- Inconsistent with M3 spec

AFTER: 20dp radius, 320/340dp max width, bodyLarge, 10/12dp padding, AI label
- Visually prominent, modern messenger feel
- Aligns with M3 large-bubble spec
- AI sender label adds visual affordance (who's talking)
```

## Связанные файлы

- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/ChatInputRow.kt` — Telegram pill + Crossfade
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/ChatRecordingOverlay.kt` — mic timer fix (%1$s arg removed)
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/ChatMessageBubble.kt` — 20dp corners, offset(-6dp), 4-action row, AI label
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/ChatHeader.kt` — TopAppBar Small left-aligned
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/ChatPricingRow.kt` — pill redesign (Surface 12dp, sparkle icon)
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/ChatDayDivider.kt` — new composable (today divider)
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/components/AiChatFeaturesHelpSheet.kt` — new composable (8 examples)
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/ChatRoute.kt` — onVoiceRecordingStarted, audioRecorder.start(), SideEffect resolver
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/ChatScreenContract.kt` — OnThumbUpClick, OnFeaturesHelpClick intents
- `feature/aichat/impl/src/commonMain/kotlin/.../presentation/ChatViewModel.kt` — handlers for new intents
- `feature/analyze/impl/src/commonMain/kotlin/.../picker/FilePicker.android.kt` — split photoLauncher + docLauncher
- `feature/analyze/impl/src/commonMain/kotlin/.../picker/FilePickerResult.kt` — AUDIO variant
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` — 12 new keys EN
- `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` — 12 new keys RU

**Commit:** c4257cbd feat(aichat): M3 design polish and mic gesture fixes

---

## Дополнительные паттерны

### Trigger-Flag для LaunchedEffect (из item-attachments)
```kotlin
// ViewModel state + intent
data class ChatScreenState(
    val shouldOpenMicRecorder: Boolean = false,
)

// Intent
data class OnVoiceRecordingStarted(...)

// LaunchedEffect trigger
LaunchedEffect(shouldOpenMicRecorder) {
    if (shouldOpenMicRecorder) {
        audioRecorder.start()
        sendIntent(OnVoiceRecordingStarted())  // reset flag via handler
    }
}
```

### Crossfade Button Morphing
```kotlin
Crossfade(
    targetState = canSend,
    animationSpec = tween(200),
    label = "mic_send_morph"
) { state ->
    if (state) {
        // Send button
    } else {
        // Mic gesture
    }
}
```

Duration 200ms is imperceptible yet shows motion (not instant flip). Label helps debug recomposition.

### Attachment-Aware Placeholder
```kotlin
val placeholderText = remember(attachmentCount) {
    if (attachmentCount > 0) {
        Res.string.chat_input_placeholder_with_attachment  // "Add more or send..."
    } else {
        Res.string.chat_input_placeholder  // "Message"
    }
}
```

Recomputes only when count changes; placeholder updates on first attach, reverts when last removed.
