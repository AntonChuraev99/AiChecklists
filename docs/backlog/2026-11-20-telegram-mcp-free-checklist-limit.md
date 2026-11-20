---
title: "Telegram bot and MCP create checklists past the Free limit"
date: 2026-11-20
type: backlog
status: ready
severity: medium
effort: M
why_not_now: the gap predates the AI chat (phase 1 «Checklist from lines» and every MCP create already skip it); closing it needs the limit readable server-side, which it is not today
modules: [telegram-bot, mcp-server, firebase-functions]
keywords: [free tier, max_checklists_free, requires_premium, telegram bot, mcp, create_checklist, limit]
project: gisti-ai-checklists
---

# Telegram bot and MCP create checklists past the Free limit

The app refuses a sixth checklist for a Free user: the agent tool `create_checklist` answers
`requires_premium` after `ToolCallDispatcherImpl.freeChecklistCeilingReached()` reads
`max_checklists_free` from Firebase Remote Config. The server-side writers do not check it:

- Telegram bot — «Checklist from lines», «Checklist with AI» (phase 1) and the AI chat's
  `create_checklist` (`telegram-bot/src/agentTools.ts`, phase 2).
- MCP — `create_checklist_empty`, `create_checklist_ai`.

Why it is not a one-line fix: the limit lives in the Firebase Remote Config client template, which
the workers do not read (they read the Firestore doc `remote_config/current`). Options: mirror
`max_checklists_free` into `remote_config/current` and read it in both workers, or enforce it in a
Python endpoint that both call before a create. A hardcoded 5 in the workers is the drift this
repo already tracks in [duplicated-limit-constants](2026-08-03-duplicated-limit-constants.md).
