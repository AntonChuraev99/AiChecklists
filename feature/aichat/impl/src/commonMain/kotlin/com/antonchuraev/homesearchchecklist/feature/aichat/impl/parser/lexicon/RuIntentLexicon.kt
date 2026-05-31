package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon

/**
 * Russian keyword sets per intent for [LocalIntentRouterImpl].
 *
 * All values are lowercase. Matching code lowercases input before comparison.
 * Phrases are sorted longest-first during matching to avoid substring shadowing.
 */
internal object RuIntentLexicon {

    // ─── CreateItem ───────────────────────────────────────────────────────────
    // "добавь молоко в покупки", "запиши молоко", "купи молоко", "закинь в дела", "кинь", "вбей"
    val createItem: Set<String> = setOf(
        "добавить",
        "добавь",
        "записать",
        "запиши",
        "купить",
        "купи",
        "положи",
        "поставь",
        "закинь",
        "кинь",
        "вбей",
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

    // ─── AttachToItem ─────────────────────────────────────────────────────────
    // «прикрепи это к молоко в покупках», «приложи файл к яйцам», «вложи к молоку», «прикинь к»
    val attachToItem: Set<String> = setOf(
        "прикрепить к",
        "прикрепи к",
        "прикрепи это к",
        "приложить к",
        "приложи к",
        "приложи это к",
        "вложить к",
        "вложи к",
        "прикинуть к",
        "прикинь к",
        "добавить файл к",
        "добавь файл к",
        "добавь к",
        "прикрепить",
        "прикрепи",
    )

    // ─── Hint prepositions (for ChecklistHintExtractor) ───────────────────────
    val hintPrepositions: Set<String> = setOf("в", "к", "для", "из", "со", "с")

    // ─── Referential payloads (context-dependent confirmations) ───────────────
    // Bare quantifiers/pronouns that point at previously-suggested items rather than
    // naming a real one. When a command verb's object reduces to only these, Layer 1
    // cannot resolve it locally and must escalate to Layer 3 (which has the chat
    // history). Prevents «да добавь все» → CreateItem("все").
    val referentialPayloads: Set<String> = setOf(
        "все", "всё", "их", "это", "этого", "всё это", "все это", "вот это",
    )

    // Completion markers stripped from the tail of a payload before the referential
    // check, so «отметь все выполнено» still reduces to a bare referent.
    val completionMarkers: Set<String> = setOf("выполнено", "сделано", "готово")
}
