# Retention Local Pushes — Phase 4+5 (android-platform-expert scratch, 2026-07-03)

Local AlarmManager retention pushes: behavioral-timing + streak-save (Ph4), overdue + digest (Ph5).

## Design (locked)
- ONE daily retention alarm (req-code -1_000_001, action ACTION_RETENTION_DAILY) at resolved hour
  → evaluates streak_save > overdue, picks ONE. Reschedules self +1d (re-resolve hour).
- ONE weekly digest alarm (req-code -1_000_002, action ACTION_RETENTION_DIGEST) Sunday@hour.
  Reschedules self +7d. channel=promo.
- Shared daily cap by local date (RetentionPrefs) → first-wins, <=1/day (residual race <=2/day OK).
- Behavioral hour = argmax(24-slot histogram in DataStore); fixed arm = 19. RC push_timing_arm.
- Sticky user-property push_ab_arm=arm (guarded), event param push_ab_experiment=timing.
- Show event = Push.RECEIVED (push_type local-exclusive → separable). Tap = Push.OPENED emitted by
  EXISTING MainActivity path (writeExtras marks isPushIntent → suppresses reminder_notification_tapped).
- Streak: recurring sched, repeatNextAt-now in (0,26h] (daily-ish), default fill has unchecked. Deep-link.
- Overdue: default fill unchecked>0 AND fill.updatedAt < now-24h (abandoned in-progress). Pick max-unchecked. Deep-link.
- Digest: overdue count + active checklists, honest (no fake weekly-done; no checkedAt field exists).

## ANTI-REGRESSION guarantee
User-set reminders untouched: new code only ADDS RetentionPushReceiver + 2 new alarm req-codes
(-1_000_001/-1_000_002, negative → cannot collide with existing 100k/200k/300k positive ranges).
ReminderScheduler/ReminderReceiver/user reminder scheduling NOT modified. Behavioral timing only
sets the trigger time of the 2 NEW auto-push alarms; never reads/writes user reminder times.

## Files (ALL APPLIED)
- [x] core/remoteconfig/api RemoteConfigKeys.kt — PUSH_TIMING_ARM + default
- [x] core/remoteconfig/impl/androidMain FirebaseRemoteConfigProvider.kt — added key to defaults map
- [x] core/common/api AnalyticsEvents.kt — object LocalPushType
- [x] composeApp/androidMain retention/RetentionPrefs.kt (NEW)
- [x] composeApp/androidMain retention/PushTimingResolver.kt (NEW)
- [x] composeApp/androidMain retention/RetentionPushScheduler.kt (NEW)
- [x] composeApp/androidMain retention/RetentionPushReceiver.kt (NEW)
- [x] composeApp/androidMain res/values/strings.xml — retention strings (EN only)
- [x] composeApp/androidMain di/PlatformModule.android.kt — DI bindings
- [x] composeApp/androidMain GistiApplication.kt — scheduleRetentionPushes + cold hour
- [x] composeApp/androidMain MainActivity.kt — onResume record hour
- [x] composeApp/androidMain notification/BootCompletedReceiver.kt — retention recovery (step 5)
- [x] androidApp/src/main/AndroidManifest.xml — <receiver RetentionPushReceiver>

Verified: platformModule() ⊂ appModule; AppDatastore/RemoteConfigProvider/AnalyticsTracker/AppLogger
all pre-registered singletons; checklists flow excludes isDeleted. NOT built (main compiles).

STATUS: OK — implementation complete, compilation pending (main agent).
