# Plan: Weekly Mode — Per-Week Completion Log

**Date:** 2026-05-05
**Status:** Draft / parked — user wants to think about it
**Branch:** TBD (likely `feat/weekly-per-week-completion`)
**Related:** `feat/weekly-mode` (commit `a91b636d` shipped initial weekly mode)

---

## Problem

Weekly mode currently uses `ChecklistFillItem.checked: Boolean` as the completion flag.
This is a **single global flag for the lifetime of the item**. As a result:

1. User checks a Monday task on May 4 → the flag stays `true` forever.
2. Next Monday (May 11), the same item appears already checked. There is no
   notion of "completed *this* week vs. *last* week".
3. To make a recurring weekly plan honest, we would need a scheduled job that
   wipes `checked` every Monday at 00:00. That job would lose all history and
   break user trust ("I did 5/7 last week — where did it go?").

This is the core mismatch between Gisti's data model (item-level boolean) and
the UX promise of a recurring weekly planner. Every serious habit-tracker
(Habitify, Loop, TickTick→Habits) and weekly-planner (Sunsama, Akiflow) solves
this with a per-date completion log.

## Goal

Replace the `checked` flag (for weekly-mode items) with a per-week journal so:
- Each ISO week starts with all items unchecked, automatically and without a scheduler.
- Past weeks remain queryable for streaks, history, statistics.
- "Overdue" filter becomes truthful: "no completion record for THIS week AND weekday < today".

## Out of scope

- Standard mode unchanged (keeps `ChecklistFillItem.checked` as one-shot done).
- No daily reset (only weekly).
- No habit "skip" semantics (Habitify has "skipped vs. missed" — not for v1).
- No carry-over of overdue tasks to today (kept as static "Overdue" section).

## Data model change

### New table

```kotlin
@Entity(
    tableName = "weekly_completions",
    primaryKeys = ["fillId", "itemId", "weekStart"],
    indices = [
        Index(value = ["fillId", "weekStart"]),
        Index(value = ["itemId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChecklistFillEntity::class,
            parentColumns = ["id"],
            childColumns = ["fillId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class WeeklyItemCompletionEntity(
    val fillId: Long,
    val itemId: String,           // matches ChecklistFillItem.id
    val weekStart: Long,          // millis of ISO Monday 00:00 in user's TZ at write time
    val completedAt: Long,        // when toggle happened
)
```

### Model exposure

```kotlin
// Domain
data class WeeklyItemCompletion(
    val itemId: String,
    val weekStart: Long,
    val completedAt: Long,
)

// Repository
interface ChecklistRepository {
    fun observeCompletionsForWeek(fillId: Long, weekStart: Long): Flow<Set<String>>
    suspend fun setCompletion(fillId: Long, itemId: String, weekStart: Long, completed: Boolean)
}
```

UI receives `Set<String>` of `itemId`s completed this week. Render: `item.id in completedThisWeek` → checkbox checked.

### Time-zone & week-start semantics

- `weekStart` = millis of `LocalDate(today, ISO).with(MONDAY).atStartOfDay(zone).toInstant()`.
- Computed in user's current TZ at write time. Stored as absolute millis, not date string, so DST and TZ changes don't break equality.
- All queries compute `currentWeekStart` once per ViewModel session (or update via `LocalDate.now()` Flow to handle midnight rollover while app is open).

## Migration (Room 10 → 11)

1. Create `weekly_completions` table.
2. **Backfill rule**: for every `ChecklistFillItem` where `weekday != null` and `checked == true`, insert one row with `weekStart = current ISO Monday millis` and `completedAt = now()`. This way users who shipped to weekly mode and already checked stuff don't see their checks vanish on first launch of the new build.
3. Remove `checked = false` writes from weekly items going forward (still write for standard mode). Or: deprecate `checked` for weekly items entirely — set to `false` after backfill, never read it for weekly.

Migration code outline:

```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE weekly_completions (
                fillId INTEGER NOT NULL,
                itemId TEXT NOT NULL,
                weekStart INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                PRIMARY KEY (fillId, itemId, weekStart),
                FOREIGN KEY (fillId) REFERENCES checklist_fills(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_completions_fill_week ON weekly_completions(fillId, weekStart)")
        db.execSQL("CREATE INDEX idx_completions_item ON weekly_completions(itemId)")

        // Backfill — handled in Kotlin after migration runs (read fills, write completions).
        // Kept out of SQL because items live inside JSON-serialized `items` column on the fill row.
    }
}
```

The JSON-encoded items in the existing schema make pure-SQL backfill awkward.
Plan: run a Kotlin one-shot backfill in `ChecklistRepository.init()` that
checks a `weekly_completions_backfill_done` DataStore flag and migrates the
data once.

## UI changes

### ViewModel

`ChecklistDetailViewModel` exposes `completedThisWeek: Set<String>` as part of `Content` state, sourced from `repository.observeCompletionsForWeek(fillId, weekStart)`.

`OnItemCheckedChange(itemId, checked)` for weekly mode → `repository.setCompletion(...)` (insert if checked=true, delete if checked=false). Skip the existing `withChecked` flow entirely.

### Composables

`WeeklyChecklistDetailContent` reads `state.completedThisWeek`:

```kotlin
val isItemCompleted: (String) -> Boolean = { it in state.completedThisWeek }

ChecklistItemCard(
    item = item.copy(checked = isItemCompleted(item.id)),  // or a wrapper view-model
    onCheckedChange = { ... },
    ...
)
```

`isOverdue` predicate updates to use the per-week set:

```kotlin
internal fun isOverdue(
    item: ChecklistFillItem,
    todayWeekday: Int,
    completedThisWeek: Set<String>,
): Boolean {
    if (item.id in completedThisWeek) return false
    val weekday = item.weekday ?: return false
    return weekday < todayWeekday
}
```

## Free-tier extras (cheap wins)

Once the journal exists, two small features become trivial:

- **Streak counter** in the weekly checklist header: `COUNT(DISTINCT weekStart) WHERE itemId=X` over the last N consecutive weeks where every weekday slot was completed.
- **Weekly recap** card on Sunday evening: "This week: 5/7 days fully done". Push notification potential.

Defer to a follow-up.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Migration backfill races with first-screen render | Block startup loading screen until DataStore flag flips. Worst case: a 100ms loader on first launch after upgrade. |
| Time-zone change mid-week (user travels) | Compute `weekStart` strictly at write time in current TZ. UI re-derives `weekStart` on resume. Worst case: user sees "carried" item from old TZ as still-uncompleted in new TZ; acceptable. |
| Standard-mode `checked` field divergence | Keep `checked` for standard mode. Document in `Checklist.kt`: "weekly items use weekly_completions table; checked field is unused for them." |
| Bloat over years (52 weeks × N items × M years) | Tiny. 7 items × 52 weeks × 5 years × ~50 bytes/row = ~92 KB. No-op. |
| Sync conflict if cloud sync ships later | Operation is idempotent: `INSERT OR REPLACE` by primary key. Latest `completedAt` wins on tie. |

## Phasing

1. **Phase A — schema + write path** (1 day)
   - Add Room entity + migration + DAO.
   - Wire `setCompletion` in repository.
   - Backfill job + DataStore guard flag.
2. **Phase B — read path + UI** (1 day)
   - `observeCompletionsForWeek` Flow, plumb through ViewModel.
   - `ChecklistItemCard` rendering uses `completedThisWeek`.
   - Overdue filter signature update.
3. **Phase C — polish** (0.5 day)
   - Edge cases: midnight rollover while app open (Flow re-emits when current `weekStart` changes — small ticker or onResume hook).
   - Tests: migration test, completion repository test, ViewModel state test.
4. **Phase D — streak / recap** (deferred, separate spec)

## Decision points to revisit before starting

- [ ] Keep `ChecklistFillItem.checked` for weekly items (deprecated but readable for safety) **vs** strip from JSON entirely. Trade-off: backwards readability of old fills vs. clean schema.
- [ ] Where to compute `weekStart` — single source of truth (repository) vs. inject `Clock` for testability.
- [ ] Whether to expose past-week views in this change (e.g. a horizontal pager for last 4 weeks) or punt to Phase D.

## Alternative considered

**A. Reset-on-Monday scheduler.** WorkManager periodic at Monday 00:00 setting `checked = false` on all weekly items.
- Pro: tiny diff, no migration.
- Con: loses all history, can fire late if device asleep, can fire twice if user changes TZ, breaks streak feature, no audit trail.
- Verdict: rejected. Hides data model debt instead of fixing it.

**B. Add `lastCompletedAt: Long?` to `ChecklistFillItem`.** Compute "completed this week" by checking if `lastCompletedAt` falls within current week.
- Pro: smaller change, no new table.
- Con: still single-instance per item, no history of multiple weeks, no streak. Just postpones the real fix.
- Verdict: rejected. Solves the immediate UX bug but blocks streak/recap features that compound on the journal.

## Why not now

User wants to think it over. The shipping ISO-order fix (also 2026-05-05) addresses
the most visible symptom ("Monday is at the bottom") without touching the data
model. Per-week log is the right architectural move but it's a bigger commit;
no need to bundle it with a UX polish PR.

---

When ready: branch off `feat/weekly-mode`, work through Phase A → C, ship behind
the existing weekly-mode flag (no new flag needed since the feature is already
gated by `ChecklistViewMode.Weekly`).
