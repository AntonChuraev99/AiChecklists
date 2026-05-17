package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Routing layer that classified the intent.
 * Drives the pricing chip ("≈X credits") in the chat UI:
 *   Local       → 0 credits (all of Phase A MVP)
 *   Classifier  → 1–2 credits (Phase B)
 *   FullChat    → 3–5 credits (Phase C)
 */
enum class RoutingLayer {
    Local,
    Classifier,
    FullChat
}
