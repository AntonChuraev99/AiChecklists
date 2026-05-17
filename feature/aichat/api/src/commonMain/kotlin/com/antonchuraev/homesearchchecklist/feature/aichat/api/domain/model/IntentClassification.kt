package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Result of classifying a user's input into a [ChatIntent].
 *
 * [confidence] scoring convention (Layer 1 local router):
 *   1.0 — exact keyword match at start + all entities extracted
 *   0.8 — keyword match + partial entities (e.g. itemText found, checklistHint missing)
 *   0.6 — fuzzy keyword detection
 *   0.0 — no match → [ChatIntent.Unknown]
 *
 * [layer] is always [RoutingLayer.Local] in Phase A MVP.
 * Phase B will add [RoutingLayer.Classifier] and [RoutingLayer.FullChat].
 */
data class IntentClassification(
    val intent: ChatIntent,
    val confidence: Float,
    val layer: RoutingLayer
)
