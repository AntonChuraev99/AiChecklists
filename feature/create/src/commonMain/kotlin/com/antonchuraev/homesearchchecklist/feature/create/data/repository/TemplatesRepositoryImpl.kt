package com.antonchuraev.homesearchchecklist.feature.create.data.repository

import aichecklists.feature.create.generated.resources.Res
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplatesConfig
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

class TemplatesRepositoryImpl(
    private val logger: AppLogger
) : TemplatesRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val categoryNames = mapOf(
        "real_estate" to "Real Estate",
        "travel" to "Travel",
        "shopping" to "Shopping",
        "health" to "Health & Wellness",
        "work" to "Work & Productivity",
        "home" to "Home & Garden",
        "events" to "Events & Planning",
        "education" to "Education",
        "fitness" to "Fitness",
        "cooking" to "Cooking",
        "finance" to "Finance",
        "other" to "Other"
    )

    private var cachedTemplates: List<ChecklistTemplate>? = null

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun getTemplates(): List<ChecklistTemplate> {
        cachedTemplates?.let { return it }

        val bytes = try {
            Res.readBytes("files/templates.json")
        } catch (e: Exception) {
            logger.error(
                "TemplatesRepository",
                "Resource read failed for files/templates.json: ${e::class.simpleName}: ${e.message}"
            )
            return emptyList()
        }

        val templatesJson = bytes.decodeToString()
        logger.debug(
            "TemplatesRepository",
            "Read templates.json: ${bytes.size} bytes, first16='${templatesJson.take(16)}'"
        )

        return try {
            val config = json.decodeFromString<TemplatesConfig>(templatesJson)
            logger.debug("TemplatesRepository", "Loaded ${config.templates.size} templates from bundled JSON")
            config.templates.also { cachedTemplates = it }
        } catch (e: Exception) {
            logger.error(
                "TemplatesRepository",
                "JSON parse failed (${bytes.size} bytes): ${e::class.simpleName}: ${e.message}"
            )
            emptyList()
        }
    }

    override suspend fun getTemplatesByCategory(): List<TemplateCategory> {
        val templates = getTemplates()

        return templates
            .groupBy { it.category }
            .map { (categoryId, categoryTemplates) ->
                TemplateCategory(
                    id = categoryId,
                    name = categoryNames[categoryId] ?: categoryId.replaceFirstChar { it.uppercase() },
                    templates = categoryTemplates
                )
            }
            .sortedBy { it.name }
    }

    override suspend fun getTemplateById(id: String): ChecklistTemplate? {
        return getTemplates().find { it.id == id }
    }
}
