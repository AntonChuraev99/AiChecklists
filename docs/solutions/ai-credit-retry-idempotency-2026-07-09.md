---
title: "AI Credit Idempotency — Double-Charge Fix via Request ID Deduplication"
date: 2026-07-09
type: bug-fix
modules: [ai-charge, cloud-functions, firebase, client-http]
keywords: [payment, idempotency, double-charge, retry, HttpRequestRetry, reserve_credits, refund_credits, request_id, charging-endpoint, Firestore-dedup, analyze_and_fill_checklist, generate_checklist]
project: gisti-ai-checklists
---

# AI Credit Idempotency — Double-Charge Fix via Request ID Deduplication

## Problem / Context

This session added `HttpRequestRetry` to `FirebaseAiServiceImpl` for resilience against connection drops (ECONNRESET, "unexpected end of stream", socket timeout). On charging endpoints (`generate_checklist`, `analyze_and_fill_checklist`, cost = 20 credits), this introduced a **double-charge vulnerability**:

1. Client POSTs request → server `reserve_credits(−20)` → Gemini call succeeds → server returns 200 OK.
2. **Client socket closes before receiving the 200** (flaky network, timeout after server commit).
3. `HttpRequestRetry` retries the same request → server reserves **another 20 credits** → one visible result, two charges.
4. Existing `refund_credits` only fires on server-side 5xx exceptions (logic errors); it never fires for "server succeeded, client didn't receive the response" — no idempotency key, no dedup, no refund.

The dangerous vector is **transport-exception retry after a committed server success**. `retryOnServerErrors` alone is safe (a 5xx means the server rolled back); the problem is `retryOnExceptionIf` triggering on connection drops.

**Discovery:** L2 bug-pattern-reviewer flagged this post-ship; vc62 was 20% staged, bounded exposure.

## Solution

Implemented **proper idempotency via request ID deduplication**, merged into the existing Firestore transaction layer. Stricter than client mitigation; preserves retry resilience.

### Server-Side (`firebase-functions/`)

**New module: `credits_logic.py`**  
Extracted pure decision logic to mirror `cors.py` / `generated_items.py` pattern — testable without Firestore/network.

```python
def reservation_decision(user_exists: bool, balance: float, cost: float, prior_remaining: Optional[float]) -> str:
    """
    Pure branch: returns one of 'no_user', 'replay', 'insufficient', 'reserve'.
    If prior_remaining is not None → replay (return cached, no deduct).
    """
    if not user_exists: return 'no_user'
    if prior_remaining is not None: return 'replay'
    if balance < cost: return 'insufficient'
    return 'reserve'
```

**Modified `reserve_credits(user_id, request_id=None)` in `main.py`**  
Folded dedup INTO the existing Firestore transaction:

- On entry, reads **both** the user doc (balance) AND a `credit_reservations/{user_id}__{request_id}` doc (if `request_id` is present).
- If the reservation doc exists (prior call with same request_id), returns the cached `remaining_after` **without deducting again** → closes the double-charge.
- If the reservation doc does not exist, applies the normal reserve logic, and writes a new reservation doc atomically within the txn.
- `request_id=None` (old clients) → original non-deduped behaviour. **Fully backward-compatible.**

Cost: one extra Firestore read per AI request when `request_id` is present (negligible).

**Modified `refund_credits(user_id, amount, reason, request_id=None)`**  
Non-obvious trap discovered mid-session: on a Gemini 5xx the server calls `refund_credits` to roll back the reserved credits. But the reservation doc would remain after the refund. On a subsequent `retryOnServerErrors` retry:
- The retry re-sends the same request_id.
- `reserve_credits` sees the stale reservation doc and returns the cached (rolled-back) balance.
- The **Gemini call retries and succeeds** (usually true for transient 5xx).
- Result: **one charge, two successes, one user-visible "success" with a free action** + a stale `ai_credits` in the response.

**Fix:** on a **successful** refund, `refund_credits` now **deletes the matching `credit_reservations` doc**, maintaining reserve/refund symmetry on the (user_id, request_id) key. If the refund itself fails (rare), the reservation is kept so a retry replays (charged once, still gets a result).

- All 4 call sites pass `request_id`: parse error, Gemini error (both endpoints).
- Added `logger.exception` / `logger.error` on txn-fail, user-missing, audit-log-fail paths — the one place a user can be wrongly charged now has telemetry (was silent).

### Client-Side (`FirebaseAiServiceImpl.kt`)

**Request DTO changes**  
`FillChecklistRequest` and `GenerateChecklistRequest` gained a `request_id` field (`@SerialName("request_id")`), generated once per call via `newRequestId()` (existing idiom: `Uuid.random()`).

`HttpRequestRetry` re-sends the identical request body (including `request_id`) across retries → server sees the same request_id → server dedups.

### Test Coverage

**`firebase-functions/tests/test_credit_idempotency.py` — 8/8 green:**
- `test_fresh_deduct` — new request_id, normal reserve path
- `test_replay_no_deduct` — same request_id replayed, returns cached balance
- `test_replay_wins_when_now_insufficient` — replay happens even if balance later drops below cost
- `test_replay_zero_balance` — cached result is returned even if user deleted
- `test_insufficient` — insufficient balance on fresh request
- `test_exact_balance` — boundary case: cost == balance
- `test_no_user` — prior-missing user
- `test_no_user_precedence` — no_user returned even if request_id is present

**Client build:** `:feature:analyze:compileKotlinWasmJs` BUILD SUCCESSFUL (wasmJs variant confirmed).

## Shipped to Production

**Server deployed (2026-07-09):**  
```
firebase deploy --only functions:generate_checklist,analyze_and_fill_checklist
→ Deploy complete! (us-central1)
```

**E2E verified via real prod path:**
1. Registered throwaway test user.
2. Call #1: `generate_checklist` with request_id=A → balance 100 → 80 ✓ (normal reserve).
3. Call #2: same request_id=A (simulating replay) → balance still 80 ✓ (no double-charge).
4. Call #3: new request_id=B → balance 80 → 60 ✓ (new deduction).

**Client deployed (vc63 / 1.17.13):**  
- Signed AAB via `:androidApp:bundleRelease`.
- Published to **production at 100%** via Play Developer API.
- Supersedes vc62 (20% staged rollout) and vc60 (remainder).
- Release notes: en-US + ru-RU.

## Intentional Out-of-Scope

- **`increment_usage`** is NOT deduped → a replay double-counts one daily usage tick (not money; a counter). Accepted; probability is low (only on real post-success drop after Gemini success).
- **Chat layer** (`classify_chat_intent` / `chat_agent`) uses a separate reserve mirror (`CHAT_INTENT_COST`) with a different client service. If that client gains transport-retry, apply the same `request_id` pattern.

## Why This Approach

1. **Durability:** covers all retry paths + offline resends, not just transport-drops.
2. **Idempotency:** standard pattern for charging systems (AWS, Stripe, Twilio use request IDs). Replay returns the cached result **atomically** within the same transaction that checks balance — no time-of-check-time-of-use race.
3. **Backward-compatible:** `request_id=None` triggers the original non-deduped path; old clients and internal test scripts work unchanged.
4. **Cost:** negligible (one extra Firestore read per request) vs. client-side mitigation (would lose connection-drop resilience added this session).

## Lessons Learned

**Asymmetry trap:** reserve/refund operations must stay symmetric in their primary key (user_id, request_id). When refund deletes the reservation, it closes the idle-replay window. **When refund fails, keep the reservation so a retry still only charges once** (idempotency protects both directions).

**Observability:** refund failures were silent (returned `False` with no log). Added exception/error logging — the one path where a user is wrongly charged (refund did not apply) now has telemetry.

**Scope:** idempotency key must be at the **charging boundary** (before money is reserved), not inside Gemini call. Placing it at the Gemini level (post-reserve) would miss the reserve-to-receive drop.

## Related Files / Verification

- Server: `firebase-functions/main.py` (reserve/refund), `firebase-functions/credits_logic.py` (decision logic)
- Client: `feature/analyze/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/analyze/data/remote/FirebaseAiServiceImpl.kt` (request_id generation)
- Tests: `firebase-functions/tests/test_credit_idempotency.py`
- Rule added to registry: `retry-without-idempotency-on-charging-endpoint` (backend-deploy, runtime, medium)

**Verification checklist for future charging endpoints:**
1. Does the endpoint call `reserve_credits`? → Gain `request_id` and pass through from client.
2. Are there side effects after `reserve_credits` (Gemini, parse, external API)? → Ensure `refund_credits` is called on all error paths.
3. Does `refund_credits` delete or update the reservation? → Maintain symmetry: successful refund deletes the key, failed refund leaves it.
4. Does the client retry? → Pass identical `request_id` on retry (via `HttpRequestRetry` default or explicit UUID generation).
