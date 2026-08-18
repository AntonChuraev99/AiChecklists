# Full-screen reminder — checklist-level support

**Status:** RESOLVED 2026-07-13 — checklist-level FSI implemented, build green (androidApp assembleDebug + schema 17 + gate test + wasm), installed on Pixel 9. NOT committed yet at time of writing.
**Created:** 2026-07-13
**Blocked-by:** —

## Done (2026-07-13)

- `Checklist.reminderFullScreen` (domain) + `ChecklistEntity` column + `MIGRATION_16_17` (DB v17, schema 17.json generated) — verified via build.
- `ChecklistSyncData.reminderFullScreen` + mapping in ALL 4 SyncRepositoryImpl functions (toSyncData / toDomain / toInsertEntity / toUpdateEntity).
- `ChecklistRepository.setReminderFullScreen` (DEFAULT no-op in interface so 27 test fakes need no change) + `ChecklistRepositoryImpl` override + `ChecklistDao.updateReminderFullScreen`.
- ChecklistDetail: intent `OnChecklistReminderFullScreenToggled` + VM `handleChecklistReminderFullScreenToggled` (persists immediately via setReminderFullScreen, reuses `maybeShowFullScreenIntentInstruction`). Checklist-level ReminderSheet call-site binds `fullScreenEnabled = state.checklist.reminderFullScreen` (single source of truth — no separate state field / no seeding).
- `ReminderReceiver.showNotification` (checklist-level) FSI branch mirroring showItemNotification.
- MCP TS verified safe: `mcp-server/src/model.ts` reads fields manually (no strict Zod) → extra field ignored.
- Persistence note: mirrors `updateReminder` (bare column update, no syncStatus dirty) — local-first; consistent with existing checklist reminder behavior.

## Progress snapshot (2026-07-13)

- ✅ **androidMain done** — `ReminderReceiver.showNotification` gained a `fullScreen: Boolean` param + the FSI branch mirroring `showItemNotification` (CATEGORY_ALARM + setFullScreenIntent → FullScreenReminderActivity, gated by `shouldUseFullScreenIntent`/`canPostFullScreen`); both `handleOneShot`/`handleRepeat` pass `checklist.reminderFullScreen`. **REVERTED** to keep the tree compilable (it referenced the not-yet-added `Checklist.reminderFullScreen`). Patch saved this session: scratchpad `reminderreceiver-checklist-fsi.patch` (also trivially reconstructable — plain mirror of the committed per-item branch).
- ❌ **commonMain NOT started** — `Checklist.reminderFullScreen` field, `ChecklistEntity` column, `MIGRATION_16_17` (+version 17), `ChecklistSyncData` field + both-direction mapping, ChecklistDetail state/intent/VM/call-site. See "Steps when resumed" below.
- ⚠️ MCP TS contract (`mcp-server/src/model.ts`) not yet verified for the new `ChecklistSyncData.reminderFullScreen` field.

## Resume order (do commonMain FIRST, then re-apply android patch, then build)

## Context

Per-item full-screen (alarm-style) reminders shipped 2026-07-13 (see the FSI solution doc).
The opt-in lives in the shared `ReminderSheet` but is surfaced **only** for the per-item
reminder flow (`showFullScreenOption = true` at the item call-site). The checklist-level
reminder sheet, the item-create "Repeat" sheet, and onboarding pass `showFullScreenOption = false`.

## Why deferred

Per-item was migration-free: `ChecklistFillItem.reminderFullScreen` lives in the `items` JSON
blob (Room `itemsJson`), and fills sync via `FillSyncData.itemsJson` — no Room migration, no
Firestore mapping change.

Checklist-level is heavier:
- `Checklist.reminderAt`/`repeatRule` are **SQL columns** in `ChecklistEntity`, so a
  `reminderFullScreen` column needs **Room MIGRATION_16_17** (`ALTER TABLE checklists ADD COLUMN
  reminderFullScreen INTEGER NOT NULL DEFAULT 0`) + entity `toDomain`/`toEntity`.
- It is a **structured** sync field → add to `ChecklistSyncData` + the Firestore
  `toMap`/sync-data mapping (rule `android-firestore-manual-map-sync-fields`), and check the
  MCP-server TS contract (`mcp-server/src/model.ts`/`encode.ts`) decodes/ignores it cleanly.

## Resume

User says «сделай напоминание на весь экран и для чек-листа целиком / checklist-level full-screen
reminder / полноэкранное напоминание на весь чек-лист».

## Steps when resumed

1. `Checklist.reminderFullScreen: Boolean = false` + `ChecklistEntity` column + `MIGRATION_16_17`
   (bump DB version 16→17, add schema json).
2. `ChecklistSyncData.reminderFullScreen` + Firestore mapping both directions; verify MCP TS side.
3. Surface the toggle at the checklist-level `ReminderSheet` call-site (`showFullScreenOption = true`).
4. `ReminderReceiver.showNotification` (checklist-level) reads the flag → same FSI branch as
   `showItemNotification` already has.
5. Tests: gate + a migration test.
