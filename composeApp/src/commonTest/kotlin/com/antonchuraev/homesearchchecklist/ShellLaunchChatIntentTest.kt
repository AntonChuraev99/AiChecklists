package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunch
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatScreenIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [chatIntentForOpenChat]: what a [ShellLaunch.OpenChat] hand-off does to the chat. autoSend goes
 * through [ChatScreenIntent.OnPrefillAndSend] — the chat's own send path, not a parallel one.
 */
class ShellLaunchChatIntentTest {

    @Test
    fun noPrefill_opensEmptyChat_regardlessOfAutoSend() {
        assertNull(chatIntentForOpenChat(ShellLaunch.OpenChat(prefill = null)))
        assertNull(chatIntentForOpenChat(ShellLaunch.OpenChat(prefill = null, autoSend = true)))
    }

    @Test
    fun prefillWithoutAutoSend_onlyFillsTheComposer() {
        assertEquals(
            ChatScreenIntent.OnPrefillInput("Plan my week"),
            chatIntentForOpenChat(ShellLaunch.OpenChat(prefill = "Plan my week")),
        )
    }

    @Test
    fun prefillWithAutoSend_sendsThroughTheRegularSendPath_withoutForcingTheAgent() {
        assertEquals(
            ChatScreenIntent.OnPrefillAndSend("Plan my week", forceAgent = false),
            chatIntentForOpenChat(ShellLaunch.OpenChat(prefill = "Plan my week", autoSend = true)),
        )
    }
}
