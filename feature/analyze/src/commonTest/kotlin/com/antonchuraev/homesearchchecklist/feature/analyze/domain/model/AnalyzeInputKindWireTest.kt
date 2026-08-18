package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The join between the two AI-entry events, checked by the compiler's only available proxy: a test.
 *
 * `ai_entry_tapped` sends `AnalyzeInputKind.wire`; `ai_analyze_started` sends
 * `state.selectedInputType?.name?.lowercase()` — the [InputDataType] name — and has done so since
 * before [AnalyzeInputKind] existed. The funnel "reached for Analyze → started an analysis" is
 * segmented by `input_type` on BOTH events, so the two spellings have to be the same string or the
 * funnel joins `link` against `web_link` and reads as zero while each event looks healthy alone.
 *
 * `AiEntry.kt` states this rule in a KDoc ("Keep `wire` in lockstep with
 * `InputDataType.name.lowercase()`"). This project has already paid for trusting that shape of
 * comment: a comment saying "mirrors X" is not checked by the compiler, so it is a smell, not
 * documentation. This test is the check.
 *
 * Loop over `entries`, never a hand-written list: a kind added later is covered the day it lands,
 * and the mapping it needs is already forced to exist by [toInputDataType]'s exhaustive `when`.
 */
class AnalyzeInputKindWireTest {

    @Test
    fun everyKindsWireValueMatchesItsInputDataTypeName() {
        AnalyzeInputKind.entries.forEach { kind ->
            assertEquals(
                kind.toInputDataType().name.lowercase(),
                kind.wire,
                "$kind: ai_entry_tapped sends `${kind.wire}` while ai_analyze_started sends " +
                    "`${kind.toInputDataType().name.lowercase()}` — the cross-event funnel on " +
                    "input_type cannot join these and would read as zero",
            )
        }
    }
}
