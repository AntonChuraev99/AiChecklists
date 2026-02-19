# CSAT Show Logic v2 — Brainstorm

**Date:** 2026-02-19
**Status:** Approved

## What We're Building

Improved CSAT bottom sheet display logic that:
- Differentiates cooldown for **submit** vs **dismiss** (currently same 30 days)
- Increases submit cooldown to 45 days (from 30) to reduce survey fatigue
- Shortens dismiss cooldown to 3 days to maximize feedback collection
- Adds **export** and **share** as trigger events
- Increases delay from 3s to 5s for better UX

## Why This Approach

**Context:** Product is in early stage with low retention. Priority is collecting maximum user feedback before users churn. This means:
- No install age gate (would lose feedback from users who don't return)
- No session count gate (YAGNI — min_actions already sufficient)
- No annual limit (cooldown naturally controls frequency)
- Short dismiss cooldown (3 days) — user may have been busy, try again quickly
- Aggressive but not annoying — after submit, give 45-day breather

## Key Decisions

### 1. Separate Cooldowns for Submit vs Dismiss

| Outcome | Cooldown | Rationale |
|---------|----------|-----------|
| User submitted feedback | **45 days** | Got the data, don't annoy |
| User dismissed (no rating) | **3 days** | May have been busy, retry soon |

**New DataStore keys needed:**
- `csat_last_outcome` — "submitted" or "dismissed" (to determine which cooldown applies)
- Reuse existing `csat_last_shown_date` for the timestamp

### 2. Delay Increase: 3s → 5s

After a trigger event, wait 5 seconds before showing. Gives user time to:
- See the result of their action (checklist created, fill saved)
- Orient themselves on the main screen
- Not feel ambushed

### 3. Expanded Trigger Events

| Event | Already exists | New |
|-------|---------------|-----|
| `checklist_created` | Yes | |
| `fill_created` | Yes | |
| `default_fill_updated` | Yes | |
| `checklist_exported` | | **New** |
| `checklist_shared` | | **New** |

Export and share are "completed outcome" moments — user got value, good time to ask.

### 4. Parameters NOT Changed (YAGNI)

| Parameter | Value | Why not change |
|-----------|-------|----------------|
| Min actions | >= 2 | Works well, don't over-engineer |
| Install age gate | None | Early stage — need feedback ASAP |
| Min sessions | None | min_actions already gates this |
| Annual limit | None | Cooldowns control frequency naturally |
| Dismiss escalation | None | 3-day dismiss CD is already short, no need to escalate |

## Complete Logic Summary

```
CSAT shows when ALL conditions are true:
1. Event is in {checklist_created, fill_created, default_fill_updated, checklist_exported, checklist_shared}
2. csat_action_count >= 2
3. Cooldown passed:
   - If last outcome was "submitted": 45+ days since last shown
   - If last outcome was "dismissed": 3+ days since last shown
   - If never shown: always passes
4. csatShownThisSession == false (max 1 per session)
5. Wait 5 seconds before displaying
```

## Open Questions

- None — all parameters decided.

## Research References

- Refiner: In-App CSAT Survey Guide — 60-90 day cooldown after submit
- DoorDash, Uber: show after completed outcome with 5-15s delay
- Industry standard: differentiate dismiss vs submit cooldown
- Early-stage products: prioritize feedback volume over frequency concerns
