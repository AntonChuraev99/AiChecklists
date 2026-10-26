package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The system Inbox's stored name is whatever language was active when the row was created (or on
 * the device that created it, via sync). Showing it verbatim left "Входящие" on an English UI.
 */
class ChecklistDisplayNameTest {

    @Test
    fun inbox_storedInAnotherLanguage_showsTheCurrentLocaleName() {
        assertEquals(
            "Inbox",
            checklistDisplayName(storedName = "Входящие", isInbox = true, inboxName = "Inbox"),
        )
    }

    @Test
    fun inbox_followsWhicheverLocaleIsResolvedNow() {
        // Two different resolved names, so a hardcoded "Inbox" in the helper cannot pass.
        assertEquals(
            "Входящие",
            checklistDisplayName(storedName = "Inbox", isInbox = true, inboxName = "Входящие"),
        )
    }

    @Test
    fun project_keepsItsOwnName_evenWhenItLooksLikeAnInbox() {
        assertEquals(
            "Входящие",
            checklistDisplayName(storedName = "Входящие", isInbox = false, inboxName = "Inbox"),
        )
    }
}
