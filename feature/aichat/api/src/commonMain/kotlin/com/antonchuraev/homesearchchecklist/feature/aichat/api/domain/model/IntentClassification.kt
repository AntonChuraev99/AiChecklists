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
 * [layer] = [RoutingLayer.Classifier] in Phase B when Layer 2 cloud classifier is used.
 *
 * [preBuiltToolCall] is non-null only when [layer] = [RoutingLayer.Classifier]: the
 * cloud function extracted entities server-side and built the [ToolCall] already.
 * [ChatViewModel] prefers [preBuiltToolCall] over re-running local entity extraction
 * from raw text via [buildToolCall]. Null for Layer 1 results and Unknown/free_form intents.
 */
data class IntentClassification(
    val intent: ChatIntent,
    val confidence: Float,
    val layer: RoutingLayer,
    val preBuiltToolCall: ToolCall? = null,
)
