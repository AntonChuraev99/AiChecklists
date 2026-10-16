package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.NEW_CHECKLIST_SEPARATE_COMPLETED
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the trap behind "new checklists separate completed items by default" (redesign B).
 *
 * The default for NEW lists is applied at the creation sites via [NEW_CHECKLIST_SEPARATE_COMPLETED].
 * The constructor / serializer default must stay `false`, because it is also what a payload written
 * before the field existed decodes to. If someone "simplifies" this by flipping the model default,
 * every legacy list would switch layout on its next read — these tests fail first.
 */
class SeparateCompletedLegacyDecodeTest {

    // Same configuration as the wasmJs Firestore data source (`WasmFirestoreSyncDataSource.jsonParser`).
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun syncDocumentWithoutSeparateCompleted_decodesAsFalse() {
        val legacy = """{"cloudId":"c1","name":"Old list","itemsJson":"[]","updatedAt":1}"""

        val decoded = json.decodeFromString(ChecklistSyncData.serializer(), legacy)

        assertEquals(false, decoded.separateCompleted)
    }

    @Test
    fun checklistPayloadWithoutSeparateCompleted_decodesAsFalse() {
        val legacy = """{"id":7,"name":"Old list","items":[]}"""

        val decoded = json.decodeFromString(Checklist.serializer(), legacy)

        assertEquals(false, decoded.separateCompleted)
    }

    @Test
    fun newChecklistDefault_isSeparateCompleted() {
        assertTrue(NEW_CHECKLIST_SEPARATE_COMPLETED)
        // ...while the bare constructor (= every decode path) keeps the legacy value.
        assertEquals(false, Checklist(name = "x", items = emptyList()).separateCompleted)
    }
}
