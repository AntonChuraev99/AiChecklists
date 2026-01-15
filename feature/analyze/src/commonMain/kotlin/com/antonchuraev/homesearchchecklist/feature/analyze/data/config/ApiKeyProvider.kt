package com.antonchuraev.homesearchchecklist.feature.analyze.data.config

/**
 * Configuration for Gemini AI analyzer.
 * Provided via dependency injection from the app module.
 */
data class GeminiConfig(
    val apiKey: String
)

/**
 * Provides the Gemini API key.
 * Returns empty string if not configured.
 */
interface ApiKeyProvider {
    fun getGeminiApiKey(): String
}

/**
 * Default implementation that uses injected GeminiConfig.
 */
class DefaultApiKeyProvider(private val config: GeminiConfig) : ApiKeyProvider {
    override fun getGeminiApiKey(): String = config.apiKey
}
