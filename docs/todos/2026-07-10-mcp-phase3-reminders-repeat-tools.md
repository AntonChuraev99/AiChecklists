# Deferred: MCP Phase 3 — reminders / repeat via tools

**Status:** Deferred
**Created:** 2026-07-10
**Area:** `mcp-server/` (remote MCP worker) + `feature/checklist` (Kotlin contract test)

## What's deferred
Expose item-level **reminders** (`reminderAt`) and **repeat schedules** (`repeatRule`) through MCP tools — e.g. `set_reminder(checklistId, itemId, at, fillId?)` and `set_repeat(...)`. Currently the MCP reads and round-trips these fields verbatim (never edits them); named fills shipped this session, but reminders/repeat writes did NOT.

## Why deferred (not just skipped)
The item-level `repeatRule` is a **nested `ReminderRepeatRule`** with a **polymorphic `RepeatEndCondition`** (Daily/Weekly/Monthly/Weekdays/Biweekly/Quarterly/Yearly/Custom + end conditions). kotlinx.serialization of a polymorphic sealed hierarchy has a non-trivial JSON shape (type discriminator, per-variant fields). Writing it from the TS encoder risks byte-drift from the Kotlin serializer.

Named fills were safe to ship without a new contract test because a named fill is the same `FillSyncData` the encoder already pins (no new serialized shape). Reminders/repeat is the opposite: it introduces **new serialized shapes**, so it MUST get its own Kotlin↔TS byte-equality contract test first (mirroring `ItemsJsonEncodingContractTest.kt`) before any write tool ships. Cramming it next to the named-fills pass would lower quality (user chose "named fills only" this session).

## Resume steps (when picked up)
1. Read `ReminderRepeatRule` + `RepeatEndCondition` sealed defs in `feature/checklist/.../domain/model/`; determine kotlinx polymorphic JSON shape (discriminator key, `@SerialName`s).
2. Add a golden fixture with a populated `repeatRule` to BOTH `mcp-server/src/encode.test.ts` and the Kotlin `ItemsJsonEncodingContractTest.kt`; assert byte-equal (drift alarm).
3. Extend the TS encoder (`encode.ts`) to emit `reminderAt`/`repeatRule` (respecting omit-defaults + polymorphic tag).
4. Add mutate ops (`setReminder`/`setRepeat`/`clearReminder`) + MCP tools; honor `fillId` (state-op, same as toggle/edit_note).
5. Optional: pin `ReminderRepeatRule`/`Attachment` field order Kotlin-side (TS already covers it via verbatim round-trip).

**Resume trigger:** "делаем reminders/repeat в MCP" / "MCP Phase 3 напоминания".
Design log: `docs/active/gisti-mcp-server-design-2026-07-10.md` (§Phase 3 DEFERRED).
