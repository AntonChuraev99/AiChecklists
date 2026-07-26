package com.antonchuraev.homesearchchecklist.feature.create.presentation.preview

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplatePreviewViewModel(
    private val templateId: String,
    private val appNavigator: AppNavigator,
    private val templatesRepository: TemplatesRepository,
    private val checklistRepository: ChecklistRepository,
    private val analyticsTracker: AnalyticsTracker,
) : AppViewModel<TemplatePreviewScreenState, TemplatePreviewScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(TemplatePreviewScreenState())
    override val screenState: StateFlow<TemplatePreviewScreenState> = _screenState.asStateFlow()

    init {
        loadTemplate()
    }

    override fun onIntent(intent: TemplatePreviewScreenIntent) {
        when (intent) {
            TemplatePreviewScreenIntent.OnBackClick -> appNavigator.onBack()
            is TemplatePreviewScreenIntent.OnRemoveItem -> removeItem(intent.index)
            is TemplatePreviewScreenIntent.OnNewItemTextChange -> updateNewItemText(intent.text)
            TemplatePreviewScreenIntent.OnAddItem -> addItem()
            TemplatePreviewScreenIntent.OnCreateChecklist -> createChecklist()
            TemplatePreviewScreenIntent.OnDismissError -> dismissError()
        }
    }

    private fun loadTemplate() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true) }

            try {
                val template = templatesRepository.getTemplateById(templateId)
                if (template != null) {
                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            template = template,
                            editableItems = template.items.toList()
                        )
                    }

                    // Emitted only on a successful load: a template that failed to load was
                    // never seen, and counting it would inflate the previewed -> used funnel.
                    analyticsTracker.event(
                        AnalyticsEvents.Template.PREVIEWED,
                        mapOf(
                            AnalyticsParams.TEMPLATE_SLUG to template.id,
                            AnalyticsParams.TEMPLATE_CATEGORY to template.category,
                            AnalyticsParams.ITEM_COUNT to template.items.size,
                        ),
                    )
                } else {
                    _screenState.update {
                        it.copy(isLoading = false, error = getString(Res.string.error_template_not_found))
                    }
                }
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isLoading = false, error = e.message ?: getString(Res.string.error_template_load_failed))
                }
            }
        }
    }

    private fun removeItem(index: Int) {
        _screenState.update { state ->
            val newItems = state.editableItems.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            state.copy(editableItems = newItems)
        }
    }

    private fun updateNewItemText(text: String) {
        _screenState.update { it.copy(newItemText = text) }
    }

    private fun addItem() {
        val text = _screenState.value.newItemText.trim()
        if (text.isNotEmpty()) {
            _screenState.update { state ->
                state.copy(
                    editableItems = state.editableItems + text,
                    newItemText = ""
                )
            }
        }
    }

    private fun createChecklist() {
        val state = _screenState.value
        val template = state.template ?: return

        viewModelScope.launch {
            if (state.editableItems.isEmpty()) {
                _screenState.update { it.copy(error = getString(Res.string.error_add_at_least_one_item)) }
                return@launch
            }

            _screenState.update { it.copy(isCreating = true) }

            try {
                val checklist = Checklist(
                    name = template.name,
                    items = state.editableItems.map { ChecklistItem(text = it, checked = false) }
                )

                val checklistId = checklistRepository.addChecklist(checklist)

                analyticsTracker.event(AnalyticsEvents.Checklist.CREATED, mapOf(
                    AnalyticsParams.SOURCE to ChecklistSource.TEMPLATE.wire,
                    AnalyticsParams.ITEM_COUNT to checklist.items.size,
                ))

                // Alongside CREATED, never instead of it: the create funnel must stay complete,
                // this only adds which template (and category) produced the checklist.
                analyticsTracker.event(
                    AnalyticsEvents.Template.USED,
                    mapOf(
                        AnalyticsParams.TEMPLATE_SLUG to template.id,
                        AnalyticsParams.TEMPLATE_CATEGORY to template.category,
                        AnalyticsParams.ITEM_COUNT to checklist.items.size,
                        // Templates are editable before creating — tells apart "used as shipped"
                        // from "used as a starting point", which is a different product signal.
                        AnalyticsParams.WAS_EDITED to (state.editableItems != template.items),
                    ),
                )

                _screenState.update { it.copy(isCreating = false) }

                appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isCreating = false, error = e.message ?: getString(Res.string.error_create_checklist_failed))
                }
            }
        }
    }

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}
