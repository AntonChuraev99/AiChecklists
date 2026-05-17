package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Represents a single message in the AI chat dialog.
 *
 * [costCredits] is always 0 in Phase A (Layer 1 local routing).
 * [routedLayer] indicates which classification tier handled this message.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long,
    val costCredits: Int = 0,
    val routedLayer: RoutingLayer? = null
)
