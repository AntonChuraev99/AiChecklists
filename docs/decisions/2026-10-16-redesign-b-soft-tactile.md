---
title: "Redesign B \"Soft & tactile\" for Inbox, checklist detail and AI chat"
summary: "Owner picked direction B of three Claude Design directions; warm palette, grouped lists, Ask pill with mic replacing the centre AI FAB, new checklists separate completed items."
date: 2026-10-16
type: decision
modules: [core/designsystem, feature/home, feature/aichat, composeApp, feature/checklist, feature/create, feature/analyze, feature/onboarding]
keywords: [redesign, soft-tactile, design-tokens, ask-pill, voice-entry, separateCompleted, grouped-list, claude-design]
project: Checklists
---

# Redesign B "Soft & tactile"

## Решение

v2 Inbox, checklist detail, AI chat and the design tokens move to direction B, picked by the owner from three Claude Design directions. Specs: `docs/designs/redesign-b-2026-10-16/DESIGN_SPEC_B.md` plus owner overrides in `docs/designs/redesign-b-2026-10-16/DESIGN_SPEC_B_owner_decisions.md` (overrides win).

- **Tokens:** warm page, cream cards, contrast fixes; brand blue and the cool bottom chrome unchanged.
- **Grouped lists:** one rounded group per section (`Modifier.groupSegment`), round checkboxes.
- **Bottom plate:** "Ask Gisti or add a task" pill with a mic above four equal tabs; centre AI FAB removed.
- **Inbox:** project chips replace pager dots; section headers with icons and counts.
- **Detail:** header card with progress ring, "Ask Gisti about this list" row, Completed section, one "Add a task" control.
- **Chat:** no per-reply label, feedback on the latest reply, credits chip inside the input.

## Почему так

- Owner reopened two older decisions on purpose: the "no debt counter" on Inbox section headers (counts ON) and the raised centre FAB.
- The pill mic must not be a dead affordance, so it opens the chat dock already recording (`VoiceEntry.kt`; tap to start, tap the dock mic to finish — the hold gesture cannot be done from a tap).

## Ловушки

- **A model default is also the value for absent fields.** `separateCompleted = true` for NEW checklists is set explicitly at every creation site (`NEW_CHECKLIST_SEPARATE_COMPLETED` in `Checklist.kt`); the data-class, Room, sync and parser defaults stay `false`, otherwise old synced lists without the field would flip. Pinned by `SeparateCompletedLegacyDecodeTest`.
- **Dismiss is not close.** On the detail capture plate BACK/scrim send `OnItemCreateDismissed` (keeps the draft); `OnDockItemCreateClosed` still clears it. A reset for another Weekly day must clear staged attachments too.
- **Global tokens move every screen.** Re-record all Roborazzi goldens and look at them; the store raw pass `StoreListingFramesScreenshotTest.a*` is the cheapest full-app before/after.

## Не сделано

- The MCP server (`mcp-server/src/mutate.ts`) still creates lists with `separateCompleted = false` — owner's choice (2026-10-17); the interactive onboarding toggle now starts ON.
- Not verified on a device or on the wasmJs dev server: keyboard lifting the capture plate, tap on chat bubble text, mic permission flow.
