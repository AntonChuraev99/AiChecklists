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

    // ── item-create chips: what `selected` actually resolves to ──────────────
    //
    // The selected state is the ONE thing about these chips that is not visible in their label, and
    // it is carried twice over: as the blue fill and as the `selected` semantics property a screen
    // reader announces. These two tests pin the DATA half; the rendered/announced half is
    // `GistiSelectableChipRowSemanticsTest`.

    /**
     * The four reminder presets are single-select and the two property toggles are independent — so
     * naming one preset must mark exactly ONE chip, and must not disturb Important / Repeat.
     */
    @Test
    fun gistiItemCreatePromptChips_reminderPresets_areSingleSelect() {
        val chips = itemCreateChips(selectedReminder = GistiItemCreateAction.REMIND_TONIGHT)

        assertEquals(
            listOf(GistiItemCreateAction.REMIND_TONIGHT),
            chips.filter { it.selected }.map { it.action },
        )
    }

    /** Important and Repeat are independent toggles: both on, with no reminder chosen at all. */
    @Test
    fun gistiItemCreatePromptChips_propertyToggles_areIndependentOfTheReminder() {
        val chips = itemCreateChips(
            selectedReminder = null,
            importantSelected = true,
            repeatSelected = true,
        )

        assertEquals(
            listOf(GistiItemCreateAction.IMPORTANT, GistiItemCreateAction.REPEAT),
            chips.filter { it.selected }.map { it.action },
        )
    }

    private fun itemCreateChips(
        selectedReminder: GistiItemCreateAction?,
        importantSelected: Boolean = false,
        repeatSelected: Boolean = false,
    ) = gistiItemCreatePromptChips(
        in1HourLabel = "In 1 hour",
        tomorrowMorningLabel = "Tomorrow morning",
        tonightLabel = "Tonight",
        pickTimeLabel = "Pick time…",
        importantLabel = "Important",
        repeatLabel = "Repeat",
        selectedReminder = selectedReminder,
        importantSelected = importantSelected,
        repeatSelected = repeatSelected,
    )
}
