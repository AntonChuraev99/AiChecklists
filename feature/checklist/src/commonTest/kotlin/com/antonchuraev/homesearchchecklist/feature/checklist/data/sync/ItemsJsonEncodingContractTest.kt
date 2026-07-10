package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin↔TS byte-equality contract — the Kotlin side of the sync-encoding drift alarm.
 *
 * This test pins the checklist/fill `itemsJson` bytes produced by the REAL sync serializer
 * (`Json { ignoreUnknownKeys = true }` from `SyncRepositoryImpl.kt:53`, encoding via the same
 * `ListSerializer(...serializer())` as production) to hand-traced golden strings.
 *
 * It is the mirror of `mcp-server/src/encode.test.ts` (the TypeScript encoder used by the MCP
 * server writes the same Firestore `itemsJson`). The two golden constants are IDENTICAL across
 * the two files:
 *  - Kotlin: [goldenTemplateJson] / [goldenFillJson] here
 *  - TS:     GOLDEN_TEMPLATE_JSON / GOLDEN_FILL_JSON in encode.test.ts
 *
 * If a future dev adds/renames/reorders a serialized field on [ChecklistItem] or
 * [ChecklistFillItem] (or changes a default so a previously-omitted field now appears), THIS
 * test breaks FIRST. That is the signal that the TS encoder + its golden in encode.test.ts must
 * be updated in lockstep — otherwise the Kotlin app and the MCP server silently diverge on the
 * itemsJson bytes and sync corrupts. Do NOT weaken these assertions to make them pass: fix the
 * TS side to match, then update both goldens together.
 *
 * Encoding rules exercised by the fixture (kotlinx defaults, `encodeDefaults=false`):
 *  - default-valued fields are OMITTED (`checked=false`/`priority=0` on template, `type=ITEM`,
 *    all null reminder fields, `attachments=[]`);
 *  - [ChecklistItem.type] appears only on the FOLDER node (non-default), by enum constant name;
 *  - [ChecklistFillItem.checked] is ALWAYS present (it has no default) even when false;
 *  - field order follows primary-constructor declaration order.
 */
class ItemsJsonEncodingContractTest {

    /** Exact serializer the sync layer uses to build itemsJson (SyncRepositoryImpl.kt:53). */
    private val json = Json { ignoreUnknownKeys = true }

    // ── "Trip" fixture ids (identical to the constants in encode.test.ts) ──
    private val bags = "1720000000001_1000"
    private val passport = "1720000000002_2000"
    private val charger = "1720000000003_3000"

    private val goldenTemplateJson =
        """[{"text":"Bags","id":"$bags","type":"FOLDER"},""" +
            """{"text":"Passport","id":"$passport","parentId":"$bags"},""" +
            """{"text":"Charger","id":"$charger","parentId":"$bags"}]"""

    private val goldenFillJson =
        """[{"text":"Bags","checked":false,"id":"1720000000010_5000","templateItemId":"$bags"},""" +
            """{"text":"Passport","checked":true,"id":"1720000000011_6000","templateItemId":"$passport"},""" +
            """{"text":"Charger","checked":false,"id":"1720000000012_7000","templateItemId":"$charger"}]"""

    @Test
    fun encodeTemplateItems_tripFolderTree_matchesTsGolden() {
        val template = listOf(
            ChecklistItem(text = "Bags", type = ChecklistNodeType.FOLDER).withId(bags),
            ChecklistItem(text = "Passport", parentId = bags).withId(passport),
            ChecklistItem(text = "Charger", parentId = bags).withId(charger),
        )

        val encoded = json.encodeToString(ListSerializer(ChecklistItem.serializer()), template)

        assertEquals(goldenTemplateJson, encoded)
    }

    @Test
    fun encodeFillItems_tripDefaultFill_matchesTsGolden() {
        val fill = listOf(
            fillItem(text = "Bags", checked = false, id = "1720000000010_5000", templateItemId = bags),
            fillItem(text = "Passport", checked = true, id = "1720000000011_6000", templateItemId = passport),
            fillItem(text = "Charger", checked = false, id = "1720000000012_7000", templateItemId = charger),
        )

        val encoded = json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), fill)

        assertEquals(goldenFillJson, encoded)
    }

    /**
     * Builds a [ChecklistFillItem] with an explicit fixed id.
     *
     * [ChecklistFillItem]'s id-taking primary constructor is private (@ConsistentCopyVisibility)
     * and its public constructor assigns a random `generateId()` — there is no `withId(...)` — so
     * a deterministic id can only be injected through the serializer. Decoding a minimal element
     * (only the non-default fields the fixture sets) is exactly the field set the sync layer
     * round-trips; the golden pins the ENCODE step, which is what would drift against the TS side.
     */
    private fun fillItem(
        text: String,
        checked: Boolean,
        id: String,
        templateItemId: String,
    ): ChecklistFillItem = json.decodeFromString(
        ChecklistFillItem.serializer(),
        """{"text":"$text","checked":$checked,"id":"$id","templateItemId":"$templateItemId"}""",
    )
}
