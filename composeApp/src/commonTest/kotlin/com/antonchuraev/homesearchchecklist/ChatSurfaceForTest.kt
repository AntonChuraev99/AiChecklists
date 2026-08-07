package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatSurface
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Green coverage for [chatSurfaceFor] — the single mapping from "what is on the back stack" to
 * "what the chat may claim to see".
 *
 * The three invariants worth locking:
 *  1. the CONTROL arm never leaves [ChatSurface.Unknown] (no wire field, no screen-aware branch),
 *  2. an open checklist beats the tab underneath it (Expanded two-pane renders both),
 *  3. a non-tab route pushed on TOP of a tab is Unknown — the tab is no longer what the user sees.
 */
class ChatSurfaceForTest {

    private val detail = AppNavRoute.ChecklistDetail(checklistId = 7L)

    @Test
    fun `control arm is Unknown for every stack`() {
        listOf(
            listOf(AppNavRoute.Inbox),
            listOf(AppNavRoute.Inbox, AppNavRoute.Calendar),
            listOf(AppNavRoute.Main),
            listOf(AppNavRoute.Overview),
        ).forEach { stack ->
            assertEquals(
                ChatSurface.Unknown,
                chatSurfaceFor(stack, NavVariant.CONTROL, openChecklistId = null),
                "control arm must stay Unknown for $stack",
            )
        }
    }

    @Test
    fun `open checklist wins over the tab beneath it`() {
        assertEquals(
            ChatSurface.ChecklistDetail,
            chatSurfaceFor(listOf(AppNavRoute.Inbox), NavVariant.V2, openChecklistId = 42L),
        )
        // …and even in the control arm: the detail dock is the pre-experiment behaviour.
        assertEquals(
            ChatSurface.ChecklistDetail,
            chatSurfaceFor(listOf(AppNavRoute.Main), NavVariant.CONTROL, openChecklistId = 42L),
        )
    }

    @Test
    fun `each v2 tab maps to its own surface`() {
        assertEquals(
            ChatSurface.Inbox,
            chatSurfaceFor(listOf(AppNavRoute.Inbox), NavVariant.V2, null),
        )
        assertEquals(
            ChatSurface.Agenda,
            chatSurfaceFor(listOf(AppNavRoute.Inbox, AppNavRoute.Calendar), NavVariant.V2, null),
        )
        assertEquals(
            ChatSurface.Projects,
            chatSurfaceFor(listOf(AppNavRoute.Inbox, AppNavRoute.Projects), NavVariant.V2, null),
        )
        assertEquals(
            ChatSurface.Overview,
            chatSurfaceFor(listOf(AppNavRoute.Inbox, AppNavRoute.Overview), NavVariant.V2, null),
        )
    }

    @Test
    fun `Main maps to the Projects surface`() {
        // Mirrors v2TabFor: a deep link can re-root the stack around Main in the v2 arm.
        assertEquals(
            ChatSurface.Projects,
            chatSurfaceFor(listOf(AppNavRoute.Main), NavVariant.V2, null),
        )
    }

    @Test
    fun `a non-tab route on top of a tab is Unknown`() {
        assertEquals(
            ChatSurface.Unknown,
            chatSurfaceFor(listOf(AppNavRoute.Inbox, detail), NavVariant.V2, null),
        )
        assertEquals(
            ChatSurface.Unknown,
            chatSurfaceFor(listOf(AppNavRoute.Calendar, AppNavRoute.Settings), NavVariant.V2, null),
        )
    }

    @Test
    fun `an empty stack is Unknown, never a defaulted Inbox`() {
        assertEquals(ChatSurface.Unknown, chatSurfaceFor(emptyList(), NavVariant.V2, null))
    }

    @Test
    fun `only the two capture surfaces carry a wire value that reaches the server`() {
        // Guards the byte-identical-control-arm rule from the type side: a null wireValue is what
        // makes ChatAgentApiServiceImpl drop the whole `context_screen` key.
        assertEquals(null, ChatSurface.Unknown.wireValue)
        assertEquals(null, ChatSurface.ChecklistDetail.wireValue)
        assertEquals("inbox", ChatSurface.Inbox.wireValue)
        assertEquals("agenda", ChatSurface.Agenda.wireValue)
    }
}
