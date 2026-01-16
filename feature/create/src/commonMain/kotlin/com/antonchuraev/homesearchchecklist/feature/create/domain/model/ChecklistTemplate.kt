package com.antonchuraev.homesearchchecklist.feature.create.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a checklist template fetched from Remote Config.
 */
@Serializable
data class ChecklistTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String,
    val category: String,
    val items: List<String>
)

/**
 * Wrapper for templates JSON from Remote Config.
 */
@Serializable
data class TemplatesConfig(
    val templates: List<ChecklistTemplate>
)

/**
 * Template category for grouping templates.
 */
@Serializable
data class TemplateCategory(
    val id: String,
    val name: String,
    val templates: List<ChecklistTemplate>
)
