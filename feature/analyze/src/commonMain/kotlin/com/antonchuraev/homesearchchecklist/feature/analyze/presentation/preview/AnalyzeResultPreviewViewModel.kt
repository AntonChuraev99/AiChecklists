package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyzeResultPreviewViewModel(
    private val appNavigator: AppNavigator,
    private val checklistRepository: ChecklistRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val activationCoordinator: ActivationCoordinator,
    private val remoteConfigProvider: RemoteConfigProvider
) : AppViewModel<AnalyzeResultPreviewScreenState, AnalyzeResultPreviewScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(AnalyzeResultPreviewScreenState())
    override val screenState: StateFlow<AnalyzeResultPreviewScreenState> = _screenState.asStateFlow()

    private var targetChecklistId: Long? = null

    /** True when this preview came from the new-user activation hero (drives the activation funnel on confirm). */
    private var fromActivation: Boolean = false

    init {
        loadData()
    }

    override fun onIntent(intent: AnalyzeResultPreviewScreenIntent) {
        when (intent) {
            AnalyzeResultPreviewScreenIntent.OnBackClick -> {
                AnalyzeResultHolder.clear()
                appNavigator.onBack()
            }
            is AnalyzeResultPreviewScreenIntent.OnChecklistNameChanged -> updateChecklistName(intent.name)
            is AnalyzeResultPreviewScreenIntent.OnRemoveItem -> removeItem(intent.index)
            is AnalyzeResultPreviewScreenIntent.OnNewItemTextChange -> updateNewItemText(intent.text)
            AnalyzeResultPreviewScreenIntent.OnAddItem -> addItem()
            AnalyzeResultPreviewScreenIntent.OnCreateChecklist -> createChecklist()
            AnalyzeResultPreviewScreenIntent.OnDismissError -> dismissError()
            is AnalyzeResultPreviewScreenIntent.OnUseFoldersChanged -> setUseFolders(intent.enabled)
            AnalyzeResultPreviewScreenIntent.OnToggleOverflowExpanded -> toggleOverflowExpanded()
            is AnalyzeResultPreviewScreenIntent.OnAddOverflowItem -> addOverflowItem(intent.index)
            AnalyzeResultPreviewScreenIntent.OnAddAllOverflowItems -> addAllOverflowItems()
        }
    }

    private fun loadData() {
        val data = AnalyzeResultHolder.get()
        if (data == null) {
            viewModelScope.launch {
                _screenState.update { it.copy(isLoading = false, error = getString(Res.string.error_no_data_available)) }
            }
            return
        }

        targetChecklistId = data.targetChecklistId
        fromActivation = data.fromActivation

        // "Did the AI return a structure?" — drives ONLY whether the "Use folders" toggle is
        // offered. Folders never apply in fill mode. Whether folders are actually USED defaults to
        // OFF (useFolders) — the user opts in. We always keep the structured items so toggling on
        // can render/create the tree without re-fetching.
        val aiReturnedFolders = data.hasFolders && !data.isFillMode

        // Flat editable text list. In folder mode the flat list is the checkable LEAVES only
        // (folders aren't checklist items); for a flat response it's every item. This is the list
        // shown/created on the folders-off path.
        val flatTexts: List<String> = when {
            data.isFillMode && data.fillDefaultItems != null -> data.fillDefaultItems.map { it.text }
            aiReturnedFolders -> data.items.filter { it.type == ChecklistNodeType.ITEM }.map { it.text }
            else -> data.items.map { it.text }
        }

        // Soft 10-item recommendation — flat NEW-checklist path only. We include the first
        // MAX_RECOMMENDED_ITEMS by default and hold the rest in an expandable section (nothing is
        // lost). Skip the split in fill mode: there the editable list is index-aligned with
        // fillDefaultItems, so dropping rows would desync checked/note state.
        val applySoftCap = !data.isFillMode && flatTexts.size > MAX_RECOMMENDED_ITEMS
        val included = if (applySoftCap) flatTexts.take(MAX_RECOMMENDED_ITEMS) else flatTexts
        val overflow = if (applySoftCap) flatTexts.drop(MAX_RECOMMENDED_ITEMS) else emptyList()

        _screenState.update {
            it.copy(
                isLoading = false,
                checklistName = data.suggestedName,
                editableItems = included,
                overflowItems = overflow,
                isOverflowExpanded = false,
                summary = data.summary,
                isFillMode = data.isFillMode,
                fillDefault = data.fillDefault,
                fillDefaultItems = data.fillDefaultItems ?: emptyList(),
                targetChecklistName = data.targetChecklistName,
                aiReturnedFolders = aiReturnedFolders,
                useFolders = false,
                structuredItems = data.items.toList()
            )
        }
    }

    private fun setUseFolders(enabled: Boolean) {
        // Only meaningful when the AI returned a structure; ignore otherwise.
        if (!_screenState.value.aiReturnedFolders) return
        _screenState.update { it.copy(useFolders = enabled) }
    }

    private fun toggleOverflowExpanded() {
        _screenState.update { it.copy(isOverflowExpanded = !it.isOverflowExpanded) }
    }

    private fun addOverflowItem(index: Int) {
        _screenState.update { state ->
            val overflow = state.overflowItems.toMutableList()
            if (index !in overflow.indices) return@update state
            val moved = overflow.removeAt(index)
            state.copy(
                editableItems = state.editableItems + moved,
                overflowItems = overflow,
                // Collapse the section once it empties so the UI doesn't show an empty expander.
                isOverflowExpanded = if (overflow.isEmpty()) false else state.isOverflowExpanded
            )
        }
    }

    private fun addAllOverflowItems() {
        _screenState.update { state ->
            if (state.overflowItems.isEmpty()) return@update state
            state.copy(
                editableItems = state.editableItems + state.overflowItems,
                overflowItems = emptyList(),
                isOverflowExpanded = false
            )
        }
    }

    private fun updateChecklistName(name: String) {
        _screenState.update { it.copy(checklistName = name) }
    }

    private fun removeItem(index: Int) {
        // In folder mode the preview is read-only: editing a flat index would desync the tree
        // (structuredItems). The user restructures after creation.
        if (_screenState.value.useFolders) return
        _screenState.update { state ->
            val newItems = state.editableItems.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            val newFillItems = state.fillDefaultItems.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            state.copy(editableItems = newItems, fillDefaultItems = newFillItems)
        }
    }

    private fun updateNewItemText(text: String) {
        _screenState.update { it.copy(newItemText = text) }
    }

    private fun addItem() {
        // Adding a flat row in folder mode would land it outside the tree; disabled for v1.
        if (_screenState.value.useFolders) return
        val text = _screenState.value.newItemText.trim()
        if (text.isNotEmpty()) {
            _screenState.update { state ->
                val newFillItem = ChecklistFillItem(text = text, checked = false, note = null)
                // New items appear at the TOP of the list
                state.copy(
                    editableItems = listOf(text) + state.editableItems,
                    fillDefaultItems = if (state.isFillMode) {
                        listOf(newFillItem) + state.fillDefaultItems
                    } else {
                        state.fillDefaultItems
                    },
                    newItemText = ""
                )
            }
        }
    }

    private fun createChecklist() {
        val state = _screenState.value

        if (state.fillDefault) {
            applyToDefaultFill()
            return
        }

        viewModelScope.launch {
            if (state.editableItems.isEmpty()) {
                _screenState.update { it.copy(error = getString(Res.string.error_add_at_least_one_item)) }
                return@launch
            }

            if (state.checklistName.isBlank()) {
                _screenState.update { it.copy(error = getString(Res.string.analyze_error_empty_name)) }
                return@launch
            }

            _screenState.update { it.copy(isCreating = true) }

            try {
                if (state.isFillMode && targetChecklistId != null) {
                    // Create a fill for existing checklist using fillDefaultItems
                    // which keep checked states and notes in sync with editableItems
                    val fillItems = state.fillDefaultItems.ifEmpty {
                        state.editableItems.map { text ->
                            ChecklistFillItem(text = text, checked = false, note = null)
                        }
                    }

                    val newFill = ChecklistFill(
                        checklistId = targetChecklistId!!,
                        name = state.checklistName,
                        items = fillItems,
                        createdAt = currentTimeMillis()
                    )

                    val fillId = checklistRepository.addFill(newFill)
                    analyticsTracker.event(AnalyticsEvents.Checklist.FILL_CREATED, mapOf(
                        "source" to "ai",
                        "item_count" to state.editableItems.size
                    ))
                    _screenState.update { it.copy(isCreating = false) }
                    AnalyzeResultHolder.clear()
                    appNavigator.navigateToFillDetail(fillId, clearBackStack = true)
                } else {
                    // Create new checklist. Only when the user opted IN to folders (useFolders)
                    // do we use the structured items verbatim so parentId/type reach the template
                    // and set foldersEnabled; the default fill (created by addChecklist) then gets a
                    // linked row for every node — folders too — so folder reminders/progress
                    // resolve. By DEFAULT (folders off) we create a FLAT checklist from the
                    // included (soft-capped) editable text list — the folder structure is
                    // intentionally flattened away.
                    val checklist = if (state.useFolders) {
                        Checklist(
                            name = state.checklistName,
                            items = state.structuredItems,
                            foldersEnabled = true
                        )
                    } else {
                        Checklist(
                            name = state.checklistName,
                            items = state.editableItems.map { ChecklistItem(text = it, checked = false) }
                        )
                    }

                    val checklistId = checklistRepository.addChecklist(checklist)
                    analyticsTracker.event(AnalyticsEvents.Checklist.CREATED, mapOf(
                        "source" to "ai",
                        "item_count" to checklist.items.size,
                        "has_folders" to state.useFolders
                    ))
                    // New-user activation funnel: when this create came from the hero, hand the
                    // new checklist to the coordinator (it owns the new-user / show-once gating)
                    // so FIRST_AI_CHECKLIST_CREATED + the reminder opt-in still fire — the chat
                    // dispatcher used to be the only create path that did this.
                    if (fromActivation) {
                        val activationEnabled = remoteConfigProvider.getBoolean(
                            RemoteConfigKeys.ACTIVATION_BUNDLE_V1,
                            RemoteConfigDefaults.ACTIVATION_BUNDLE_V1,
                        )
                        activationCoordinator.onAiChecklistCreated(checklistId, activationEnabled)
                    }
                    _screenState.update { it.copy(isCreating = false) }
                    AnalyzeResultHolder.clear()
                    appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
                }
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isCreating = false, error = e.message ?: getString(Res.string.error_create_checklist_failed))
                }
            }
        }
    }

    private fun applyToDefaultFill() {
        viewModelScope.launch {
            _screenState.update { it.copy(isCreating = true) }
            try {
                val checklistId = targetChecklistId ?: run {
                    _screenState.update { it.copy(isCreating = false, error = getString(Res.string.checklist_not_found)) }
                    return@launch
                }

                val defaultFill = checklistRepository
                    .getDefaultFillByChecklistId(checklistId)
                    .first()

                if (defaultFill == null) {
                    _screenState.update { it.copy(isCreating = false, error = getString(Res.string.error_fill_not_found)) }
                    return@launch
                }

                val fillDefaultItems = _screenState.value.fillDefaultItems
                val updatedItems = defaultFill.items.mapIndexed { index, existingItem ->
                    val aiResult = fillDefaultItems.getOrNull(index)
                    if (aiResult != null) {
                        existingItem
                            .withChecked(aiResult.checked)
                            .let { item ->
                                aiResult.note?.let { note -> item.withNote(note) } ?: item
                            }
                    } else {
                        existingItem
                    }
                }

                val updatedFill = defaultFill.copy(items = updatedItems)
                checklistRepository.updateFill(updatedFill)

                analyticsTracker.event(AnalyticsEvents.Checklist.DEFAULT_FILL_UPDATED)

                AnalyzeResultHolder.clear()
                appNavigator.navigateToChecklistDetail(checklistId)
            } catch (e: Exception) {
                _screenState.update { it.copy(isCreating = false, error = e.message) }
            }
        }
    }

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}
