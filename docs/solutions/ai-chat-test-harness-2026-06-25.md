---
title: "AI Chat Scenario Test Harness — Offline coverage for parsing & routing"
date: 2026-06-25
type: pattern
modules: [feature/aichat, firebase-functions]
keywords: [ai-chat, scenario-testing, offline-harness, parsing, intent-routing, compose-viewmodel-fakes, test-expert-pattern, dead-path-detection]
project: checklists
---

# AI Chat Scenario Test Harness — Offline Coverage for Parsing & Routing

## Problem / Context

AI Chat has three layers of logic: (1) **Layer 1 — parsing/intent extraction** (in-process, Kotlin); (2) **Layer 2 — server-side classification/embedding** (Cloud Functions); (3) **Layer 3 — LLM generation** (Gemini API, paid). Bugs in Layer 1 routing (like #3: ambiguous item-add not prompting for list selection; or #2: attachments never dispatching) are:

- **Hard to catch:** compile + unit tests pass, but integration through public `ChatViewModel` API catches the dead path.
- **Expensive to test:** prior approach relied on Firebase integration tests (slow, flaky, cost money if using real Gemini).
- **Hard to grow:** no scenario inventory, so each regression test was ad-hoc.

Recurring issue: the compile+test gate *for a given layer* is not sufficient. A `ChatViewModel` method may pass unit tests but never get called because the `when(intent)` dispatcher has a dead branch, or the parser produces a value that downstream code doesn't handle.

## Solution

**Tier 1 — Offline scenario harness** (`ChatScenarioHarness.kt` + `AiChatScenariosTest.kt`):

A **data-driven scenario table** that drives the REAL `ChatViewModel` with deterministic fakes for all network/cloud layers. Each scenario is a struct: input message, expected parser output (intent type, parameters), expected dispatcher action (show choice, write item, etc.). The harness runs in `commonTest` with zero network cost, zero money, 100% deterministic. Growth = add a row.

**Tier 2 — Multi-model Gemini eval harness** (`firebase-functions/tests/ai_model_eval.py`):

Python harness runs Layer 3 (Gemini LLM) scenarios against the deployed Cloud Functions. Cost-gated by default (dry-run), requires `--yes` flag to execute. Estimates credit cost up-front. Opt-in, not in CI. Single-model baseline runs in CI; multi-model cross-eval deferred to manual analyst runs.

## Why This Approach

1. **Zero network cost for Layer 1/2:** Fakes replace Firebase entirely. Add a scenario in 30 seconds; run 46 scenarios in <1 second.
2. **Catches dead paths:** Tier 1 harness found #2 (attachments never dispatched) and #3 (ambiguous item-add) because it exercises the real public API, not isolated units.
3. **Reusable pattern:** Works for any Compose feature with a deterministic public API (ViewModel). Applied here to AI Chat; also applicable to Analyze (multi-model input), Paywall (redemption flows), Checklist editing (reorder/complete/delete).
4. **Living roadmap:** RED-OK scenarios (deferred work) stay in the dashboard, allowing future contributors to see known limitations and add fixes. E.g., "C4: multi-item detection needs NLU classifier" is visible and testable the day the classifier is added.

## Implementation

### Tier 1: Scenario Harness (Layer 1 & 2)

**File:** `feature/aichat/impl/src/commonTest/kotlin/.../presentation/ChatScenarioHarness.kt`

```kotlin
data class ChatScenario(
    val id: String,
    val input: String,
    val language: String = "EN",
    val context: ChatContext = ChatContext.default(),  // previous lists/items
    val expectedIntent: String,  // "AddItem", "CompleteItem", "CreateChecklist", etc.
    val expectedHint: String? = null,
    val expectedItem: String? = null,
    val expectedAction: String,  // "showWriteChoice", "addItemDirect", "showWhichListChoice", etc.
    val expectRedNow: Boolean = false  // TRUE = known roadmap, not a bug
)

class ChatScenarioHarness(
    private val viewModel: ChatViewModel,
    private val fakeRepository: FakeChatAgentRepository  // zero-network fakes
) {
    fun runScenario(scenario: ChatScenario): ScenarioResult {
        // (1) Set context
        viewModel.loadContext(scenario.context)
        
        // (2) Send message
        viewModel.sendIntent(ChatScreenContract.Intent.SendMessage(scenario.input))
        
        // (3) Verify routing
        val action = fakeRepository.lastDispatchedAction
        val parseResult = viewModel.currentParseState
        
        return ScenarioResult(
            passed = (parseResult.intent == scenario.expectedIntent 
                && action == scenario.expectedAction),
            actual = ScenarioActual(parseResult.intent, action),
            expected = scenario
        )
    }
}

// Invoked by: AiChatScenariosTest.kt
class AiChatScenariosTest {
    @Test
    fun testAllScenarios() {
        val harness = ChatScenarioHarness(viewModel, fakeRepository)
        val results = SCENARIOS.map(harness::runScenario)
        
        val green = results.filter(it.passed && !it.scenario.expectRedNow).size
        val fixedThisSession = results.filter(it.passed && it.scenario.id in listOf("C3", "C3-ru", ...)).size
        val redOk = results.filter(!it.passed && it.scenario.expectRedNow).size
        val hardFail = results.filter(!it.passed && !it.scenario.expectRedNow)
        
        println("""
            |SCENARIO DASHBOARD
            |GREEN: $green  FIXED!: $fixedThisSession  RED-OK (roadmap): $redOk  HARD-FAIL: ${hardFail.size}
            |${hardFail.map { "  FAIL: ${it.scenario.id}: ${it.scenario.input} → expected ${it.expected.expectedAction}, got ${it.actual.action}" }.joinToString("\n")}
        """.trimMargin())
        
        assertTrue(hardFail.isEmpty(), "Hard failures present: ${hardFail.map(it.scenario.id)}")
    }
}
```

**Current inventory:** 46 scenarios (30 EN complete, 16 RU ~50%):
- **AddItem** (C1–C3): basic, ambiguous (no hint), with context
- **CompleteItem** (C4–C6, RU mirror)
- **CreateChecklist** (C7–C9, with items, from templates)
- **AttachFile** (C10–C12, without target)
- **DeleteItem** (C13–C15)
- **ReorderItems** (C16–C18)
- … [30+ more, organized by intent type]

**Results (after fixes):** GREEN 42, FIXED! 6, RED-OK 4, HARD-FAIL 0.

### Tier 2: Gemini Model Eval (Layer 3)

**File:** `firebase-functions/tests/ai_model_eval.py`

```python
#!/usr/bin/env python3
"""
Multi-model Gemini evaluation for AI Chat scenarios.
Runs scenarios × [gemini-2.0-flash, gemini-1.5-pro, gemini-1.5-flash, ...].

COST-GATED: dry-run by default. `--yes` required to execute.
"""

import argparse
from enum import Enum

MODEL_OVERRIDE_TEST_SECRET = "test-secret-xyz"  # from CF env
SCENARIOS = [...]  # same data-class as Tier 1, JSON-serialized

def estimate_cost(scenario_count: int, models: list[str]) -> dict:
    """Estimate Gemini API cost before running."""
    # gemini-2.0-flash: $0.075/1M in, $0.3/1M out (cache helps)
    # 1.5-pro: $3.50/1M in, $14/1M out
    calls = scenario_count * len(models)
    avg_tokens_in, avg_tokens_out = 120, 85  # empirical
    cost_flash = (calls * (avg_tokens_in/1M * 0.075 + avg_tokens_out/1M * 0.3))
    cost_pro = (calls * (avg_tokens_in/1M * 3.50 + avg_tokens_out/1M * 14.0))
    return {
        "total_calls": calls,
        "estimated_cost_flash_only": cost_flash,
        "estimated_cost_all_models": cost_flash + cost_pro
    }

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--yes", action="store_true", help="Execute (default: dry-run)")
    parser.add_argument("--full", action="store_true", help="Full scenario set (default: core subset)")
    parser.add_argument("--models", default="flash", help="Model list: flash, pro, all (default: flash)")
    args = parser.parse_args()
    
    scenarios = SCENARIOS if args.full else SCENARIOS[:8]  # tiny subset default
    models = ["gemini-2.0-flash-lite"] if args.models == "flash" else ALL_MODELS
    
    cost = estimate_cost(len(scenarios), models)
    print(f"DRY RUN: {cost['total_calls']} calls, ~${cost['estimated_cost_flash_only']:.4f} (flash) or ${cost['estimated_cost_all_models']:.2f} (all models)")
    
    if not args.yes:
        print("Use --yes to execute (costs money). Default is dry-run.")
        exit(0)
    
    # (run actual scenarios via CF with test_secret override)
    results = run_scenarios(scenarios, models, CF_URL, MODEL_OVERRIDE_TEST_SECRET)
    
    # Print summary
    print(f"RESULTS: {len([r for r in results if r.success])}/{len(results)} passed")
    ...
```

**Deployment:** Manual user step (not auto-run):
1. Set CF env var: `MODEL_OVERRIDE_TEST_SECRET=<strong-secret>`.
2. Deploy CF: `gcloud functions deploy chat_agent --set-env-vars MODEL_OVERRIDE_TEST_SECRET=...`
3. Run harness: `python firebase-functions/tests/ai_model_eval.py --full --yes --models all`

### Bugs Fixed (Found via Tier 1)

**#3 — Ambiguous item-add (flagship):** `extractItemAndHint("добавь в чеклист пункт молоко")` was treating "чеклист" as a list hint instead of filtering it. Now:
- Detects generic-target words (RU чеклист/чек-лист/список; EN checklist/list after article strip) and nulls the hint.
- Strips filler words from item start (пункт, item, task).
- In `handleSend` for `AddItem`: if hint==null and ≥2 lists, shows `showWhichListChoice()` (reuses `AmbiguousMatch` builder + `chat_choice_which_list` string).

**#2 — Dead path: attachments never dispatched.** `handleSend` was clearing `pendingAttachments` before the `when(intent)` block, so `AttachToItem` never saw the attachments. Now captures into `sentAttachments` first, then clears.

**#3b — Lexicon gap:** EN `completeItem` intent missing "bought" (RU had «купил»). Added.

### Known Roadmap (RED-OK Scenarios)

Scenarios marked `expectRedNow = true` are deferred but tracked:

- **C4 / C4-ru:** Multi-item detection ("apple and banana") — needs NLU classifier to distinguish from single item with coordinated articles.
- **C7:** Create-with-items ("create my shopping list with milk, eggs") — requires context flow from create-phase parser.
- **C22:** Attach-without-target ("attach this") — deferred UX (pick-file vs which-item choice).

## Links

- Implementation: `feature/aichat/impl/src/commonTest/.../presentation/{ChatScenarioHarness, AiChatScenariosTest}.kt`
- Model eval: `firebase-functions/tests/ai_model_eval.py`
- Test fakes: `FakeChatAgentRepository` (in tests module)
- Related: `feature/aichat/impl/src/commonMain/.../presentation/ChatViewModel.kt`, `EnIntentLexicon.kt`, `firebase-functions/main.py` (resolve_model gate)

## Patterns for Other Features

This scenario-harness pattern is reusable for:
- **Analyze:** multi-model input routes (Photo/PDF/Text/Link/Voice) → `AnalyzeViewModel` + fakes for Gemini.
- **Paywall:** redemption flow variants (restore/subscription/trial) → `PaywallViewModel` + fake RevenueCat.
- **Checklist detail:** reorder/complete/delete state transitions → `ChecklistDetailViewModel` + fake repository.

Key principle: any Compose feature with a deterministic public API (ViewModel) + layered dependencies (service / repository) benefits from scenario-harness testing. It's cheaper and faster than network-heavy e2e tests, and catches integration bugs that unit tests miss.
