# Retention androidMain — scratch 2026-07-09

Status: OK — all edits applied, compiles-pending (main agent builds).

## Feature A — D0→D1 come-back nudge (applied)
- composeApp/src/androidMain/.../retention/RetentionPrefs.kt — comeback state machine (none/scheduled/fired) + isComebackArmed/isComebackPending/markComebackScheduled/markComebackFired/comebackChecklistId/comebackArmedAt/lastActiveMs
- retention/RetentionPushScheduler.kt — scheduleComeback / rescheduleComebackAt / cancelComeback; COMEBACK_REQUEST_CODE=-1_000_003; debug delay 20s vs 22h
- retention/RetentionPushReceiver.kt — ACTION_RETENTION_COMEBACK + handleComeback + findComeback + emitSkipped + notificationsEnabled; force debug path; COMEBACK_SKIPPED reasons
- GistiApplication.kt — scheduleComebackOnFirstChecklist() observes checklists, arms on 0→1
- notification/BootCompletedReceiver.kt — re-arm if isComebackPending
- composeApp/src/androidMain/res/values/strings.xml — retention_comeback_title + retention_comeback_body plural (Android res, no apostrophes)

## Feature B — widget analytics (applied)
- widget/data/WidgetStateManager.kt — markAdoptedIfNew dedup + clearWidget clears adopted key
- widget/config/WidgetConfigActivity.kt — Widget.ADDED on config confirm (dedup)
- widget/actions/OpenChecklistAction.kt — Widget.OPENED on tap

## adb (debug, emulator-5554)
- grant: adb -s emulator-5554 shell pm grant com.antonchuraev.aichecklists android.permission.POST_NOTIFICATIONS
- force show: adb -s emulator-5554 shell am broadcast -n com.antonchuraev.aichecklists/com.antonchuraev.homesearchchecklist.retention.RetentionPushReceiver -a com.antonchuraev.aichecklists.ACTION_RETENTION_COMEBACK --ez gisti.comeback.force true

## Deviation flagged
Strings put in androidMain res/values (Android res, synchronous R.plurals in receiver) NOT core/designsystem Compose Resources — mirrors existing retention_* strings; Compose Resources getPluralString is suspend + inconsistent with findStreakSave/findOverdue.
