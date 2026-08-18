---
title: "Cross-Platform Firestore Auto-Sync for Checklists via Google Account"
date: 2026-05-24
type: architecture
modules: [feature:checklist, feature:user, core:datastore, core:common, wasmJs, Android, iOS]
keywords: [firestore, sync, room, lte-write-wins, kmp, cross-platform, google-auth, bidirectional-sync, conflict-resolution]
project: gisti-checklists
---

# Cross-Platform Firestore Auto-Sync (Google Auth)

## Problem / Context

Users log in with Google account and want their checklists synchronized across Android, iOS, and Web. A checklist created or edited on phone should appear on web immediately, and changes made offline should sync when connectivity restored.

**Requirements:**
- Single source of truth (Room database locally, Firestore cloud)
- Real-time sync (within ~5 seconds)
- Offline-first (changes accumulate in PENDING status)
- Conflict handling (last-write-wins based on timestamp)
- Platform-agnostic architecture (Android Firebase SDK, wasmJs JS SDK, iOS stub)

## Solution

### 1. Data Model: Sync Fields in Room Entities

Both `ChecklistEntity` and `ChecklistFillEntity` gain 5 fields:

```kotlin
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    
    // Sync fields (v15)
    val cloudId: String?,               // Firestore doc ID (null until first sync)
    val userId: String?,                // Owner Firebase UID
    val updatedAt: Long,                // Milliseconds UTC (for LWW)
    val syncStatus: String,             // SYNCED | PENDING | CONFLICT | FAILED (enum)
    val isDeleted: Boolean = false,     // Soft delete for sync tracking
    
    // Original fields
    val createdAt: Long,
    val position: Int,
    val hasReminder: Boolean,
    ...
)
```

**Why this design:**
- `cloudId` tracks Firestore document ID (null = new local checklist, not yet pushed)
- `userId` enables per-user filtering (Firestore rules + repository queries)
- `updatedAt` drives LWW conflict resolution (no complex 3-way merge)
- `syncStatus` visible to UI for sync-indicator, also state-machine for background service
- `isDeleted` allows soft-delete propagation (important for sync: delete locally → PENDING → Firestore rules enforce owner can delete)

### 2. Platform-Specific FirestoreSyncDataSource

Common interface, three implementations:

```kotlin
// commonMain
expect interface FirestoreSyncDataSource {
    fun getChecklists(userId: String): Flow<List<ChecklistSyncData>>
    suspend fun updateChecklist(data: ChecklistSyncData)
    suspend fun deleteChecklist(cloudId: String)
}

// androidMain
class AndroidFirestoreSyncDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : FirestoreSyncDataSource {
    override fun getChecklists(userId: String): Flow<List<ChecklistSyncData>> =
        callbackFlow {
            val listener = firestore.collection("checklists")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else {
                        trySend(snapshot?.toObjects<ChecklistSyncData>() ?: emptyList())
                    }
                }
            awaitClose { listener.remove() }
        }
    // updateChecklist, deleteChecklist via direct Firebase API calls
}

// wasmJsMain
class WasmFirestoreSyncDataSource : FirestoreSyncDataSource {
    override fun getChecklists(userId: String): Flow<List<ChecklistSyncData>> =
        flow {
            val db = js("globalThis.Firestore")
            val unsubscribe = db.onSnapshot(
                query = db.query(db.collection("checklists"), where("userId", "==", userId)),
                onNext = { snapshot ->
                    val docs = snapshot.docs.map { it.data() }
                    emit(docs)
                }
            )
            awaitClose { unsubscribe() }
        }
    // updateChecklist via batchWrite (see JS bridge section below)
}

// iosMain
class IosFirestoreSyncDataSource : FirestoreSyncDataSource {
    // Stub: returns empty flow (iOS local-only until platform strategy determined)
    override fun getChecklists(userId: String): Flow<List<ChecklistSyncData>> =
        flowOf(emptyList())
}
```

**Why expect/actual over generic implementation:**
- Each platform's SDK differs significantly (Firestore Android, JS, Swift native)
- Common code cannot abstract over platform-specific listener patterns (Kotlin callback vs JS Promise vs Swift delegate)
- Thin interface (3 methods) lets each platform implement independently without back-references

### 3. Repository: Bidirectional Sync with LWW

```kotlin
class SyncRepositoryImpl(
    private val checklistDao: ChecklistDao,
    private val syncDataSource: FirestoreSyncDataSource,
    private val appLogger: AppLogger,
) : SyncRepository {
    
    fun getChecklistsWithSync(userId: String): Flow<List<Checklist>> {
        // Combine Room (local) + Firestore (cloud)
        return combine(
            checklistDao.observeAll(),
            syncDataSource.getChecklists(userId)
        ) { local, cloud ->
            // LWW merge: for each cloudId, take max(local.updatedAt, cloud.updatedAt)
            mergeWithLWW(local, cloud)
        }
    }
    
    private fun mergeWithLWW(
        local: List<ChecklistEntity>,
        cloud: List<ChecklistSyncData>
    ): List<Checklist> {
        val result = mutableListOf<Checklist>()
        val processed = mutableSetOf<String>()
        
        // Process local items
        for (item in local) {
            if (item.isDeleted && item.syncStatus == SyncStatus.PENDING) {
                // Soft-deleted locally, not synced yet → skip (will push delete)
                processed.add(item.id)
                continue
            }
            
            val cloudMatch = cloud.find { it.cloudId == item.cloudId }
            if (cloudMatch != null) {
                // Both exist → LWW by updatedAt
                val winner = if (item.updatedAt >= cloudMatch.updatedAt) {
                    item.toDomain()
                } else {
                    // Cloud is newer → update local Room
                    checklistDao.upsert(cloudMatch.toEntity())
                    cloudMatch.toDomain()
                }
                result.add(winner)
                processed.add(item.id)
            } else {
                // Only local → include
                result.add(item.toDomain())
                processed.add(item.id)
            }
        }
        
        // Process cloud items not in local
        for (item in cloud) {
            if (!processed.contains(item.cloudId)) {
                checklistDao.upsert(item.toEntity())
                result.add(item.toDomain())
            }
        }
        
        return result
    }
}
```

**LWW conflict resolution:**
- Each entity has `updatedAt: Long` (milliseconds UTC)
- When same `cloudId` exists in both Room and Firestore, compare timestamps
- Winner is max(local.updatedAt, cloud.updatedAt)
- If cloud is newer, local Room is updated immediately (reconciliation)
- If local is newer, it stays; sync service pushes to Firestore in background

**Bidirectional flow:**
1. User edits checklist on phone → Room updated → syncStatus = PENDING
2. SyncService polls Room for PENDING items, calls `syncDataSource.updateChecklist()`
3. Firestore updated with new timestamp
4. Real-time listener on all devices gets snapshot → Room updated
5. UI observes Room → recomposition shows latest state

### 4. Sync Status State Machine

```kotlin
enum class SyncStatus(val value: String) {
    SYNCED("synced"),           // Matches cloud, no pending changes
    PENDING("pending"),         // Local changes not yet pushed
    CONFLICT("conflict"),       // Local differs from cloud (user should resolve)
    FAILED("failed")            // Push to cloud failed (will retry)
}
```

Background service monitors Room PENDING items:
```kotlin
class SyncService(
    private val repository: SyncRepository,
    private val appDispatcher: DispatcherProvider,
) {
    fun startSync(userId: String) = scope.launch(appDispatcher.io) {
        repository.observePendingChecklists(userId).collect { pending ->
            for (checklist in pending) {
                try {
                    repository.pushChecklistToCloud(checklist)
                    repository.updateSyncStatus(checklist.id, SyncStatus.SYNCED)
                } catch (e: Exception) {
                    repository.updateSyncStatus(checklist.id, SyncStatus.FAILED)
                    appLogger.error("Sync failed for ${checklist.id}", e)
                    // Retry on next connectivity change (via WorkManager + NetworkCallback)
                }
            }
        }
    }
}
```

### 5. wasmJs JavaScript Bridge

Firestore JS SDK requires exact interfaces. Kotlin objects sent as JSON strings must be parsed:

**init.js.template:**
```javascript
globalThis.Firestore = {
  batchWrite: async (operations) => {
    const batch = db.batch();
    for (const op of operations) {
      // CRITICAL: op.data is JSON string from Kotlin, parse it
      const data = typeof op.data === 'string' ? JSON.parse(op.data) : op.data;
      const docRef = db.collection(op.collection).doc(op.docId);
      
      if (op.type === 'set') {
        batch.set(docRef, data, { merge: op.merge ?? false });
      } else if (op.type === 'update') {
        batch.update(docRef, data);
      } else if (op.type === 'delete') {
        batch.delete(docRef);
      }
    }
    await batch.commit();
  },
  
  polling: () => {
    // Fallback when onSnapshot unavailable (flaky connection, quota)
    setInterval(async () => {
      const snapshot = await db.collection('checklists')
        .where('userId', '==', globalThis.currentUserId)
        .get();
      globalThis.firestorePoller?.emit(snapshot.docs.map(d => d.data()));
    }, 30000); // Every 30 seconds
  }
};
```

**Why JSON.parse is required:**
- Kotlin serializes objects as JSON strings for JS bridge
- Firestore JS SDK methods (batch.set, batch.update) expect plain JS objects
- Without parse, SDK receives string like `"{"title":"My List"}"` and fails silently

### 6. Firestore Rules (Server-Side LWW Enforcement)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /checklists/{checklistId} {
      // Only owner can read/write
      allow read, write: if request.auth.uid == resource.data.userId;
      
      // Enforce LWW: incoming timestamp must be >= existing
      allow update: if request.resource.data.updatedAt >= resource.data.updatedAt;
      
      // New documents must have valid userId and updatedAt
      allow create: if request.resource.data.userId == request.auth.uid
                    && request.resource.data.updatedAt is timestamp;
    }
  }
}
```

**Why server-side LWW:**
- Client logic can be bypassed (malicious or buggy client sends old data)
- Server as source of truth: if two clients send conflicting updates, Firestore evaluates rule, rejects older timestamp
- Prevents data inconsistency from client bugs

### 7. UI: Sync Status Indicator

```kotlin
@Composable
fun MainScreen(
    state: MainScreenState,
    onIntent: (MainScreenIntent) -> Unit,
) {
    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.main_title),
                actions = {
                    // Sync indicator
                    when (state.syncStatus) {
                        SyncStatus.SYNCED -> {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        SyncStatus.PENDING -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                        SyncStatus.FAILED -> {
                            Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                        }
                        else -> {} // CONFLICT: not shown in AppBar, shown in sheet
                    }
                }
            )
        }
    ) {
        // Checklist list
    }
}
```

- SYNCED: green check (or hidden if no recent changes)
- PENDING: spinner (syncing in background)
- FAILED: red error icon (tap to retry)
- CONFLICT: not in AppBar, resolved in ItemDetailsSheet for each item

## Why This Approach

1. **Room = Ground Truth Locally**
   - Offline-first: all writes to Room first, even before Firestore
   - PENDING status flags unsync'd state
   - No race between Room observer and Firestore listener

2. **Firestore = Authoritative Cloud Layer**
   - Single document per checklist (no sharding)
   - Real-time snapshots via listeners
   - Server-enforced LWW rules prevent conflicts

3. **LWW (Last-Write-Wins) Chosen Over CRDT**
   - Simple: single timestamp comparison
   - No complex merge logic
   - Suitable for checklist domain (mostly independent items)
   - Trade-off: concurrent edits on different fields on same checklist → last write wins (acceptable for this product)
   - CRDT would be overkill for this scale/domain

4. **expect/actual for Platform APIs**
   - Android: native Firebase SDK is battle-tested, use it
   - wasmJs: JS SDK with globalThis bridge, no need for Kotlin wrapper
   - iOS: stub for now, full impl when platform strategy determined

5. **Real-Time + Polling Fallback**
   - Real-time via onSnapshot (fast, event-driven)
   - Polling every 30s (robust, catches edge cases)
   - Together they ensure sync within acceptable latency

## Related Files

- `feature/checklist/src/commonMain/data/db/ChecklistEntity.kt` — sync fields
- `feature/checklist/src/commonMain/data/sync/FirestoreSyncDataSource.kt` — interface
- `composeApp/src/androidMain/.../sync/AndroidFirestoreSyncDataSource.kt` — Android impl
- `composeApp/src/wasmJsMain/.../sync/WasmFirestoreSyncDataSource.kt` — wasmJs impl
- `composeApp/src/wasmJsMain/resources/init.js.template` — JS bridge
- `firestore.rules` — server rules
- `feature/checklist/src/commonMain/data/repository/ChecklistRepositoryImpl.kt` — merge logic
- `feature/home/src/commonMain/ui/MainScreen.kt` — sync indicator

## Implementation Notes

**Room migrations:** v14→v15 with nullable sync fields (defaults for existing rows)
**Firestore collections:** `/checklists/{userId}/{checklistId}` or flat `/checklists/{checklistId}` with userId field
**Timestamps:** Always use `System.currentTimeMillis()` before Room write, not Firestore server timestamps (client must control LWW)
**Auth flow:** Google Sign-In → Firebase Auth → fetch userId → pass to SyncRepositoryImpl → start sync service
**Offline handling:** NetworkCallback monitoring (Android) + polling (wasmJs) detects connectivity restore → retries FAILED items

## Alternatives Considered

1. **Client-side CRDT (e.g., Automerge):**
   - Merges concurrent edits on different fields
   - Complex integration with Room DAO
   - Overhead for simple checklist domain
   - **Rejected:** LWW sufficient, no need for full CRDT

2. **Firestore's Built-in Offline Persistence:**
   - Firestore SDK manages local cache + sync queue
   - Less control over Room schema and migrations
   - **Rejected:** Need explicit control over sync fields, migrations, conflict logic

3. **One-Way Sync (Server-Authoritative):**
   - Server fetches all data to client on login
   - Client changes always pushed immediately
   - Simpler implementation
   - **Rejected:** Offline-first experience requires local changes to be visible before sync
