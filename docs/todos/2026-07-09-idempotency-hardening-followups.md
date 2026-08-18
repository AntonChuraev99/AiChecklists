---
status: deferred
opened: 2026-07-09
blocking_reason: low-severity money-path hardening surfaced by bug-pattern-reviewer L2 AFTER the idempotency fix shipped; not worth an extra deploy cycle during session close-out. Core double-charge fix is SOUND + verified in prod.
resume_trigger: hardening the credit idempotency further, OR the AI-chat client (feature/aichat) gains an HttpRequestRetry / any retry policy, OR a "free AI action" / usage-cap-off-by-one report
keywords: [idempotency, refund, atomic-transaction, reserve_chat_credit, increment_usage, double-charge, credit_reservations, request_id]
---

# Credit idempotency — L2 low-severity hardening follow-ups

Follow-ups to the shipped fix (`2026-07-09-ai-credit-retry-idempotency.md`, resolved). The core
guarantee (no **double-charge**) is sound and prod-verified. These are LOW-severity edges L2 flagged.

## 1. Make the reservation-doc delete atomic with the refund txn (low, user-favorable)
`firebase-functions/main.py` `refund_credits` — the `credit_reservations` doc `delete()` runs
**outside** the refund transaction (next statement after commit). Under a **concurrent** (not
sequential) same-`request_id` retry, attempt#2's `reserve` can read the still-present reservation
doc in the gap between refund-commit and delete → `replay` branch → no deduct → **free action**
(never a double-charge — the failure direction is user-favorable, which is why this is low).
- **Fix:** fold the delete into the refund transaction — build `res_ref` in `refund_credits` (as
  `reserve_credits` does) and call `transaction.delete(res_ref)` inside the `@firestore.transactional`
  txn instead of the post-commit `db.collection(...).delete()`. Makes refund+rollback atomic.
- Server-only change → `firebase deploy --only functions:generate_checklist,analyze_and_fill_checklist`
  (no client rebuild). Re-verify with a concurrent double-send (proxy killing the socket mid-request).

## 2. `reserve_chat_credit` is not idempotent (low, latent — NOT currently exploitable)
`firebase-functions/main.py:~2234` `reserve_chat_credit` (AI Chat, 1 credit) is the sibling charging
path and was **not** migrated — plain deduct, no `request_id`. **Not exploitable today:** the only
client `HttpRequestRetry` is in `FirebaseAiServiceImpl.kt` (analyze/generate); the chat client
(`feature/aichat/**`) installs no retry, so nothing auto-re-sends. Reopens if the chat client ever
gains a retry policy (or a gateway retries).
- **Fix when triggered:** extend the same `request_id` dedup pattern to `reserve_chat_credit` /
  `refund_chat_credit` (client `ChatAgentApiService` sends a stable `request_id`).

## 3. (Awareness only, not a fix) `increment_usage` doubles on a genuine replay
Intentional per the main fix: a replay re-runs `increment_usage` (double daily-usage tick + a
duplicate `requests[]` entry) and re-invokes Gemini (~$0.0002). Not money. Only gate it behind
"was this a fresh reserve, not a replay" if daily-cap accuracy becomes a concern.
