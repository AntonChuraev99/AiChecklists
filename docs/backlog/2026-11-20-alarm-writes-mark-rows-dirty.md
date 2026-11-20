---
title: "Alarm-driven writes mark a possibly stale row for upload and can overwrite newer cloud edits"
date: 2026-11-20
type: backlog
status: ready
severity: medium
effort: M
why_not_now: predates the Telegram work and needs a product call on where a repeat's «reset checks» should happen (device or server)
modules: [composeApp/androidMain/notification, feature/checklist]
keywords: [ReminderReceiver, updateFill, resetDefaultFillChecks, PENDING_UPLOAD, touchForSync, last-write-wins, sync, data loss]
project: gisti-ai-checklists
---

# Alarm-driven writes can overwrite newer cloud edits

A row in `PENDING_UPLOAD` skips the remote snapshot on merge and is then pushed over the cloud copy
(`feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/sync/SyncRepositoryImpl.kt`, the `PENDING_UPLOAD` branch of the merge).
So any write made by an ALARM on a row this phone has not pulled lately can erase an edit made
meanwhile on the web, in the Telegram bot's AI chat or via MCP.

Phase 4 (2026-11-20) moved the alarm bookkeeping off that path: `advanceRepeatSchedule`,
`clearFiredReminder`, `clearFinishedRepeatSchedule` and `updateFillLocalOnlyFromAlarm` (item
handlers) are local-only. One alarm path still writes through it: a repeat with «reset checks» calls
`resetDefaultFillChecks` from the alarm.

Related gaps found on the way (also pre-existing): `handleOneShot` in `ReminderReceiver` fires an
armed checklist alarm even if the reminder was cleared in the cloud meanwhile (with channel push/both
the phone shows a stale notification); future item reminders that arrive by sync are not armed until
the next reboot.

The reset must reach the cloud somehow (the reset is user-visible on every device). Options: a
local-only item bookkeeping write + a server-side reset at the occurrence the server already computes
for Telegram reminders, or a merge that applies only the reset fields instead of the whole row.
