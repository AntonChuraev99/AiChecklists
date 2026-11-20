---
title: "Server-side writer of app data must carry the keys it does not model"
summary: "mcp-server re-encoded every fill item from a key whitelist, so each bot/MCP write reset alarmEnabled and reminderFullScreen on all items; fixed by modelling both and carrying unknown keys."
date: 2026-11-20
type: bug-fix
modules: [mcp-server, telegram-bot]
keywords: [itemsJson, encodeFillItem, alarmEnabled, reminderFullScreen, unknown keys, round-trip, extra, mcp, telegram bot]
project: Checklists
---

# Server-side writer of app data must carry the keys it does not model

## Проблема / Контекст

MCP and the Telegram bot write checklists through `mcp-server/src/`. Any fill write re-encodes
**every** item of `fills[].itemsJson`, and `encodeFillItem` emitted a fixed key list that lacked
the app's `reminderFullScreen` and `alarmEnabled`. One tick in the bot re-armed every "date, no
alarm" item and cleared every full-screen reminder of the list.

## Решение

- `mcp-server/src/model.ts`: `TEMPLATE_ITEM_KEYS` / `FILL_ITEM_KEYS` list the known keys in Kotlin
  declaration order; both fields are modelled (absent `alarmEnabled` reads as `true`); any key the
  parser does not know goes into `item.extra`.
- `mcp-server/src/encode.ts`: the two fields are emitted only when they differ from the Kotlin
  default (the app writes with `encodeDefaults = false`), then `appendExtra` writes the `extra` keys
  after the known ones — for template items too.
- The document level was already safe: `writeChecklist` PATCHes with `updateMask` =
  `CHECKLIST_DOC_FIELD_PATHS`, so top-level fields outside that list survive.

Tests in `mcp-server/src/encode.test.ts` failed on the old code for both fields and for an unknown
future key; after the fix mcp-server is 82/82 (источник: `npm test --prefix mcp-server`, 2026-11-20).

## Почему именно так

Adding the two fields alone would close today's bug and reopen it with the next field the app adds.
Carrying unknown keys makes the mirror safe by construction; listing the known keys in one array keeps
the parser and the encoder from disagreeing (a test pins that both use the same list).

## Связанные файлы

- `mcp-server/src/model.ts`, `mcp-server/src/encode.ts`, `mcp-server/src/mutate.ts`
- `mcp-server/src/encode.test.ts`
- Task doc: `docs/active/telegram-bot-phase2-ai-chat-2026-11-20.md`
