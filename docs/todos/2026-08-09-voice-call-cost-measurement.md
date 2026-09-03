# Voice Call — Cost Per Minute (Measurement & Display)

**Status:** Open

**Severity:** P2 — UX debt, no user-facing cost visibility

**Trigger:** First N live voice calls on debug-build (10–50 calls, distributed across call lengths 10s–5min).

---

## Problem

Parameter `voiceCallCostCredits` is passed `null` to the UI. String resource `voice_call_cost_per_minute` exists but is unused. Without displaying cost before/during the call, users have no way to estimate if a voice call is worth the credit spend.

Cost must be measured empirically from real calls before being shown (no guessing).

## Measurement Plan

1. **Instrument TurnBased + Live engines:**
   - Log `voice_call_duration_ms` and `voice_call_credits_used` to Amplitude on call end
   - Segment by call length: [0–10s], [10–30s], [30–60s], [1–3m], [3–5m], [5+m]

2. **Collect 10–50 calls per segment** (N=50+ minimum for 95% CI on median)

3. **Calculate cost per minute:**
   - mean_duration per segment → cost_per_min = median(credits_used) / (median(duration_ms) / 60000)
   - Expected TurnBased: ~1 credit/min (same as 20-token text message at ~5 tokens/10sec = ~30 tokens/min = 1.5 credits in 3-credit chunks)
   - Expected Live: much higher or variable (depends on metering solution)

4. **Update UI:**
   - Hard-code `voiceCallCostCredits = 1` (for TurnBased) or make it RC-key if Live cost differs
   - Display before call: `"Call will cost ~1 credit per minute"` (or RC-parameterized message)

## Verification

- [ ] Amplitude chart: `voice_call_duration_ms` and `voice_call_credits_used` populated for ≥10 calls
- [ ] `voice_call_cost_per_minute` string resource displayed on `VoiceCallScreen` pre-call
- [ ] Manual check: compare credit debit logs vs. UI-shown cost (should match within ±1 credit)

## Deferred Until

- User acceptance test on debug-build yields 10+ completed calls
- Cost variance is <20% (predictable per-minute rate)
- Business decision: show cost before, during, or post-call
