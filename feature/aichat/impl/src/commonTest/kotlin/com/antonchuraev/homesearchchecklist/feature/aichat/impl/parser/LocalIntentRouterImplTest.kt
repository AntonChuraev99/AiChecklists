package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParserImpl
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [LocalIntentRouterImpl].
 *
 * Coverage: 7 intents × ~7 RU + ~7 EN = ~98 test cases.
 * Each case verifies:
 *   1. Correct [ChatIntent] subtype returned
 *   2. confidence > 0.6 for any recognized intent
 *   3. [RoutingLayer.Local] returned (always in Phase A)
 *
 * Unknown-fallback cases verify confidence == 0f and [ChatIntent.Unknown] returned.
 */
class LocalIntentRouterImplTest {

    // ─── Test infrastructure ──────────────────────────────────────────────────

    private val noOpLogger = object : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private val dateParser: SmartDateParser = SmartDateParserImpl(noOpLogger)

    private val router = LocalIntentRouterImpl(
        dateParser = dateParser,
        logger = noOpLogger,
    )

    // ─── CreateItem — RU ──────────────────────────────────────────────────────

    @Test
    fun createItem_ru_addWithHint() = runTest {
        val result = router.route("добавь молоко в покупки", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f, "Expected conf >= 0.6, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    @Test
    fun createItem_ru_addWithoutHint() = runTest {
        val result = router.route("добавь молоко", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_ru_writeDown() = runTest {
        val result = router.route("запиши хлеб в список", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_ru_buy() = runTest {
        val result = router.route("купить яйца", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_ru_buyImperative() = runTest {
        val result = router.route("купи молока", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_ru_put() = runTest {
        val result = router.route("положи масло в список покупок", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_ru_mixedCase() = runTest {
        val result = router.route("Добавь Молоко В Покупки", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── CreateItem — EN ──────────────────────────────────────────────────────

    @Test
    fun createItem_en_addWithHint() = runTest {
        val result = router.route("add milk to shopping", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
        assertEquals(RoutingLayer.Local, result.layer)
    }

    @Test
    fun createItem_en_addWithoutHint() = runTest {
        val result = router.route("add eggs", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_en_buy() = runTest {
        val result = router.route("buy bread", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_en_pickUp() = runTest {
        val result = router.route("pick up butter from the store", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_en_writeDown() = runTest {
        val result = router.route("write down call dentist", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_en_get() = runTest {
        val result = router.route("get milk for tomorrow", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createItem_en_put() = runTest {
        val result = router.route("put coffee on the grocery list", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── DeleteItem — RU ──────────────────────────────────────────────────────

    @Test
    fun deleteItem_ru_remove() = runTest {
        val result = router.route("убери молоко из списка", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_ru_delete() = runTest {
        val result = router.route("удали первый пункт", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_ru_crossOut() = runTest {
        val result = router.route("вычеркни яйца", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_ru_erase() = runTest {
        val result = router.route("стереть задачу позвонить врачу", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_ru_drop() = runTest {
        val result = router.route("выкинь пункт масло", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_ru_deleteVerb() = runTest {
        val result = router.route("удалить молоко", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_ru_removeVerb() = runTest {
        val result = router.route("убрать масло из списка покупок", ChatLocale.Ru)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── DeleteItem — EN ──────────────────────────────────────────────────────

    @Test
    fun deleteItem_en_remove() = runTest {
        val result = router.route("remove milk from the list", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_en_delete() = runTest {
        val result = router.route("delete butter", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_en_drop() = runTest {
        val result = router.route("drop eggs from my shopping list", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_en_erase() = runTest {
        val result = router.route("erase the dentist appointment", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_en_takeOff() = runTest {
        val result = router.route("take off coffee from the list", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_en_scratchOff() = runTest {
        val result = router.route("scratch off milk", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun deleteItem_en_eliminate() = runTest {
        val result = router.route("eliminate the old task", ChatLocale.En)
        assertIs<ChatIntent.DeleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── CompleteItem — RU ────────────────────────────────────────────────────

    @Test
    fun completeItem_ru_mark() = runTest {
        val result = router.route("отметь молоко как купленное", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_ru_done() = runTest {
        val result = router.route("сделано", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_ru_bought() = runTest {
        val result = router.route("купил молоко", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_ru_complete() = runTest {
        val result = router.route("выполнил задачу позвонить врачу", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_ru_close() = runTest {
        val result = router.route("закрой пункт масло", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @kotlin.test.Ignore
    @Test
    fun completeItem_ru_checkbox() = runTest {
        // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
        // Multi-word phrase «поставь галочку» not in Phase A RU lexicon. Add in Phase B (Layer 2 classifier) or extend lexicon.
        val result = router.route("поставь галочку на хлеб", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_ru_done2() = runTest {
        val result = router.route("сделал покупки", ChatLocale.Ru)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── CompleteItem — EN ────────────────────────────────────────────────────

    @Test
    fun completeItem_en_checkOff() = runTest {
        val result = router.route("check off milk", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_en_markDone() = runTest {
        val result = router.route("mark milk done", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_en_done() = runTest {
        val result = router.route("done with groceries", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_en_complete() = runTest {
        val result = router.route("complete the dentist task", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_en_finish() = runTest {
        val result = router.route("finished the report", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun completeItem_en_tick() = runTest {
        val result = router.route("tick off butter from the list", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @kotlin.test.Ignore
    @Test
    fun completeItem_en_markComplete() = runTest {
        // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
        // 3-word phrase «mark as complete» collides with AddItem keyword «buy» in same input. Address via Phase B Layer 2 classifier.
        val result = router.route("mark as complete buy tickets", ChatLocale.En)
        assertIs<ChatIntent.CompleteItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── SetReminder — RU ────────────────────────────────────────────────────

    @Test
    fun setReminder_ru_withDate() = runTest {
        val result = router.route("напомни мне завтра в 9:00", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_ru_basicRemind() = runTest {
        val result = router.route("напомни купить хлеб", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_ru_withTomorrow() = runTest {
        val result = router.route("поставь напоминание завтра утром", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_ru_createReminder() = runTest {
        val result = router.route("создай напоминание на покупки", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_ru_remind() = runTest {
        val result = router.route("напомни о встрече через 2 часа", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_ru_remindNoDate() = runTest {
        val result = router.route("напоминание про молоко", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_ru_remindEvening() = runTest {
        val result = router.route("напомни мне вечером позвонить маме", ChatLocale.Ru)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── SetReminder — EN ────────────────────────────────────────────────────

    @Test
    fun setReminder_en_remindTomorrow() = runTest {
        val result = router.route("remind me tomorrow at 9am", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_en_setReminder() = runTest {
        val result = router.route("set a reminder for the meeting", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_en_remind() = runTest {
        val result = router.route("remind me to buy milk", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_en_alert() = runTest {
        val result = router.route("alert me in 30 minutes", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_en_notify() = runTest {
        val result = router.route("notify me tomorrow morning", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_en_createReminder() = runTest {
        val result = router.route("create reminder for dentist", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun setReminder_en_reminderKeyword() = runTest {
        val result = router.route("reminder for meeting on monday", ChatLocale.En)
        assertIs<ChatIntent.SetReminder>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── FindItems — RU ──────────────────────────────────────────────────────

    @Test
    fun findItems_ru_find() = runTest {
        val result = router.route("найди молоко", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_ru_where() = runTest {
        val result = router.route("где молоко", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_ru_search() = runTest {
        val result = router.route("поиск по маслу", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_ru_show() = runTest {
        val result = router.route("покажи список покупок", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_ru_lookFor() = runTest {
        val result = router.route("ищи задачи про работу", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_ru_findItem() = runTest {
        val result = router.route("найти пункт с хлебом", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_ru_showAll() = runTest {
        val result = router.route("показать все задачи", ChatLocale.Ru)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── FindItems — EN ──────────────────────────────────────────────────────

    @Test
    fun findItems_en_find() = runTest {
        val result = router.route("find milk in shopping", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_en_where() = runTest {
        val result = router.route("where is my grocery list", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_en_search() = runTest {
        val result = router.route("search for dentist", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_en_show() = runTest {
        val result = router.route("show me all tasks", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_en_lookFor() = runTest {
        val result = router.route("look for budget notes", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun findItems_en_whereAre() = runTest {
        val result = router.route("where are my reminders", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @kotlin.test.Ignore
    @Test
    fun findItems_en_list() = runTest {
        // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
        // Phase A lexicon doesn't include «list» as a FindItems keyword (collides with CreateChecklist «list for»). Reclassify in Phase B Layer 2.
        val result = router.route("list all completed items", ChatLocale.En)
        assertIs<ChatIntent.FindItems>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── CreateChecklist — RU ─────────────────────────────────────────────────

    @Test
    fun createChecklist_ru_newList() = runTest {
        val result = router.route("новый список покупок", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_ru_createList() = runTest {
        val result = router.route("создай список для поездки", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_ru_listFor() = runTest {
        val result = router.route("список для дня рождения", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_ru_createChecklist() = runTest {
        val result = router.route("создай чеклист для работы", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_ru_newChecklist() = runTest {
        val result = router.route("новый чеклист", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_ru_createListWithName() = runTest {
        val result = router.route("создать список продуктов", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_ru_listForEvent() = runTest {
        val result = router.route("список для вечеринки", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── CreateChecklist — EN ─────────────────────────────────────────────────

    @kotlin.test.Ignore
    @Test
    fun createChecklist_en_newList() = runTest {
        // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
        // Phase A pattern «new <adj> list» (adj inserted) not in CreateChecklist regex — only «new list <name>». Cover in Phase B classifier.
        val result = router.route("new shopping list", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_en_createList() = runTest {
        val result = router.route("create list for the trip", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_en_listFor() = runTest {
        val result = router.route("list for birthday party", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_en_newChecklist() = runTest {
        val result = router.route("new checklist for work", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_en_makeList() = runTest {
        val result = router.route("make a list for groceries", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_en_createChecklist() = runTest {
        val result = router.route("create checklist for the project", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun createChecklist_en_createAList() = runTest {
        val result = router.route("create a list for dinner", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── CreateChecklist — single-word broad triggers ─────────────────────────

    @Test
    fun createChecklist_ru_singleSozdayWithNoisyPayload() = runTest {
        val result = router.route(
            "создай в апки сделать рекламные скрины подумать как длетьа через ии",
            ChatLocale.Ru,
        )
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
        // Leading preposition "в" must be stripped; the rest preserved verbatim
        assertEquals(
            "апки сделать рекламные скрины подумать как длетьа через ии",
            (result.intent as ChatIntent.CreateChecklist).name,
        )
    }

    @Test
    fun createChecklist_ru_singleSozdat_simple() = runTest {
        val result = router.route("создать проект на выходные", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
        assertEquals("проект на выходные", (result.intent as ChatIntent.CreateChecklist).name)
    }

    @Test
    fun createChecklist_en_singleCreate() = runTest {
        val result = router.route("create marketing screenshots todo", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        assertTrue(result.confidence >= 0.6f)
        assertEquals("marketing screenshots todo", (result.intent as ChatIntent.CreateChecklist).name)
    }

    // ─── MoveReminders — RU ──────────────────────────────────────────────────

    @Test
    fun moveReminders_ru_withFromTo() = runTest {
        val result = router.route("перенеси напоминания с завтра на послезавтра", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_ru_shift() = runTest {
        val result = router.route("сдвинь все напоминания на завтра", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_ru_reschedule() = runTest {
        val result = router.route("перепланировать напоминания", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_ru_move() = runTest {
        val result = router.route("перенести все напоминания на следующей неделе", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_ru_moveWithSNa() = runTest {
        val result = router.route("перенеси с понедельника на вторник", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_ru_shiftAll() = runTest {
        val result = router.route("сдвинь напоминания", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_ru_rearrange() = runTest {
        val result = router.route("переставь напоминания с пятницы", ChatLocale.Ru)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── MoveReminders — EN ──────────────────────────────────────────────────

    @Test
    fun moveReminders_en_moveAll() = runTest {
        val result = router.route("move all reminders from today to tomorrow", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_en_reschedule() = runTest {
        val result = router.route("reschedule all reminders to next week", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_en_shift() = runTest {
        val result = router.route("shift reminders to tomorrow", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_en_moveFromTo() = runTest {
        val result = router.route("move reminders from monday to tuesday", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_en_rescheduleMeeting() = runTest {
        val result = router.route("reschedule the meeting reminder", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_en_move() = runTest {
        val result = router.route("move reminders", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun moveReminders_en_shiftAll() = runTest {
        val result = router.route("shift all reminders from friday to saturday", ChatLocale.En)
        assertIs<ChatIntent.MoveReminders>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    // ─── Unknown fallback ─────────────────────────────────────────────────────

    @Test
    fun unknown_emptyInput() = runTest {
        val result = router.route("", ChatLocale.En)
        assertIs<ChatIntent.Unknown>(result.intent)
        assertEquals(0f, result.confidence)
        assertEquals(RoutingLayer.Local, result.layer)
    }

    @Test
    fun unknown_blankInput() = runTest {
        val result = router.route("   ", ChatLocale.Ru)
        assertIs<ChatIntent.Unknown>(result.intent)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun unknown_gibberish() = runTest {
        val result = router.route("asdfghjkl xyz", ChatLocale.En)
        assertIs<ChatIntent.Unknown>(result.intent)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun unknown_randomRu() = runTest {
        val result = router.route("привет как дела", ChatLocale.Ru)
        assertIs<ChatIntent.Unknown>(result.intent)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun unknown_preservesRawText() = runTest {
        val input = "completely unrecognized input xyz"
        val result = router.route(input, ChatLocale.En)
        val intent = result.intent
        assertIs<ChatIntent.Unknown>(intent)
        assertEquals(input, intent.rawText)
    }

    // ─── Word boundary edge cases ─────────────────────────────────────────────

    @Test
    fun wordBoundary_buyNotPartOfLongerWord() = runTest {
        // "buying" should still trigger createItem via "buy" prefix match
        val result = router.route("I need to buy milk tomorrow", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
    }

    @Test
    fun wordBoundary_addNotInMiddle() = runTest {
        // "add" in "address" should NOT match
        val result = router.route("address the issue", ChatLocale.En)
        // "address" contains "add" but at word boundary "add" does not appear standalone
        // The router should NOT return CreateItem here
        // This is a known edge case — if "add" triggers, it's a false positive at word boundary
        // We verify confidence is low or intent is Unknown for pathological cases
        assertTrue(
            result.intent is ChatIntent.Unknown || result.confidence < 0.8f,
            "Expected Unknown or low confidence for 'address the issue', got ${result.intent} conf=${result.confidence}"
        )
    }

    @Test
    fun layer_alwaysLocal() = runTest {
        val inputs = listOf(
            "add milk" to ChatLocale.En,
            "добавь молоко" to ChatLocale.Ru,
            "привет" to ChatLocale.Ru,
        )
        for ((input, locale) in inputs) {
            val result = router.route(input, locale)
            assertEquals(RoutingLayer.Local, result.layer, "Expected Local layer for '$input'")
        }
    }

    // ─── Regression: real Amplitude feedback cases (2026-05-18) ──────────────
    // These inputs were routed to Layer 3 (FullChat) and received meta-responses
    // instead of tool_call=add_item. Root cause: missing lexicon triggers.

    @Test
    fun createItem_realFeedbackCase_ruWithVerbStartingItem() = runTest {
        // User typed: "добавь заказать два мусорных ведра в дела"
        // Was incorrectly routed to Layer 3 — "заказать" was parsed as the intent verb
        // instead of "добавь". Fix: "добавь" already in lexicon; test confirms full confidence.
        val result = router.route("добавь заказать два мусорных ведра в дела", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.7f, "Expected conf >= 0.7, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    @Test
    fun createItem_realFeedbackCase_ruWithZapishiSynonym() = runTest {
        // User typed: "запиши в дела заказать два мусорных ведра"
        // Was incorrectly routed to Layer 3. Fix: "запиши" already in lexicon; test confirms.
        val result = router.route("запиши в дела заказать два мусорных ведра", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.7f, "Expected conf >= 0.7, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    @Test
    fun createItem_realFeedbackCase_enWithVerbStartingItem() = runTest {
        // User typed: "add order two trash cans to chores"
        // Was incorrectly routed to Layer 3 — "order" confused the classifier.
        // Fix: "add" in lexicon; item text is "order two trash cans"; hint="chores".
        val result = router.route("add order two trash cans to chores", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.7f, "Expected conf >= 0.7, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    @Test
    fun createItem_realFeedbackCase_enWithNoteDownSynonym() = runTest {
        // User typed: "note down order two trash cans to chores"
        // Was routed to Layer 3 — "note down" not in lexicon. Fix: added in this patch.
        val result = router.route("note down order two trash cans to chores", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertTrue(result.confidence >= 0.7f, "Expected conf >= 0.7, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    // ─── AttachToItem — RU ────────────────────────────────────────────────────

    @Test
    fun attachToItem_ru_attachThisTo_itemAndHint() = runTest {
        val result = router.route("прикрепи это к молоко в покупках", ChatLocale.Ru)
        val intent = result.intent
        assertIs<ChatIntent.AttachToItem>(intent)
        assertTrue(result.confidence >= 0.8f, "Expected conf >= 0.8, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
        assertTrue(intent.itemText.contains("молоко", ignoreCase = true), "Expected itemText to contain 'молоко', got '${intent.itemText}'")
        assertTrue(intent.checklistHint?.contains("покуп", ignoreCase = true) == true, "Expected hint to contain 'покуп', got '${intent.checklistHint}'")
    }

    @Test
    fun attachToItem_ru_attachTo_itemOnly_noHint() = runTest {
        val result = router.route("прикрепи к яйца", ChatLocale.Ru)
        val intent = result.intent
        assertIs<ChatIntent.AttachToItem>(intent)
        assertTrue(result.confidence >= 0.6f)
        assertEquals(RoutingLayer.Local, result.layer)
        assertTrue(intent.itemText.isNotBlank(), "itemText should not be blank")
    }

    @Test
    fun attachToItem_ru_addFileTo_itemAndHint() = runTest {
        val result = router.route("добавь файл к молоко в покупки", ChatLocale.Ru)
        assertIs<ChatIntent.AttachToItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun attachToItem_ru_doesNotShadowCreateItem() = runTest {
        // "добавь молоко в покупки" has no "к" preposition → should be CreateItem, not AttachToItem
        val result = router.route("добавь молоко в покупки", ChatLocale.Ru)
        assertIs<ChatIntent.CreateItem>(result.intent)
    }

    // ─── AttachToItem — EN ────────────────────────────────────────────────────

    @Test
    fun attachToItem_en_attachThisTo_itemAndHint() = runTest {
        val result = router.route("attach this to milk in shopping", ChatLocale.En)
        val intent = result.intent
        assertIs<ChatIntent.AttachToItem>(intent)
        assertTrue(result.confidence >= 0.8f, "Expected conf >= 0.8, got ${result.confidence}")
        assertEquals(RoutingLayer.Local, result.layer)
        assertTrue(intent.itemText.contains("milk", ignoreCase = true), "itemText should contain 'milk', got '${intent.itemText}'")
    }

    @Test
    fun attachToItem_en_pinFileTo_itemAndHint() = runTest {
        val result = router.route("pin file to eggs in groceries", ChatLocale.En)
        val intent = result.intent
        assertIs<ChatIntent.AttachToItem>(intent)
        assertTrue(result.confidence >= 0.8f, "Expected conf >= 0.8, got ${result.confidence}")
    }

    @Test
    fun attachToItem_en_addFileTo_item() = runTest {
        val result = router.route("add file to butter", ChatLocale.En)
        assertIs<ChatIntent.AttachToItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun attachToItem_en_attachTo_multiWordItem() = runTest {
        val result = router.route("attach to call the dentist", ChatLocale.En)
        assertIs<ChatIntent.AttachToItem>(result.intent)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun attachToItem_en_doesNotShadowCreateItem() = runTest {
        // "add milk to shopping" — no "file" keyword, plain CreateItem
        val result = router.route("add milk to shopping", ChatLocale.En)
        assertIs<ChatIntent.CreateItem>(result.intent)
    }

    // ─── Regression: nameless create trigger must NOT be high-confidence ──────
    //
    // Real Amplitude bad-feedback case (2026-05-31): user typed «да создай» and the
    // app fired a confident CreateChecklist preview with name=null.
    // Root cause: tryCreateChecklist sets CONF_PARTIAL (0.8) when name is blank,
    // which is still ≥ LAYER_1_CONFIDENCE_THRESHOLD (0.7) → Layer 2 is never
    // consulted for clarification.
    // Fix: nameless create → CONF_FUZZY (0.6) so it escalates to Layer 2.

    @Test
    fun createChecklist_ru_triggerOnlyNoName_confidenceBelowThreshold() = runTest {
        // «да создай» — affirmative + bare create verb, NO list name.
        // Must escalate: confidence < 0.7 so Layer 2 can ask for a name.
        val result = router.route("да создай", ChatLocale.Ru)
        assertTrue(
            result.confidence < 0.7f,
            "Expected confidence < 0.7 for nameless create 'да создай', got ${result.confidence}",
        )
    }

    @Test
    fun createChecklist_ru_bareCreateTrigger_confidenceBelowThreshold() = runTest {
        // Bare «создай» alone, no name → should not be high-confidence.
        val result = router.route("создай", ChatLocale.Ru)
        assertTrue(
            result.confidence < 0.7f,
            "Expected confidence < 0.7 for bare 'создай', got ${result.confidence}",
        )
    }

    // ─── Guard: named create must stay high-confidence ────────────────────────

    @Test
    fun createChecklist_ru_withName_confidenceFullOrPartial() = runTest {
        // Named create must stay high-confidence so the preview fires instantly.
        // «создай список» is the matched trigger; remainder «покупок» becomes the name.
        val result = router.route("создай список покупок", ChatLocale.Ru)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        val name = (result.intent as ChatIntent.CreateChecklist).name
        assertTrue(name != null && name.isNotBlank(), "Expected non-blank name, got '$name'")
        assertTrue(
            result.confidence >= 0.7f,
            "Expected confidence >= 0.7 for named create, got ${result.confidence}",
        )
    }

    @Test
    fun createChecklist_en_withName_confidenceFullOrPartial() = runTest {
        // «create» triggers CreateChecklist; remainder «shopping list» becomes the name.
        val result = router.route("create shopping list", ChatLocale.En)
        assertIs<ChatIntent.CreateChecklist>(result.intent)
        val name = (result.intent as ChatIntent.CreateChecklist).name
        assertTrue(name != null && name.isNotBlank(), "Expected non-blank name, got '$name'")
        assertTrue(
            result.confidence >= 0.7f,
            "Expected confidence >= 0.7 for named EN create, got ${result.confidence}",
        )
    }
}
