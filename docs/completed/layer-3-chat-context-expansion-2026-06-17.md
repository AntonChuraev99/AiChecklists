---
title: "Layer 3 Chat Context Expansion — Recent Checklist Items"
date: 2026-06-17
type: feature
modules: [feature/aichat, firebase-functions, core/designsystem]
keywords: [ai-chat, layer-3, deep-thinking, cloud-function, context-injection, recent-items, checklist-context, chat_agent, privacy-policy]
project: gisti-checklists
---

# Layer 3 Chat Context Expansion — Recent Checklist Items

## Problem / Context

AI Chat Layer 3 (Cloud Function `chat_agent`) operates with **zero context** about user's checklists — it receives only the free-form user query and responds generically. Example:

**User:** "What should I prioritize first?"  
**Layer 3 (old):** "Here are 5 generic prioritization tips..." *(doesn't know which items exist)*

**Goal:** Provide Layer 3 with last-6 items per checklist (up to 30 total leaf-items) to generate contextual advice scoped to **user's actual data**.

## Solution

### 1. Data Model (Android/KMP)

Added `ChecklistItemContext` to `ChatCompletionApiService`:
```kotlin
data class ChecklistItemContext(
    val text: String,
    val checked: Boolean,
    val position: Int,          // Proxy for temporal order (no timestamp in Item model)
    val checklistId: String,
    val checklistName: String
)

data class ChatMessage(
    val text: String,
    val recentItems: List<ChecklistItemContext> = emptyList()  // ← NEW
)
```

### 2. ViewModel (KMP)

**`AiChatRepository.getRecentItems()`** query:
```kotlin
// Returns last-6 items per checklist, global cap 30 leaf-items
// Filters out folder nodes (type == "folder")
// Stops at 30 total items across all checklists
suspend fun getRecentItems(limit: Int = 30): List<ChecklistItemContext>
```

Wired in `ChatViewModel.onIntent()`:
```kotlin
intent is Intent.SendMessage -> {
    val recentItems = repository.getRecentItems()
    // Attach to chat message before sending to Layer 3
}
```

### 3. Cloud Function (Python)

**`firebase-functions/main.py`** — `chat_agent` entrypoint:

```python
def _format_checklists_summary(items: List[dict]) -> str:
    """Common helper for chat_agent + chat_completion CF."""
    if not items:
        return "(No checklist items to reference.)"
    
    summary = "User's current checklist items:\n"
    for item in items:
        status = "✓" if item.get("checked") else "○"
        summary += f"  {status} {item['checklistName']}: {item['text']}\n"
    return summary

@functions_framework.http
def chat_agent(request):
    data = request.get_json()
    user_query = data["text"]
    recent_items = data.get("recentItems", [])  # ← NEW
    
    context = _format_checklists_summary(recent_items)
    
    # Inject into system prompt
    prompt = f"""You are a helpful assistant. {context}
    
    User query: {user_query}
    """
    
    # Call Gemini
    ...
```

### 4. Privacy Boundary Expansion

**First time** checklist item text leaves the device and reaches the server (Deep Thinking only).

Updated **`hosting/public/privacy-policy.html`:**
```html
<h3>Deep Thinking Mode</h3>
<p>When Deep Thinking is enabled, we send your recent checklist items 
(last 6 per checklist) to our AI servers to provide contextual advice. 
This data is not stored or used for model training.</p>
```

Updated **strings (`core/designsystem/values*/strings.xml`, EN+RU):**
```xml
<!-- EN -->
<string name="chat_settings_deep_thinking_subtitle">
    Enable to share your recent items with AI for personalized advice. Your data is private.
</string>

<!-- RU -->
<string name="chat_settings_deep_thinking_subtitle">
    Включите, чтобы поделиться недавними элементами с ИИ для персонализованных рекомендаций. Ваши данные приватны.
</string>
```

### 5. Key Design Decision: No Per-Item Timestamp

**Problem:** `ChecklistItem` model has no `createdAt` field. Adding one requires:
- Room schema migration (17 → 18)
- Firestore mapper updates (Android + wasmJs)
- UI for sorting by date

**Decision:** Use `position` as temporal proxy (newer items added to end → higher position = more recent). Limitation: reorder changes position meaning.

**Deferred:** True timestamp per item → new todo `docs/todos/2026-06-17-chatcontext-item-timestamp.md`

## Verification

✅ `:feature:aichat:impl:testAndroidHostTest` — 3 new green tests
- `getRecentItems_returnsLast6PerChecklist()`
- `getRecentItems_capsAt30Total()`
- `formatChecklistsSummary_handlesEmptyList()`

✅ `:androidApp:compileDebugKotlin` PASS
✅ Prompts in gitignored `prompts_private.py` (IP protected)

## Deployment Requirements

**Manual user steps:**

1. Deploy Cloud Function `chat_agent` with updated `main.py`:
   ```bash
   gcloud functions deploy chat_agent \
     --region=europe-west1 \
     --runtime=python311 \
     --source=firebase-functions
   ```

2. Verify deployment:
   ```bash
   gcloud functions describe chat_agent --region=europe-west1
   ```

3. (Optional) Deploy `chat_completion` CF with same `_format_checklists_summary` helper (dead code previously; used by fallback path).

**Client side:** No manual steps. Update installs automatically after app deploy.

## Related Files

- `feature/aichat/api/.../ChatCompletionApiService.kt` (±ChecklistItemContext)
- `feature/aichat/impl/.../AiChatRepository.kt` (±getRecentItems())
- `firebase-functions/main.py` (_format_checklists_summary, chat_agent integration)
- `hosting/public/privacy-policy.html` (Deep Thinking disclosure)
- `core/designsystem/values*/strings.xml` (chat_settings_deep_thinking_subtitle EN+RU)

## Architecture Note

Layer 3 now receives **contextual user data** but:
- No real-time sync (snapshot at query time)
- No item metadata beyond text/checked/position
- No timestamps (deferred, future work)
- **Privacy:** Deep Thinking only (user opt-in), not sent in Light/Standard modes

This enables contextual responses like: *"Based on your 'Grocery Store' list, prioritize fresh produce before items with long shelf life."*
