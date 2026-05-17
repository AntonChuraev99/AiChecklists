package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon

/**
 * Russian keyword sets per intent for [LocalIntentRouterImpl].
 *
 * All values are lowercase. Matching code lowercases input before comparison.
 * Phrases are sorted longest-first during matching to avoid substring shadowing.
 */
internal object RuIntentLexicon {

    // ─── CreateItem ───────────────────────────────────────────────────────────
    // "добавь молоко в покупки", "запиши молоко", "купи молоко"
    val createItem: Set<String> = setOf(
        "добавить",
        "добавь",
        "записать",
        "запиши",
        "купить",
        "купи",
        "положи",
        "поставь",
    )

    // ─── DeleteItem ───────────────────────────────────────────────────────────
    // "убери молоко", "удали первый пункт", "вычеркни молоко"
    val deleteItem: Set<String> = setOf(
        "удалить",
        "удали",
        "убрать",
        "убери",
        "вычеркнуть",
        "вычеркни",
        "выкинуть",
        "выкинь",
        "стереть",
        "сотри",
    )

    // ─── CompleteItem ─────────────────────────────────────────────────────────
    // "отметь молоко выполненным", "сделал", "купил молоко"
    val completeItem: Set<String> = setOf(
        "отметить",
        "отметь",
        "выполнить",
        "выполнил",
        "выполнено",
        "сделать",
        "сделано",
        "сделал",
        "закрыть",
        "закрой",
        "купил",
        "галочку",
    )

    // ─── SetReminder ──────────────────────────────────────────────────────────
    // "напомни мне завтра в 9", "поставь напоминание"
    val setReminder: Set<String> = setOf(
        "напомнить",
        "напомни",
        "напоминание",
        "напомни мне",
        "ремайнд",
        "напомни о",
        "поставь напоминание",
        "создай напоминание",
    )

    // ─── FindItems ────────────────────────────────────────────────────────────
    // "найди молоко", "где молоко", "покажи список покупок"
    val findItems: Set<String> = setOf(
        "найти",
        "найди",
        "поиск",
        "ищи",
        "искать",
        "показать",
        "покажи",
        "где",
        "найдите",
    )

    // ─── CreateChecklist ──────────────────────────────────────────────────────
    // "новый список покупок", "создай список для поездки", "создай в апки сделать ..."
    val createChecklist: Set<String> = setOf(
        // Single-word broad triggers — match anything starting with these
        "создай", "создать", "новый",
        // Multi-word specific triggers (kept for precision when present)
        "новый список",
        "создать список",
        "создай список",
        "список для",
        "новый чеклист",
        "создать чеклист",
        "создай чеклист",
    )

    // ─── MoveReminders ────────────────────────────────────────────────────────
    // "перенеси все напоминания с завтра на послезавтра", "сдвинь напоминания"
    val moveReminders: Set<String> = setOf(
        "перенести",
        "перенеси",
        "сдвинуть",
        "сдвинь",
        "переставить",
        "переставь",
        "перепланировать",
        "перенести все напоминания",
    )

    // ─── Hint prepositions (for ChecklistHintExtractor) ───────────────────────
    val hintPrepositions: Set<String> = setOf("в", "к", "для", "из", "со", "с")
}
