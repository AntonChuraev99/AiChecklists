package com.antonchuraev.homesearchchecklist.feature.home.presentation.picker

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the "Add to existing checklist" picker for the ACTION_PROCESS_TEXT flow.
 *
 * [initialText] is the user's selected text from another app. [purpose] decides what selecting
 * a checklist does:
 * - [AddToChecklistPurpose.ADD_ITEM]: appends the text as ONE item (to both the default fill and
 *   the template, mirroring the detail-screen dual-update so it shows up immediately AND syncs to
 *   Firestore) and opens the detail screen focused on the new item.
 * - [AddToChecklistPurpose.FILL_AI]: opens Analyze in fill-mode for the chosen checklist with the
 *   text pre-filled as raw input — AI fills the template, no item is appended here.
 */
class AddToChecklistPickerViewModel(
    private val initialText: String,
    private val purpose: AddToChecklistPurpose,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val logger: AppLogger,
) : AppViewModel<AddToChecklistPickerState, AddToChecklistPickerIntent, Nothing>() {

    private val _screenState = MutableStateFlow(AddToChecklistPickerState())
    override val screenState: StateFlow<AddToChecklistPickerState> = _screenState.asStateFlow()

    init {
        observeChecklists()
    }

    private fun observeChecklists() {
        viewModelScope.launch {
            checklistRepository.checklists.collect { checklists ->
                _screenState.update { it.copy(isLoading = false, checklists = checklists) }
            }
        }
    }

    override fun onIntent(intent: AddToChecklistPickerIntent) {
        when (intent) {
            AddToChecklistPickerIntent.OnBackClick -> appNavigator.onBack()
            AddToChecklistPickerIntent.OnCreateNewClick ->
                appNavigator.navigateToCreateChecklistScreen(initialText = initialText)
            is AddToChecklistPickerIntent.OnChecklistSelected -> onChecklistSelected(intent.checklist.id)
        }
    }

    private fun onChecklistSelected(checklistId: Long) {
        when (purpose) {
            AddToChecklistPurpose.ADD_ITEM -> addItemAndOpen(checklistId)
            AddToChecklistPurpose.FILL_AI -> openAnalyzeFill(checklistId)
        }
    }

    /**
     * FILL_AI: hand off to Analyze in fill-mode for [checklistId] with [initialText] pre-filled.
     * Does NOT append an item. The picker is popped first so OS/back returns to wherever the
     * process-text action was launched from, not back into this picker.
     */
    private fun openAnalyzeFill(checklistId: Long) {
        appNavigator.onBack()
        appNavigator.navigateToAnalyzeScreen(
            checklistId = checklistId,
            fillDefault = true,
            initialText = initialText,
        )
    }

    private fun addItemAndOpen(checklistId: Long) {
        val text = initialText.trim()
        if (text.isEmpty()) {
            // Guarded earlier (ProcessTextActivity rejects blank), but keep defensive.
            logger.warning(TAG, "Skipping add: blank text for checklist $checklistId")
            appNavigator.navigateToChecklistDetail(checklistId)
            return
        }

        viewModelScope.launch {
            runCatching {
                val checklist = checklistRepository.getChecklistById(checklistId)
                    ?: error("Checklist $checklistId not found")

                // Dual-update (see .claude/rules/checklist-domain.md): the detail screen reads the
                // FILL, the edit screen reads the TEMPLATE — update both so the item is visible
                // immediately and the focusItemId matches a fill item. updateChecklistTemplate (not
                // updateChecklist) keeps the fill IDs stable so newFillItem.id stays valid.
                // Template first so its stable id can be linked into the fill item, keeping the
                // template↔fill pair reconciled without relying on text backfill.
                val newTemplateItem = ChecklistItem(text = text)
                val newFillItem = ChecklistFillItem(
                    text = text,
                    checked = false,
                    note = null,
                    templateItemId = newTemplateItem.id,
                )

                val defaultFill = checklistRepository.getDefaultFillByChecklistId(checklistId).first()
                if (defaultFill != null) {
                    checklistRepository.updateFill(
                        defaultFill.copy(items = defaultFill.items + newFillItem)
                    )
                }
                checklistRepository.updateChecklistTemplate(
                    checklist.copy(items = checklist.items + newTemplateItem)
                )
                newFillItem.id
            }.onSuccess { newItemId ->
                appNavigator.navigateToChecklistDetail(
                    checklistId = checklistId,
                    focusItemId = newItemId,
                    clearBackStack = true,
                )
            }.onFailure { e ->
                logger.error(TAG, "Failed to add shared text to checklist $checklistId: ${e.message}", e)
                // Still open the checklist so the action isn't a silent no-op.
                appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
            }
        }
    }

    private companion object {
        const val TAG = "AddToChecklistPicker"
    }
}
