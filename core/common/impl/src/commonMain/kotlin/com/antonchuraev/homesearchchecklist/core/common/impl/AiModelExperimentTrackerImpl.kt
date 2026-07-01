package com.antonchuraev.homesearchchecklist.core.common.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AiModelArm
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiExperimentPrefsRepository
import kotlin.concurrent.Volatile

private const val TAG = "AiModelExperiment"

/**
 * Single home for the A/B-arm side effects that used to live inline in ChatViewModel — now shared by
 * chat, analyze and create so all three attribute the same server arm without duplicating the logic.
 *
 * The per-process [reportedVariant] guard mirrors the old `ChatViewModel.reportedModelVariant`: the
 * arm is deterministic per user, so the sticky user-property is set (and the arm persisted) once per
 * cold start, not on every AI response. See [AiModelExperimentTracker] for the attribution rationale.
 */
class AiModelExperimentTrackerImpl(
    private val analytics: AnalyticsTracker,
    private val prefs: AiExperimentPrefsRepository,
    private val logger: AppLogger,
) : AiModelExperimentTracker {

    @Volatile
    private var reportedVariant: String? = null

    override suspend fun report(variant: String?, modelId: String?, aiFlow: String?) {
        if (variant.isNullOrBlank()) return
        if (variant == reportedVariant) return
        reportedVariant = variant

        // Sticky user-property so every later event (incl. the paywall funnel) is arm-segmentable.
        runCatching {
            analytics.setUserProperties(mapOf(AnalyticsParams.AI_MODEL_VARIANT to variant))
        }.onFailure { e ->
            logger.warning(TAG, "report: failed to set user-property — ${e.message}")
        }

        // Persist so a later purchase (possibly no AI interaction that session) can attribute revenue.
        runCatching {
            prefs.setModelArm(variant, modelId)
        }.onFailure { e ->
            logger.warning(TAG, "report: failed to persist arm — ${e.message}")
        }

        logger.debug(TAG, "report: arm=$variant modelId=$modelId aiFlow=$aiFlow")
    }

    override suspend fun current(): AiModelArm? =
        runCatching {
            val variant = prefs.getModelVariant()?.takeIf { it.isNotBlank() }
            variant?.let { AiModelArm(variant = it, modelId = prefs.getModelId()) }
        }.getOrElse { e ->
            logger.warning(TAG, "current: failed to read persisted arm — ${e.message}")
            null
        }
}
