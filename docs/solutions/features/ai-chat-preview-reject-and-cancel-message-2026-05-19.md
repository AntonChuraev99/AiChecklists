---
title: "AI Chat Preview Reject Button + Cancel Message"
date: 2026-05-19
type: feature
modules: [feature/aichat/api, feature/aichat/impl, core/designsystem]
keywords: [ai-chat, preview-card, reject-button, layer-escalation, cancel-feedback, multi-button-sheet, skipLayer1-flag, sourceLayer-tracking]
project: aichecklists
---

# AI Chat — Preview Reject Button + Cancel Assistant Message

## Problem / Context

In the existing AI Chat three-layer architecture (Local → Classifier → FullChat):
- **Problem 1:** When Layer 1 local parser misclassifies a user intent (e.g., "how to add attachment in item" misread as `AddItem`), the user must manually rephrase to reach the next layer. No direct escalation button exists.
- **Problem 2:** When user taps Cancel on a preview-card, the chat history shows the user's message but no AI response. Violates CLAUDE.md hard rule: "users must always see feedback for their actions, even dismissals."

**Context:** Feature flows like `CreateChecklistFromAttachment` use a single narrowly-scoped intent; for these, a reject/escalation button creates UI clutter and semantic confusion.

## Solution

### Escalation via `skipLayer1: Boolean` Repository Flag

Extended `AiChatRepository.classify()` contract with optional `skipLayer1 = false`:

```kotlin
suspend fun classify(
    input: String,
    locale: Locale,
    skipLayer1: Boolean = false  // ← new flag
): Classification
```

**Implementation in AiChatRepositoryImpl:**
- When `skipLayer1 = true`:
  - Skip Layer 1 local router entirely (ignore Deep Thinking toggle)
  - Call Layer 2 classifier directly
  - If Layer 2 returns FreeForm/Unknown or network error → return FreeForm
  - ViewModel will escalate FreeForm to Layer 3 via `completeFreeForm()`

**Why:** Single extension point (one flag) avoids duplicating routing logic elsewhere. Fine-grained control without new code paths.

### PendingPreview State Tracking

Added to `ChatScreenContract.PendingPreview`:
```kotlin
data class PendingPreview(
    val outcome: DispatchOutcome,
    val originalText: String,      // ← original user input
    val sourceLayer: RoutingLayer   // ← Layer.Local, Layer.Classifier, Layer.FullChat
)
```

Stored in `ChatViewModel` after `handleSend()`. Allows reject handler to know:
1. **What to re-classify:** `originalText` (not the preview-preview again)
2. **Where to escalate:** `sourceLayer` tells the ladder which rung to jump to

### Three-Branch Reject Handler

In `ChatViewModel.handlePreviewReject()`:

```kotlin
// sourceLayer == Local → escalate to Classifier
classify(originalText, locale, skipLayer1 = true)
  .let { newClassification ->
    if (newClassification.intent is Intent.FreeForm) {
      handleFreeForm(originalText, newClassification)  // Layer 3
    } else {
      // Show new preview with sourceLayer = Classifier
      pendingPreview = PendingPreview(
        outcome = newClassification.outcome,
        originalText = originalText,
        sourceLayer = RoutingLayer.Classifier
      )
    }
  }

// sourceLayer == Classifier → go straight to Layer 3
completeFreeForm(originalText)

// sourceLayer == FullChat → safety fallback (should not occur)
showSnackbar("chat_freeform_failed")
```

### Cancel Handler → Assistant Message (Not Silent)

```kotlin
ChatScreenIntent.OnPreviewCancel -> {
  emitSideEffect(ShowAssistantMessage("chat_preview_cancelled_message"))
  pendingPreview = null
}
```

String keys:
- EN: "Action cancelled."
- RU: "Действие отменено."

Resolved via existing `ChatRoute.messageKeyResolver()` pattern (no new architecture).

### UI Changes

**ChatPreviewCard:** Three-button row:
- Cancel (text)
- Reject (text) — `showRejectButton: Boolean = true` parameter
- Apply (primary)

**CreateChecklistFromAttachment:** Passes `showRejectButton = false` (reject lacks semantic meaning when attachment upload = single intent).

## Why This Approach

### 1. **skipLayer1 Flag = No Duplicate Routing**
Alternative: Add separate `escalateToClassifier()` and `escalateToFullChat()` methods → bloats repository interface. Instead, one boolean parameter with clear semantics allows the existing layer-chain logic to be reused.

### 2. **sourceLayer Tracking = Audit Trail + Correct Escalation**
Storing source layer in state (not deriving it) allows:
- Future analytics: "how many rejects per layer → next layer?"
- Correct handler branching: each sourceLayer knows exactly where to escalate
- No introspection: don't inspect intermediate classification states

### 3. **Cancel → ShowAssistantMessage = Transparent to User**
Violates CLAUDE.md hard rule silently (silent-skip violation). Cost: two XML keys. Benefit: users see acknowledgment and don't feel the chat "hung" on their input.

### 4. **Reject Button Hidden for Single-Intent Flows**
When flow design constrains user to one action type (attach → create), offering a "reject" button creates confusion ("reject what? the entire attachment?"). Cleaner UX: hide the button where it lacks meaning.

## Examples

### Scenario 1: User Misclassified by Layer 1
```
User: "how to add attachment to item"
Layer 1 parser: AddItem (trigger "add" from EN lexicon)
Preview shown: "Add item: how to add attachment to item"
User taps: Reject
→ classify(originalText, skipLayer1=true)
→ Layer 2: FreeForm (classifier unsure of intent)
→ ViewModel: handleFreeForm(user input)
→ Layer 3: (Cloud Function) "You probably meant: 1) attach file, 2) modify existing item, ..."
```

### Scenario 2: User Cancels Preview
```
User: "remind me tomorrow"
Layer 1: SetReminder (confident)
Preview shown: "Set reminder for tomorrow at 09:00"
User taps: Cancel
→ ViewModel: emitSideEffect(ShowAssistantMessage("chat_preview_cancelled_message"))
→ Chat history shows: User message + "Action cancelled." (AI acknowledgment)
```

### Scenario 3: CreateChecklistFromAttachment (Reject Hidden)
```
Photo uploaded → attachment preview shown
Buttons: Cancel | Apply (no Reject—flow is single-intent)
User taps: Cancel
→ ViewModel: ShowAssistantMessage("chat_preview_cancelled_message")
```

## Testing

- **AiChatRepositoryImpl:** skipLayer1=true → router.route() NOT called, classifierApi called directly; Layer 2 vague (FreeForm/Unknown) → returns FreeForm intent
- **ChatViewModel:** OnPreviewCancel → emits ShowAssistantMessage, clears pendingPreview
- **ChatViewModel:** OnPreviewReject from Local → calls classify(skipLayer1=true); from Classifier → calls completeFreeForm() directly
- **9 new tests** covering escalation paths, fallbacks, and edge cases. All PASS.

## Related Files

- `feature/aichat/api/src/commonMain/kotlin/.../AiChatRepository.kt` — contract extended
- `feature/aichat/impl/src/commonMain/kotlin/.../AiChatRepositoryImpl.kt` — escalation logic
- `feature/aichat/impl/src/commonMain/kotlin/.../ChatScreenContract.kt` — PendingPreview + intents
- `feature/aichat/impl/src/commonMain/kotlin/.../ChatViewModel.kt` — handlers
- `feature/aichat/impl/src/commonMain/kotlin/.../ChatPreviewCard.kt` — UI buttons
- `feature/aichat/impl/src/commonMain/kotlin/.../ChatRoute.kt` — messageKey resolver
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` — EN keys
- `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` — RU keys
- Tests: `feature/aichat/impl/src/commonTest/kotlin/.../ChatViewModelTest.kt`, `AiChatRepositoryImplTest.kt`

## Production Notes

- Pixel 9 smoke validation: preview reject → escalation preview shown ✓; cancel → AI message ✓
- No new analytics planned (not in scope); future sessions can add Amplitude if A/B-testing reject adoption becomes priority
- Pattern is generalizable to future Layer N chains (e.g., Layer 4 if added)
