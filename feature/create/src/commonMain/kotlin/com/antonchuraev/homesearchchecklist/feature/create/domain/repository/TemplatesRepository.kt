package com.antonchuraev.homesearchchecklist.feature.create.domain.repository

import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory

/**
 * Repository for fetching checklist templates from Remote Config.
 */
interface TemplatesRepository {
    /**
     * Get all available templates.
     */
    suspend fun getTemplates(): List<ChecklistTemplate>

    /**
     * Get templates grouped by category.
     */
    suspend fun getTemplatesByCategory(): List<TemplateCategory>

    /**
     * Get a specific template by ID.
     */
    suspend fun getTemplateById(id: String): ChecklistTemplate?
}
