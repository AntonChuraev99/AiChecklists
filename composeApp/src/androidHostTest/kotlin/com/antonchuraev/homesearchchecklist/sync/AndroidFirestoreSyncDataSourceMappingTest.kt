package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.ChecklistSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FillSyncData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Off-device round-trip over the Android data source's hand-written Firestore mapping
 * (`toMap()` → wire → `toChecklistSyncData()`).
 *
 * Why this exists: the wasmJs data source serializes `ChecklistSyncData.serializer()`, so a new
 * sync field reaches the cloud there for free. Android maps every field BY HAND in two places, and
 * forgetting one compiles perfectly while silently dropping the field — it never leaves the device,
 * and the next pull (LWW `remote.updatedAt > local.updatedAt` → full row overwrite) resets the
 * local value to its default. `reminderFullScreen` was added to the model + repository in 1.17.16
 * and to neither map, which is the regression these tests pin down.
 */
class AndroidFirestoreSyncDataSourceMappingTest {

    /**
     * Models the one transformation the real SDK applies that an in-memory map does not: Firestore
     * has a single 64-bit integer type, so every `Int` written by `toMap()` (position,
     * repeatOccurrenceCount, repeatTimeOfDayMinutes) comes back as a `Long` — which is exactly what
     * the read side casts to (`as? Long`). Round-tripping the raw map would leave those as `Int`,
     * make the casts fail, and have the test disagree with production in BOTH directions. Nested
     * fill maps go through the same conversion, as they do on the wire.
     */
    private fun Map<String, Any?>.throughFirestoreWire(): Map<String, Any?> =
        mapValues { (_, value) ->
            when (value) {
                is Int -> value.toLong()
                is List<*> -> value.map { element ->
                    @Suppress("UNCHECKED_CAST")
                    if (element is Map<*, *>) (element as Map<String, Any?>).throughFirestoreWire() else element
                }
                else -> value
            }
        }

    /** Every field set to a NON-default value, so a dropped field cannot hide behind its default. */
    private fun fullyPopulated() = ChecklistSyncData(
        cloudId = "checklist-cloud-1",
        name = "Trip packing",
        itemsJson = """[{"text":"Passport"}]""",
        reminderAt = 1_700_000_000_000L,
        repeatRule = """{"type":"Daily"}""",
        repeatTimeOfDayMinutes = 540,
        repeatNextAt = 1_700_086_400_000L,
        repeatOccurrenceCount = 7,
        reminderFullScreen = true,
        separateCompleted = true,
        position = 3,
        autoDeleteCompleted = true,
        viewMode = "Weekly",
        foldersEnabled = true,
        updatedAt = 1_700_000_999_000L,
        isDeleted = true,
        isInbox = true,
        fills = listOf(
            FillSyncData(
                cloudId = "fill-cloud-1",
                name = "Sunday run",
                itemsJson = """[{"text":"Passport","checked":true}]""",
                coverImagePath = "/local/cover.jpg",
                createdAt = 1_699_000_000_000L,
                isDefault = true,
                updatedAt = 1_700_000_111_000L,
                isDeleted = true,
            ),
        ),
    )

    @Test
    fun checklistSyncData_roundTripThroughFirestoreMap_preservesEveryField() {
        val original = fullyPopulated()

        val restored = original.toMap()
            .throughFirestoreWire()
            .toChecklistSyncData(documentId = original.cloudId)

        // Whole-object equality: this assertion keeps holding for fields that do not exist yet, so
        // the next sync field added without touching AndroidFirestoreSyncDataSource fails here
        // instead of in production.
        assertEquals(
            original,
            restored,
            "every ChecklistSyncData field must survive the Android toMap()/toChecklistSyncData() " +
                "round-trip — a missing key silently drops the field on Android only",
        )
    }

    @Test
    fun checklistSyncData_toMap_writesReminderFullScreen() {
        // The write half of the 1.17.16 regression: absent from toMap(), the user's full-screen
        // reminder opt-in never reaches Firestore at all.
        val map = fullyPopulated().toMap()

        assertTrue(
            "reminderFullScreen" in map,
            "toMap() must write reminderFullScreen, otherwise the opt-in never leaves the device",
        )
        assertEquals(true, map["reminderFullScreen"])
    }

    @Test
    fun checklistSyncData_toChecklistSyncData_readsReminderFullScreen() {
        // The read half: absent from the reader, a cloud document carrying reminderFullScreen = true
        // is parsed as false, and the LWW merge then overwrites the correct local row with it.
        val cloudDocument = fullyPopulated().toMap().throughFirestoreWire()

        val restored = cloudDocument.toChecklistSyncData(documentId = "checklist-cloud-1")

        assertTrue(
            restored.reminderFullScreen,
            "a cloud document with reminderFullScreen = true must parse back as true, else the " +
                "pull merge wipes the flag on every other device",
        )
    }

    @Test
    fun checklistSyncData_toMap_writesIsInbox() {
        // Write half for the v2 system Inbox flag: absent from toMap(), the marker never reaches
        // Firestore, so a second Android device pulls the row as an ordinary checklist and creates
        // its OWN Inbox — the user ends up with two.
        val map = fullyPopulated().toMap()

        assertTrue(
            "isInbox" in map,
            "toMap() must write isInbox, otherwise the system-Inbox marker never leaves the device",
        )
        assertEquals(true, map["isInbox"])
    }

    @Test
    fun checklistSyncData_toChecklistSyncData_readsIsInbox() {
        // Read half: parsed as false, the LWW merge would demote the local Inbox to a normal
        // checklist, which then shows up in the Projects list and eats a free-tier slot.
        val cloudDocument = fullyPopulated().toMap().throughFirestoreWire()

        val restored = cloudDocument.toChecklistSyncData(documentId = "checklist-cloud-1")

        assertTrue(
            restored.isInbox,
            "a cloud document with isInbox = true must parse back as true, else the pull merge " +
                "demotes the system Inbox into an ordinary project",
        )
    }

    @Test
    fun checklistSyncData_toChecklistSyncData_defaultsIsInboxToFalse_whenKeyAbsent() {
        // Documents written before this field existed (and by the MCP worker) carry no isInbox key.
        // They must decode as a plain project, not blow up — the local write-once merge guard in
        // SyncRepositoryImpl is what protects an existing local Inbox from such a document.
        val legacyDocument = fullyPopulated().toMap().throughFirestoreWire() - "isInbox"

        val restored = legacyDocument.toChecklistSyncData(documentId = "checklist-cloud-1")

        assertEquals(false, restored.isInbox)
    }

    @Test
    fun checklistSyncData_roundTrip_preservesEmbeddedFillFields() {
        val original = fullyPopulated()

        val restored = original.toMap()
            .throughFirestoreWire()
            .toChecklistSyncData(documentId = original.cloudId)

        assertEquals(
            original.fills,
            restored.fills,
            "fills are embedded in the checklist document — every FillSyncData field must survive too",
        )
    }
}
