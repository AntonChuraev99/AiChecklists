package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * Persists the last server-assigned AI-model A/B arm (variant + concrete model id) so revenue can
 * be attributed to it even when a purchase happens in a session with no AI interaction.
 *
 * Global (NOT keyed per-uid, unlike [FirstChecklistRepository]): the arm is deterministic per user
 * and stable across the app's lifetime, so a single stored value is sufficient. Never throws.
 */
interface AiExperimentPrefsRepository {
    /** Persisted arm variant (e.g. "variant_b"), or null if never set. */
    suspend fun getModelVariant(): String?

    /** Persisted concrete model id (e.g. "gemini-3.1-flash-lite"), or null if never set / omitted. */
    suspend fun getModelId(): String?

    /** Stores the arm. [modelId] may be null when the server omitted it. Idempotent. */
    suspend fun setModelArm(variant: String, modelId: String?)
}
