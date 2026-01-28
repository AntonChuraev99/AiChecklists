package com.antonchuraev.homesearchchecklist.widget.data

import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toDomain
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/**
 * Repository for widget data access.
 * CRITICAL: Always read progress from ChecklistFill, NOT from Checklist template!
 */
@Singleton
class WidgetRepository(
    private val checklistDao: ChecklistDao,
    private val fillDao: ChecklistFillDao
) {

    /**
     * Get all checklists for configuration screen
     */
    fun observeAllChecklists(): Flow<List<Checklist>> {
        return checklistDao.observeChecklists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Observe checklist with default fill for widget display.
     * Returns Flow<ChecklistWidgetData> that emits new data when fill changes.
     * Used with collectAsState() in widget composable.
     */
    fun observeChecklistWithDefaultFill(checklistId: Long): Flow<ChecklistWidgetData> {
        return combine(
            checklistDao.observeChecklistById(checklistId),
            fillDao.observeDefaultFillByChecklistId(checklistId)
        ) { checklist, defaultFill ->
            if (checklist == null) {
                return@combine ChecklistWidgetData.notFound(checklistId)
            }

            val items = defaultFill?.items ?: checklist.items.map { templateItem ->
                ChecklistFillItem(
                    text = templateItem.text,
                    checked = false,
                    note = null
                )
            }

            ChecklistWidgetData(
                checklistId = checklist.id,
                name = checklist.name,
                items = items,
                fillId = defaultFill?.id
            )
        }
    }

    /**
     * Get checklist with its default fill for widget display.
     * Returns ChecklistWidgetData with items from default fill (user progress).
     */
    suspend fun getChecklistWithDefaultFill(checklistId: Long): ChecklistWidgetData {
        val checklist = checklistDao.getById(checklistId)
            ?: return ChecklistWidgetData.notFound(checklistId)

        val defaultFill = fillDao.getDefaultFillByChecklistId(checklistId)

        // Use items from default fill if exists, otherwise fallback to template items (all unchecked)
        val items = defaultFill?.items ?: checklist.items.map { templateItem ->
            ChecklistFillItem(
                text = templateItem.text,
                checked = false,
                note = null
            )
        }

        return ChecklistWidgetData(
            checklistId = checklist.id,
            name = checklist.name,
            items = items,
            fillId = defaultFill?.id
        )
    }

    /**
     * Toggle item checked state in the fill.
     * If fillId is null, creates a new default fill first.
     */
    suspend fun toggleItem(checklistId: Long, fillId: Long?, itemIndex: Int): Long? {
        val actualFillId = fillId ?: createDefaultFill(checklistId) ?: return null
        val fill = fillDao.getById(actualFillId) ?: return null

        if (itemIndex < 0 || itemIndex >= fill.items.size) return null

        val updatedItems = fill.items.toMutableList()
        val currentItem = updatedItems[itemIndex]
        updatedItems[itemIndex] = currentItem.copy(checked = !currentItem.checked)

        val updatedFill = fill.copy(items = updatedItems)
        fillDao.insert(updatedFill)

        return actualFillId
    }

    /**
     * Create a default fill for checklist if it doesn't exist.
     * Returns the fill ID.
     */
    private suspend fun createDefaultFill(checklistId: Long): Long? {
        val checklist = checklistDao.getById(checklistId) ?: return null

        // Check if default fill already exists
        val existingFill = fillDao.getDefaultFillByChecklistId(checklistId)
        if (existingFill != null) return existingFill.id

        // Create new default fill from template items
        val newFill = ChecklistFillEntity(
            checklistId = checklistId,
            name = "Default",
            coverImagePath = null,
            items = checklist.items.map { templateItem ->
                ChecklistFillItem(
                    text = templateItem.text,
                    checked = false,
                    note = null
                )
            },
            createdAt = System.currentTimeMillis(),
            isDefault = true
        )

        return fillDao.insert(newFill)
    }
}
