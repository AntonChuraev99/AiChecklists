package com.antonchuraev.homesearchchecklist.feature.create.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.flow.first

class CreateWeeklyChecklistUseCase(
    private val checklistRepository: ChecklistRepository,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
) {
    sealed interface Result {
        data class Created(val checklistId: Long) : Result
        data object RequiresUpgrade : Result
    }

    /**
     * Creates a weekly-mode checklist named [name]. The caller resolves the localized
     * default name via getString, keeping Compose Resources out of the domain layer.
     * Returns [Result.RequiresUpgrade] for free users already at the weekly limit.
     */
    suspend operator fun invoke(name: String): Result {
        val limits = getUserLimitsUseCase().first()
        if (!limits.canCreateWeeklyChecklist) return Result.RequiresUpgrade
        val checklist = Checklist(
            name = name,
            items = emptyList(),
            viewMode = ChecklistViewMode.Weekly,
        )
        val checklistId = checklistRepository.addChecklist(checklist)
        return Result.Created(checklistId)
    }
}
