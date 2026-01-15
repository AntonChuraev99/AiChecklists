package com.antonchuraev.homesearchchecklist.feature.create.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplatesConfig
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import kotlinx.serialization.json.Json

class TemplatesRepositoryImpl(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger
) : TemplatesRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Category display names (in the future, these could come from Remote Config too)
    private val categoryNames = mapOf(
        "real_estate" to "Real Estate",
        "travel" to "Travel",
        "shopping" to "Shopping",
        "health" to "Health & Wellness",
        "work" to "Work & Productivity",
        "home" to "Home & Garden",
        "events" to "Events & Planning",
        "education" to "Education",
        "other" to "Other"
    )

    override suspend fun getTemplates(): List<ChecklistTemplate> {
        val templatesJson = remoteConfigProvider.getString(
            RemoteConfigKeys.TEMPLATES_JSON,
            RemoteConfigDefaults.TEMPLATES_JSON
        )

        if (templatesJson.isBlank()) {
            logger.debug("TemplatesRepository", "No templates JSON found in Remote Config")
            return getDefaultTemplates()
        }

        return try {
            val config = json.decodeFromString<TemplatesConfig>(templatesJson)
            logger.debug("TemplatesRepository", "Loaded ${config.templates.size} templates from Remote Config")
            config.templates
        } catch (e: Exception) {
            logger.error("TemplatesRepository", "Failed to parse templates JSON: ${e.message}")
            getDefaultTemplates()
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

    /**
     * Default templates to show when Remote Config is empty or fails.
     * These provide a good starting experience for users.
     */
    private fun getDefaultTemplates(): List<ChecklistTemplate> {
        return listOf(
            // Real Estate
            ChecklistTemplate(
                id = "apartment_viewing",
                name = "Apartment Viewing",
                icon = "apartment",
                category = "real_estate",
                items = listOf(
                    "Check water pressure in kitchen and bathroom",
                    "Test all light switches and electrical outlets",
                    "Look for signs of water damage or mold",
                    "Check window conditions and seals",
                    "Test heating and cooling systems",
                    "Inspect flooring for damage",
                    "Check storage space and closets",
                    "Note noise levels from neighbors/street",
                    "Verify included appliances work",
                    "Check cell phone signal strength"
                )
            ),
            ChecklistTemplate(
                id = "house_inspection",
                name = "House Inspection",
                icon = "home",
                category = "real_estate",
                items = listOf(
                    "Check roof condition and gutters",
                    "Inspect foundation for cracks",
                    "Test plumbing in all bathrooms",
                    "Check electrical panel and wiring",
                    "Inspect HVAC system and filters",
                    "Look for pest damage or infestation",
                    "Check attic insulation",
                    "Inspect basement for moisture",
                    "Test garage door operation",
                    "Verify property boundaries"
                )
            ),

            // Travel
            ChecklistTemplate(
                id = "travel_packing",
                name = "Travel Packing",
                icon = "luggage",
                category = "travel",
                items = listOf(
                    "Passport and travel documents",
                    "Phone charger and power bank",
                    "Toiletries and medications",
                    "Weather-appropriate clothing",
                    "Comfortable walking shoes",
                    "Travel adapter if needed",
                    "Headphones and entertainment",
                    "Snacks for the journey",
                    "Copy of hotel reservations",
                    "Travel insurance documents"
                )
            ),
            ChecklistTemplate(
                id = "vacation_prep",
                name = "Vacation Preparation",
                icon = "flight_takeoff",
                category = "travel",
                items = listOf(
                    "Book flights and accommodation",
                    "Arrange airport transfer",
                    "Check passport validity",
                    "Apply for visa if required",
                    "Notify bank of travel dates",
                    "Set up out-of-office replies",
                    "Arrange pet care if needed",
                    "Stop mail and newspaper delivery",
                    "Check weather at destination",
                    "Download offline maps"
                )
            ),

            // Shopping
            ChecklistTemplate(
                id = "grocery_essentials",
                name = "Grocery Essentials",
                icon = "shopping_cart",
                category = "shopping",
                items = listOf(
                    "Bread and bakery items",
                    "Milk and dairy products",
                    "Fresh fruits and vegetables",
                    "Meat or protein alternatives",
                    "Eggs",
                    "Rice, pasta, or grains",
                    "Cooking oil",
                    "Salt and basic spices",
                    "Coffee or tea",
                    "Cleaning supplies"
                )
            ),

            // Work
            ChecklistTemplate(
                id = "meeting_prep",
                name = "Meeting Preparation",
                icon = "groups",
                category = "work",
                items = listOf(
                    "Review meeting agenda",
                    "Prepare presentation materials",
                    "Test video conferencing setup",
                    "Gather relevant documents",
                    "Prepare questions to ask",
                    "Review attendee list",
                    "Set up meeting room or link",
                    "Send reminder to participants",
                    "Prepare notes template",
                    "Have backup plan for tech issues"
                )
            ),
            ChecklistTemplate(
                id = "project_launch",
                name = "Project Launch",
                icon = "rocket_launch",
                category = "work",
                items = listOf(
                    "Complete final testing",
                    "Update documentation",
                    "Prepare launch announcement",
                    "Brief support team",
                    "Set up monitoring alerts",
                    "Create rollback plan",
                    "Schedule launch window",
                    "Notify stakeholders",
                    "Archive previous version",
                    "Celebrate with team"
                )
            ),

            // Events
            ChecklistTemplate(
                id = "party_planning",
                name = "Party Planning",
                icon = "celebration",
                category = "events",
                items = listOf(
                    "Create guest list",
                    "Send invitations",
                    "Plan menu and drinks",
                    "Order or bake cake",
                    "Buy decorations",
                    "Arrange music playlist",
                    "Plan activities or games",
                    "Confirm RSVPs",
                    "Prepare venue setup",
                    "Buy party supplies"
                )
            ),
            ChecklistTemplate(
                id = "moving_house",
                name = "Moving House",
                icon = "local_shipping",
                category = "events",
                items = listOf(
                    "Book moving company",
                    "Start packing non-essentials",
                    "Change address with post office",
                    "Update address with bank/bills",
                    "Cancel or transfer utilities",
                    "Clean old residence",
                    "Take meter readings",
                    "Collect all keys",
                    "Unpack essentials first",
                    "Meet new neighbors"
                )
            ),

            // Health
            ChecklistTemplate(
                id = "doctor_visit",
                name = "Doctor Visit Prep",
                icon = "medical_services",
                category = "health",
                items = listOf(
                    "List current symptoms",
                    "Note when symptoms started",
                    "Bring current medications list",
                    "Prepare questions to ask",
                    "Bring insurance information",
                    "Bring ID documents",
                    "List allergies",
                    "Note family medical history",
                    "Write down recent test results",
                    "Bring someone for support if needed"
                )
            ),

            // Home
            ChecklistTemplate(
                id = "spring_cleaning",
                name = "Spring Cleaning",
                icon = "cleaning_services",
                category = "home",
                items = listOf(
                    "Deep clean kitchen appliances",
                    "Wash windows inside and out",
                    "Clean behind furniture",
                    "Organize and declutter closets",
                    "Shampoo carpets or rugs",
                    "Clean light fixtures",
                    "Wash curtains and blinds",
                    "Clean out garage/storage",
                    "Service HVAC system",
                    "Check smoke detectors"
                )
            )
        )
    }
}
