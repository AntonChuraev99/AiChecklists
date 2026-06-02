package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM regression guards for [gistiDefaultPromptChips] — no Compose / Robolectric needed,
 * so they run fast on the host JVM with the default JUnit runner.
 *
 * These lock the home prompt-chip set: the flagship "✨ Create with AI" chip (the dedicated
 * entry to conversational AI checklist creation) must stay FIRST, and the full action order
 * must not drift silently. They guard the [GistiQuickAction.CREATE_WITH_AI] wiring added when
 * the create-with-AI affordance moved from a primary button to a prompt chip.
 */
class GistiPromptChipsFactoryTest {

    @Test
    fun gistiDefaultPromptChips_firstChip_isCreateWithAi() {
        assertEquals(
            GistiQuickAction.CREATE_WITH_AI,
            gistiDefaultPromptChips().first().action,
        )
    }

    @Test
    fun gistiDefaultPromptChips_actionOrder_isStable() {
        assertEquals(
            listOf(
                GistiQuickAction.CREATE_WITH_AI,
                GistiQuickAction.PHOTO,
                GistiQuickAction.REMIND,
                GistiQuickAction.LINK,
                GistiQuickAction.PLAN_DAY,
            ),
            gistiDefaultPromptChips().map { it.action },
        )
    }

    @Test
    fun gistiDefaultPromptChips_createWithAiChip_usesLabelAndSparkleEmoji() {
        val chip = gistiDefaultPromptChips(createAiLabel = "Create with AI")
            .first { it.action == GistiQuickAction.CREATE_WITH_AI }
        assertEquals("Create with AI", chip.label)
        assertEquals("✨", chip.emoji)
    }
}
