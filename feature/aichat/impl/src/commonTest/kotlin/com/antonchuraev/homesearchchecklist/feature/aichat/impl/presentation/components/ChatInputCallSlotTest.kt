package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatInputCallSlot
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.chatInputCallSlot
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatInputCallSlotTest {

    private fun slot(
        hasCallEntry: Boolean = true,
        simpleSendOnly: Boolean = false,
        isTranscribing: Boolean = false,
        canSend: Boolean = false,
        isEnabled: Boolean = true,
        isRecording: Boolean = false,
    ) = chatInputCallSlot(
        hasCallEntry = hasCallEntry,
        simpleSendOnly = simpleSendOnly,
        isTranscribing = isTranscribing,
        canSend = canSend,
        isEnabled = isEnabled,
        isRecording = isRecording,
    )

    @Test
    fun idleChat_withEntry_isEnabled() {
        assertEquals(ChatInputCallSlot.Enabled, slot())
    }

    @Test
    fun noEntry_isHidden() {
        // Web / iOS / voice_call_enabled off: the host passes null — never a dead button.
        assertEquals(ChatInputCallSlot.Hidden, slot(hasCallEntry = false))
    }

    @Test
    fun itemCreateMode_isHidden() {
        assertEquals(ChatInputCallSlot.Hidden, slot(simpleSendOnly = true))
    }

    @Test
    fun transcribing_isHidden() {
        assertEquals(ChatInputCallSlot.Hidden, slot(isTranscribing = true))
    }

    @Test
    fun textToSend_isHidden_likeTheMicItSitsBeside() {
        assertEquals(ChatInputCallSlot.Hidden, slot(canSend = true))
    }

    @Test
    fun recording_isDisabled() {
        assertEquals(ChatInputCallSlot.Disabled, slot(isRecording = true))
    }

    @Test
    fun processingReply_isDisabled() {
        assertEquals(ChatInputCallSlot.Disabled, slot(isEnabled = false))
    }

    @Test
    fun processingReply_withTypedText_isHidden() {
        // Typed text during processing: the trailing slot is Send, so the call stays out.
        assertEquals(ChatInputCallSlot.Hidden, slot(isEnabled = false, canSend = true))
    }
}
