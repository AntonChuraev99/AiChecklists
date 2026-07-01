package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AiExperimentPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.flow.first

private const val KEY_MODEL_VARIANT = "ai_exp_model_variant"
private const val KEY_MODEL_ID = "ai_exp_model_id"

class AiExperimentPrefsRepositoryImpl(
    private val dataStore: AppDatastore,
) : AiExperimentPrefsRepository {

    override suspend fun getModelVariant(): String? =
        dataStore.observeString(KEY_MODEL_VARIANT, defaultValue = "").first().takeIf { it.isNotEmpty() }

    override suspend fun getModelId(): String? =
        dataStore.observeString(KEY_MODEL_ID, defaultValue = "").first().takeIf { it.isNotEmpty() }

    override suspend fun setModelArm(variant: String, modelId: String?) {
        dataStore.saveString(KEY_MODEL_VARIANT, variant)
        // Empty string is the "absent" sentinel — observeString maps it back to null via takeIf above.
        dataStore.saveString(KEY_MODEL_ID, modelId ?: "")
    }
}
