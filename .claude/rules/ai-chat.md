---
paths:
  - "**/feature/aichat/**"
  - "**/firebase-functions/**"
---

# AI Chat Assistant (`feature/aichat/`) — flagship interaction layer

Natural-language assistant with tiered routing: **Layer 2** (cloud classifier `classify_chat_intent`, 1 credit) → **Layer 3** (full chat `chat_completion`, 3 credits). The Layer 3 system prompt lives in `firebase-functions/main.py` (`CHAT_COMPLETION_PROMPT_TEMPLATE` + `FEATURE_CATALOG_RU/EN`). <!-- docs-leak-scan: reviewed — constant NAMES only, no prompt body -->

**Layer 1** (local parser `LocalIntentRouterImpl`, 0 credits) is **temporarily unrouted** — see below.

## Hard rule — Layer 1 is parked, not deleted

Decision 2026-07-15 (`docs/decisions/2026-07-15-remove-ai-chat-layer1.md`): the owner temporarily disconnected Layer 1 from routing — `AiChatRepositoryImpl` no longer takes a `LocalIntentRouter`, and its Koin binding is gone. Every message now starts at Layer 2. **This is a disconnect, not a removal**, and the distinction is the whole point of the decision (*«временно отключаем L1, потом дорабатываем и возвращаем»*).

- **The L1 code stays in the repo and its tests keep running.** `LocalIntentRouterImpl`, `RuIntentLexicon`/`EnIntentLexicon`, `ChecklistHintExtractor`, and the 168 tests in `LocalIntentRouterImplTest.kt` are a **parked asset**, not dead weight: without the tests the parser silently rots and "bring it back" becomes "rewrite it". Do not delete them, do not stop running them.
- **Do not build a second local parser.** If a 0-credit fast-path is wanted again, re-route the existing L1 — re-adding it is a revert of the disconnect commit. Writing a new one throws away 168 tests' worth of precision work.
- **Bringing L1 back is a product call, not a code call.** Condition (owner's words): *«когда я буду им доволен»*. The work list is `docs/todos/2026-07-13-aichat-layer1-thumbsdown-backlog.md`. Do not re-route it because a bug looks L1-shaped.
- **Precision-first still governs any future L1 work** (superseded ADR `docs/decisions/2026-07-10-ai-chat-layer1-precision-first.md`, kept for its rationale): L1 is a **timid fast-path**, NOT a natural-language understander — it fires only on unambiguous imperatives (`«добавь молоко»`), everything else escalates. **Never widen the lexicon to chase a bad answer** — add an escalation guard instead. Widening recall is what caused the whole `ai_chat_feedback` backlog.
- **What the disconnect cost** (accepted knowingly, do not "fix" by re-routing L1 on your own): every trivial mutation now costs a credit, so free users hit the paywall on the core action; chat no longer works offline; trivial commands went from <5 ms to 500 ms–2 s; L2/L3 load rose by ~35% of traffic. <!-- docs-leak-scan: reviewed — credit cost of a design choice, not a traffic measurement -->
- **Deep Thinking ON → straight to Layer 3** (bypasses L2, so an open question stays 3 credits, not 4). The old guard "DT ON but L1 matched a confident command → run it as a command" is gone with L1 — nothing local can identify a command any more. Cost: a user who leaves the toggle on pays 3 credits for «добавь молоко». Keep the toggle meaningful — if a change makes DT ON behave identically to DT OFF, the toggle is a lie and the change is wrong.

## Hard rule — every 402 goes through `emitInsufficientCredits`

Fixed 2026-07-16 (`docs/todos/2026-07-16-aichat-insufficient-credits-shows-unknown-hint.md`): a
refusal for lack of credits **must never be flattened into a generic intent**. `ChatIntent.InsufficientCredits`
carries the cause in the type; every call-site routes through the single `emitInsufficientCredits(layer)`
helper in `ChatViewModel`. Its contract: say the wallet is empty, do **not** escalate to Layer 3
(that bills 3 credits to a wallet that just refused 1), charge the turn **0** credits, and show the
"Become Pro" CTA → `NavigateToPaywall(source = SOURCE_INSUFFICIENT_CREDITS)`.

- **Never give a call-site its own dialect of "out of credits".** That is exactly how this bug hid:
  Layer 2 was fixed while the Layer-3 sibling kept shipping a snackbar with no CTA, `outcome="error"`,
  and `credits_used=3` for a turn the server charged 0 for. **Fixing one emit-site means grepping
  the siblings** (`InsufficientCredits`) before you call it done.
- **The credit number is Remote Config** (`ai_daily_limit_premium`), never a literal — the CTA
  promises a number that must not drift. A test fake serves `777` so a hardcoded 300 fails.
- Known exception: the voice path (`ChatViewModel:2066`) keeps a plain snackbar — transcription
  never became a chat turn, so there is no assistant bubble to attach a CTA to, and its analytics
  are already honest. Whether it deserves a paywall route is an open product question.

## Hard rule — feature catalog coverage

Any user-facing feature shipped in the app MUST be added to **both** `FEATURE_CATALOG_RU` and `FEATURE_CATALOG_EN` in the same release, **before** redeploying `chat_completion`. Without an entry the model replies "I can't help with that" to how-to questions about the feature — a UX bug, not a limitation. Full enforcement guide (catalog rules, deploy steps, format-test, anti-patterns): `docs/guidelines/ai-chat-feature-coverage.md`. <!-- docs-leak-scan: reviewed — constant NAMES only, no prompt body -->

## Hard rule — fix bad chat answers test-first (TDD)

**Scope — a bad *answer*: wrong behaviour that reproduces on today's code.** Such an answer, found in Amplitude `ai_chat_feedback` (thumbs-down) or reported by the user, MUST be fixed TDD-style: write a **RED test that reproduces the exact bad behaviour first**, confirm it fails, then fix the responsible layer to green. This locks progress and stops the chat oscillating (a lexicon tweak that fixes one phrase silently re-breaks another).

**Not a bad answer → no RED test.** Feature work, a redesign of an existing flow, or a changed interaction model gets **green coverage written alongside or after the implementation** (`@test-expert` WRITE mode, or the domain specialist), never a red-first cycle. This holds even when a user complaint triggered the redesign: the target behaviour does not exist yet, so there is nothing to reproduce and the "RED" test is just an unimplemented feature failing to compile. Red-first on a feature only on the user's explicit request. Mirrors the global rule (`~/.claude/CLAUDE.md` → «Красный тест — ТОЛЬКО для баг-фиксов»); precedent: AI-chat Stage 1 / D1 (2026-07-15) — the plan prescribed RED tests for a UX rework and was corrected.

Pick the test type by layer:

- **Client layer** (Layer 1 parser, routing in `AiChatRepositoryImpl`, ViewModel) → Kotlin unit test in `feature/aichat/impl/src/commonTest`, run `./gradlew :feature:aichat:impl:testAndroidHostTest`.
- **Cloud Function layer** (Layer 2/3 prompts in `firebase-functions/main.py`) → a **Python API test** that hits the deployed function (register throwaway user → call endpoint → assert answer logic, e.g. "must NOT claim it added items without a tool_call"). Keep under `firebase-functions/tests/`, run after each redeploy.

Use the project skill **`/ai-chat-feedback-fixer`** — it encodes this mine-feedback → red-test → fix loop for both layers.

## Cloud Function deploy / diagnostics

All AI inference is server-side (`analyze_and_fill_checklist`, `generate_checklist`, `chat_completion`, `classify_chat_intent`, `transcribe_audio`) in `aichecklists-40230`. Client holds **zero** Gemini credentials. Deploy with `gcloud functions deploy <fn> ... --set-secrets="GEMINI_API_KEY=gemini-api-key:latest"`. Debugging "AI не ответил": `docs/cloud-functions-diagnostics.md` (server) + `docs/client-diagnostics.md` (client HTTP layer).
