---
title: "Room 3.0 + Web SQLite NULL Handling — NPE on Corrupted OPFS Data"
date: 2026-07-20
type: bug-fix
modules: [feature/checklist, core/data]
keywords: [crash, database, web, null-pointer-exception, room3.0, sqlite, wasmjs, opfs, nullable-projection, robust-mapper, platform-quirk, bundled-driver, describeForLog, android]
project: checklists
---

# Room 3.0 + Web SQLite NULL Handling — NPE on Corrupted OPFS Data

## Problem / Context

**Symptom:** Web app (app.gisti-ai.com) crashes with `kotlin.NullPointerException` on Home screen for signed-in users after Google sign-in. Stack trace: `at ChecklistEntity.<init>` when reading checklists from Room database via `repository.checklists` StateFlow. All 4 initiation paths (ChatVM default-checklist-name, MainScreenVM user-properties/pending-sync/checklists-fetch) converge on this single query — one corrupted row blocks the entire app.

**Root Cause:** Platform-specific SQLite driver behavior divergence:
- **Host JVM** (BundledSQLiteDriver, used in tests + gradle tasks): coerces SQL `NULL` in TEXT columns to empty string `""` when returned via `cursor.getString()`.
- **Web** (browser SQLite OPFS driver): returns truthful `null` from `cursor.getString()` for SQL NULL.

When a single row in the OPFS database has `name = NULL` or `items = NULL`, the host JVM returns `""` (string zero), but the web platform returns actual `null`. The `ChecklistEntity` data class declares these fields as **non-null** (`String`, not `String?`):

```kotlin
data class ChecklistEntity(
  val id: String,
  val name: String,       // ← non-null declaration
  val items: String,      // ← non-null declaration
  ...
)
```

When Room's generated mapper tries to call `cursor.getString(nameIdx)` on a NULL value in the web environment, it throws NPE because the language expects `String` but gets `null`.

**Why only web?** The host JVM never exposes true NULL to the constructor (coerces to `""`), so the contract is respected in tests. A real browser environment reading real OPFS-persisted data encounters the true NULL and crashes.

**How did the row get corrupted?** Unknown root, likely:
- Edge-case in past sync reconciliation (null assign during LWW merge).
- Browser crash mid-write to OPFS.
- Legacy schema migration that lost data integrity.

The fix does not heal the row — that requires a separate LWW-pull from Firestore or explicit update query.

## Solution

**Three-part approach:** nullable-projection (DAO layer) → robust mapper (domain layer) → loud logging (observability).

### 1. Nullable Projection (DAO)

Define an intermediate data class that mirrors the query projection but declares nullable fields:

```kotlin
// feature/checklist/src/commonMain/kotlin/.../db/ChecklistRow.kt
data class ChecklistRow(
  @ColumnInfo("id") val id: String,
  @ColumnInfo("name") val name: String?,    // ← nullable read
  @ColumnInfo("items") val items: String?,  // ← nullable read
  @ColumnInfo("color_tag") val colorTag: String?,
  // ... other fields mirrored from ChecklistEntity
)
```

The DAO returns `ChecklistRow` (nullable) instead of `ChecklistEntity` (non-null):

```kotlin
@Dao
interface ChecklistDao {
  @Query("SELECT id, name, items, ... FROM checklist WHERE deleted = 0")
  fun observeChecklistRows(): Flow<List<ChecklistRow>>
}
```

### 2. Robust Mapper (Domain Layer)

Map `ChecklistRow` → `ChecklistEntity` with fallbacks and loud logging:

```kotlin
// feature/checklist/src/commonMain/kotlin/.../data/db/ChecklistRow.kt (extension function)
fun ChecklistRow.toChecklistSafe(): Checklist {
  val safeName = name ?: ""  // fallback to empty string
  val safeItemsList = try {
    if (items == null || items.isEmpty()) {
      emptyList()
    } else {
      parseJsonToList<ChecklistItem>(items)  // or your deserializer
    }
  } catch (e: Throwable) {
    AppLogger.error(TAG, "checklist_row_items_parse_failed id=$id items=$items", e)
    emptyList()
  }

  // Log even on success if fallback was taken
  if (name == null || items == null) {
    AppLogger.warning(
      TAG,
      "checklist_row_recovered id=$id name_null=${name == null} " +
      "items_null=${items == null} items_len=${items?.length ?: 0}",
      // No throwable — this is a recovery event, not an error
    )
  }

  return Checklist(
    id = id,
    name = safeName,
    items = safeItemsList,
    ...  // other fields
  )
}
```

Use in repository:

```kotlin
class ChecklistRepositoryImpl(private val dao: ChecklistDao, ...) : ChecklistRepository {
  override val checklists: StateFlow<List<Checklist>> = dao
    .observeChecklistRows()
    .map { rows -> rows.map { it.toChecklistSafe() } }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

### 3. Logging Strategy — `Throwable.describeForLog()`

Always use `Throwable.describeForLog()` for cross-platform logging (web console.error prints only message; stack is lost if null):

```kotlin
// core/common/impl/...AppLogger.kt
fun AppLogger.error(tag: String, message: String, throwable: Throwable?) {
  when {
    isAndroid -> {
      android.util.Log.e(tag, message, throwable)
      if (throwable != null) Crashlytics.recordException(throwable)
    }
    isWeb -> {
      console.error("[$tag] $message\n${throwable?.describeForLog() ?: ""}")
    }
  }
}

// Extension in Throwable (Kotlin stdlib, works cross-platform)
private fun Throwable.describeForLog(): String {
  val stack = stackTraceToString()  // full trace, works on web via Kotlin stdlib
  return "$this\n$stack"
}
```

## Why This Approach

1. **Nullable-projection is the first line of defense.** Don't pretend the read is safe if the storage can be corrupted. Use the type system to express that these fields may be null.

2. **Mapper takes the fallback burden.** The domain layer (ViewModel, UseCase) expects non-null Checklist — the mapper bridges the gap with sensible defaults (empty string, empty list) so callers don't have to.

3. **Loud logging for observability.** Even when recovery succeeds, log the event so we can:
   - Measure how often corruption occurs (Crashlytics analytics).
   - Correlate with user devices/versions (segment the issue).
   - Monitor when the Firestore sync heals the row (next pull, or explicit heal logic).

4. **No skip strategy.** Skipping corrupted rows (returning `emptyList` instead of mapping each row) breaks UX silently — a user's checklists vanish. The default approach (name="", items=[]) is visible (empty list appears in UI, user sees the damage and can report).

## Room 3.0 Migration (Breaking Change)

When upgrading from Room 3.0-alpha to stable, `@TypeConverter` annotation is renamed:

- **Old (alpha):** `@TypeConverter fun toJson(obj: T): String`
- **New (3.0.0+):** `@ColumnTypeConverter fun toJson(obj: T): String`

Update all converters in the codebase:

```kotlin
// feature/checklist/.../ChecklistItemConverters.kt
class ChecklistItemConverters {
  @ColumnTypeConverter  // was @TypeConverter
  fun fromJson(json: String): List<ChecklistItem> { ... }

  @ColumnTypeConverter  // was @TypeConverter
  fun toJson(items: List<ChecklistItem>): String { ... }
}
```

Update `@Database` annotation:

```kotlin
@Database(
  entities = [ChecklistEntity::class],
  version = 11,
)
@ColumnTypeConverters(ChecklistItemConverters::class)  // was @TypeConverters
abstract class ChecklistDatabase : RoomDatabase() { ... }
```

Schema hash remains stable (no destructive migration needed).

## Testing Strategy

### Unit Test — Mapper Handles NULL

```kotlin
// feature/checklist/src/commonTest/.../ChecklistRowRecoveryTest.kt
class ChecklistRowRecoveryTest {
  @Test
  fun nullName_mapsToEmptyString() {
    val row = ChecklistRow(id = "1", name = null, items = "[]", ...)
    val result = row.toChecklistSafe()
    assertEquals("", result.name)
  }

  @Test
  fun nullItems_mapsToEmptyList() {
    val row = ChecklistRow(id = "1", name = "Test", items = null, ...)
    val result = row.toChecklistSafe()
    assertEquals(emptyList(), result.items)
  }

  @Test
  fun malformedJson_items_fallsBackToEmptyList() {
    val row = ChecklistRow(id = "1", name = "Test", items = "{invalid", ...)
    val result = row.toChecklistSafe()
    assertEquals(emptyList(), result.items)
    // Verify AppLogger was called (mock or inspect)
  }
}
```

### Integration Test — Red-First (Host JVM)

Simulate corrupted OPFS data by manually inserting NULL into host SQLite:

```kotlin
// feature/checklist/src/androidHostTest/.../ChecklistCorruptRowRecoveryTest.kt
class ChecklistCorruptRowRecoveryTest {
  @Test
  fun corruptedRow_doesNotCrash_Home() = runTest {
    // Setup: insert a checklist with NULL name
    database.execSQL("INSERT INTO checklist (id, name, items, ...) VALUES ('bad-id', NULL, '[]', ...)")

    // Act: read via DAO
    val rows = dao.observeChecklistRows().first()

    // Assert: row is mapped safely (no NPE)
    assertEquals(1, rows.size)
    assertEquals("", rows[0].toChecklistSafe().name)
  }
}
```

### Smoke Test — Web (Browser)

Run wasmJsBrowserDevelopmentRun and sign in:

```
./gradlew composeApp:wasmJsBrowserDevelopmentRun
# Browser opens at localhost:9090
# 1. Sign in with Google
# 2. Home screen appears (checklists load)
# 3. Browser console clean (no TypeError, no Throwable.describeForLog errors unless intentional)
```

If the OPFS database contains corrupted rows, the browser console will show:

```
[Sync] checklist_row_recovered id=abc123 name_null=true items_null=false items_len=2
```

This confirms the mapper is catching NULL and logging the recovery.

## Platform-Specific Quirks

| Platform | NULL Behavior | Test Driver |
|---|---|---|
| **Host JVM (android tests)** | `cursor.getString()` → `""` (coerced) | BundledSQLiteDriver |
| **Web (browser)** | `cursor.getString()` → actual `null` | OPFS SQLite driver |
| **Android prod** | Uses system SQLite (rarely corrupts, Firestore sync recovers) | Device SQLite |

**Key lesson:** Unit tests on the host JVM are insufficient to prove web-platform fixes. Always smoke-test the web app in a real browser before prod deploy.

## Related Files

- `feature/checklist/src/commonMain/.../data/db/ChecklistRow.kt` — nullable-projection
- `feature/checklist/src/commonMain/.../data/repository/ChecklistRepositoryImpl.kt` — mapper in use
- `feature/checklist/src/commonTest/.../ChecklistRowRecoveryTest.kt` — unit tests
- `feature/checklist/src/androidHostTest/.../ChecklistCorruptRowRecoveryTest.kt` — integration red-first
- `gradle/libs.versions.toml` — Room 3.0.0, sqlite 2.7.0

## Deferred Work

- **Heal:** one-shot update `SET name = "" WHERE name IS NULL AND syncStatus != "SYNCED"` (optional, LWW-pull already fetches clean data from Firestore on next sync).
- **Auth-state audit:** Firestore `403 Permission denied` on user-properties fetch is a separate issue (not through this mapper).
