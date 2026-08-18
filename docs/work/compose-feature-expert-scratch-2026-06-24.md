# AiChoiceResponse refactor scratch (2026-06-24)

Phase 1 client only. Replaced ChatPreviewCard + AgentPlanCard with AiChoiceResponse (Claude-style pills).

## Files applied (all written)
- [x] NEW feature/aichat/api/.../domain/model/ChatChoice.kt
- [x] NEW feature/aichat/impl/.../presentation/components/AiChoiceChip.kt (internal)
- [x] NEW feature/aichat/impl/.../presentation/components/AiChoiceResponse.kt (PUBLIC — used cross-module in App.kt)
- [x] EDIT ChatMessageBubble.kt: AiSenderLabel private->internal
- [x] EDIT ChatScreenContract.kt: PendingChoice(+executingLabel,batchItems) + OnChoiceSelected/Dismissed/EditChange/EditConfirmed; dropped pendingPreview/pendingAgentPlan/PendingPreview/AgentPlan/old intents; kept AgentPlanItem
- [x] EDIT ChatViewModel.kt: showWriteChoice / handleChoiceSelected / executeChoice / handleChoiceEditConfirmed / handleChoiceDismissed / escalateChoice + ToolCall copy helpers + choiceString() test-safe getString wrapper + agent-batch choice + ambiguous-match candidate chips
- [x] EDIT ChatScreen.kt: AiChoiceResponse item; totalItemCount; isEnabled; mockChoice
- [x] EDIT composeApp/App.kt: dock render site swapped to AiChoiceResponse; hasLastAnswer
- [x] EDIT strings.xml + values-ru (chat_choice_* keys)
- [x] DELETE ChatPreviewCard.kt, AgentPlanCard.kt
- [x] FIX ChatViewModelTest.kt (migrated 1-40 + P5 + A10) + added C1-C7 green tests

## KEY RISK handled: getString throws "Resources.getSystem not mocked" in testAndroidHostTest
-> choiceString() wraps getString in runCatching -> "…" fallback in tests; on-device resolves normally.
Tests assert on toolCall/role/id structure, NOT resolved copy.

## Phase 1 Status: DONE

## PHASE 2 — AI-generated answer options (chat_agent type:"options")
- [x] api ChatAgentApiService.kt: AgentStepResult.Options(prompt, options, creditsRemaining)
- [x] impl/data ChatAgentApiServiceImpl.kt: parse type:"options" (trim/dedup/2..4 + Final-fallback if <2 / ServiceError if blank prompt); DTO prompt+options; request supports_options=true gate
- [x] api/domain ChatChoice.kt: ChoiceAction.SendMessage(text) (fresh turn, NOT re-classify)
- [x] ChatViewModel.kt: runAgentTurn is Options → persist prompt as assistant msg + pendingChoice(empty prompt, SendMessage chips, Dismiss escape); sendOptionAsTurn() forceAgent-style; handleChoiceSelected SendMessage; handleChoiceDismissed options-case = clear w/o reply; CHOICE_OPTION_PREFIX
- [x] AiChoiceResponse.kt: blank prompt → render ONLY chips (no label, no bubble)
- [x] tests: ViewModel C8-C10 + parser options(4 tests) incl supports_options body assert

NO new string keys (reused chat_choice_cancel).

## PHASE 2 FIX — options question hidden in inline dock
Bug: dock overlays only pendingChoice; persisted question was hidden behind it.
Fix: question lives INSIDE choice block (ChatChoice.prompt = result.prompt, non-empty), persisted ONLY on resolve:
- [x] runAgentTurn Options: no addAndPersist here; pendingChoice prompt = stepResult.prompt (non-empty)
- [x] sendOptionAsTurn: persist question (assistant) THEN label (user) THEN runAgentTurn → order [Q][label][answer]
- [x] handleChoiceDismissed options-case: persist question before clear (stays visible), no cancelled reply
- [x] AiChoiceResponse: non-empty prompt → Phase-1 path (label+bubble+chips) automatically; blank-branch left as safety
- [x] tests C8 (not persisted until resolve, prompt in block), C9 (order [Q][label][answer]), C10 (dismiss persists Q)

## Status: DONE Phase 1 + Phase 2 + P2-fix (pending build/test run by main)
